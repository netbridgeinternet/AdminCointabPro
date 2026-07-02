package com.urbancointabpro.admin.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.urbancointabpro.admin.R
import com.urbancointabpro.admin.drive.DriveManager
import com.urbancointabpro.admin.ui.MainActivity
import kotlinx.coroutines.*

class DriveSyncService : Service() {
    companion object {
        private const val TAG = "DriveSyncService"
        private const val CHANNEL_ID = "drive_sync_channel"
        private const val NOTIFICATION_ID = 3001
        const val ACTION_START = "com.urbancointabpro.admin.START_SYNC"
        const val ACTION_STOP = "com.urbancointabpro.admin.STOP_SYNC"
        const val EXTRA_INTERVAL_SECONDS = "interval_seconds"

        // Default: poll every 30 seconds (power-efficient)
        const val DEFAULT_INTERVAL_SECONDS = 30L
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var driveManager: DriveManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isRunning = false
    private var syncInterval = DEFAULT_INTERVAL_SECONDS

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        driveManager = DriveManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSync()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                syncInterval = intent.getLongExtra(EXTRA_INTERVAL_SECONDS, DEFAULT_INTERVAL_SECONDS)
                startSync()
            }
        }
        return START_STICKY
    }

    private fun startSync() {
        if (isRunning) return
        isRunning = true

        // Acquire partial wake lock (CPU stays on, screen can turn off)
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AdminCointabPro::DriveSync")
            .apply { acquire(30 * 60 * 1000L) } // 30 min max

        showNotification("Monitoring for new photos...")

        serviceScope.launch {
            while (isRunning && driveManager?.isInitialized() == true) {
                try {
                    val changes = driveManager?.pollForChanges() ?: 0
                    if (changes > 0) {
                        showNotification("New photo received from kiosk!")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Sync error: ${e.message}")
                }
                delay(syncInterval * 1000)
            }
        }

        Log.i(TAG, "Drive sync started (interval: ${syncInterval}s)")
    }

    private fun stopSync() {
        isRunning = false
        serviceScope.cancel()
        wakeLock?.release()
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "Drive sync stopped")
    }

    private fun showNotification(text: String) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Admin CointabPro")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Drive Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors Google Drive for new photos from kiosk devices"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopSync()
        super.onDestroy()
    }
}
