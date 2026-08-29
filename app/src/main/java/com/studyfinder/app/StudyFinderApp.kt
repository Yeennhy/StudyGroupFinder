package com.studyfinder.app

import android.app.Application
import com.google.firebase.FirebaseApp
import com.studyfinder.app.notification.NotificationHelper

/**
 * Application class (§5). Wires the ServiceLocator and creates the
 * notification channel the reminder worker posts into (§8).
 */
class StudyFinderApp : Application() {

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        ServiceLocator.init(this)
        NotificationHelper.createChannel(this)
    }
}
