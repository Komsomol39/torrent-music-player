package com.apia.musicplayer.player

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.apia.musicplayer.domain.model.PlayerState
import com.apia.musicplayer.domain.model.RepeatMode
import com.apia.musicplayer.domain.model.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class PlayerController(private val player: ExoPlayer) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { syncState() }
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) { syncState() }
            override fun onPlaybackStateChanged(state: Int) {
                syncState()
                if (state == Player.STATE_ENDED) Log.d("Player","Playback ended")
                if (state == Player.STATE_BUFFERING) Log.d("Player","Buffering...")
            }
            override fun onShuffleModeEnabledChanged(on: Boolean) { syncState() }
            override fun onRepeatModeChanged(mode: Int) { syncState() }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e("Player", "Error: ${error.message} cause=${error.cause?.message}")
            }
        })
        // Прогресс каждые 500мс
        scope.launch {
            while (isActive) { updateProgress(); delay(500) }
        }
    }

    fun playQueue(tracks: List<Track>, startIndex: Int = 0) {
        val items = tracks.map { it.toMediaItem() }
        player.setMediaItems(items, startIndex, 0L)
        player.prepare()
        player.play()
        Log.d("Player", "playQueue: ${tracks.size} tracks, start=$startIndex")
    }

    fun playTrack(track: Track) {
        Log.d("Player", "playTrack: ${track.title} uri=${track.uri}")
        player.setMediaItem(track.toMediaItem())
        player.prepare()
        player.play()
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun skipNext()     = player.seekToNextMediaItem()
    fun skipPrevious() {
        if (player.currentPosition > 3000) player.seekTo(0)
        else player.seekToPreviousMediaItem()
    }

    fun seekTo(fraction: Float) {
        val d = player.duration
        if (d > 0) player.seekTo((d * fraction).toLong())
    }

    fun toggleShuffle()   { player.shuffleModeEnabled = !player.shuffleModeEnabled }
    fun addToQueue(track: Track) { player.addMediaItem(track.toMediaItem()) }

    fun cycleRepeatMode() {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else                   -> Player.REPEAT_MODE_OFF
        }
    }

    private fun syncState() {
        val idx = player.currentMediaItemIndex
        val meta = player.getMediaItemAt(idx.coerceAtLeast(0)).mediaMetadata
        _state.update { s ->
            s.copy(
                isPlaying   = player.isPlaying,
                repeatMode  = when (player.repeatMode) {
                    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                    else                   -> RepeatMode.OFF
                },
                isShuffleOn = player.shuffleModeEnabled
            )
        }
    }

    private fun updateProgress() {
        val duration = player.duration.takeIf { it > 0 } ?: return
        val position = player.currentPosition
        _state.update {
            it.copy(
                currentPositionMs = position,
                durationMs        = duration,
                progress          = position.toFloat() / duration
            )
        }
    }

    private fun Track.toMediaItem(): MediaItem {
        val isHttp = uri.startsWith("http")
        // Определяем MIME по расширению для правильной обработки ExoPlayer
        val mime = when {
            uri.contains(".mp3")  -> MimeTypes.AUDIO_MPEG
            uri.contains(".flac") -> MimeTypes.AUDIO_FLAC
            uri.contains(".ogg")  -> MimeTypes.AUDIO_OGG
            uri.contains(".m4a")  -> MimeTypes.AUDIO_MP4
            uri.contains(".opus") -> MimeTypes.AUDIO_OPUS
            uri.contains(".aac")  -> MimeTypes.AUDIO_AAC
            isHttp                -> MimeTypes.AUDIO_MPEG  // HTTP-стримы обычно MP3
            else                  -> null
        }
        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(id)
            .apply { mime?.let { setMimeType(it) } }
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(artworkUri?.let { android.net.Uri.parse(it) })
                    .build()
            )
            .build()
    }
}
