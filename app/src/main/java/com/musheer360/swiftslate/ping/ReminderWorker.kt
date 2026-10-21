package com.musheer360.swiftslate.ping

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Fires one reminder Bell set, then takes it off the list. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.success()
        val text = inputData.getString(KEY_TEXT).orEmpty()
        PingScheduler.notify(applicationContext, "⏰ Reminder from Bell", text, id = 7200 + (id.hashCode() and 0xFFFF))
        PingStore(applicationContext).removeReminder(id)
        return Result.success()
    }

    companion object {
        const val KEY_ID = "id"
        const val KEY_TEXT = "text"
    }
}
