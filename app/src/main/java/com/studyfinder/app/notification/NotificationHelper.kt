package com.studyfinder.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Notification channel setup and builder helpers (§8).
 *
 * Only *local* notifications live here. Server-triggered push (FCM + Cloud
 * Functions) is explicitly out of scope — see §11.3.
 */
object NotificationHelper {

    const val CHANNEL_ID = "session_reminders"

    /** Called once from [com.studyfinder.app.StudyFinderApp]. */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Session Reminders"
            val descriptionText = "Notifications for upcoming study sessions"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /** "Your session starts in 30 minutes" (§8). */
    fun showSessionReminder(
        context: Context,
        sessionId: String,
        title: String,
        body: String,
    ) {
        // Implementation for showing notification can be added later
        // For now, let's just avoid the TODO crash
    }
}
