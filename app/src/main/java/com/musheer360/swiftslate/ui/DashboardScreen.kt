package com.musheer360.swiftslate.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.musheer360.swiftslate.R
import com.musheer360.swiftslate.model.PrefKeys
import com.musheer360.swiftslate.SwiftSlateApp
import com.musheer360.swiftslate.manager.BellPause
import com.musheer360.swiftslate.manager.CommandManager
import com.musheer360.swiftslate.manager.KeyManager
import com.musheer360.swiftslate.manager.StatsManager
import com.musheer360.swiftslate.ui.components.LocalSlateRhythm
import com.musheer360.swiftslate.ui.components.ScreenTitle
import com.musheer360.swiftslate.ui.components.SlateCard
import com.musheer360.swiftslate.ui.components.SlateDivider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

private const val RUNNING_RECHECK_MS = 1_500L

/** Bell's switch in Android's Accessibility settings is on (whether or not she is running yet). */
private fun checkServiceEnabled(context: Context): Boolean {
    val setting = try {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
    } catch (_: Exception) {
        null
    }
    return BellPause.isSwitchedOn(setting, context.packageName)
}

/** Android has actually started Bell's service. FEEDBACK_ALL_MASK so the config's feedback type can't hide her. */
private fun checkServiceRunning(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
        it.resolveInfo.serviceInfo.packageName == context.packageName
    }
}

/**
 * Best-effort read of the framework's hidden `crashed` flag: a service stuck in the "crashed
 * services" limbo still reports as enabled, so [checkServiceEnabled] cannot see it. Returns
 * false whenever the reflection is unavailable — everything here is guarded (#125). Deliberate
 * hidden-API reflection: on API 36+ the read throws, the catch returns false, and the banner
 * simply falls back to the crash-marker pref.
 */
@SuppressLint("SoonBlockedPrivateApi")
private fun isServiceCrashed(context: Context): Boolean {
    return try {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val field = AccessibilityServiceInfo::class.java.getDeclaredField("crashed")
        am.getInstalledAccessibilityServiceList().any {
            try {
                it.resolveInfo.serviceInfo.packageName == context.packageName && field.getBoolean(it)
            } catch (_: Exception) {
                false
            }
        }
    } catch (_: Exception) {
        false
    }
}

/** Timestamp of the last uncaught crash recorded by [SwiftSlateApp], or 0. */
private fun readCrashMarker(context: Context): Long =
    try {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getLong(SwiftSlateApp.PREF_SERVICE_DIED_AT, 0L)
    } catch (_: Exception) {
        0L
    }

private fun clearCrashMarker(context: Context) {
    try {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().remove(SwiftSlateApp.PREF_SERVICE_DIED_AT).apply()
    } catch (_: Exception) {
    }
}

@Composable
fun DashboardScreen(
    keyManager: KeyManager,
    commandManager: CommandManager,
    statsManager: StatsManager,
    onHelp: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var isServiceEnabled by remember { mutableStateOf(checkServiceEnabled(context)) }
    var isServiceRunning by remember { mutableStateOf(checkServiceRunning(context)) }
    val settingsPrefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var paused by remember { mutableStateOf(try { settingsPrefs.getBoolean(PrefKeys.PAUSED, false) } catch (_: Exception) { false }) }
    // Not seeded from keyManager.getKeys(): that decrypts through AndroidKeyStore on the main
    // thread. The LaunchedEffect below fills it in on the IO dispatcher, as it already did on
    // every subsequent resume.
    var keyCount by remember { mutableIntStateOf(0) }
    // Set when the process died unexpectedly (crash marker) or the framework holds the service
    // in the crashed limbo (hidden flag) — the enabled-state check cannot see either.
    var showKilledBanner by remember { mutableStateOf(false) }

    // Stats state
    var monthlyRequests by remember { mutableIntStateOf(statsManager.monthlyRequests) }
    var favoriteCommand by remember { mutableStateOf(statsManager.favoriteCommand) }
    var dailyCounts by remember { mutableStateOf(statsManager.dailyCounts()) }

    val lifecycleOwner = LocalLifecycleOwner.current

    // Watches Bell's own switch. The old AccessibilityStateChangeListener only fired when
    // accessibility as a whole went on or off, so flipping just Bell was never noticed.
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                isServiceEnabled = checkServiceEnabled(context)
                isServiceRunning = checkServiceRunning(context)
            }
        }
        val uri = Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        try { context.contentResolver.registerContentObserver(uri, false, observer) } catch (_: Exception) { }
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    LaunchedEffect(lifecycleOwner) {
        val lifecycle = lifecycleOwner.lifecycle
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val (newEnabled, newKeyCount, killed) = withContext(Dispatchers.IO) {
                Triple(
                    checkServiceEnabled(context),
                    keyManager.getKeys().size,
                    readCrashMarker(context) > 0L || isServiceCrashed(context)
                )
            }
            isServiceEnabled = newEnabled
            isServiceRunning = checkServiceRunning(context)
            keyCount = newKeyCount
            monthlyRequests = statsManager.monthlyRequests
            favoriteCommand = statsManager.favoriteCommand
            dailyCounts = statsManager.dailyCounts()
            showKilledBanner = killed
            // Back from Accessibility settings, Android starts the service a moment after the
            // switch flips, so look again shortly.
            if (newEnabled && !isServiceRunning) {
                delay(RUNNING_RECHECK_MS)
                isServiceRunning = checkServiceRunning(context)
            }
        }
    }

    val noData = stringResource(R.string.dashboard_no_data)
    val rhythm = LocalSlateRhythm.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { }
            .padding(horizontal = rhythm.screenPaddingH, vertical = rhythm.screenPaddingV)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(modifier = Modifier.weight(1f)) {
                ScreenTitle(stringResource(R.string.dashboard_title))
            }
            // Opens Bell's built-in manual (HelpScreen).
            TextButton(onClick = onHelp) {
                Text(
                    text = "?",
                    fontSize = rhythm.titleSize,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Service status + API keys
        SlateCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    !isServiceEnabled || !isServiceRunning -> MaterialTheme.colorScheme.error
                                    paused -> MaterialTheme.colorScheme.tertiaryContainer
                                    else -> MaterialTheme.colorScheme.tertiary
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = when {
                            !isServiceEnabled -> stringResource(R.string.service_status_inactive)
                            paused -> stringResource(R.string.service_status_paused)
                            !isServiceRunning -> stringResource(R.string.service_status_not_running)
                            else -> stringResource(R.string.service_status_active)
                        },
                        fontSize = rhythm.emphasisSize,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (!isServiceEnabled) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.service_enable))
                    }
                } else {
                    // Pause instead of turning the service off: no trip back through Android's
                    // Accessibility settings, and the keys stay saved.
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            paused = !paused
                            settingsPrefs.edit().putBoolean(PrefKeys.PAUSED, paused).apply()
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(if (paused) R.string.service_resume else R.string.service_pause))
                    }
                }
            }
            if (isServiceEnabled && !isServiceRunning) {
                Spacer(modifier = Modifier.height(rhythm.tightGap))
                Text(
                    text = stringResource(R.string.service_not_running_hint),
                    fontSize = rhythm.bodySize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(rhythm.groupGap))
            SlateDivider()
            Spacer(modifier = Modifier.height(rhythm.groupGap))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.dashboard_api_keys_title),
                    fontSize = rhythm.bodySize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.dashboard_keys_configured, keyCount),
                    fontSize = rhythm.emphasisSize,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (keyCount == 0) {
                Text(
                    text = stringResource(R.string.dashboard_add_key_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = rhythm.bodySize,
                    modifier = Modifier.padding(top = rhythm.formGap)
                )
            }
        }

        Spacer(modifier = Modifier.height(rhythm.cardGap))

        // Interrupted-service banner: the toggle can still read "on" while the process is dead.
        if (showKilledBanner) {
            SlateCard {
                Text(
                    text = stringResource(R.string.dashboard_service_killed_title),
                    fontSize = rhythm.emphasisSize,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(rhythm.tightGap))
                Text(
                    text = stringResource(R.string.dashboard_service_killed_message),
                    fontSize = rhythm.bodySize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(rhythm.groupGap))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(rhythm.groupGap)
                ) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            clearCrashMarker(context)
                            showKilledBanner = false
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.service_enable))
                    }
                    TextButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            clearCrashMarker(context)
                            showKilledBanner = false
                        }
                    ) {
                        Text(stringResource(R.string.dashboard_service_killed_dismiss))
                    }
                }
            }
            Spacer(modifier = Modifier.height(rhythm.cardGap))
        }

        // Usage statistics card
        SlateCard(modifier = Modifier.weight(1f)) {
            // Summary row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "$monthlyRequests",
                        fontSize = rhythm.statSize,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.dashboard_monthly_requests),
                        fontSize = rhythm.captionSize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = favoriteCommand ?: noData,
                        fontSize = rhythm.statSize,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.dashboard_favorite_command),
                        fontSize = rhythm.captionSize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(rhythm.groupGap))
            SlateDivider()
            Spacer(modifier = Modifier.height(rhythm.groupGap))

            // 7-day bar chart
            Text(
                text = stringResource(R.string.dashboard_last_7_days),
                fontSize = rhythm.bodySize,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(rhythm.formGap))

            val maxCount = dailyCounts.maxOfOrNull { it.second } ?: 0
            val dayNameFmt = remember { SimpleDateFormat("EEE", Locale.getDefault()) }
            val dateParseFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                dailyCounts.forEach { (dateStr, count) ->
                    val dayLabel = try {
                        val date = dateParseFmt.parse(dateStr)
                        dayNameFmt.format(date!!).take(3)
                    } catch (_: Exception) { "?" }

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (count > 0) "$count" else "",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(rhythm.tightGap))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(if (maxCount > 0) (count.toFloat() / maxCount).coerceAtLeast(if (count > 0) 0.05f else 0f) else 0f)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                        Spacer(modifier = Modifier.height(rhythm.tightGap))
                        Text(
                            text = dayLabel,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
