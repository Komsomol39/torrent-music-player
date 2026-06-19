package com.apia.musicplayer.ui.screens.torrent

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
import com.apia.musicplayer.data.torrent.TorrentState
import com.apia.musicplayer.data.torrent.TorrentStatus
import com.apia.musicplayer.ui.util.formatSize
import com.apia.musicplayer.ui.util.formatSpeed

@Composable
fun TorrentDownloadsScreen(viewModel: TorrentViewModel = hiltViewModel()) {
    val downloads by viewModel.downloads.collectAsState()

    if (downloads.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.DownloadDone, null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("No active downloads", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(downloads.values.toList(), key = { it.infoHash }) { torrent ->
            TorrentDownloadCard(
                torrent = torrent,
                onPause = { viewModel.pause(it) },
                onResume = { viewModel.resume(it) },
                onRemove = { viewModel.remove(it) }
            )
        }
    }
}

@Composable
fun TorrentDownloadCard(
    torrent: TorrentState,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        torrent.name.ifBlank { "Fetching metadata..." },
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusChip(torrent.status)
                        if (torrent.status == TorrentStatus.DOWNLOADING) {
                            Text(
                                torrent.downloadSpeed.formatSpeed(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "S:${torrent.seeders} P:${torrent.peers}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                // Controls
                when (torrent.status) {
                    TorrentStatus.DOWNLOADING -> IconButton(onClick = { onPause(torrent.infoHash) }) {
                        Icon(Icons.Default.Pause, "Pause")
                    }
                    TorrentStatus.PAUSED -> IconButton(onClick = { onResume(torrent.infoHash) }) {
                        Icon(Icons.Default.PlayArrow, "Resume", tint = MaterialTheme.colorScheme.primary)
                    }
                    TorrentStatus.FINISHED -> Icon(
                        Icons.Default.CheckCircle, "Done",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(12.dp)
                    )
                    else -> {}
                }
                IconButton(onClick = { onRemove(torrent.infoHash) }) {
                    Icon(Icons.Default.Delete, "Remove", tint = MaterialTheme.colorScheme.error)
                }
            }

            if (torrent.status == TorrentStatus.DOWNLOADING || torrent.status == TorrentStatus.CHECKING) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { torrent.progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${(torrent.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${torrent.downloadedBytes.formatSize()} / ${torrent.totalBytes.formatSize()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            torrent.error?.let { err ->
                Spacer(Modifier.height(4.dp))
                Text(err, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun StatusChip(status: TorrentStatus) {
    val (label, color) = when (status) {
        TorrentStatus.DOWNLOADING -> "Downloading" to MaterialTheme.colorScheme.primary
        TorrentStatus.SEEDING     -> "Seeding"     to MaterialTheme.colorScheme.secondary
        TorrentStatus.PAUSED      -> "Paused"      to MaterialTheme.colorScheme.onSurfaceVariant
        TorrentStatus.FINISHED    -> "Done"         to MaterialTheme.colorScheme.primary
        TorrentStatus.ERROR       -> "Error"        to MaterialTheme.colorScheme.error
        TorrentStatus.CHECKING    -> "Checking"     to MaterialTheme.colorScheme.tertiary
        TorrentStatus.QUEUED      -> "Queued"       to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(label, style = MaterialTheme.typography.labelSmall, color = color)
}
