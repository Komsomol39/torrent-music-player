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

    /**
     * Воспроизвести или скачать результат.
     * - Прямые URL (VK, SoundCloud, Archive.org, Deezer preview) → плеер напрямую
     * - Magnet ссылки → торрент движок
     * - 1337x detail URL → резолвим magnet сначала
     */
    fun playOrDownload(result: TorrentResult) {
        viewModelScope.launch {
            _playingId.value = result.id

            val source = SearchSource.entries.find {
                it.name.equals(result.source, ignoreCase = true) ||
                it.meta.displayName.equals(result.source, ignoreCase = true)
            } ?: SearchSource.ARCHIVE

            try {
                val resolvedUrl = aggregator.getMagnet(result, source)

                when {
                    // Прямая ссылка — передаём в плеер
                    resolvedUrl.startsWith("http") && !resolvedUrl.contains("magnet:?") -> {
                        val track = Track(
                            id = result.id,
                            title = result.title,
                            artist = result.artist ?: result.source,
                            album = result.album ?: result.source,
                            duration = result.sizeBytes.takeIf { it < 10_000_000 } ?: 0L,
                            uri = resolvedUrl,
                            artworkUri = null
                        )
                        player.playTrack(track)
                    }

                    // Magnet — торрент движок
                    resolvedUrl.contains("magnet:?") || resolvedUrl.startsWith("magnet:") -> {
                        context.startForegroundService(
                            Intent(context, TorrentDownloadService::class.java).apply {
                                action = TorrentDownloadService.ACTION_ADD_MAGNET
                                putExtra(TorrentDownloadService.EXTRA_MAGNET, resolvedUrl)
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                _sourceStatuses.update { map ->
                    map + (source to SourceStatus(error = "Play failed: ${e.message}"))
                }
            } finally {
                _playingId.value = null
            }
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
