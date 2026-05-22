package com.otgprinthub.print

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.otgprinthub.R
import com.otgprinthub.domain.model.PrintJob
import com.otgprinthub.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PrintForegroundService : Service() {

    private val CHANNEL_ID = "print_jobs_channel"
    private val NOTIFICATION_ID = 1001

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val jobName = intent?.getStringExtra(EXTRA_JOB_NAME) ?: "Document"
        startForeground(NOTIFICATION_ID, buildPrintingNotification(jobName))
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    fun updateProgress(jobName: String, progress: Int) {
        val notification = buildProgressNotification(jobName, progress)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun showCompleteNotification(jobName: String) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID + 1, buildCompleteNotification(jobName))
        stopSelf()
    }

    fun showFailedNotification(jobName: String, error: String) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID + 2, buildFailedNotification(jobName, error))
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_print),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notif_channel_print_desc)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun getMainActivityIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun buildPrintingNotification(jobName: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_printing))
            .setContentText(jobName)
            .setSmallIcon(android.R.drawable.ic_menu_printer)
            .setContentIntent(getMainActivityIntent())
            .setOngoing(true)
            .build()
    }

    private fun buildProgressNotification(jobName: String, progress: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_printing))
            .setContentText(jobName)
            .setProgress(100, progress, false)
            .setSmallIcon(android.R.drawable.ic_menu_printer)
            .setContentIntent(getMainActivityIntent())
            .setOngoing(true)
            .build()
    }

    private fun buildCompleteNotification(jobName: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_print_complete))
            .setContentText(jobName)
            .setSmallIcon(android.R.drawable.ic_menu_printer)
            .setAutoCancel(true)
            .build()
    }

    private fun buildFailedNotification(jobName: String, error: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_print_failed))
            .setContentText("$jobName: $error")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .build()
    }

    companion object {
        const val EXTRA_JOB_NAME = "job_name"
        const val ACTION_STOP = "com.otgprinthub.STOP_PRINT"
    }
}
