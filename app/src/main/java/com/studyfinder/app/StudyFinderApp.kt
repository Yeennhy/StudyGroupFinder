package com.studyfinder.app

import android.app.Application
import com.cloudinary.android.MediaManager
import com.google.firebase.FirebaseApp
import com.studyfinder.app.notification.NotificationHelper
import com.studyfinder.app.util.CloudinaryConfig

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

        initCloudinary()
    }

    private fun initCloudinary() {
        val config = mapOf(
            "cloud_name" to CloudinaryConfig.CLOUD_NAME,
            "api_key" to CloudinaryConfig.API_KEY,
            "api_secret" to CloudinaryConfig.API_SECRET
        )
        MediaManager.init(this, config)
    }
}
