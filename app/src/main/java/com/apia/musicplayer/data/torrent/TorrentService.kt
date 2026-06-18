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
class TorrentService : Service() {

    @Inject lateinit var engine: TorrentEngine

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val CHANNEL_ID = "torrent_downloads"
    private val NOTIF_ID = 2001

    override fun onCreate() {
        super.onCreate()
        createChannel()
        engine.start()
        startForeground(NOTIF_ID, buildNotification("Starting..."))

        // Обновляем уведомление при изменении прогресса
        scope.launch {
            engine.downloads.collectLatest { downloads ->
                val active = downloads.values.filter {
                    it.status == DownloadStatus.DOWNLOADING
                }
                if (active.isEmpty()) {
                    val completed = downloads.values.count { it.status == DownloadStatus.COMPLETED }
                    updateNotification("$completed downloads complete")
                } else {
                    val avgProgress = active.map { it.progress }.average()
                    val totalRate   = active.sumOf { it.downloadRateBps }
                    updateNotification(
                        "${active.size} downloading — ${avgProgress.toInt()}% — ${formatRate(totalRate)}"
                    )
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DOWNLOAD -> {
                val magnet = intent.getStringExtra("magnet") ?: return START_NOT_STICKY
                val id     = intent.getStringExtra("id") ?: magnet.hashCode().toString()
                engine.download(magnet, id)
            }
            ACTION_PAUSE  -> engine.pause(intent.getStringExtra("id") ?: return START_NOT_STICKY)
            ACTION_RESUME -> engine.resume(intent.getStringExtra("id") ?: return START_NOT_STICKY)
            ACTION_STOP   -> stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        engine.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Torrent Downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Music download progress" }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Music Downloads")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    private fun formatRate(bps: Long): String = when {
        bps > 1_048_576 -> "%.1f MB/s".format(bps / 1_048_576.0)
        bps > 1024      -> "%.0f KB/s".format(bps / 1024.0)
        else            -> "$bps B/s"
    }

    companion object {
        const val ACTION_DOWNLOAD = "com.apia.musicplayer.DOWNLOAD"
        const val ACTION_PAUSE    = "com.apia.musicplayer.PAUSE"
        const val ACTION_RESUME   = "com.apia.musicplayer.RESUME"
        const val ACTION_STOP     = "com.apia.musicplayer.STOP"
    }
}
