package com.apia.musicplayer.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.apia.musicplayer.domain.model.RepeatMode
import com.apia.musicplayer.ui.components.AlbumArtwork
import com.apia.musicplayer.ui.util.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onEqualizer: () -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val state by viewModel.playerState.collectAsState()
    val track = state.currentTrack

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.KeyboardArrowDown, "Back")
            }
            Text(
                text = "Now Playing",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            IconButton(onClick = onEqualizer) {
                Icon(Icons.Default.GraphicEq, "Equalizer")
            }
        }

        Spacer(Modifier.height(32.dp))

        AlbumArtwork(
            uri = track?.artworkUri,
            modifier = Modifier.size(280.dp).clip(RoundedCornerShape(24.dp))
        )

        Spacer(Modifier.height(32.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track?.title ?: "Nothing playing",
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track?.artist ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (track != null) {
                IconButton(onClick = { viewModel.toggleFavorite(track) }) {
                    Icon(
                        if (track.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        "Favorite",
                        tint = if (track.isFavorite) MaterialTheme.colorScheme.secondary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = state.progress,
                onValueChange = { viewModel.seekTo(it) },
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(state.currentPositionMs.formatDuration(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(state.durationMs.formatDuration(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.toggleShuffle() }) {
                Icon(Icons.Default.Shuffle, "Shuffle",
                    tint = if (state.isShuffleOn) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { viewModel.skipPrevious() }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.SkipPrevious, "Previous", modifier = Modifier.size(36.dp))
            }
            IconButton(
                onClick = { viewModel.togglePlayPause() },
                modifier = Modifier.size(72.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
            ) {
                Icon(
                    if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    "Play/Pause",
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            IconButton(onClick = { viewModel.skipNext() }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.SkipNext, "Next", modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = { viewModel.cycleRepeatMode() }) {
                Icon(
                    when (state.repeatMode) {
                        RepeatMode.ONE -> Icons.Default.RepeatOne
                        else -> Icons.Default.Repeat
                    },
                    "Repeat",
                    tint = if (state.repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
