package com.musheer360.swiftslate.ui.processtext

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.musheer360.swiftslate.R
import com.musheer360.swiftslate.SwiftSlateApp
import com.musheer360.swiftslate.api.GeminiClient
import com.musheer360.swiftslate.api.OpenAICompatibleClient
import com.musheer360.swiftslate.manager.CommandManager
import com.musheer360.swiftslate.manager.StatsManager
import com.musheer360.swiftslate.model.Command
import com.musheer360.swiftslate.model.CommandType
import com.musheer360.swiftslate.service.CommandOutcome
import com.musheer360.swiftslate.service.runTextCommand
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** One line of the popup's follow-up chat. */
data class ChatTurn(val fromUser: Boolean, val text: String)

sealed interface UiState {
    /**
     * Commands are still being read off disk. The sheet is not shown at all in this state: it
     * used to open at the height of an empty list and then jump taller the moment the commands
     * arrived, which read as a stutter in the middle of the open animation.
     */
    data object Initializing : UiState
    data class CommandList(val commands: List<Command>) : UiState
    data class Loading(val command: Command) : UiState
    /**
     * A finished answer. [result] is always Bell's latest reply (what Copy and Replace use);
     * [chat] holds any follow-up back-and-forth typed into the popup after the first answer.
     */
    data class Preview(
        val result: String,
        val canInsert: Boolean,
        val chat: List<ChatTurn> = emptyList(),
        val thinking: Boolean = false,
        val chatError: String? = null,
        /** What the chat card shows on top; null means [result]. Set when a saved chat is restored. */
        val opener: String? = null
    ) : UiState
    /** [retry] is null for failures that re-running cannot fix (e.g. nothing configured). */
    data class Error(val message: String, val retry: Command? = null) : UiState
}

/**
 * Turns a tapped command into UI state. The request itself is [runTextCommand] — the same
 * function the accessibility service runs for a typed `?trigger`.
 */
class ProcessTextViewModel(
    app: Application,
    private val selection: Selection
) : AndroidViewModel(app) {

    private companion object {
        const val REQUEST_TIMEOUT_MS = 90_000L
        const val TAG = "ProcessTextViewModel"
        /** How much of the conversation is resent with each follow-up, newest kept. */
        const val MAX_CHAT_CONTEXT_CHARS = 6_000
        /** Room for the whole Help manual when Ask Bell sends it along. */
        const val MAX_BACKGROUND_CHARS = 20_000
        const val FOLLOW_UP_PROMPT =
            "You are Chaos Bell, chatting in a small pop-up. The text given to you holds what I " +
            "highlighted, the conversation so far, and my new follow-up. Answer the follow-up " +
            "directly and fully, like you're talking to a smart adult, using the highlighted " +
            "text and the conversation for context. Reply with only your answer."
        const val CHAT_GREETING = "Hey! What's on your mind? 🔔"
    }

    /** The first answer in the popup, kept so follow-ups can carry it as context. */
    private var firstAnswer: String = ""

    // All lazy: each constructor touches SharedPreferences (and, for KeyManager, the
    // Keystore), and this class is built on the main thread. First touch of each happens
    // inside a Dispatchers.IO block.
    // KeyManager is the process-wide one: benched keys have to be shared with the accessibility
    // service, or this flow re-tries keys that one already knows are rate-limited or invalid.
    private val keyManager by lazy { (app as SwiftSlateApp).keyManager }
    private val commandManager by lazy { CommandManager(app) }
    private val statsManager by lazy { StatsManager(app) }
    private val geminiClient by lazy { GeminiClient() }
    private val chatStore by lazy { ChatSessionStore(app) }
    /** Settings chat and Help's Ask Bell keep separate conversations. */
    private val chatKind = if (selection.text.isBlank()) "settings" else "help"
    private val openAIClient by lazy { OpenAICompatibleClient() }

    private val _uiState = MutableStateFlow<UiState>(UiState.Initializing)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Loaded once; the picker returns to this list rather than re-reading it from disk. */
    private var commands: List<Command> = emptyList()

    /**
     * Gated synchronously in [run] before any suspension point — two rapid taps must not both
     * get past it, which a read-then-update on a StateFlow would allow.
     */
    private val inFlight = AtomicBoolean(false)

    init {
        viewModelScope.launch {
            // SharedPreferences is disk-backed, and viewModelScope runs on
            // Dispatchers.Main.immediate — never touch it on the main thread.
            commands = try {
                withContext(Dispatchers.IO) {
                    // Built-ins are the clipboard/undo commands, which need the live field the
                    // accessibility service has and this flow does not. Filtered on isBuiltIn, not
                    // on trigger text: the prefix is user-configurable, so matching "?copy" would
                    // silently stop filtering the moment someone changed it.
                    // Answer-mode built-ins (?talk, ?huh, ?search) only need the selected text,
                    // so they are the exception and stay in the list.
                    commandManager.getCommands().filterNot { it.isBuiltIn && !it.answerMode }
                }
            } catch (e: Exception) {
                // This activity shares the process with the accessibility service — an
                // uncaught exception here would kill the service too (#125). Degrade to an
                // empty list instead.
                Log.w(TAG, "loading commands failed", e)
                emptyList()
            }
            _uiState.value = if (selection.chatMode) {
                // Opened from Settings or Help: skip the command list and go straight to a chat.
                firstAnswer = CHAT_GREETING
                val saved = try {
                    withContext(Dispatchers.IO) { chatStore.load(chatKind) }
                } catch (e: Exception) {
                    Log.w(TAG, "restoring chat failed", e)
                    emptyList()
                }
                UiState.Preview(
                    result = saved.lastOrNull { !it.fromUser }?.text ?: CHAT_GREETING,
                    canInsert = false,
                    chat = saved,
                    opener = CHAT_GREETING
                )
            } else {
                UiState.CommandList(commands)
            }
        }
    }

    fun run(command: Command) {
        if (!inFlight.compareAndSet(false, true)) return

        // A snippet needs no request at all — resolve it without touching the network.
        if (command.type == CommandType.TEXT_REPLACER) {
            inFlight.set(false)
            _uiState.value = UiState.Preview(command.prompt, canInsert = !selection.readOnly)
            viewModelScope.launch {
                try { withContext(Dispatchers.IO) { statsManager.recordUsage(command.trigger) } }
                catch (e: Exception) { Log.w(TAG, "recording usage failed", e) }
            }
            return
        }

        _uiState.value = UiState.Loading(command)
        viewModelScope.launch {
            _uiState.value = try {
                // On IO: KeyManager is Keystore-backed and prefs are disk-backed, both read on
                // whatever dispatcher calls them (the HTTP clients switch to IO themselves).
                val outcome = withTimeout(REQUEST_TIMEOUT_MS) {
                    withContext(Dispatchers.IO) {
                        runTextCommand(
                            getApplication<Application>(), keyManager, geminiClient, openAIClient,
                            command.prompt, selection.text,
                            answerMode = command.answerMode, forceSearch = command.forceSearch,
                            useSoul = command.useSoul
                        )
                    }
                }
                when (outcome) {
                    is CommandOutcome.Success -> {
                        try { withContext(Dispatchers.IO) { statsManager.recordUsage(command.trigger) } }
                        catch (e: Exception) { Log.w(TAG, "recording usage failed", e) }
                        firstAnswer = outcome.text
                        UiState.Preview(outcome.text, canInsert = !selection.readOnly)
                    }
                    is CommandOutcome.Refusal ->
                        UiState.Error(string(R.string.error_safety_blocked))
                    is CommandOutcome.Unavailable -> UiState.Error(outcome.message)
                    is CommandOutcome.Failure -> UiState.Error(outcome.message, retry = command)
                }
            } catch (_: TimeoutCancellationException) {
                UiState.Error(string(R.string.toast_request_timed_out), retry = command)
            } catch (e: Exception) {
                // Same-process safety net — never let an unexpected failure take the service
                // down with this activity (#125).
                Log.w(TAG, "running command failed unexpectedly", e)
                UiState.Error(string(R.string.error_bad_request), retry = command)
            } finally {
                inFlight.set(false)
            }
        }
    }

    /**
     * Sends a follow-up typed into the popup. Each follow-up carries the highlighted text and
     * the conversation so far, so it works the same whichever command produced the first answer
     * (Bell's own short-term memory only records answer-mode commands).
     */
    fun followUp(question: String) {
        val q = question.trim()
        val current = _uiState.value as? UiState.Preview ?: return
        if (q.isEmpty() || current.thinking) return
        if (!inFlight.compareAndSet(false, true)) return

        val chat = current.chat + ChatTurn(fromUser = true, text = q)
        _uiState.value = current.copy(chat = chat, thinking = true, chatError = null)

        val transcript = buildString {
            append("Bell: ").append(firstAnswer).append("\n")
            chat.dropLast(1).forEach { t ->
                append(if (t.fromUser) "Me: " else "Bell: ").append(t.text).append("\n")
            }
        }.takeLast(MAX_CHAT_CONTEXT_CHARS)
        val background = when {
            !selection.chatMode -> "Highlighted text:\n" + selection.text.take(MAX_CHAT_CONTEXT_CHARS) + "\n\n"
            selection.text.isNotBlank() ->
                "Background (use this to answer questions about Chaos Bell):\n" +
                    selection.text.take(MAX_BACKGROUND_CHARS) + "\n\n"
            else -> ""
        }
        val payload = background +
            "Conversation so far:\n" + transcript +
            "\nMy follow-up: " + q

        viewModelScope.launch {
            val next: UiState = try {
                val outcome = withTimeout(REQUEST_TIMEOUT_MS) {
                    withContext(Dispatchers.IO) {
                        runTextCommand(
                            getApplication<Application>(), keyManager, geminiClient, openAIClient,
                            FOLLOW_UP_PROMPT, payload, answerMode = true
                        )
                    }
                }
                val base = _uiState.value as? UiState.Preview ?: current
                when (outcome) {
                    is CommandOutcome.Success -> {
                        val updated = chat + ChatTurn(fromUser = false, text = outcome.text)
                        if (selection.chatMode) {
                            try { withContext(Dispatchers.IO) { chatStore.save(chatKind, updated) } }
                            catch (e: Exception) { Log.w(TAG, "saving chat failed", e) }
                        }
                        base.copy(result = outcome.text, chat = updated, thinking = false)
                    }
                    is CommandOutcome.Refusal ->
                        base.copy(thinking = false, chatError = string(R.string.error_safety_blocked))
                    is CommandOutcome.Unavailable ->
                        base.copy(thinking = false, chatError = outcome.message)
                    is CommandOutcome.Failure ->
                        base.copy(thinking = false, chatError = outcome.message)
                }
            } catch (_: TimeoutCancellationException) {
                (_uiState.value as? UiState.Preview ?: current)
                    .copy(thinking = false, chatError = string(R.string.toast_request_timed_out))
            } catch (e: Exception) {
                Log.w(TAG, "follow-up failed unexpectedly", e)
                (_uiState.value as? UiState.Preview ?: current)
                    .copy(thinking = false, chatError = string(R.string.error_bad_request))
            } finally {
                inFlight.set(false)
            }
            _uiState.value = next
        }
    }

    /** Returns to the picker, e.g. to apply a different command to the same selection. */
    fun backToCommands() {
        if (inFlight.get()) return
        _uiState.value = UiState.CommandList(commands)
    }

    private fun string(resId: Int) = getApplication<Application>().getString(resId)
}
