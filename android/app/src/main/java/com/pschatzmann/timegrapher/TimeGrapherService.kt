package com.pschatzmann.timegrapher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/** Foreground service (type microphone) that keeps the measurement running while the screen is off. */
class TimeGrapherService : Service() {
    private val engine get() = (application as TimeGrapherApp).engine

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            engine.stop()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Measurement", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(
            this, 1, Intent(this, TimeGrapherService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_clock)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Measuring…")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .build()
        ServiceCompat.startForeground(this, 1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        engine.start()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        engine.stop()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "measure"
        private const val ACTION_STOP = "stop"

        fun start(c: Context) = ContextCompat.startForegroundService(c, Intent(c, TimeGrapherService::class.java))
        fun stop(c: Context) = c.startService(Intent(c, TimeGrapherService::class.java).setAction(ACTION_STOP))
    }
}
