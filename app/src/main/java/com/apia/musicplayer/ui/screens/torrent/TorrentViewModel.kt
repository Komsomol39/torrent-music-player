package com.apia.musicplayer.ui.screens.torrent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apia.musicplayer.domain.model.TorrentResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TorrentViewModel @Inject constructor(
    private val repository: TorrentRepository
) : ViewModel() {

    val query = MutableStateFlow("")
    private val _results = MutableStateFlow<List<TorrentResult>>(emptyList())
    val results = _results.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

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
            repository.startDownload(result)
        }
    }
}