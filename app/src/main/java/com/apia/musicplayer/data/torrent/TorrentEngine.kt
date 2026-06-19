package com.apia.musicplayer.data.torrent

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.libtorrent4j.*
import org.libtorrent4j.alerts.*
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
    private val handleMap = mutableMapOf<String, TorrentHandle>()

    val downloadDir: File = File(context.getExternalFilesDir(null), "Music").also { it.mkdirs() }

    init {
        setupAlertListener()
        val sp = SettingsPack()
        sp.setInteger(settings_pack.int_types.active_downloads.swigValue(), 5)
        sp.setInteger(settings_pack.int_types.active_seeds.swigValue(), 3)
        sp.setInteger(settings_pack.int_types.active_limit.swigValue(), 10)
        sessionManager.start(SessionParams(sp))
    }

    private fun setupAlertListener() {
        sessionManager.addListener(object : AlertListener {
            override fun types(): IntArray? = null

            override fun alert(alert: Alert<*>) {
                when (alert.type()) {
                    AlertType.ADD_TORRENT -> {
                        val a = alert as AddTorrentAlert
                        val handle = a.handle()
                        handle.resume()
                        val hash = handle.infoHash().toHex()
                        handleMap[hash] = handle
                        _torrents.update { map ->
                            map + (hash to TorrentState(
                                infoHash = hash,
                                name = try { handle.status().name() } catch (e: Exception) { "Loading..." },
                                status = TorrentStatus.QUEUED
                            ))
                        }
                    }

                    AlertType.TORRENT_FINISHED -> {
                        val a = alert as TorrentFinishedAlert
                        val hash = a.handle().infoHash().toHex()
                        _torrents.update { map ->
                            val cur = map[hash] ?: return@update map
                            map + (hash to cur.copy(status = TorrentStatus.FINISHED, progress = 1f))
                        }
                    }

                    AlertType.TORRENT_ERROR -> {
                        val a = alert as TorrentErrorAlert
                        val hash = a.handle().infoHash().toHex()
                        val errMsg = a.error().message   // поле, не метод
                        _torrents.update { map ->
                            val cur = map[hash] ?: return@update map
                            map + (hash to cur.copy(status = TorrentStatus.ERROR, error = errMsg))
                        }
                    }

                    AlertType.BLOCK_DOWNLOADING -> {
                        val a = alert as BlockDownloadingAlert
                        val handle = a.handle()
                        val hash = handle.infoHash().toHex()
                        try {
                            val st = handle.status()
                            _torrents.update { map ->
                                val cur = map[hash] ?: return@update map
                                map + (hash to cur.copy(
                                    progress = st.progress(),
                                    downloadSpeed = st.downloadPayloadRate().toLong(),
                                    uploadSpeed = st.uploadPayloadRate().toLong(),
                                    seeders = st.numSeeds(),
                                    peers = st.numPeers(),
                                    status = TorrentStatus.DOWNLOADING,
                                    totalBytes = st.totalWanted(),
                                    downloadedBytes = st.totalWantedDone()
                                ))
                            }
                        } catch (e: Exception) { /* ignore */ }
                    }

                    else -> {}
                }
            }
        })
    }

    fun addMagnet(magnetUri: String): String {
        // SessionManager.download(magnetUri, saveDir) — правильный вызов
        sessionManager.download(magnetUri, downloadDir)
        return magnetUri
            .substringAfter("xt=urn:btih:", "")
            .substringBefore("&")
            .lowercase()
            .ifBlank { magnetUri.hashCode().toString() }
    }

    fun prioritizeAudioFiles(infoHash: String) {
        val handle = handleMap[infoHash] ?: return
        val info = handle.torrentFile() ?: return
        val audioExts = setOf("mp3", "flac", "m4a", "ogg", "opus", "wav", "aac")
        // Priority.TWO = высокий приоритет (0=ignore, 1=normal, 2..7=высокий)
        val priorities = Array(info.numFiles()) { Priority.IGNORE }
        for (i in 0 until info.numFiles()) {
            val name = info.files().fileName(i).lowercase()
            if (audioExts.any { name.endsWith(".$it") }) {
                priorities[i] = Priority.TWO
            }
        }
        handle.prioritizeFiles(priorities)
    }

    fun getAudioFiles(infoHash: String): List<File> {
        val handle = handleMap[infoHash] ?: return emptyList()
        val info = handle.torrentFile() ?: return emptyList()
        val audioExts = setOf("mp3", "flac", "m4a", "ogg", "opus", "wav", "aac")
        return (0 until info.numFiles())
            .map { info.files().filePath(it) }
            .filter { path -> audioExts.any { path.lowercase().endsWith(".$it") } }
            .map { File(downloadDir, it) }
    }

    fun pause(infoHash: String) {
        handleMap[infoHash]?.pause()
        _torrents.update { map ->
            val cur = map[infoHash] ?: return@update map
            map + (infoHash to cur.copy(status = TorrentStatus.PAUSED))
        }
    }

    fun resume(infoHash: String) = handleMap[infoHash]?.resume()

    fun remove(infoHash: String, deleteFiles: Boolean = false) {
        val handle = handleMap[infoHash] ?: return
        // remove без флага удаления — просто убираем из сессии
        sessionManager.remove(handle)
        handleMap.remove(infoHash)
        _torrents.update { it - infoHash }
    }

    fun getTorrentState(infoHash: String): TorrentState? = _torrents.value[infoHash]

    fun stop() = sessionManager.stop()
}
