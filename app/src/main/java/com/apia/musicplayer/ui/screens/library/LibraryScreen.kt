package com.apia.musicplayer.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.apia.musicplayer.domain.model.Track
import com.apia.musicplayer.ui.components.AlbumArtwork
import com.apia.musicplayer.ui.util.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onTrackClick: () -> Unit,
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    scanViewModel: ScanViewModel = hiltViewModel()
) {
    val tracks by libraryViewModel.tracks.collectAsState()
    val isScanning by scanViewModel.isScanning.collectAsState()
    val lastCount by scanViewModel.lastScanCount.collectAsState()
    var showSnack by remember { mutableStateOf(false) }

    LaunchedEffect(lastCount) {
        if (lastCount > 0) showSnack = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = {
                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 8.dp))
                    } else {
                        IconButton(onClick = { scanViewModel.scan() }) {
                            Icon(Icons.Default.Refresh, "Scan library")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (tracks.isEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { scanViewModel.scan() },
                    icon = { Icon(Icons.Default.LibraryMusic, null) },
                    text = { Text("Scan Library") }
                )
            }
        },
        snackbarHost = {
            if (showSnack) {
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = { TextButton(onClick = { showSnack = false }) { Text("OK") } }
                ) {
                    Text("Found $lastCount tracks")
                }
            }
        }
    ) { padding ->
        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.LibraryMusic, null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("No tracks yet", style = MaterialTheme.typography.titleMedium)
                    Text("Tap Scan to find music on your device",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("or search the Torrent tab to download",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                itemsIndexed(tracks, key = { _, t -> t.id }) { _, track ->
                    TrackItem(
                        track = track,
                        onClick = {
                            libraryViewModel.playTrack(track, tracks)
                            onTrackClick()
                        }
                    )
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

@Composable
fun TrackItem(track: Track, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArtwork(
            uri = track.artworkUri,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${track.artist} • ${track.duration.formatDuration()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = { }) {
            Icon(Icons.Default.MoreVert, "More",
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
