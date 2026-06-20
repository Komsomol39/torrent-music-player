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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorrentDownloadsScreen(viewModel: TorrentViewModel = hiltViewModel()) {
    val downloads by viewModel.downloads.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                actions = {
                    if (downloads.isNotEmpty()) {
                        Text(
                            "${downloads.size} torrent${if (downloads.size != 1) "s" else ""}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (downloads.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Download, null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("No active downloads", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Tap ⬇ on a torrent result in Find tab",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            "▶ Stream results are saved automatically to Library",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(downloads.values.toList(), key = { it.infoHash }) { torrent ->
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

@Composable
fun TorrentCard(
    torrent: TorrentState,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove download?") },
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
            // Заголовок
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusIcon(torrent.status)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        torrent.name.ifBlank { "Fetching metadata…" },
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    StatusRow(torrent)
                }
            }

            // Прогресс
            if (torrent.status == TorrentStatus.DOWNLOADING || torrent.status == TorrentStatus.CHECKING) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { torrent.progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp)
                )
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${(torrent.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                    if (torrent.totalBytes > 0)
                        Text("${torrent.downloadedBytes.formatSize()} / ${torrent.totalBytes.formatSize()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Ошибка
            torrent.error?.let { err ->
                Spacer(Modifier.height(6.dp))
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
                    Text("⚠ $err", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(8.dp, 4.dp))
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(thickness = 0.5.dp)
            Spacer(Modifier.height(4.dp))

            // Кнопки
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                when (torrent.status) {
                    TorrentStatus.DOWNLOADING ->
                        OutlinedButton(onClick = { onPause(torrent.infoHash) }, modifier = Modifier.height(32.dp)) {
                            Icon(Icons.Default.Pause, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Pause", style = MaterialTheme.typography.labelMedium)
                        }
                    TorrentStatus.PAUSED ->
                        Button(onClick = { onResume(torrent.infoHash) }, modifier = Modifier.height(32.dp)) {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Resume", style = MaterialTheme.typography.labelMedium)
                        }
                    TorrentStatus.FINISHED ->
                        FilledTonalButton(onClick = {}, modifier = Modifier.height(32.dp)) {
                            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Done", style = MaterialTheme.typography.labelMedium)
                        }
                    else -> {}
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { confirmDelete = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.DeleteOutline, "Remove",
                        tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun StatusIcon(status: TorrentStatus) {
    val (icon, color) = when (status) {
        TorrentStatus.DOWNLOADING -> Icons.Default.Downloading  to MaterialTheme.colorScheme.primary
        TorrentStatus.SEEDING     -> Icons.Default.Upload       to MaterialTheme.colorScheme.secondary
        TorrentStatus.PAUSED      -> Icons.Default.PauseCircle  to MaterialTheme.colorScheme.onSurfaceVariant
        TorrentStatus.FINISHED    -> Icons.Default.CheckCircle  to MaterialTheme.colorScheme.primary
        TorrentStatus.ERROR       -> Icons.Default.Error        to MaterialTheme.colorScheme.error
        TorrentStatus.CHECKING    -> Icons.Default.Sync         to MaterialTheme.colorScheme.secondary
        TorrentStatus.QUEUED      -> Icons.Default.HourglassTop to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
}

@Composable
fun StatusRow(torrent: TorrentState) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            when (torrent.status) {
                TorrentStatus.DOWNLOADING -> "Downloading"
                TorrentStatus.SEEDING     -> "Seeding"
                TorrentStatus.PAUSED      -> "Paused"
                TorrentStatus.FINISHED    -> "Complete"
                TorrentStatus.ERROR       -> "Error"
                TorrentStatus.CHECKING    -> "Checking"
                TorrentStatus.QUEUED      -> "Queued"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (torrent.status == TorrentStatus.DOWNLOADING) {
            if (torrent.downloadSpeed > 0)
                Text("↓ ${torrent.downloadSpeed.formatSpeed()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            if (torrent.seeders > 0)
                Text("S:${torrent.seeders} P:${torrent.peers}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
