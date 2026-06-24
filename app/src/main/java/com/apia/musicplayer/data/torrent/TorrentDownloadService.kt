package com.apia.musicplayer.data.torrent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class TorrentDownloadService : Service() {

    @Inject lateinit var engine: TorrentEngine

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val ACTION_ADD_MAGNET = "com.apia.musicplayer.ADD_MAGNET"
        const val ACTION_PAUSE      = "com.apia.musicplayer.PAUSE"
        const val ACTION_RESUME     = "com.apia.musicplayer.RESUME"
        const val ACTION_REMOVE     = "com.apia.musicplayer.REMOVE"
        const val EXTRA_MAGNET      = "magnet"
        const val EXTRA_HASH        = "hash"
        const val EXTRA_NAME        = "name"
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(1, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ADD_MAGNET -> {
                val magnet = intent.getStringExtra(EXTRA_MAGNET) ?: return START_NOT_STICKY
                val name   = intent.getStringExtra(EXTRA_NAME) ?: ""
                // addMagnet suspend — запускаем в coroutine
                scope.launch { engine.addMagnet(magnet, name) }
            }
            ACTION_PAUSE  -> { val h = intent.getStringExtra(EXTRA_HASH) ?: return START_NOT_STICKY; engine.pause(h) }
            ACTION_RESUME -> { val h = intent.getStringExtra(EXTRA_HASH) ?: return START_NOT_STICKY; engine.resume(h) }
            ACTION_REMOVE -> { val h = intent.getStringExtra(EXTRA_HASH) ?: return START_NOT_STICKY; engine.remove(h) }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val channelId = "torrent_downloads"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(channelId) == null) {
            nm.createNotificationChannel(NotificationChannel(channelId, "Downloads", NotificationManager.IMPORTANCE_LOW))
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Downloading torrents")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .build()
    }
}
