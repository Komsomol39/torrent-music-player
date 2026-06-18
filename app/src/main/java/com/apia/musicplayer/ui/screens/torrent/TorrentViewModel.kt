package com.apia.musicplayer.ui.screens.torrent

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apia.musicplayer.data.torrent.TorrentService
import com.apia.musicplayer.domain.model.TorrentResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TorrentViewModel @Inject constructor(
    private val repository: TorrentRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val query = MutableStateFlow("")
    private val _results = MutableStateFlow<List<TorrentResult>>(emptyList())
    val results = _results.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _downloadingIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingIds = _downloadingIds.asStateFlow()

    fun onQueryChange(q: String) { query.value = q }

    fun search() {
        val q = query.value.trim()
        if (q.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                _results.value = repository.search(q)
                if (_results.value.isEmpty()) _error.value = null
            } catch (e: Exception) {
                _error.value = "Search failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun download(result: TorrentResult) {
        viewModelScope.launch {
            _downloadingIds.value = _downloadingIds.value + result.id
            try {
                // Резолвим magnet если нужно (1337x)
                val magnet = repository.resolveMagnet(result)
                // Запускаем TorrentService
                val intent = Intent(context, TorrentService::class.java).apply {
                    action = TorrentService.ACTION_DOWNLOAD
                    putExtra("magnet", magnet)
                    putExtra("id", result.id)
                }
                context.startForegroundService(intent)
            } finally {
                _downloadingIds.value = _downloadingIds.value - result.id
            }
        }
    }
}
