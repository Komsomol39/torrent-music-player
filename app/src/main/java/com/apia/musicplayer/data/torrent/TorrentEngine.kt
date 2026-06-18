package com.apia.musicplayer.data.torrent

import android.content.Context
import android.util.Log
import com.apia.musicplayer.domain.model.DownloadState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.frostwire.jlibtorrent.*
import com.frostwire.jlibtorrent.alerts.*
import com.frostwire.jlibtorrent.swig.settings_pack
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

    private var session: SessionManager? = null
    private val _downloads = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadState>> = _downloads.asStateFlow()

    val downloadDir: File = File(context.getExternalFilesDir(null), "Music").also { it.mkdirs() }

    fun start() {
        if (session != null) return
        Log.i(TAG, "Starting libtorrent session")

        val settings = SettingsPack().apply {
            setString(settings_pack.string_types.user_agent.swigValue(), "MusicPlayer/1.0")
            setInteger(settings_pack.int_types.active_downloads.swigValue(), 5)
            setInteger(settings_pack.int_types.active_seeds.swigValue(), 5)
            setInteger(settings_pack.int_types.connection_speed.swigValue(), 20)
            setBoolean(settings_pack.bool_types.enable_dht.swigValue(), true)
            setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), true)
            setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), true)
        }

        session = SessionManager().apply {
            addListener(object : AlertListener {
                override fun types() = null  // слушаем все

                override fun alert(alert: Alert<*>) {
                    when (alert.type()) {
                        AlertType.TORRENT_ADDED -> {
                            val a = alert as TorrentAddedAlert
                            Log.d(TAG, "Torrent added: ${a.torrentName()}")
                        }
                        AlertType.BLOCK_FINISHED -> {
                            val a = alert as BlockFinishedAlert
                            val h = a.handle()
                            if (h.isValid) updateProgress(h)
                        }
                        AlertType.TORRENT_FINISHED -> {
                            val a = alert as TorrentFinishedAlert
                            val h = a.handle()
                            val infoHash = h.infoHashes().v1().toString()
                            Log.i(TAG, "Download complete: ${h.name()}")
                            updateState(infoHash) { it.copy(
                                progress = 1f,
                                status = DownloadStatus.COMPLETED,
                                localPath = File(downloadDir, h.name()).absolutePath
                            )}
                        }
                        AlertType.TORRENT_ERROR -> {
                            val a = alert as TorrentErrorAlert
                            Log.e(TAG, "Torrent error: ${a.error()}")
                        }
                        else -> {}
                    }
                }
            })
            start(settings)
        }
        Log.i(TAG, "libtorrent session started, DHT nodes: ${session?.stats()?.dhtNodes()}")
    }

    fun download(magnetLink: String, id: String) {
        val s = session ?: run { start(); session!! }

        val savePath = downloadDir.absolutePath
        val priority = Priority.DEFAULT

        updateState(id) { DownloadState(
            id = id,
            magnetLink = magnetLink,
            status = DownloadStatus.DOWNLOADING,
            progress = 0f
        )}

        try {
            s.download(magnetLink, File(savePath))
            Log.i(TAG, "Started download: $id")
        } catch (e: Exception) {
            Log.e(TAG, "Download error: ${e.message}")
            updateState(id) { it.copy(status = DownloadStatus.ERROR, error = e.message) }
        }
    }

    fun pause(infoHash: String) {
        session?.find(Sha1Hash(infoHash))?.pause()
        updateState(infoHash) { it.copy(status = DownloadStatus.PAUSED) }
    }

    fun resume(infoHash: String) {
        session?.find(Sha1Hash(infoHash))?.resume()
        updateState(infoHash) { it.copy(status = DownloadStatus.DOWNLOADING) }
    }

    fun remove(infoHash: String, deleteFiles: Boolean = false) {
        session?.find(Sha1Hash(infoHash))?.let { handle ->
            session?.remove(handle)
        }
        _downloads.value = _downloads.value - infoHash
    }

    fun stop() {
        session?.stop()
        session = null
    }

    private fun updateProgress(handle: TorrentHandle) {
        val status = handle.status()
        val infoHash = handle.infoHashes().v1().toString()
        val progress = status.progress()
        val downloadRate = status.downloadPayloadRate()
        val uploadRate   = status.uploadPayloadRate()
        val seeds  = status.numSeeds()
        val peers  = status.numPeers()

        updateState(infoHash) { it.copy(
            progress = progress,
            downloadRateBps = downloadRate.toLong(),
            uploadRateBps = uploadRate.toLong(),
            seeds = seeds,
            peers = peers,
            eta = if (downloadRate > 0 && progress < 1f) {
                val remaining = (it.totalBytes * (1f - progress)).toLong()
                remaining / downloadRate
            } else 0L
        )}
    }

    private fun updateState(id: String, transform: (DownloadState) -> DownloadState) {
        val current = _downloads.value[id] ?: DownloadState(id = id)
        _downloads.value = _downloads.value + (id to transform(current))
    }
}
