package com.apia.musicplayer.ui.screens.torrent

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apia.musicplayer.data.search.SearchAggregator
import com.apia.musicplayer.data.search.SourceResult
import com.apia.musicplayer.data.torrent.TorrentDownloadService
import com.apia.musicplayer.data.torrent.TorrentEngine
import com.apia.musicplayer.data.torrent.TorrentState
import com.apia.musicplayer.domain.model.SearchSource
import com.apia.musicplayer.domain.model.TorrentResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SourceStatus(
    val loading: Boolean = false,
    val resultCount: Int = 0,
    val error: String? = null
)

@HiltViewModel
class TorrentViewModel @Inject constructor(
    private val aggregator: SearchAggregator,
    private val engine: TorrentEngine,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val query = MutableStateFlow("")
    private val _results = MutableStateFlow<List<TorrentResult>>(emptyList())
    val results = _results.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _sourceStatuses = MutableStateFlow<Map<SearchSource, SourceStatus>>(emptyMap())
    val sourceStatuses = _sourceStatuses.asStateFlow()

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
            // Инициализируем статус всех источников как "loading"
            _sourceStatuses.value = sources.associateWith { SourceStatus(loading = true) }

            aggregator.searchAll(q, sources) { sr: SourceResult ->
                // Обновляем результаты
                if (sr.results.isNotEmpty()) {
                    _results.update { current ->
                        (current + sr.results).sortedByDescending { it.seeders }
                    }
                }
                // Обновляем статус источника
                _sourceStatuses.update { map ->
                    map + (sr.source to SourceStatus(
                        loading = false,
                        resultCount = sr.results.size,
                        error = sr.error
                    ))
                }
            }

            _isLoading.value = false
        }
    }

    fun download(result: TorrentResult) {
        viewModelScope.launch {
            val source = SearchSource.entries.find {
                it.name.equals(result.source, ignoreCase = true) ||
                it.meta.displayName.equals(result.source, ignoreCase = true)
            } ?: SearchSource.TPB

            val magnet = try {
                aggregator.getMagnet(result, source)
            } catch (e: Exception) { result.magnetLink }

            // Прямые ссылки (VK, YouTube, SoundCloud и т.д.) — не торрент
            if (!magnet.contains("magnet:?") && (magnet.startsWith("http") || magnet.startsWith("https"))) {
                // TODO: передать в плеер напрямую как stream
                return@launch
            }

            context.startForegroundService(
                Intent(context, TorrentDownloadService::class.java).apply {
                    action = TorrentDownloadService.ACTION_ADD_MAGNET
                    putExtra(TorrentDownloadService.EXTRA_MAGNET, magnet)
                }
            )
        }
    }

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
