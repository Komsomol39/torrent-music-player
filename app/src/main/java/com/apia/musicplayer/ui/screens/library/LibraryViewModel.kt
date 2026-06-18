package com.apia.musicplayer.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apia.musicplayer.data.db.TrackDao
import com.apia.musicplayer.domain.model.Track
import com.apia.musicplayer.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val trackDao: TrackDao,
    private val player: PlayerController
) : ViewModel() {

    val tracks = trackDao.getAllTracks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val favorites = trackDao.getFavorites().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun playTrack(track: Track, allTracks: List<Track>) {
        val index = allTracks.indexOf(track)
        player.playQueue(allTracks, index.coerceAtLeast(0))
    }
}