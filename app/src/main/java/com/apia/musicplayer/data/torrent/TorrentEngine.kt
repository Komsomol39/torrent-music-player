package com.apia.musicplayer.data.torrent

import android.content.Context
import android.util.Log
import com.apia.musicplayer.domain.model.DownloadState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.libtorrent4j.AlertListener
import org.libtorrent4j.SessionManager
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.alerts.TorrentErrorAlert
import org.libtorrent4j.alerts.TorrentFinishedAlert
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TorrentEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "TorrentEngine"
    }

    private val session = SessionManager()
    private val _downloads = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadState>> = _downloads.asStateFlow()
    val downloadDir: File = File(context.getExternalFilesDir(null), "Music").also { it.mkdirs() }
    private var started = false

    fun start() {
        if (started) return
        started = true
        Log.i(TAG, "Starting libtorrent session")

        session.addListener(object : AlertListener {
            override fun types(): IntArray? = null

            override fun alert(alert: Alert<*>) {
                when (alert.type()) {
                    AlertType.TORRENT_FINISHED -> {
                        val a = alert as TorrentFinishedAlert
                        val h = a.handle()
                        if (!h.isValid) return
                        val hash = h.infoHash().toString()
                        val name = h.name  // property, not method in 2.1.x
                        Log.i(TAG, "Download complete: $name")
                        updateState(hash) { it.copy(
                            progress = 1f,
                            status = DownloadStatus.COMPLETED,
                            localPath = File(downloadDir, name).absolutePath
                        )}
                    }
                    AlertType.TORRENT_ERROR -> {
                        val a = alert as TorrentErrorAlert
                        val h = a.handle()
                        if (!h.isValid) return
                        val hash = h.infoHash().toString()
                        val msg = a.error().getMessage()
                        Log.e(TAG, "Torrent error: $msg")
                        updateState(hash) { it.copy(status = DownloadStatus.ERROR, error = msg) }
                    }
                    AlertType.BLOCK_FINISHED,
                    AlertType.PIECE_FINISHED -> updateAllProgress()
                    else -> {}
                }
            }
        })

        session.start()
        Log.i(TAG, "libtorrent session started, DHT: ${session.isDhtRunning}")
    }

    fun download(magnetLink: String, id: String) {
        if (!started) start()

        updateState(id) {
            DownloadState(id = id, magnetLink = magnetLink, status = DownloadStatus.DOWNLOADING)
        }

        try {
            session.download(magnetLink, downloadDir)
            Log.i(TAG, "Started download id=$id")
        } catch (e: Exception) {
            Log.e(TAG, "Download error: ${e.message}")
            updateState(id) { it.copy(status = DownloadStatus.ERROR, error = e.message) }
        }
    }

    fun pause(id: String) {
        findHandle(id)?.pause()
        updateState(id) { it.copy(status = DownloadStatus.PAUSED) }
    }

    fun resume(id: String) {
        findHandle(id)?.resume()
        updateState(id) { it.copy(status = DownloadStatus.DOWNLOADING) }
    }

    fun remove(id: String, deleteFiles: Boolean = false) {
        findHandle(id)?.let { session.remove(it) }
        _downloads.value = _downloads.value - id
    }

    fun stop() {
        session.stop()
        started = false
    }

    private fun findHandle(id: String): TorrentHandle? {
        return try {
            session.find(Sha1Hash.parseHex(id))
        } catch (e: Exception) { null }
    }

    private fun updateAllProgress() {
        for ((id, state) in _downloads.value) {
            if (state.status != DownloadStatus.DOWNLOADING) continue
            val h = findHandle(id) ?: continue
            if (!h.isValid) continue
            val s = h.status()
            updateState(id) { it.copy(
                progress = s.progress(),
                downloadRateBps = s.downloadPayloadRate().toLong(),
                seeds = s.numSeeds(),
                peers = s.numPeers()
            )}
        }
    }

    private fun updateState(id: String, transform: (DownloadState) -> DownloadState) {
        val current = _downloads.value[id] ?: DownloadState(id = id)
        _downloads.value = _downloads.value + (id to transform(current))
    }
}
