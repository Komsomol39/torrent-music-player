package com.apia.musicplayer.ui.screens.downloads

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apia.musicplayer.data.torrent.DownloadStatus
import com.apia.musicplayer.data.torrent.TorrentEngine
import com.apia.musicplayer.data.torrent.TorrentService
import com.apia.musicplayer.domain.model.DownloadState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val engine: TorrentEngine,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val downloads = engine.downloads
        .map { it.values.toList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun startDownload(magnetLink: String, id: String) {
        val intent = Intent(context, TorrentService::class.java).apply {
            action = TorrentService.ACTION_DOWNLOAD
            putExtra("magnet", magnetLink)
            putExtra("id", id)
        }
        context.startForegroundService(intent)
    }

    fun pause(id: String) = engine.pause(id)
    fun resume(id: String) = engine.resume(id)
    fun remove(id: String, deleteFiles: Boolean = false) = engine.remove(id, deleteFiles)
}
