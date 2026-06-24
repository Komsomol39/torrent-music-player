package com.apia.musicplayer.data.torrent

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.libtorrent4j.*
import org.libtorrent4j.alerts.*
import org.libtorrent4j.swig.settings_pack
import org.libtorrent4j.swig.torrent_flags_t
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
    private val userNames = mutableMapOf<String, String>()
    // Держим handles сами — sessionManager.handles() не существует в libtorrent4j
    private val handleMap = mutableMapOf<String, TorrentHandle>()

    val downloadDir: File = File(context.getExternalFilesDir(null), "Music").also { it.mkdirs() }

    init {
        setupAlerts()
        val sp = SettingsPack()
        sp.setInteger(settings_pack.int_types.active_downloads.swigValue(), 5)
        sp.setInteger(settings_pack.int_types.active_seeds.swigValue(), 3)
        sp.setInteger(settings_pack.int_types.active_limit.swigValue(), 10)
        sessionManager.start(SessionParams(sp))
    }

    private fun setupAlerts() {
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
                        val savedName = userNames[hash]
                        val metaName = try { handle.status().name().ifBlank { null } } catch (e: Exception) { null }
                        _torrents.update { map ->
                            map + (hash to TorrentState(
                                infoHash = hash,
                                name = metaName ?: savedName ?: "Loading...",
                                status = TorrentStatus.QUEUED
                            ))
                        }
                        Log.d("TorrentEngine", "Added: hash=$hash name=${metaName ?: savedName}")
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

    /**
     * Добавить magnet ссылку.
     * Используем fetchMagnet -> bdecode -> download — как в TorrentStream-Android.
     * Вызывать из IO dispatcher (блокирующий вызов до 30 сек).
     */
    suspend fun addMagnet(magnetUri: String, displayName: String = ""): String =
        withContext(Dispatchers.IO) {
            val hash = magnetUri
                .substringAfter("xt=urn:btih:", "")
                .substringBefore("&")
                .lowercase()
                .ifBlank { magnetUri.hashCode().toString() }

            // Сохраняем имя сразу чтобы UI показал его
            if (displayName.isNotBlank()) {
                userNames[hash] = displayName
                _torrents.update { map ->
                    if (map.containsKey(hash)) map
                    else map + (hash to TorrentState(
                        infoHash = hash, name = displayName, status = TorrentStatus.QUEUED
                    ))
                }
            }

            try {
                // Шаг 1: получаем metadata (до 30 сек)
                Log.d("TorrentEngine", "fetchMagnet: $magnetUri")
                val data = sessionManager.fetchMagnet(magnetUri, 30, downloadDir)
                if (data != null) {
                    // Шаг 2: парсим TorrentInfo
                    val ti = TorrentInfo.bdecode(data)
                    val realHash = ti.infoHash().toHex()
                    if (displayName.isNotBlank()) userNames[realHash] = displayName
                    // Шаг 3: загружаем (игнорируем все файлы кроме аудио)
                    val priorities = Array(ti.numFiles()) { Priority.IGNORE }
                    val audioExts = setOf("mp3","flac","m4a","ogg","opus","wav","aac")
                    for (i in 0 until ti.numFiles()) {
                        val name = ti.files().fileName(i).lowercase()
                        if (audioExts.any { name.endsWith(".$it") }) {
                            priorities[i] = Priority.TWO
                        }
                    }
                    sessionManager.download(ti, downloadDir, null, priorities, null, torrent_flags_t.from_int(0))
                    Log.d("TorrentEngine", "download started: ${ti.name()}")
                    realHash
                } else {
                    Log.w("TorrentEngine", "fetchMagnet returned null for $magnetUri")
                    hash
                }
            } catch (e: Exception) {
                Log.e("TorrentEngine", "addMagnet error: ${e.message}")
                _torrents.update { it + (hash to TorrentState(
                    infoHash = hash, name = displayName.ifBlank { "Error" },
                    status = TorrentStatus.ERROR, error = e.message
                )) }
                hash
            }
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
        handleMap.remove(infoHash)
        userNames.remove(infoHash)
        _torrents.update { it - infoHash }
    }

    private fun findHandle(infoHash: String): TorrentHandle? = handleMap[infoHash]

    fun stop() = sessionManager.stop()
}
