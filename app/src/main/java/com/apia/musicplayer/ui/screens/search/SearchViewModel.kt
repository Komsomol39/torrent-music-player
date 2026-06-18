package com.apia.musicplayer.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apia.musicplayer.data.db.TrackDao
import com.apia.musicplayer.domain.model.Track
import com.apia.musicplayer.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val trackDao: TrackDao,
    private val player: PlayerController
) : ViewModel() {

    val query = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val results: StateFlow<List<Track>> = query
        .debounce(300)
        .flatMapLatest { q ->
            if (q.isBlank()) trackDao.getAllTracks()
            else trackDao.search(q)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onQueryChange(q: String) { query.value = q }

    fun playTrack(track: Track, queue: List<Track>) {
        player.playQueue(queue, queue.indexOf(track).coerceAtLeast(0))
    }
}