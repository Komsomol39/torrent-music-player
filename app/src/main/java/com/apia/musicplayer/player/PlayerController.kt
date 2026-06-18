package com.apia.musicplayer.player

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
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
            override fun onIsPlayingChanged(isPlaying: Boolean) { updateState() }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { updateState() }
            override fun onPlaybackStateChanged(playbackState: Int) { updateState() }
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { updateState() }
            override fun onRepeatModeChanged(repeatMode: Int) { updateState() }
        })
        scope.launch {
            while (isActive) { updateProgress(); delay(500) }
        }
    }

    fun playQueue(tracks: List<Track>, startIndex: Int = 0) {
        player.setMediaItems(tracks.map { it.toMediaItem() }, startIndex, 0L)
        player.prepare()
        player.play()
    }

    fun playTrack(track: Track) = playQueue(listOf(track))
    fun togglePlayPause() { if (player.isPlaying) player.pause() else player.play() }
    fun skipNext() = player.seekToNextMediaItem()
    fun skipPrevious() {
        if (player.currentPosition > 3000) player.seekTo(0)
        else player.seekToPreviousMediaItem()
    }
    fun seekTo(fraction: Float) {
        val d = player.duration
        if (d > 0) player.seekTo((d * fraction).toLong())
    }
    fun toggleShuffle() { player.shuffleModeEnabled = !player.shuffleModeEnabled }
    fun cycleRepeatMode() {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
    fun addToQueue(track: Track) { player.addMediaItem(track.toMediaItem()) }

    private fun updateState() {
        _state.update { current ->
            current.copy(
                isPlaying = player.isPlaying,
                repeatMode = when (player.repeatMode) {
                    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                    else -> RepeatMode.OFF
                },
                isShuffleOn = player.shuffleModeEnabled
            )
        }
    }

    private fun updateProgress() {
        val duration = player.duration.takeIf { it > 0 } ?: return
        val position = player.currentPosition
        _state.update { it.copy(
            currentPositionMs = position,
            durationMs = duration,
            progress = position.toFloat() / duration
        )}
    }

    private fun Track.toMediaItem() = MediaItem.Builder()
        .setUri(uri)
        .setMediaId(id)
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