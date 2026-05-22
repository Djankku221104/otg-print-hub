package com.otgprinthub

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class OtgPrintHubApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val printChannel = NotificationChannel(
            PRINT_CHANNEL_ID,
            "Print Jobs",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifications for active print jobs"
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(printChannel)
    }

    companion object {
        const val PRINT_CHANNEL_ID = "print_jobs_channel"
    }
}
