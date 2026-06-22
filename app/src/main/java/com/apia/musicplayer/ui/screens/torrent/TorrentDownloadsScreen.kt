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
import com.apia.musicplayer.data.torrent.HttpDownloadStatus
import com.apia.musicplayer.data.torrent.TorrentStatus
import com.apia.musicplayer.ui.util.formatSize
import com.apia.musicplayer.ui.util.formatSpeed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorrentDownloadsScreen(viewModel: TorrentViewModel = hiltViewModel()) {
    val torrentDownloads by viewModel.downloads.collectAsState()
    val httpDownloads by viewModel.httpDownloads.collectAsState()

    val totalActive = torrentDownloads.size + httpDownloads.values.count {
        it.status == HttpDownloadStatus.DOWNLOADING
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                actions = {
                    if (totalActive > 0) {
                        Text("$totalActive active",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 16.dp))
                    }
                }
            )
        }
    ) { padding ->
        if (torrentDownloads.isEmpty() && httpDownloads.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Download, null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("No downloads yet", style = MaterialTheme.typography.titleMedium)
                    Text("Play a track from Find tab — it saves automatically",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // HTTP загрузки (SoundCloud, Archive.org, Deezer и т.д.)
            if (httpDownloads.isNotEmpty()) {
                item {
                    Text("Stream Downloads",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
                items(httpDownloads.values.toList(), key = { it.id }) { dl ->
                    HttpDownloadCard(dl)
                }
            }

            // Торрент загрузки
            if (torrentDownloads.isNotEmpty()) {
                item {
                    Text("Torrent Downloads",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
                items(torrentDownloads.values.toList(), key = { it.infoHash }) { torrent ->
                    TorrentCard(
                        torrent  = torrent,
                        onPause  = { viewModel.pause(it) },
                        onResume = { viewModel.resume(it) },
                        onRemove = { viewModel.remove(it) }
                    )
                }
            }
        }
    }
}

@Composable
fun HttpDownloadCard(dl: com.apia.musicplayer.data.torrent.HttpDownload) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when (dl.status) {
                        HttpDownloadStatus.DOWNLOADING -> Icons.Default.Downloading
                        HttpDownloadStatus.DONE        -> Icons.Default.CheckCircle
                        HttpDownloadStatus.ERROR       -> Icons.Default.Error
                    },
                    null,
                    tint = when (dl.status) {
                        HttpDownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
                        HttpDownloadStatus.DONE        -> MaterialTheme.colorScheme.primary
                        HttpDownloadStatus.ERROR       -> MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(dl.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            when (dl.status) {
                                HttpDownloadStatus.DOWNLOADING -> "Downloading..."
                                HttpDownloadStatus.DONE        -> "Saved to library"
                                HttpDownloadStatus.ERROR       -> "Error"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (dl.totalBytes > 0)
                            Text(dl.totalBytes.formatSize(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (dl.status == HttpDownloadStatus.DOWNLOADING) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { dl.progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp)
                )
                Spacer(Modifier.height(2.dp))
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${(dl.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                    if (dl.totalBytes > 0)
                        Text("${dl.bytesDownloaded.formatSize()} / ${dl.totalBytes.formatSize()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun TorrentCard(
    torrent: com.apia.musicplayer.data.torrent.TorrentState,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove?") },
            text  = { Text(torrent.name.ifBlank { "This torrent" }) },
            confirmButton = {
                TextButton(onClick = { onRemove(torrent.infoHash); confirmDelete = false }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (icon, tint) = when (torrent.status) {
                    TorrentStatus.DOWNLOADING -> Icons.Default.Downloading to MaterialTheme.colorScheme.primary
                    TorrentStatus.SEEDING     -> Icons.Default.Upload      to MaterialTheme.colorScheme.secondary
                    TorrentStatus.PAUSED      -> Icons.Default.PauseCircle to MaterialTheme.colorScheme.onSurfaceVariant
                    TorrentStatus.FINISHED    -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
                    TorrentStatus.ERROR       -> Icons.Default.Error       to MaterialTheme.colorScheme.error
                    else                      -> Icons.Default.HourglassTop to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(torrent.name.ifBlank { "Fetching metadata…" },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(torrent.status.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (torrent.status == TorrentStatus.DOWNLOADING && torrent.downloadSpeed > 0)
                            Text("↓ ${torrent.downloadSpeed.formatSpeed()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        if (torrent.seeders > 0)
                            Text("S:${torrent.seeders}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = { confirmDelete = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.DeleteOutline, "Remove",
                        tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
            if (torrent.status == TorrentStatus.DOWNLOADING || torrent.status == TorrentStatus.CHECKING) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { torrent.progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp))
                Spacer(Modifier.height(2.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("${(torrent.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                    if (torrent.totalBytes > 0)
                        Text("${torrent.downloadedBytes.formatSize()} / ${torrent.totalBytes.formatSize()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.End) {
                    when (torrent.status) {
                        TorrentStatus.DOWNLOADING ->
                            OutlinedButton(onClick = { onPause(torrent.infoHash) },
                                modifier = Modifier.height(30.dp)) {
                                Icon(Icons.Default.Pause, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Pause", style = MaterialTheme.typography.labelSmall)
                            }
                        TorrentStatus.PAUSED ->
                            Button(onClick = { onResume(torrent.infoHash) },
                                modifier = Modifier.height(30.dp)) {
                                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Resume", style = MaterialTheme.typography.labelSmall)
                            }
                        else -> {}
                    }
                }
            }
        }
    }
}
