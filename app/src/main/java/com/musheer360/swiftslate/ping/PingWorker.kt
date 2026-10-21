package com.musheer360.swiftslate.ping

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.musheer360.swiftslate.api.GeminiClient
import com.musheer360.swiftslate.api.OpenAICompatibleClient
import com.musheer360.swiftslate.manager.KeyManager
import com.musheer360.swiftslate.service.CommandOutcome
import com.musheer360.swiftslate.service.runTextCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Ping: Bell's own check-in. On each run (inside the active hours) she reads her soul, what she
 * knows about the user and her reminders, then either stays quiet (PING_OK) or sends one short
 * message as a notification. Every run lands in the activity log in plain words.
 */
class PingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext
        val store = PingStore(ctx)
        val now = System.currentTimeMillis()
        val forced = inputData.getBoolean(KEY_FORCE, false)

        if (!forced) {
            if (!store.enabled) return@withContext Result.success()
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (!PingStore.isActive(hour, store.startHour, store.endHour)) return@withContext Result.success()
        }

        val upcoming = store.reminders().filter { it.atMillis > now }
        val recentlySaid = store.log().filter { it.status == PingStore.Status.SAID }.takeLast(3)
        val details = buildString {
            append("Current local time: ").append(Reminders.format(now)).append('\n')
            if (upcoming.isEmpty()) append("Reminders: none.\n")
            else {
                append("Reminders:\n")
                upcoming.take(10).forEach { append("- ").append(Reminders.format(it.atMillis)).append(": ").append(it.text).append('\n') }
            }
            if (recentlySaid.isNotEmpty()) {
                append("Messages you already sent recently (don't repeat them):\n")
                recentlySaid.forEach { append("- ").append(it.text).append('\n') }
            }
        }

        val outcome = try {
            runTextCommand(
                context = ctx,
                keyManager = KeyManager(ctx),
                geminiClient = GeminiClient(),
                openAIClient = OpenAICompatibleClient(),
                prompt = store.prompt,
                text = details,
                answerMode = true,
                remember = false,
                allowWebSearch = false
            )
        } catch (e: Exception) {
            CommandOutcome.Failure(e.message ?: "Something went wrong")
        }

        when (outcome) {
            is CommandOutcome.Success -> {
                val reply = outcome.text.trim()
                if (reply.isEmpty() || reply.contains(PingStore.OK_WORD)) {
                    store.addLog(PingStore.Entry(now, PingStore.Status.OK, "All good, nothing to say."))
                } else {
                    PingScheduler.notify(ctx, "🔔 Bell", reply)
                    store.addLog(PingStore.Entry(now, PingStore.Status.SAID, reply))
                }
            }
            is CommandOutcome.Refusal ->
                store.addLog(PingStore.Entry(now, PingStore.Status.FAIL, "The AI refused to answer this check-in."))
            is CommandOutcome.Unavailable ->
                store.addLog(PingStore.Entry(now, PingStore.Status.SKIP, outcome.message))
            is CommandOutcome.Failure ->
                store.addLog(PingStore.Entry(now, PingStore.Status.FAIL, outcome.message))
        }
        Result.success()
    }

    companion object {
        const val KEY_FORCE = "force"
    }
}
