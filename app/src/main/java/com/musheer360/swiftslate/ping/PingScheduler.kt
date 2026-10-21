package com.musheer360.swiftslate.ping

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.musheer360.swiftslate.MainActivity
import com.musheer360.swiftslate.R
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Starts and stops Ping, sets reminder alarms, and posts what Bell has to say. */
object PingScheduler {

    private const val PING_WORK = "bell_ping"
    private const val PING_NOW_WORK = "bell_ping_now"
    private const val CHANNEL_ID = "bell_ping"
    private const val PING_NOTIFICATION_ID = 7100

    /** Matches WorkManager to the saved settings. Safe to call any time. */
    fun apply(context: Context) {
        val store = PingStore(context)
        val wm = WorkManager.getInstance(context)
        if (!store.enabled) {
            wm.cancelUniqueWork(PING_WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<PingWorker>(store.intervalMinutes.toLong(), TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(PING_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** One Ping right away, ignoring the on/off switch and active hours, for trying it out. */
    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<PingWorker>()
            .setInputData(workDataOf(PingWorker.KEY_FORCE to true))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(PING_NOW_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    fun scheduleReminder(context: Context, found: Reminders.Found) {
        val id = UUID.randomUUID().toString()
        PingStore(context).addReminder(PingStore.Reminder(id, found.atMillis, found.text))
        val delay = (found.atMillis - System.currentTimeMillis()).coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ReminderWorker.KEY_ID to id, ReminderWorker.KEY_TEXT to found.text))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("reminder_$id", ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelReminder(context: Context, id: String) {
        WorkManager.getInstance(context).cancelUniqueWork("reminder_$id")
        PingStore(context).removeReminder(id)
    }

    // Checked in canNotify(); a permission taken away in between is caught below.
    @SuppressLint("MissingPermission")
    fun notify(context: Context, title: String, text: String, id: Int = PING_NOTIFICATION_ID) {
        if (!canNotify(context)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Bell's check-ins and reminders", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) { }
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
