package com.apia.musicplayer.ui.screens.torrent

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apia.musicplayer.data.search.SearchAggregator
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
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _loadingSources = MutableStateFlow<Set<String>>(emptySet())
    val loadingSources = _loadingSources.asStateFlow()

    val downloads: StateFlow<Map<String, TorrentState>> = engine.torrents
    val enabledSources get() = aggregator.enabledSources

    fun onQueryChange(q: String) { query.value = q }

    fun search() {
        val q = query.value.trim()
        if (q.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _results.value = emptyList()
            _loadingSources.value = aggregator.enabledSources.map { it.name }.toSet()

            try {
                aggregator.searchAll(q, aggregator.enabledSources) { source, partialResults ->
                    _results.update { current ->
                        (current + partialResults).sortedByDescending { it.seeders }
                    }
                    _loadingSources.update { it - source.name }
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
                _loadingSources.value = emptySet()
            }
        }
    }

    fun download(result: TorrentResult) {
        viewModelScope.launch {
            val source = SearchSource.entries.find { it.name.lowercase() == result.source.lowercase() }
                ?: SearchSource.TPB
            val magnet = aggregator.getMagnet(result, source)
            // Если это прямая ссылка (VK, YouTube) — играем напрямую, не через торрент
            if (magnet.startsWith("http") && !magnet.contains("magnet:")) {
                // TODO: добавить в очередь плеера напрямую
                return@launch
            }
            val intent = Intent(context, TorrentDownloadService::class.java).apply {
                action = TorrentDownloadService.ACTION_ADD_MAGNET
                putExtra(TorrentDownloadService.EXTRA_MAGNET, magnet)
            }
            context.startForegroundService(intent)
        }
    }

    fun pause(infoHash: String) {
        context.startService(Intent(context, TorrentDownloadService::class.java).apply {
            action = TorrentDownloadService.ACTION_PAUSE
            putExtra(TorrentDownloadService.EXTRA_HASH, infoHash)
        })
    }

    fun resume(infoHash: String) {
        context.startService(Intent(context, TorrentDownloadService::class.java).apply {
            action = TorrentDownloadService.ACTION_RESUME
            putExtra(TorrentDownloadService.EXTRA_HASH, infoHash)
        })
    }

    fun remove(infoHash: String) {
        context.startService(Intent(context, TorrentDownloadService::class.java).apply {
            action = TorrentDownloadService.ACTION_REMOVE
            putExtra(TorrentDownloadService.EXTRA_HASH, infoHash)
        })
    }
}
