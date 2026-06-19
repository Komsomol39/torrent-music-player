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
import org.libtorrent4j.Sha1Hash
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
            setString(
                settings_pack.string_types.dht_bootstrap_nodes.swigValue(),
                "router.bittorrent.com:6881,router.utorrent.com:6881,dht.transmissionbt.com:6881"
            )
            setInteger(settings_pack.int_types.active_downloads.swigValue(), 5)
            setInteger(settings_pack.int_types.active_seeds.swigValue(), 3)
            setInteger(settings_pack.int_types.connections_limit.swigValue(), 200)
            setBoolean(settings_pack.bool_types.enable_dht.swigValue(), true)
            setBoolean(settings_pack.bool_types.enable_lsd.swigValue(), true)
            setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), true)
            setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), true)
        }
        sessionManager.start(SessionParams(sp))
    }

    private fun setupAlerts() {
        sessionManager.addListener(object : AlertListener {
            override fun types(): IntArray? = null

            override fun alert(alert: Alert<*>) {
                when (alert.type()) {
                    AlertType.ADD_TORRENT -> {
                        val h = (alert as AddTorrentAlert).handle()
                        if (!h.isValid) return
                        h.resume()
                        val hash = h.infoHash().toHex()
                        _torrents.update { map ->
                            map + (hash to TorrentState(
                                infoHash = hash,
                                name = safeGetName(h),
                                status = TorrentStatus.QUEUED,
                                savePath = safeGetSavePath(h)
                            ))
                        }
                    }
                    AlertType.STATE_UPDATE -> {
                        // Poll all handles for status
                        val updates = mutableMapOf<String, TorrentState>()
                        for (h in sessionManager.handles) {
                            if (!h.isValid) continue
                            val hash = h.infoHash().toHex()
                            val st = h.status()
                            updates[hash] = TorrentState(
                                infoHash = hash,
                                name = safeGetName(h),
                                progress = st.progress(),
                                downloadSpeed = st.downloadPayloadRate().toLong(),
                                uploadSpeed = st.uploadPayloadRate().toLong(),
                                seeders = st.numSeeds(),
                                peers = st.numPeers(),
                                status = mapState(st.state()),
                                savePath = safeGetSavePath(h),
                                totalBytes = st.totalWanted(),
                                downloadedBytes = st.totalWantedDone()
                            )
                        }
                        if (updates.isNotEmpty()) _torrents.update { it + updates }
                    }
                    AlertType.TORRENT_FINISHED -> {
                        val h = (alert as TorrentFinishedAlert).handle()
                        if (!h.isValid) return
                        val hash = h.infoHash().toHex()
                        prioritizeAudioFiles(hash)
                        _torrents.update { map ->
                            val cur = map[hash] ?: return@update map
                            map + (hash to cur.copy(status = TorrentStatus.FINISHED, progress = 1f))
                        }
                    }
                    AlertType.TORRENT_ERROR -> {
                        val h = (alert as TorrentErrorAlert).handle()
                        if (!h.isValid) return
                        val hash = h.infoHash().toHex()
                        val errMsg = try { (alert as TorrentErrorAlert).error().message() } catch (e: Exception) { "Unknown error" }
                        _torrents.update { map ->
                            val cur = map[hash] ?: return@update map
                            map + (hash to cur.copy(status = TorrentStatus.ERROR, error = errMsg))
                        }
                    }
                    else -> {}
                }
            }
        })
    }

    private fun safeGetName(h: TorrentHandle): String = try {
        h.status().name().ifBlank { h.infoHash().toHex().take(8) }
    } catch (e: Exception) { h.infoHash().toHex().take(8) }

    private fun safeGetSavePath(h: TorrentHandle): String = try {
        h.savePath()
    } catch (e: Exception) { downloadDir.absolutePath }

    fun addMagnet(magnetUri: String): String {
        sessionManager.download(magnetUri, downloadDir)
        return magnetUri.substringAfter("xt=urn:btih:").substringBefore("&").lowercase()
    }

    fun prioritizeAudioFiles(infoHash: String) {
        val handle = sessionManager.find(Sha1Hash(infoHash)) ?: return
        val info = handle.torrentFile() ?: return
        val audioExts = setOf("mp3", "flac", "m4a", "ogg", "opus", "wav", "aac", "wma")
        val priorities = Array(info.numFiles()) { Priority.IGNORE }
        for (i in 0 until info.numFiles()) {
            val name = info.files().fileName(i).lowercase()
            if (audioExts.any { name.endsWith(".$it") }) priorities[i] = Priority.SEVEN
        }
        handle.prioritizeFiles(priorities)
    }

    fun getAudioFiles(infoHash: String): List<File> {
        val handle = sessionManager.find(Sha1Hash(infoHash)) ?: return emptyList()
        val info = handle.torrentFile() ?: return emptyList()
        val audioExts = setOf("mp3", "flac", "m4a", "ogg", "opus", "wav", "aac", "wma")
        return (0 until info.numFiles()).mapNotNull { i ->
            val filePath = info.files().filePath(i)
            if (audioExts.any { filePath.lowercase().endsWith(".$it") })
                File(handle.savePath(), filePath)
            else null
        }
    }

    fun pause(infoHash: String) {
        sessionManager.find(Sha1Hash(infoHash))?.pause()
        _torrents.update { map ->
            val cur = map[infoHash] ?: return@update map
            map + (infoHash to cur.copy(status = TorrentStatus.PAUSED))
        }
    }

    fun resume(infoHash: String) = sessionManager.find(Sha1Hash(infoHash))?.resume()

    fun remove(infoHash: String) {
        sessionManager.find(Sha1Hash(infoHash))?.let { sessionManager.remove(it) }
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
