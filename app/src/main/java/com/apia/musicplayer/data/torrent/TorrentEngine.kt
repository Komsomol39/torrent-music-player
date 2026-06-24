package com.apia.musicplayer.data.torrent

import android.content.Context
import android.util.Log
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
    val name: String,           // Название из поискового результата или metadata
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

    // Сохраняем имена которые передал пользователь при добавлении
    private val userNames = mutableMapOf<String, String>()

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
                        val savedName = userNames[hash] ?: "Loading…"
                        val metaName = try { handle.status().name().ifBlank { null } } catch (e: Exception) { null }
                        _torrents.update { map ->
                            map + (hash to TorrentState(
                                infoHash = hash,
                                name = metaName ?: savedName,
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
                        _torrents.update { map ->
                            val cur = map[hash] ?: return@update map
                            map + (hash to cur.copy(status = TorrentStatus.ERROR, error = a.error().message))
                        }
                    }
                    AlertType.BLOCK_DOWNLOADING -> {
                        val a = alert as BlockDownloadingAlert
                        val handle = a.handle()
                        val hash = handle.infoHash().toHex()
                        try {
                            val st = handle.status()
                            // Обновляем имя если metadata пришли
                            val metaName = st.name().ifBlank { null }
                            _torrents.update { map ->
                                val cur = map[hash] ?: return@update map
                                map + (hash to cur.copy(
                                    name = metaName ?: cur.name,
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

    /** Добавить magnet с сохранением имени из поисковика */
    fun addMagnet(magnetUri: String, displayName: String = ""): String {
        sessionManager.download(magnetUri, downloadDir)
        val hash = magnetUri
            .substringAfter("xt=urn:btih:", "")
            .substringBefore("&")
            .lowercase()
            .ifBlank { magnetUri.hashCode().toString() }
        if (displayName.isNotBlank()) {
            userNames[hash] = displayName
            // Сразу создаём запись с именем чтобы UI показал его немедленно
            _torrents.update { map ->
                if (map.containsKey(hash)) map
                else map + (hash to TorrentState(
                    infoHash = hash,
                    name = displayName,
                    status = TorrentStatus.QUEUED
                ))
            }
        }
        Log.d("TorrentEngine", "Added: $displayName ($hash)")
        return hash
    }

    fun pause(infoHash: String) {
        findHandle(infoHash)?.pause()
        _torrents.update { map ->
            val cur = map[infoHash] ?: return@update map
            map + (infoHash to cur.copy(status = TorrentStatus.PAUSED))
        }
    }

    fun resume(infoHash: String) = findHandle(infoHash)?.resume()

    fun remove(infoHash: String) {
        findHandle(infoHash)?.let { sessionManager.remove(it) }
        userNames.remove(infoHash)
        _torrents.update { it - infoHash }
    }

    fun getTorrentState(infoHash: String): TorrentState? = _torrents.value[infoHash]

    private fun findHandle(infoHash: String): TorrentHandle? =
        try { sessionManager.find(Sha1Hash(infoHash)) } catch (e: Exception) { null }

    fun stop() = sessionManager.stop()
}
