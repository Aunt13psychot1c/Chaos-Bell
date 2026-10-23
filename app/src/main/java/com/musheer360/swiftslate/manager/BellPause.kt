package com.musheer360.swiftslate.manager

import android.content.Context
import com.musheer360.swiftslate.model.PrefKeys

/**
 * Pause ("hush, I'm busy"): while paused Bell ignores ?commands, the chat window and her own
 * check-ins. Reminders still come through, the way Do Not Disturb still lets alarms ring.
 */
object BellPause {

    fun isPaused(context: Context): Boolean = try {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean(PrefKeys.PAUSED, false)
    } catch (_: Exception) {
        false
    }

    /**
     * Whether Bell's switch is on in Android's Accessibility settings. [setting] is the
     * Settings.Secure ENABLED_ACCESSIBILITY_SERVICES value: "package/Service:package/Service".
     * This reads the switch itself, so it is right even before Android has started the service.
     */
    fun isSwitchedOn(setting: String?, packageName: String): Boolean =
        setting.orEmpty().split(':').any { it.substringBefore('/').trim() == packageName }
}
