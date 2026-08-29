package com.studyfinder.app.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * WorkManager job that fires a local notification when a session is about to
 * start (§8).
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sessionId = inputData.getString("sessionId") ?: return Result.failure()
        val title = inputData.getString("title") ?: "Upcoming Session"
        val body = inputData.getString("body") ?: "Your study session starts soon!"

        NotificationHelper.showSessionReminder(applicationContext, sessionId, title, body)
        return Result.success()
    }

    companion object {
        private const val TAG = "ReminderWorker"

        fun schedule(context: Context, sessionId: String, delayMillis: Long) {
            // Placeholder for scheduling logic
        }

        fun cancel(context: Context, sessionId: String) {
            // Placeholder for cancellation logic
        }
    }
}
