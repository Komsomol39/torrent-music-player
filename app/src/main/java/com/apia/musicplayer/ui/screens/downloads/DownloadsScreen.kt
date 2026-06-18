package com.apia.musicplayer.ui.screens.downloads

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.apia.musicplayer.data.torrent.DownloadStatus
import com.apia.musicplayer.domain.model.DownloadState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(viewModel: DownloadsViewModel = hiltViewModel()) {
    val downloads by viewModel.downloads.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Downloads") }) }) { padding ->
        if (downloads.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Text("No downloads yet", style = MaterialTheme.typography.titleMedium)
                    Text("Search torrents and tap Download", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(Modifier.padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(downloads, key = { it.id }) { state ->
                    DownloadItem(state = state,
                        onPause  = { viewModel.pause(state.id) },
                        onResume = { viewModel.resume(state.id) },
                        onRemove = { viewModel.remove(state.id) })
                }
            }
        }
    }
}

@Composable
fun DownloadItem(
    state: DownloadState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when (state.status) {
                        DownloadStatus.COMPLETED   -> Icons.Default.CheckCircle
                        DownloadStatus.ERROR       -> Icons.Default.Error
                        DownloadStatus.PAUSED      -> Icons.Default.PauseCircle
                        else                       -> Icons.Default.Downloading
                    },
                    contentDescription = null,
                    tint = when (state.status) {
                        DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                        DownloadStatus.ERROR     -> MaterialTheme.colorScheme.error
                        else                     -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.id, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium)
                    when (state.status) {
                        DownloadStatus.DOWNLOADING -> {
                            Text(
                                "${(state.progress * 100).toInt()}% • ${formatRate(state.downloadRateBps)} • ${state.seeds}S/${state.peers}P",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DownloadStatus.COMPLETED -> Text("Complete", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                        DownloadStatus.ERROR -> Text(state.error ?: "Error",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        else -> Text(state.status.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // Controls
                when (state.status) {
                    DownloadStatus.DOWNLOADING -> IconButton(onClick = onPause) {
                        Icon(Icons.Default.Pause, "Pause")
                    }
                    DownloadStatus.PAUSED -> IconButton(onClick = onResume) {
                        Icon(Icons.Default.PlayArrow, "Resume")
                    }
                    else -> {}
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Close, "Remove")
                }
            }
            if (state.status == DownloadStatus.DOWNLOADING) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun formatRate(bps: Long) = when {
    bps > 1_048_576 -> "%.1f MB/s".format(bps / 1_048_576.0)
    bps > 1024      -> "%.0f KB/s".format(bps / 1024.0)
    else            -> "$bps B/s"
}
