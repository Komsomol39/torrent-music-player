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
        setupAlertListener()
        val sp = SettingsPack()
        sp.activeTorrents(10)
        sp.activeDownloads(5)
        sp.connectionsLimit(200)
        sessionManager.start(SessionParams(sp))
        sessionManager.addDhtRouter("router.bittorrent.com", 6881)
        sessionManager.addDhtRouter("router.utorrent.com", 6881)
    }

    private fun setupAlertListener() {
        sessionManager.addListener(object : AlertListener {
            override fun types(): IntArray? = null

            override fun alert(alert: Alert<*>) {
                when (alert.type()) {
                    AlertType.TORRENT_ADDED -> {
                        val a = alert as TorrentAddedAlert
                        val h = a.handle()
                        val hash = h.infoHash().toString()
                        _torrents.update { map ->
                            map + (hash to TorrentState(
                                infoHash = hash,
                                name = try { h.status().name() } catch (e: Exception) { "Loading..." },
                                status = TorrentStatus.QUEUED,
                                savePath = downloadDir.absolutePath
                            ))
                        }
                    }

                    AlertType.STATE_UPDATE -> {
                        val a = alert as StateUpdateAlert
                        val updates = mutableMapOf<String, TorrentState>()
                        for (st in a.status()) {
                            val hash = st.infoHash().toString()
                            updates[hash] = TorrentState(
                                infoHash = hash,
                                name = st.name().ifBlank { "Loading..." },
                                progress = st.progress(),
                                downloadSpeed = st.downloadPayloadRate().toLong(),
                                uploadSpeed = st.uploadPayloadRate().toLong(),
                                seeders = st.numSeeds(),
                                peers = st.numPeers(),
                                status = mapState(st.state()),
                                savePath = downloadDir.absolutePath,
                                totalBytes = st.totalWanted(),
                                downloadedBytes = st.totalWantedDone()
                            )
                        }
                        if (updates.isNotEmpty()) {
                            _torrents.update { map -> map + updates }
                        }
                    }

                    AlertType.TORRENT_FINISHED -> {
                        val a = alert as TorrentFinishedAlert
                        val hash = a.handle().infoHash().toString()
                        _torrents.update { map ->
                            val cur = map[hash] ?: return@update map
                            map + (hash to cur.copy(status = TorrentStatus.FINISHED, progress = 1f))
                        }
                    }

                    AlertType.TORRENT_ERROR -> {
                        val a = alert as TorrentErrorAlert
                        val hash = a.handle().infoHash().toString()
                        _torrents.update { map ->
                            val cur = map[hash] ?: return@update map
                            map + (hash to cur.copy(status = TorrentStatus.ERROR, error = a.error().message))
                        }
                    }

                    else -> {}
                }
            }
        })
    }

    fun addMagnet(magnetUri: String): String {
        sessionManager.download(magnetUri, downloadDir)
        return magnetUri
            .substringAfter("xt=urn:btih:", "")
            .substringBefore("&")
            .lowercase()
            .ifBlank { magnetUri.hashCode().toString() }
    }

    fun prioritizeAudioFiles(infoHash: String) {
        val handle = getHandle(infoHash) ?: return
        val info = handle.torrentFile() ?: return
        val audioExts = setOf("mp3", "flac", "m4a", "ogg", "opus", "wav", "aac")
        val priorities = Array(info.numFiles()) { Priority.IGNORE }
        for (i in 0 until info.numFiles()) {
            val name = info.files().fileName(i).lowercase()
            if (audioExts.any { name.endsWith(".$it") }) {
                priorities[i] = Priority.SEVEN
            }
        }
        handle.prioritizeFiles(priorities)
    }

    fun getAudioFiles(infoHash: String): List<File> {
        val handle = getHandle(infoHash) ?: return emptyList()
        val info = handle.torrentFile() ?: return emptyList()
        val audioExts = setOf("mp3", "flac", "m4a", "ogg", "opus", "wav", "aac")
        return (0 until info.numFiles())
            .map { info.files().filePath(it) }
            .filter { path -> audioExts.any { path.lowercase().endsWith(".$it") } }
            .map { File(downloadDir, it) }
    }

    fun pause(infoHash: String) {
        getHandle(infoHash)?.pause()
        _torrents.update { map ->
            val cur = map[infoHash] ?: return@update map
            map + (infoHash to cur.copy(status = TorrentStatus.PAUSED))
        }
    }

    fun resume(infoHash: String) = getHandle(infoHash)?.resume()

    fun remove(infoHash: String, deleteFiles: Boolean = false) {
        val handle = getHandle(infoHash) ?: return
        sessionManager.remove(handle, if (deleteFiles) SessionHandle.DELETE_FILES else 0)
        _torrents.update { it - infoHash }
    }

    fun getTorrentState(infoHash: String): TorrentState? = _torrents.value[infoHash]

    private fun getHandle(infoHash: String): TorrentHandle? =
        sessionManager.handles().firstOrNull { it.infoHash().toString() == infoHash }

    private fun mapState(state: org.libtorrent4j.TorrentStatus.State?): TorrentStatus = when (state) {
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
