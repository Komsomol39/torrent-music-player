package com.apia.musicplayer.data.torrent

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.apia.musicplayer.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

@AndroidEntryPoint
class TorrentDownloadService : Service() {

    @Inject lateinit var engine: TorrentEngine

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val CHANNEL_ID = "torrent_download"
    private val NOTIFICATION_ID = 2001

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Starting torrent engine..."))
        observeTorrents()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ADD_MAGNET -> {
                val magnet = intent.getStringExtra(EXTRA_MAGNET) ?: return START_NOT_STICKY
                val name = intent.getStringExtra(EXTRA_NAME) ?: ""
                engine.addMagnet(magnet, name)
            }
            ACTION_PAUSE -> {
                val hash = intent.getStringExtra(EXTRA_HASH) ?: return START_NOT_STICKY
                engine.pause(hash)
            }
            ACTION_RESUME -> {
                val hash = intent.getStringExtra(EXTRA_HASH) ?: return START_NOT_STICKY
                engine.resume(hash)
            }
            ACTION_REMOVE -> {
                val hash = intent.getStringExtra(EXTRA_HASH) ?: return START_NOT_STICKY
                engine.remove(hash)
            }
        }
        return START_STICKY
    }

    private fun observeTorrents() {
        scope.launch {
            engine.torrents.collectLatest { torrents ->
                val downloading = torrents.values.filter {
                    it.status == TorrentStatus.DOWNLOADING
                }
                val notification = if (downloading.isEmpty()) {
                    buildNotification("No active downloads")
                } else {
                    val first = downloading.first()
                    val speedKB = first.downloadSpeed / 1024
                    buildNotification(
                        "${downloading.size} downloading — ${first.name}",
                        "${(first.progress * 100).toInt()}% • ${speedKB}KB/s"
                    )
                }
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun buildNotification(title: String, text: String = ""): Notification {
        val intent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(intent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Torrent Downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Background torrent download progress"
            setShowBadge(false)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        engine.stop()
        super.onDestroy()
    }

    companion object {
        const val ACTION_ADD_MAGNET = "com.apia.musicplayer.ADD_MAGNET"
        const val ACTION_PAUSE      = "com.apia.musicplayer.PAUSE"
        const val ACTION_RESUME     = "com.apia.musicplayer.RESUME"
        const val ACTION_REMOVE     = "com.apia.musicplayer.REMOVE"
        const val EXTRA_MAGNET      = "magnet"
        const val EXTRA_HASH        = "hash"
        const val EXTRA_NAME        = "name"
    }
}
