package com.apia.musicplayer.ui.screens.torrent

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apia.musicplayer.data.torrent.TorrentDownloadService
import com.apia.musicplayer.data.torrent.TorrentEngine
import com.apia.musicplayer.data.torrent.TorrentState
import com.apia.musicplayer.domain.model.TorrentResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TorrentViewModel @Inject constructor(
    private val repository: TorrentRepository,
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

    // Активные загрузки из движка
    val downloads: StateFlow<Map<String, TorrentState>> = engine.torrents

    fun onQueryChange(q: String) { query.value = q }

    fun search() {
        val q = query.value.trim()
        if (q.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                _results.value = repository.search(q)
            } catch (e: Exception) {
                _error.value = e.message ?: "Search failed"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun download(result: TorrentResult) {
        viewModelScope.launch {
            val magnet = repository.resolveMagnet(result)
            // Запускаем foreground service
            val intent = Intent(context, TorrentDownloadService::class.java).apply {
                action = TorrentDownloadService.ACTION_ADD_MAGNET
                putExtra(TorrentDownloadService.EXTRA_MAGNET, magnet)
            }
            context.startForegroundService(intent)
        }
    }

    fun pause(infoHash: String) {
        val intent = Intent(context, TorrentDownloadService::class.java).apply {
            action = TorrentDownloadService.ACTION_PAUSE
            putExtra(TorrentDownloadService.EXTRA_HASH, infoHash)
        }
        context.startService(intent)
    }

    fun resume(infoHash: String) {
        val intent = Intent(context, TorrentDownloadService::class.java).apply {
            action = TorrentDownloadService.ACTION_RESUME
            putExtra(TorrentDownloadService.EXTRA_HASH, infoHash)
        }
        context.startService(intent)
    }

    fun remove(infoHash: String) {
        val intent = Intent(context, TorrentDownloadService::class.java).apply {
            action = TorrentDownloadService.ACTION_REMOVE
            putExtra(TorrentDownloadService.EXTRA_HASH, infoHash)
        }
        context.startService(intent)
    }
}
