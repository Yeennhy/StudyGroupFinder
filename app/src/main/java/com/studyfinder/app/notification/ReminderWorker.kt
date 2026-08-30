package com.studyfinder.app.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * WorkManager job that fires a local notification when a session is about to
 * start (§8). One unique work item per session, keyed by `reminder_<id>`, so
 * re-scheduling replaces and leaving cancels cleanly.
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sessionId = inputData.getString(KEY_SESSION_ID) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: "Upcoming session"
        val body = inputData.getString(KEY_BODY) ?: "Your study session starts soon."

        NotificationHelper.showSessionReminder(applicationContext, sessionId, title, body)
        return Result.success()
    }

    companion object {
        private const val KEY_SESSION_ID = "sessionId"
        private const val KEY_TITLE = "title"
        private const val KEY_BODY = "body"

        private fun workName(sessionId: String) = "reminder_$sessionId"

        fun schedule(context: Context, sessionId: String, delayMillis: Long, sessionTitle: String) {
            if (delayMillis <= 0) return
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(
                    workDataOf(
                        KEY_SESSION_ID to sessionId,
                        KEY_TITLE to sessionTitle.ifBlank { "Upcoming session" },
                        KEY_BODY to "Starts in about 15 minutes.",
                    )
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                workName(sessionId), ExistingWorkPolicy.REPLACE, request,
            )
        }

        fun cancel(context: Context, sessionId: String) {
            WorkManager.getInstance(context).cancelUniqueWork(workName(sessionId))
        }
    }
}
