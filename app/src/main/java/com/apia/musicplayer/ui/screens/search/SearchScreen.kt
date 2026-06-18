package com.apia.musicplayer.ui.screens.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.apia.musicplayer.ui.screens.library.TrackItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onTrackClick: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.onQueryChange(it) },
            placeholder = { Text("Search in library...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            items(results, key = { it.id }) { track ->
                TrackItem(track = track, onClick = {
                    viewModel.playTrack(track, results)
                    onTrackClick()
                })
            }
        }
    }
}