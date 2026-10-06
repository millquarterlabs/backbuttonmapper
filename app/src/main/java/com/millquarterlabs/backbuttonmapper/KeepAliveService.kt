package com.millquarterlabs.backbuttonmapper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Keeps the app's process in the foreground so the watch doesn't freeze it.
 *
 * On the Galaxy Watch Ultra 2 the process was frozen in the background: key events and window
 * events reached BackButtonService up to 31 s late, all at once, so a hold never opened Wallet.
 * A foreground service keeps the process out of the cached state that gets frozen. Without the
 * notification permission the notification itself isn't shown.
 */
class KeepAliveService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.keep_alive_channel),
                NotificationManager.IMPORTANCE_MIN)
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: RuntimeException) {
            // Never crash the process (it also hosts the accessibility service) over this.
            EventLog.add("keep-alive failed: ${e.javaClass.simpleName}")
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "keep_alive"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, KeepAliveService::class.java))
            } catch (e: RuntimeException) {
                // Android can refuse a foreground start from the background; opening the app
                // starts it again.
                EventLog.add("keep-alive failed: ${e.javaClass.simpleName}")
            }
        }
    }
}
