package com.apia.musicplayer.data.torrent

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.libtorrent4j.AlertListener
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.alerts.AddTorrentAlert
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.alerts.StateUpdateAlert
import org.libtorrent4j.alerts.TorrentErrorAlert
import org.libtorrent4j.alerts.TorrentFinishedAlert
import org.libtorrent4j.swig.settings_pack
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class TorrentState(
    val infoHash: String,
    val name: String,
    val progress: Float = 0f,
    val downloadSpeed: Long = 0L,
    val uploadSpeed: Long = 0L,
    val seeders: Int = 0,
    val peers: Int = 0,
    val status: TorrentStatus = TorrentStatus.QUEUED,
    val savePath: String = "",
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val error: String? = null
)

enum class TorrentStatus { QUEUED, CHECKING, DOWNLOADING, SEEDING, PAUSED, ERROR, FINISHED }

@Singleton
class TorrentEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sessionManager = SessionManager()
    private val _torrents = MutableStateFlow<Map<String, TorrentState>>(emptyMap())
    val torrents: StateFlow<Map<String, TorrentState>> = _torrents.asStateFlow()

    val downloadDir: File = File(context.getExternalFilesDir(null), "Music").also { it.mkdirs() }

    init {
        setupAlerts()
        val sp = SettingsPack().apply {
            setInteger(settings_pack.int_types.active_downloads.swigValue(), 5)
            setInteger(settings_pack.int_types.active_seeds.swigValue(), 3)
            setInteger(settings_pack.int_types.connections_limit.swigValue(), 200)
            setInteger(settings_pack.int_types.upload_rate_limit.swigValue(), 0)
            setInteger(settings_pack.int_types.download_rate_limit.swigValue(), 0)
        }
        sessionManager.start(SessionParams(sp))
        sessionManager.addDhtRouter("router.bittorrent.com", 6881)
        sessionManager.addDhtRouter("router.utorrent.com", 6881)
        sessionManager.addDhtRouter("dht.transmissionbt.com", 6881)
    }

    private fun setupAlerts() {
        sessionManager.addListener(object : AlertListener {
            override fun types(): IntArray? = null

            override fun alert(alert: Alert<*>) {
                when (alert.type()) {
                    AlertType.ADD_TORRENT -> {
                        val a = alert as AddTorrentAlert
                        val h = a.handle()
                        if (!h.isValid) return
                        val hash = h.infoHash().toHex()
                        h.resume()
                        _torrents.update { map ->
                            map + (hash to TorrentState(
                                infoHash = hash,
                                name = h.status().name(),
                                status = TorrentStatus.QUEUED,
                                savePath = h.savePath()
                            ))
                        }
                    }
                    AlertType.STATE_UPDATE -> {
                        val a = alert as StateUpdateAlert
                        val updates = mutableMapOf<String, TorrentState>()
                        for (st in a.status()) {
                            val hash = st.infoHash().toHex()
                            updates[hash] = TorrentState(
                                infoHash = hash,
                                name = st.name(),
                                progress = st.progress(),
                                downloadSpeed = st.downloadPayloadRate().toLong(),
                                uploadSpeed = st.uploadPayloadRate().toLong(),
                                seeders = st.numSeeds(),
                                peers = st.numPeers(),
                                status = mapState(st.state()),
                                savePath = st.savePath(),
                                totalBytes = st.totalWanted(),
                                downloadedBytes = st.totalWantedDone()
                            )
                        }
                        if (updates.isNotEmpty()) _torrents.update { it + updates }
                    }
                    AlertType.TORRENT_FINISHED -> {
                        val h = (alert as TorrentFinishedAlert).handle()
                        val hash = h.infoHash().toHex()
                        _torrents.update { map ->
                            val cur = map[hash] ?: return@update map
                            map + (hash to cur.copy(status = TorrentStatus.FINISHED, progress = 1f))
                        }
                    }
                    AlertType.TORRENT_ERROR -> {
                        val a = alert as TorrentErrorAlert
                        val hash = a.handle().infoHash().toHex()
                        _torrents.update { map ->
                            val cur = map[hash] ?: return@update map
                            map + (hash to cur.copy(status = TorrentStatus.ERROR, error = a.error().message()))
                        }
                    }
                    else -> {}
                }
            }
        })
    }

    fun addMagnet(magnetUri: String): String {
        sessionManager.download(magnetUri, downloadDir)
        return magnetUri.substringAfter("xt=urn:btih:").substringBefore("&").lowercase()
    }

    fun prioritizeAudioFiles(handle: TorrentHandle) {
        val info = handle.torrentFile() ?: return
        val audioExts = setOf("mp3", "flac", "m4a", "ogg", "opus", "wav", "aac")
        val priorities = Array(info.numFiles()) { Priority.IGNORE }
        for (i in 0 until info.numFiles()) {
            val name = info.files().fileName(i).lowercase()
            if (audioExts.any { name.endsWith(".$it") }) priorities[i] = Priority.SEVEN
        }
        handle.prioritizeFiles(priorities)
    }

    fun getAudioFiles(infoHash: String): List<File> {
        val handle = sessionManager.find(org.libtorrent4j.Sha1Hash(infoHash)) ?: return emptyList()
        val info = handle.torrentFile() ?: return emptyList()
        val audioExts = setOf("mp3", "flac", "m4a", "ogg", "opus", "wav", "aac")
        return (0 until info.numFiles())
            .filter { audioExts.any { ext -> info.files().fileName(it).lowercase().endsWith(".$ext") } }
            .map { File(handle.savePath(), info.files().filePath(it)) }
    }

    fun pause(infoHash: String) {
        sessionManager.find(org.libtorrent4j.Sha1Hash(infoHash))?.pause()
        _torrents.update { map ->
            val cur = map[infoHash] ?: return@update map
            map + (infoHash to cur.copy(status = TorrentStatus.PAUSED))
        }
    }

    fun resume(infoHash: String) {
        sessionManager.find(org.libtorrent4j.Sha1Hash(infoHash))?.resume()
    }

    fun remove(infoHash: String, deleteFiles: Boolean = false) {
        val handle = sessionManager.find(org.libtorrent4j.Sha1Hash(infoHash)) ?: return
        sessionManager.remove(handle)
        _torrents.update { it - infoHash }
    }

    fun stop() = sessionManager.stop()

    private fun mapState(state: org.libtorrent4j.TorrentStatus.State): TorrentStatus = when (state) {
        org.libtorrent4j.TorrentStatus.State.CHECKING_FILES,
        org.libtorrent4j.TorrentStatus.State.CHECKING_RESUME_DATA -> TorrentStatus.CHECKING
        org.libtorrent4j.TorrentStatus.State.DOWNLOADING_METADATA,
        org.libtorrent4j.TorrentStatus.State.DOWNLOADING -> TorrentStatus.DOWNLOADING
        org.libtorrent4j.TorrentStatus.State.FINISHED,
        org.libtorrent4j.TorrentStatus.State.SEEDING -> TorrentStatus.SEEDING
        else -> TorrentStatus.QUEUED
    }
}
