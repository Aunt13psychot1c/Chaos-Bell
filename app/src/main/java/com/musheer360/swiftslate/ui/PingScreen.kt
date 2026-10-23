package com.musheer360.swiftslate.ui

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import com.musheer360.swiftslate.manager.BellMemory
import com.musheer360.swiftslate.ping.PingScheduler
import com.musheer360.swiftslate.ping.PingStore
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val INTERVALS = listOf(15, 30, 60, 120, 240)
/** Attention Ping choices: every Nth conversation message, 0 = off. */
private val PING_EVERY = listOf(0, 1, 2, 3, 5, 10)

/**
 * Bell's brain: her Soul (who she is), Ping (her own check-ins) and the reminders she's set.
 * Opens full screen from Settings. Everything saves as you go.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PingScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val memory = remember { BellMemory(context) }
    val store = remember { PingStore(context) }

    var soul by remember { mutableStateOf(memory.soul) }
    var enabled by remember { mutableStateOf(store.enabled) }
    var intervalIndex by remember { mutableIntStateOf(INTERVALS.indexOfFirst { it >= store.intervalMinutes }.coerceAtLeast(0)) }
    var startHour by remember { mutableFloatStateOf(store.startHour.toFloat()) }
    var endHour by remember { mutableFloatStateOf(store.endHour.toFloat()) }
    var prompt by remember { mutableStateOf(store.prompt) }
    var refresh by remember { mutableIntStateOf(0) }
    var pingEveryIndex by remember { mutableIntStateOf(PING_EVERY.indexOf(memory.pingEvery).takeIf { it >= 0 } ?: 3) }
    var newKeepsake by remember { mutableStateOf("") }
    var confirmForgetAll by remember { mutableStateOf(false) }
    val heart = remember(refresh) { memory.heart().reversed() }
    val log = remember(refresh) { store.log().reversed() }
    val reminders = remember(refresh) { store.reminders() }

    // The activity log fills in from the background, so keep it fresh while this is open.
    LaunchedEffect(Unit) { while (true) { delay(5_000); refresh++ } }

    Dialog(
        onDismissRequest = onClose,
        // decorFitsSystemWindows = false hands the keyboard's space to safeDrawingPadding, so
        // the box being typed in stays above the keyboard.
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("🧠 Soul & Ping", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)

                Section("💜 Soul") {
                    Hint("Who Bell is and how she acts. She reads this before every answer, in chat, ?talk and Ping.")
                    OutlinedTextField(
                        value = soul,
                        onValueChange = { soul = it.take(BellMemory.MAX_SOUL_CHARS); memory.soul = soul },
                        placeholder = { Text("Example: You're sassy, Southern and kind. You use glitter emoji. You never lecture.") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp)
                    )
                    Hint("${soul.length}/${BellMemory.MAX_SOUL_CHARS}")
                }

                Section("💗 Heart") {
                    Hint("Bell's long-term memory: the things she chose to keep about you for good. Every few messages in a conversation she stops for an attention Ping. She checks she's really listening, then decides if anything is worth keeping.")
                    val every = PING_EVERY[pingEveryIndex]
                    Text(
                        when (every) {
                            0 -> "Attention Ping: off"
                            1 -> "Attention Ping: every message"
                            else -> "Attention Ping: every $every messages"
                        },
                        fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = pingEveryIndex.toFloat(),
                        onValueChange = { pingEveryIndex = it.toInt() },
                        onValueChangeFinished = { memory.pingEvery = PING_EVERY[pingEveryIndex] },
                        valueRange = 0f..(PING_EVERY.size - 1).toFloat(),
                        steps = PING_EVERY.size - 2
                    )
                    if (heart.isEmpty()) Hint("Nothing kept yet. Chat with her and she'll start remembering.")
                    heart.forEach { k ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text(k.text, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text(time(k.time), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { memory.forget(k.time); refresh++ }) { Text("🗑️") }
                        }
                    }
                    OutlinedTextField(
                        value = newKeepsake,
                        onValueChange = { newKeepsake = it.take(com.musheer360.swiftslate.manager.BellMemory.MAX_KEEPSAKE_CHARS) },
                        label = { Text("Tell her something to keep") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { if (memory.keep(newKeepsake)) newKeepsake = ""; refresh++ }) { Text("💗 Keep it") }
                        if (heart.isNotEmpty()) {
                            TextButton(onClick = {
                                if (confirmForgetAll) { memory.forgetHeart(); confirmForgetAll = false; refresh++ }
                                else confirmForgetAll = true
                            }) { Text(if (confirmForgetAll) "Tap again to forget everything" else "Forget all") }
                        }
                    }
                }

                Section("🔔 Check-ins") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Bell checks in by herself", fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                            Hint("She looks over her soul, what she knows about you and your reminders, then either stays quiet or sends you a message.")
                        }
                        Switch(checked = enabled, onCheckedChange = {
                            enabled = it; store.enabled = it; PingScheduler.apply(context)
                        })
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Every ${label(INTERVALS[intervalIndex])}", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    Slider(
                        value = intervalIndex.toFloat(),
                        onValueChange = { intervalIndex = it.toInt() },
                        onValueChangeFinished = { store.intervalMinutes = INTERVALS[intervalIndex]; PingScheduler.apply(context) },
                        valueRange = 0f..(INTERVALS.size - 1).toFloat(),
                        steps = INTERVALS.size - 2
                    )
                    Text("Active hours: ${hour(startHour.toInt())} to ${hour(endHour.toInt())}", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    RangeSlider(
                        value = startHour..endHour,
                        onValueChange = { startHour = it.start; endHour = it.endInclusive },
                        onValueChangeFinished = { store.startHour = startHour.toInt(); store.endHour = endHour.toInt() },
                        valueRange = 0f..24f,
                        steps = 23
                    )
                    Hint("Each check-in uses your AI key once, like one ?talk. Android may run it a few minutes late to save battery.")
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it.take(PingStore.MAX_PROMPT_CHARS); store.prompt = prompt },
                        label = { Text("What a check-in tells Bell") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { store.prompt = ""; prompt = store.prompt }) { Text("Reset to default") }
                    }
                    Button(
                        onClick = { PingScheduler.runNow(context); refresh++ },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) { Text("🔔 Check in now (test it)") }
                }

                Section("📜 What check-ins did") {
                    if (log.isEmpty()) Hint("Nothing yet. Tap Check in now to try it.")
                    log.take(15).forEach { e ->
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text("${e.status.badge} ${time(e.time)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                color = if (e.status == PingStore.Status.FAIL) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(e.text, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                Section("⏰ Reminders") {
                    Hint("Ask Bell in Chat or with ?talk, like \"remind me at 3pm to take my meds\". She'll pop up a notification then.")
                    if (reminders.isEmpty()) Hint("No reminders set.")
                    reminders.forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text(time(r.atMillis), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                Text(r.text, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                            TextButton(onClick = { PingScheduler.cancelReminder(context, r.id); refresh++ }) { Text("🗑️") }
                        }
                    }
                }

                OutlinedButton(
                    onClick = onClose,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) { Text("Close") }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            content()
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun label(minutes: Int) = when {
    minutes < 60 -> "$minutes minutes"
    minutes == 60 -> "hour"
    else -> "${minutes / 60} hours"
}

private fun hour(h: Int) = when (h) {
    0, 24 -> "midnight"
    12 -> "noon"
    in 1..11 -> "$h AM"
    else -> "${h - 12} PM"
}

private fun time(millis: Long) = SimpleDateFormat("EEE MMM d, h:mm a", Locale.US).format(Date(millis))
