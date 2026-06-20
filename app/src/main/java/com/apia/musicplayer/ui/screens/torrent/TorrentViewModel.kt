package com.apia.musicplayer.ui.screens.torrent

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apia.musicplayer.data.search.ArchiveOrgProvider
import com.apia.musicplayer.data.search.SearchAggregator
import com.apia.musicplayer.data.search.SourceResult
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

// Файлы в архивной папке для выбора
data class ArchiveFile(val name: String, val url: String, val size: Long = 0)

@HiltViewModel
class TorrentViewModel @Inject constructor(
    private val aggregator: SearchAggregator,
    private val engine: TorrentEngine,
    private val player: PlayerController,
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

    // Диалог выбора файла из архивной папки
    private val _archiveFiles = MutableStateFlow<List<ArchiveFile>>(emptyList())
    val archiveFiles = _archiveFiles.asStateFlow()
    private val _showArchiveDialog = MutableStateFlow(false)
    val showArchiveDialog = _showArchiveDialog.asStateFlow()
    private val _pendingResult = MutableStateFlow<TorrentResult?>(null)

    val downloads: StateFlow<Map<String, TorrentState>> = engine.torrents
    val enabledSources get() = aggregator.enabledSources

    fun onQueryChange(q: String) { query.value = q }

    fun search() {
        val q = query.value.trim()
        if (q.isBlank()) return
        val sources = aggregator.enabledSources.toSet()
        if (sources.isEmpty()) return

        viewModelScope.launch {
            _isLoading.value = true
            _results.value = emptyList()
            _sourceStatuses.value = sources.associateWith { SourceStatus(loading = true) }

            aggregator.searchAll(q, sources) { sr: SourceResult ->
                if (sr.results.isNotEmpty()) {
                    _results.update { current ->
                        (current + sr.results).sortedByDescending { it.seeders }
                    }
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
                // Для Archive.org — сначала загружаем список файлов
                if (result.source == "Archive.org" || result.source == "ARC") {
                    val files = (aggregator.archive as? ArchiveOrgProvider)
                        ?.getFileList(result) ?: emptyList()
                    if (files.size == 1) {
                        // Один файл — играем сразу
                        playDirectUrl(result, files[0].url)
                    } else if (files.isNotEmpty()) {
                        // Несколько файлов — показываем диалог выбора
                        _pendingResult.value = result
                        _archiveFiles.value = files
                        _showArchiveDialog.value = true
                        _playingId.value = null
                        return@launch
                    } else {
                        // Файлов нет — пробуем getMagnet
                        val url = aggregator.getMagnet(result, source)
                        playDirectUrl(result, url)
                    }
                    return@launch
                }

                val resolvedUrl = aggregator.getMagnet(result, source)
                Log.d("TorrentVM", "Resolved: $resolvedUrl")

                when {
                    // Прямая ссылка — плеер
                    resolvedUrl.startsWith("http") && !resolvedUrl.contains("magnet:?") ->
                        playDirectUrl(result, resolvedUrl)
                    // Magnet — торрент
                    resolvedUrl.contains("magnet:") ->
                        startTorrent(resolvedUrl)
                }
            } catch (e: Exception) {
                Log.e("TorrentVM", "playOrDownload failed: ${e.message}")
            } finally {
                _playingId.value = null
            }
        }
    }

    fun playArchiveFile(file: ArchiveFile) {
        _showArchiveDialog.value = false
        val result = _pendingResult.value ?: return
        viewModelScope.launch {
            playDirectUrl(result, file.url)
        }
    }

    fun dismissArchiveDialog() {
        _showArchiveDialog.value = false
        _archiveFiles.value = emptyList()
        _pendingResult.value = null
    }

    private suspend fun playDirectUrl(result: TorrentResult, url: String) {
        val track = Track(
            id = result.id,
            title = result.title,
            artist = result.artist ?: result.source,
            album = result.album ?: result.source,
            duration = 0L,
            uri = url,
            artworkUri = null
        )
        Log.d("TorrentVM", "Playing: $url")
        player.playTrack(track)
    }

    private fun startTorrent(magnet: String) {
        context.startForegroundService(
            Intent(context, TorrentDownloadService::class.java).apply {
                action = TorrentDownloadService.ACTION_ADD_MAGNET
                putExtra(TorrentDownloadService.EXTRA_MAGNET, magnet)
            }
        )
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
