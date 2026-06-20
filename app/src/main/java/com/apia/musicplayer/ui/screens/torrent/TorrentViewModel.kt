package com.apia.musicplayer.ui.screens.torrent

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apia.musicplayer.data.db.TrackDao
import com.apia.musicplayer.data.search.ArchiveOrgProvider
import com.apia.musicplayer.data.search.SearchAggregator
import com.apia.musicplayer.data.search.SourceResult
import com.apia.musicplayer.data.torrent.StreamDownloader
import com.apia.musicplayer.data.torrent.TorrentDownloadService
import com.apia.musicplayer.data.torrent.TorrentEngine
import com.apia.musicplayer.data.torrent.TorrentState
import com.apia.musicplayer.domain.model.SearchSource
import com.apia.musicplayer.domain.model.TorrentResult
import com.apia.musicplayer.domain.model.Track
import com.apia.musicplayer.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SourceStatus(
    val loading: Boolean = false,
    val resultCount: Int = 0,
    val error: String? = null,
    val durationMs: Long = 0
)

data class ArchiveFile(val name: String, val url: String, val size: Long = 0)

@HiltViewModel
class TorrentViewModel @Inject constructor(
    private val aggregator: SearchAggregator,
    private val engine: TorrentEngine,
    private val player: PlayerController,
    private val downloader: StreamDownloader,
    private val trackDao: TrackDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val query = MutableStateFlow("")
    private val _results = MutableStateFlow<List<TorrentResult>>(emptyList())
    val results = _results.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _sourceStatuses = MutableStateFlow<Map<SearchSource, SourceStatus>>(emptyMap())
    val sourceStatuses = _sourceStatuses.asStateFlow()
    private val _playingId = MutableStateFlow<String?>(null)
    val playingId = _playingId.asStateFlow()
    private val _toast = MutableStateFlow<String?>(null)
    val toast = _toast.asStateFlow()

    // Archive file picker
    private val _archiveFiles = MutableStateFlow<List<ArchiveFile>>(emptyList())
    val archiveFiles = _archiveFiles.asStateFlow()
    private val _showArchiveDialog = MutableStateFlow(false)
    val showArchiveDialog = _showArchiveDialog.asStateFlow()
    private val _pendingResult = MutableStateFlow<TorrentResult?>(null)

    val downloads: StateFlow<Map<String, TorrentState>> = engine.torrents
    val enabledSources get() = aggregator.enabledSources

    fun onQueryChange(q: String) { query.value = q }
    fun clearToast() { _toast.value = null }

    fun search() {
        val q = query.value.trim()
        if (q.isBlank()) return
        val sources = aggregator.enabledSources.toSet()
        if (sources.isEmpty()) { _toast.value = "Enable sources in Settings first"; return }

        viewModelScope.launch {
            _isLoading.value = true
            _results.value = emptyList()
            _sourceStatuses.value = sources.associateWith { SourceStatus(loading = true) }

            aggregator.searchAll(q, sources) { sr: SourceResult ->
                if (sr.results.isNotEmpty()) {
                    _results.update { cur -> (cur + sr.results).sortedByDescending { it.seeders } }
                }
                _sourceStatuses.update { map ->
                    map + (sr.source to SourceStatus(
                        loading = false,
                        resultCount = sr.results.size,
                        error = sr.error,
                        durationMs = sr.durationMs
                    ))
                }
            }
            _isLoading.value = false
        }
    }

    fun playOrDownload(result: TorrentResult) {
        viewModelScope.launch {
            _playingId.value = result.id
            val source = findSource(result)
            try {
                // Archive.org — файловый пикер
                if (result.source.contains("Archive", ignoreCase = true)) {
                    val files = (aggregator.archive as? ArchiveOrgProvider)
                        ?.getFileList(result) ?: emptyList()
                    when {
                        files.size == 1 -> playAndSave(result.copy(title = files[0].name.substringBeforeLast(".")), files[0].url)
                        files.isNotEmpty() -> {
                            _pendingResult.value = result
                            _archiveFiles.value = files
                            _showArchiveDialog.value = true
                            _playingId.value = null
                            return@launch
                        }
                        else -> _toast.value = "No audio files found"
                    }
                    return@launch
                }

                val resolved = try {
                    aggregator.getMagnet(result, source)
                } catch (e: Exception) {
                    _toast.value = "Resolve error: ${e.message?.take(60)}"
                    return@launch
                }

                when {
                    // HTTP стрим — играем И скачиваем
                    resolved.startsWith("http") && !resolved.contains("magnet:?") ->
                        playAndSave(result, resolved)
                    // Magnet — торрент
                    resolved.contains("magnet:") ->
                        startTorrent(resolved, result.title)
                    else ->
                        _toast.value = "Cannot play this result"
                }
            } catch (e: Exception) {
                Log.e("TorrentVM", "playOrDownload: ${e.message}")
                _toast.value = "Error: ${e.message?.take(80)}"
            } finally {
                _playingId.value = null
            }
        }
    }

    /** Играем стрим И параллельно скачиваем его на устройство */
    private suspend fun playAndSave(result: TorrentResult, url: String) {
        Log.d("TorrentVM", "Play+Save: $url")

        // 1. Сразу начинаем воспроизведение
        player.playTrack(Track(
            id = result.id,
            title = result.title,
            artist = result.artist ?: result.source,
            album  = result.album  ?: result.source,
            duration = 0L,
            uri = url
        ))
        _toast.value = "▶ Playing: ${result.title.take(35)}"

        // 2. Параллельно скачиваем файл
        val fileName = buildString {
            result.artist?.let { append(it); append(" - ") }
            append(result.title.take(60))
        }
        val file = downloader.downloadAsync(url, fileName)
        if (file != null) {
            // 3. Добавляем в библиотеку
            trackDao.upsertTrack(Track(
                id       = "dl_${file.absolutePath.hashCode()}",
                title    = result.title,
                artist   = result.artist ?: "Unknown",
                album    = result.album  ?: result.source,
                duration = 0L,
                uri      = file.toURI().toString()
            ))
            _toast.value = "✓ Saved to library: ${file.name.take(40)}"
        }
    }

    fun playArchiveFile(file: ArchiveFile) {
        _showArchiveDialog.value = false
        val result = _pendingResult.value ?: return
        viewModelScope.launch {
            playAndSave(result.copy(title = file.name.substringBeforeLast(".")), file.url)
        }
    }

    fun dismissArchiveDialog() {
        _showArchiveDialog.value = false
        _archiveFiles.value = emptyList()
        _pendingResult.value = null
    }

    private fun startTorrent(magnet: String, name: String) {
        context.startForegroundService(
            Intent(context, TorrentDownloadService::class.java).apply {
                action = TorrentDownloadService.ACTION_ADD_MAGNET
                putExtra(TorrentDownloadService.EXTRA_MAGNET, magnet)
            }
        )
        _toast.value = "⬇ Download started: ${name.take(40)}"
    }

    private fun findSource(result: TorrentResult): SearchSource =
        SearchSource.entries.find {
            it.name.equals(result.source, ignoreCase = true) ||
            it.meta.displayName.equals(result.source, ignoreCase = true) ||
            result.source.contains(it.name, ignoreCase = true)
        } ?: SearchSource.ARCHIVE

    fun pause(infoHash: String) = context.startService(
        Intent(context, TorrentDownloadService::class.java).apply {
            action = TorrentDownloadService.ACTION_PAUSE
            putExtra(TorrentDownloadService.EXTRA_HASH, infoHash)
        })

    fun resume(infoHash: String) = context.startService(
        Intent(context, TorrentDownloadService::class.java).apply {
            action = TorrentDownloadService.ACTION_RESUME
            putExtra(TorrentDownloadService.EXTRA_HASH, infoHash)
        })

    fun remove(infoHash: String) = context.startService(
        Intent(context, TorrentDownloadService::class.java).apply {
            action = TorrentDownloadService.ACTION_REMOVE
            putExtra(TorrentDownloadService.EXTRA_HASH, infoHash)
        })
}
