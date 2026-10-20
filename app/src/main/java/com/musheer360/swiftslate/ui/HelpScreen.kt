package com.musheer360.swiftslate.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Bell's built-in manual. Same content as MANUAL.md in the repo, shortened for a phone.
 * Written to pass "the Aaron test": someone who isn't a techie should be able to set Bell up
 * and use her without asking anyone. Plain words, one action per step, exact button names.
 *
 * Shown two ways: automatically once on first launch ([firstRun] = true, opens on setup),
 * and any time from the "?" button on the Dashboard.
 */

const val PREF_HELP_SEEN = "help_seen"
private const val MANUAL_URL = "https://github.com/Aunt13psychot1c/Chaos-Bell/blob/main/MANUAL.md"

private enum class HelpAction(val label: String) {
    ACCESSIBILITY("Open Accessibility settings"),
    APP_INFO("Open Bell's app info"),
}

private class HelpSection(
    val title: String,
    val lines: List<String>,
    val actions: List<HelpAction> = emptyList()
)

private val SECTIONS = listOf(
    HelpSection(
        "🔔 What is Chaos Bell?",
        listOf(
            "A little AI helper that lives inside your phone. You don't open her to use her.",
            "You're texting, posting or writing like normal, and when you want help you call her right there:",
            "• Type a command at the end of your words, like ?fix",
            "• Or highlight some text and send it to her from the pop-up menu"
        )
    ),
    HelpSection(
        "🛠️ Set her up (one time)",
        listOf(
            "1. Get a key. Bell needs an AI \"key\", like a password for an AI service. The easiest free one is Groq: make an account at console.groq.com, tap API Keys, then Create API Key, and copy it right away (it's only shown once).",
            "2. Add the key. Tap the gear (Settings) below and pick your provider. Then tap the lock (Keys), paste the key and tap Add Key.",
            "3. Pick a model. In Settings under Model, pick one. For Groq, openai/gpt-oss-120b is a good start.",
            "4. Turn her on. This is the tricky part, so go in this exact order:",
            "   a. Open Accessibility settings (button below) > Installed apps > Chaos Bell. She'll look greyed out and you'll get a \"Restricted setting\" or \"App was denied access\" message. Tap OK or Close. That \"no\" is on purpose, it unlocks the next step.",
            "   b. Open Bell's app info (button below). Tap the three dots ⋮ in the top corner, then Allow restricted settings, and confirm with your PIN or fingerprint.",
            "   c. Go back to Accessibility > Installed apps > Chaos Bell and turn her on.",
            "5. Keep her awake. In Bell's app info, tap Battery and pick Unrestricted. On Samsung, also turn off \"Remove permissions if app is unused\"."
        ),
        listOf(HelpAction.ACCESSIBILITY, HelpAction.APP_INFO)
    ),
    HelpSection(
        "✍️ Way 1: Type a command",
        listOf(
            "1. Type your message like normal in any app.",
            "2. At the very end, add a command, with no space after it. Example:",
            "   i cant beleive its tuesday?fix",
            "3. Wait a second. It turns into: I can't believe it's Tuesday.",
            "Changed your mind? Type ?undo and your old words come back."
        )
    ),
    HelpSection(
        "✂️ Way 2: Highlight and send",
        listOf(
            "1. Long-press on some words to highlight them.",
            "2. In the little menu that pops up, tap the three dots ⋮ or the arrow for more.",
            "3. Tap Chaos Bell, then pick a command.",
            "4. Tap Replace to swap your text, or Copy to keep her answer.",
            "This works even in apps where typing a command doesn't, and even when Bell is switched off."
        )
    ),
    HelpSection(
        "📝 Her commands",
        listOf(
            "Commands that answer you:",
            "• ?talk  answers your question or does what you asked",
            "• ?huh  explains it like you're smart but new to it",
            "• ?search or ?look  checks the web first, then answers",
            "Commands that rewrite your words:",
            "• ?fix  spelling and grammar",
            "• ?improve  reads smoother",
            "• ?shorten  fewer words",
            "• ?expand  more detail",
            "• ?formal / ?casual  changes the tone",
            "• ?emoji  adds emoji",
            "• ?human  sounds like a real person wrote it",
            "• ?reply  writes a reply to a message",
            "Little tricks:",
            "• ?chaos  surprise me!",
            "• ?undo  puts your old text back",
            "• ?translate:es  translates (swap es for any language code: fr French, it Italian, vi Vietnamese, ar Arabic...)",
            "Make your own on the list icon (Commands) with the + button."
        )
    ),
    HelpSection(
        "📍 Where she works (and doesn't)",
        listOf(
            "Works great in texting apps, WhatsApp, Telegram, Discord, Messenger, email, browsers and most social media.",
            "Typing a command won't work in:",
            "• Password boxes. Android hides them from every helper app to keep your passwords safe.",
            "• Apps with their own special editors, like Samsung Notes, Google Keep and some document apps.",
            "• WeChat, which blocks helper apps on purpose.",
            "In those, use Way 2 (highlight and send). It usually still works.",
            "Some apps, like the Claude app, make their own short highlight menu and skip Bell. Write it somewhere else and paste.",
            "Some banking apps won't open while any helper is on. That's the bank's rule. Turn Bell off for the bank, then back on."
        )
    ),
    HelpSection(
        "✨ Her extras",
        listOf(
            "• About you (Settings): tell her about yourself so her answers fit you. She also remembers your last few back-and-forths for an hour.",
            "• Pause (Dashboard): she ignores every command until you un-pause. Nothing gets sent while paused.",
            "• Colors (Settings): pick your look.",
            "• Backup (Settings): Export saves your custom commands, Import brings them back.",
            "• Updates: Bell tells you when there's a new version. Install it right over this one, nothing gets lost."
        )
    ),
    HelpSection(
        "🛟 Something's wrong",
        listOf(
            "Greyed out in Accessibility? Do setup step 4 in the exact order. You have to get told \"no\" once first.",
            "Typed a command and nothing happened?",
            "• Check the Dashboard says active, not paused.",
            "• The command has to be at the very end, no space after.",
            "• You may be in an app that blocks it. Try highlight and send.",
            "• Your phone may have put her to sleep. See setup step 5.",
            "\"Doesn't have access to this model\" (Groq)? In the Groq console tap your project name, Manage Projects, View, then under Allowed Models tap Edit, check the model and Save.",
            "\"Invalid API key\"? Make a new key and add it again.",
            "\"Rate limited\"? You hit the free limit. Wait a minute, or add a second key."
        )
    ),
    HelpSection(
        "🔒 What Bell can see",
        listOf(
            "• She only pays attention when your text ends with a command. Everything else, she ignores.",
            "• She never sees password boxes.",
            "• When you use a command, your text goes only to the AI service you picked. For answer commands, your About you note and last few exchanges go along too.",
            "• Your keys are locked with your phone's built-in encryption.",
            "• No tracking, no ads, no analytics. The code is public for anyone to check."
        )
    )
)

private fun openSettings(context: Context, action: HelpAction) {
    val intent = when (action) {
        HelpAction.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        HelpAction.APP_INFO -> Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:" + context.packageName)
        )
    }
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // A trimmed-down settings app without that page: nothing useful to fall back to.
    }
}

@Composable
fun HelpScreen(firstRun: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    // On first run the setup section starts open; otherwise everything starts folded.
    var open by remember { mutableStateOf(if (firstRun) setOf(1) else emptySet()) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text(
                    text = if (firstRun) "Welcome to Chaos Bell 🔔" else "Help",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (firstRun)
                        "Here's everything you need, in plain words. Start with \"Set her up.\" You can come back any time with the ? button on the Dashboard."
                    else
                        "Tap a section to open it.",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))

                SECTIONS.forEachIndexed { index, section ->
                    val expanded = index in open
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 32.dp)
                                    .clickable {
                                        open = if (expanded) open - index else open + index
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = section.title,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = if (expanded) "−" else "+",
                                    fontSize = 22.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (expanded) {
                                Spacer(Modifier.height(10.dp))
                                section.lines.forEach { line ->
                                    Text(
                                        text = line,
                                        fontSize = 15.sp,
                                        lineHeight = 21.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                }
                                section.actions.forEach { action ->
                                    OutlinedButton(
                                        onClick = { openSettings(context, action) },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 6.dp)
                                    ) {
                                        Text(action.label)
                                    }
                                }
                            }
                        }
                    }
                }

                TextButton(
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(MANUAL_URL))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (_: ActivityNotFoundException) { }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Read the full manual online")
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onClose,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                ) {
                    Text(if (firstRun) "Got it, let's go!" else "Close")
                }
            }
        }
    }
}
