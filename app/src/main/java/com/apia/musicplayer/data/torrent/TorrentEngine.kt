package com.apia.musicplayer.data.torrent

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.libtorrent4j.*
import org.libtorrent4j.alerts.*
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

enum class TorrentStatus {
    QUEUED, CHECKING, DOWNLOADING, SEEDING, PAUSED, ERROR, FINISHED
}

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
            activeTorrents(10)
            activeDownloads(5)
            activeSeeds(3)
            connectionsLimit(200)
            uploadRateLimit(0)
            downloadRateLimit(0)
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
                    AlertType.TORRENT_ADDED -> {
                        val h = (alert as TorrentAddedAlert).handle()
                        if (!h.isValid) return
                        val hash = h.infoHash().toHex()
                        val st = h.status()
                        _torrents.update { map ->
                            map + (hash to TorrentState(
                                infoHash = hash,
                                name = st.name(),
                                status = TorrentStatus.QUEUED,
                                savePath = st.savePath()
                            ))
                        }
                    }
                    AlertType.STATE_UPDATE -> {
                        val updates = mutableMapOf<String, TorrentState>()
                        for (st in (alert as StateUpdateAlert).status()) {
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
                        val hash = (alert as TorrentFinishedAlert).handle().infoHash().toHex()
                        _torrents.update { map ->
                            val cur = map[hash] ?: return@update map
                            map + (hash to cur.copy(status = TorrentStatus.FINISHED, progress = 1f))
                        }
                    }
                    AlertType.TORRENT_ERROR -> {
                        val a = alert as TorrentErrorAlert
                        val hash = a.handle().infoHash().toHex()
                        val errMsg = a.error().message() // String, не функция-вызов
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

    fun addMagnet(magnetUri: String): String {
        sessionManager.download(magnetUri, downloadDir)
        // Извлекаем hash из magnet URI для отслеживания
        return magnetUri
            .substringAfter("xt=urn:btih:", "")
            .substringBefore("&")
            .lowercase()
            .ifEmpty { magnetUri.hashCode().toString() }
    }

    fun prioritizeAudioFiles(infoHash: String) {
        val handle = findHandle(infoHash) ?: return
        val info = handle.torrentFile() ?: return
        val audioExts = setOf("mp3", "flac", "m4a", "ogg", "opus", "wav", "aac", "wma")
        val count = info.numFiles()
        val priorities = Array(count) { Priority.IGNORE }
        for (i in 0 until count) {
            val name = info.files().fileName(i).lowercase()
            if (audioExts.any { name.endsWith(".$it") }) {
                priorities[i] = Priority.SIX // highest available = SEVEN is not in all versions
            }
        }
        handle.prioritizeFiles(priorities)
    }

    fun getAudioFiles(infoHash: String): List<File> {
        val handle = findHandle(infoHash) ?: return emptyList()
        val info = handle.torrentFile() ?: return emptyList()
        val audioExts = setOf("mp3", "flac", "m4a", "ogg", "opus", "wav", "aac", "wma")
        val savePath = File(handle.savePath())
        val result = mutableListOf<File>()
        for (i in 0 until info.numFiles()) {
            val path = info.files().filePath(i)
            if (audioExts.any { path.lowercase().endsWith(".$it") }) {
                result += File(savePath, path)
            }
        }
        return result
    }

    fun pause(infoHash: String) {
        findHandle(infoHash)?.pause()
        _torrents.update { map ->
            val cur = map[infoHash] ?: return@update map
            map + (infoHash to cur.copy(status = TorrentStatus.PAUSED))
        }
    }

    fun resume(infoHash: String) = findHandle(infoHash)?.resume()

    fun remove(infoHash: String, deleteFiles: Boolean = false) {
        val handle = findHandle(infoHash) ?: return
        if (deleteFiles) sessionManager.remove(handle, SessionHandle.DELETE_FILES)
        else sessionManager.remove(handle)
        _torrents.update { it - infoHash }
    }

    fun getTorrentState(infoHash: String): TorrentState? = _torrents.value[infoHash]

    // find() принимает Sha1Hash объект
    private fun findHandle(infoHash: String): TorrentHandle? =
        try { sessionManager.find(Sha1Hash(infoHash)) } catch (e: Exception) { null }

    private fun mapState(state: org.libtorrent4j.TorrentStatus.State): TorrentStatus = when (state) {
        org.libtorrent4j.TorrentStatus.State.CHECKING_FILES,
        org.libtorrent4j.TorrentStatus.State.CHECKING_RESUME_DATA -> TorrentStatus.CHECKING
        org.libtorrent4j.TorrentStatus.State.DOWNLOADING_METADATA,
        org.libtorrent4j.TorrentStatus.State.DOWNLOADING -> TorrentStatus.DOWNLOADING
        org.libtorrent4j.TorrentStatus.State.FINISHED,
        org.libtorrent4j.TorrentStatus.State.SEEDING -> TorrentStatus.SEEDING
        else -> TorrentStatus.QUEUED
    }

    fun stop() = sessionManager.stop()
}
