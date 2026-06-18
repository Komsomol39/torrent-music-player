package com.apia.musicplayer.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apia.musicplayer.data.db.TrackDao
import com.apia.musicplayer.domain.model.PlayerState
import com.apia.musicplayer.domain.model.Track
import com.apia.musicplayer.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val controller: PlayerController,
    private val trackDao: TrackDao
) : ViewModel() {

    val playerState: StateFlow<PlayerState> = controller.state

    fun playTrack(track: Track) {
        viewModelScope.launch {
            trackDao.incrementPlayCount(track.id)
            controller.playTrack(track)
        }
    }

    fun playQueue(tracks: List<Track>, startIndex: Int = 0) {
        viewModelScope.launch {
            controller.playQueue(tracks, startIndex)
        }
    }

    fun togglePlayPause() = controller.togglePlayPause()
    fun skipNext() = controller.skipNext()
    fun skipPrevious() = controller.skipPrevious()
    fun seekTo(fraction: Float) = controller.seekTo(fraction)
    fun toggleShuffle() = controller.toggleShuffle()
    fun cycleRepeatMode() = controller.cycleRepeatMode()

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            trackDao.setFavorite(track.id, !track.isFavorite)
        }
    }
}