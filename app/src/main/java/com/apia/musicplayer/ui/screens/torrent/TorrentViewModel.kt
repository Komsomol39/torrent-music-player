package com.apia.musicplayer.ui.screens.torrent

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apia.musicplayer.data.db.TrackDao
import com.apia.musicplayer.data.search.ArchiveOrgProvider
import com.apia.musicplayer.data.search.SearchAggregator
import com.apia.musicplayer.data.search.SourceResult
import com.apia.musicplayer.data.torrent.HttpDownload
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    private val _archiveFiles = MutableStateFlow<List<ArchiveFile>>(emptyList())
    val archiveFiles = _archiveFiles.asStateFlow()
    private val _showArchiveDialog = MutableStateFlow(false)
    val showArchiveDialog = _showArchiveDialog.asStateFlow()
    private val _pendingResult = MutableStateFlow<TorrentResult?>(null)

    // Торрент + HTTP загрузки
    val downloads: StateFlow<Map<String, TorrentState>> = engine.torrents
    val httpDownloads: StateFlow<Map<String, HttpDownload>> = downloader.downloads

    val enabledSources get() = aggregator.enabledSources

    fun onQueryChange(q: String) { query.value = q }
    fun clearToast() { _toast.value = null }

    fun search() {
        val q = query.value.trim()
        if (q.isBlank()) return
        val sources = aggregator.enabledSources.toSet()
        if (sources.isEmpty()) { _toast.value = "Enable sources in Settings"; return }
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
                        loading = false, resultCount = sr.results.size,
                        error = sr.error, durationMs = sr.durationMs
                    ))
                }
            }
            _isLoading.value = false
        }
    }

    fun playOrDownload(result: TorrentResult) {
        viewModelScope.launch {
            _playingId.value = result.id
            try {
                when {
                    result.source == "Archive.org" -> handleArchive(result)
                    result.magnetLink.startsWith("http") && !result.magnetLink.contains("magnet:?") ->
                        playAndSave(result, result.magnetLink)
                    else -> {
                        val source = findSource(result)
                        val resolved = aggregator.getMagnet(result, source)
                        when {
                            resolved.startsWith("http") && !resolved.contains("magnet:?") ->
                                playAndSave(result, resolved)
                            resolved.contains("magnet:") ->
                                startTorrent(resolved, result.title)
                            else -> _toast.value = "Cannot play this result"
                        }
                    }
                }
            } catch (e: Exception) {
                _toast.value = "Error: ${e.message?.take(80)}"
            } finally {
                _playingId.value = null
            }
        }
    }

    private suspend fun handleArchive(result: TorrentResult) {
        val archive = aggregator.archive as? ArchiveOrgProvider ?: return
        val files = archive.getFileList(result.magnetLink)
        when {
            files.isEmpty() -> {
                val url = archive.getMagnet(result)
                if (url.startsWith("http")) playAndSave(result, url)
                else _toast.value = "No audio files found"
            }
            files.size == 1 ->
                playAndSave(result.copy(title = files[0].name.substringBeforeLast(".")), files[0].url)
            else -> {
                _pendingResult.value = result
                _archiveFiles.value = files
                _showArchiveDialog.value = true
            }
        }
    }

    /** Играем сразу + параллельно скачиваем и сохраняем в библиотеку */
    private suspend fun playAndSave(result: TorrentResult, url: String) {
        Log.d("TorrentVM", "Play+Save: ${url.take(80)}")
        // 1. Играем сразу
        player.playTrack(Track(
            id = result.id, title = result.title,
            artist = result.artist ?: result.source,
            album  = result.album  ?: result.source,
            duration = 0L, uri = url
        ))
        _toast.value = "▶ ${result.title.take(40)}"

        // 2. Скачиваем параллельно
        val fileName = buildString {
            result.artist?.let { append(it); append(" - ") }
            append(result.title.take(60))
        }
        viewModelScope.launch {
            val file = downloader.downloadAsync(url, fileName)
            if (file != null && file.exists()) {
                // 3. Читаем реальную длительность из файла
                val duration = withContext(Dispatchers.IO) {
                    try {
                        MediaMetadataRetriever().run {
                            setDataSource(file.absolutePath)
                            val d = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                                ?.toLongOrNull() ?: 0L
                            release()
                            d
                        }
                    } catch (e: Exception) { 0L }
                }
                // 4. Сохраняем в БД с реальной длительностью
                trackDao.upsertTrack(Track(
                    id       = "dl_${file.absolutePath.hashCode()}",
                    title    = result.title,
                    artist   = result.artist ?: "Unknown",
                    album    = result.album  ?: result.source,
                    duration = duration,
                    uri      = file.toURI().toString()
                ))
                _toast.value = "✓ Saved: ${file.name.take(40)}"
                Log.d("TorrentVM", "Saved ${file.name}, duration=${duration}ms")
            }
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
                putExtra(TorrentDownloadService.EXTRA_NAME, name)
            }
        )
        _toast.value = "⬇ Download started: ${name.take(40)}"
    }

    private fun findSource(result: TorrentResult): SearchSource =
        SearchSource.entries.find {
            it.name.equals(result.source, ignoreCase = true) ||
            it.meta.displayName.equals(result.source, ignoreCase = true) ||
            result.source.contains(it.name, ignoreCase = true)
        } ?: SearchSource.TPB

    fun pause(h: String)  = context.startService(Intent(context, TorrentDownloadService::class.java).apply { action = TorrentDownloadService.ACTION_PAUSE;  putExtra(TorrentDownloadService.EXTRA_HASH, h) })
    fun resume(h: String) = context.startService(Intent(context, TorrentDownloadService::class.java).apply { action = TorrentDownloadService.ACTION_RESUME; putExtra(TorrentDownloadService.EXTRA_HASH, h) })
    fun remove(h: String) = context.startService(Intent(context, TorrentDownloadService::class.java).apply { action = TorrentDownloadService.ACTION_REMOVE; putExtra(TorrentDownloadService.EXTRA_HASH, h) })
}
