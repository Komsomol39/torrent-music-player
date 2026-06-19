package com.apia.musicplayer.ui.screens.torrent

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.apia.musicplayer.domain.model.SearchSource
import com.apia.musicplayer.domain.model.TorrentResult
import com.apia.musicplayer.ui.util.formatSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorrentSearchScreen(viewModel: TorrentViewModel = hiltViewModel()) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val sourceStatuses by viewModel.sourceStatuses.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val enabledSources = viewModel.enabledSources

    Column(modifier = Modifier.fillMaxSize()) {

        // Search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.onQueryChange(it) },
                placeholder = { Text("Search music...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search() })
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = { viewModel.search() },
                enabled = query.isNotBlank() && !isLoading
            ) {
                if (isLoading)
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else
                    Icon(Icons.Default.Search, "Search")
            }
        }

        // Нет источников
        if (enabledSources.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Settings, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("No sources enabled")
                    Text("Go to Settings → enable sources", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return
        }

        // Статус источников (показываем только после нажатия поиска)
        if (sourceStatuses.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(sourceStatuses.entries.toList(), key = { it.key.name }) { (source, status) ->
                    SourceChip(source = source, status = status)
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        // Результаты
        when {
            results.isEmpty() && !isLoading && sourceStatuses.isNotEmpty() -> {
                // Показываем ошибки если все источники провалились
                val errors = sourceStatuses.filter { it.value.error != null }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(Icons.Default.SearchOff, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("No results found", style = MaterialTheme.typography.titleMedium)
                        if (errors.isNotEmpty()) {
                            Text(
                                "${errors.size} sources failed. Check Settings → connection status.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            results.isEmpty() && !isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Search across ${enabledSources.size} sources", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("TPB, Nyaa, Archive.org work without login", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    val total = sourceStatuses.values.sumOf { it.resultCount }
                    val loading = sourceStatuses.values.count { it.loading }
                    Text(
                        "${results.size} results" + if (loading > 0) " • $loading sources loading..." else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(results, key = { it.id }) { result ->
                    val isDownloading = downloads.values.any {
                        it.name.contains(result.title.take(15), ignoreCase = true)
                    }
                    TorrentResultItem(
                        result = result,
                        isDownloading = isDownloading,
                        onDownload = { viewModel.download(result) }
                    )
                }
            }
        }
    }
}

@Composable
fun SourceChip(source: SearchSource, status: SourceStatus) {
    val containerColor = when {
        status.loading -> MaterialTheme.colorScheme.surfaceVariant
        status.error != null -> MaterialTheme.colorScheme.errorContainer
        status.resultCount > 0 -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        status.error != null -> MaterialTheme.colorScheme.onErrorContainer
        status.resultCount > 0 -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        modifier = Modifier.height(28.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(source.meta.emoji, style = MaterialTheme.typography.labelSmall)
            Text(
                source.meta.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor
            )
            when {
                status.loading -> CircularProgressIndicator(
                    modifier = Modifier.size(10.dp),
                    strokeWidth = 1.5.dp,
                    color = contentColor
                )
                status.error != null -> Icon(
                    Icons.Default.Error, null,
                    tint = contentColor,
                    modifier = Modifier.size(12.dp)
                )
                status.resultCount > 0 -> Text(
                    "${status.resultCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor
                )
                else -> Icon(
                    Icons.Default.Remove, null,
                    tint = contentColor,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
fun TorrentResultItem(
    result: TorrentResult,
    isDownloading: Boolean = false,
    onDownload: () -> Unit
) {
    val isDirect = result.magnetLink.startsWith("http") && !result.magnetLink.contains("magnet:?")

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        result.source.take(3).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(result.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (result.artist != null) {
                    Text(result.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (result.sizeBytes > 1024) Text(result.sizeBytes.formatSize(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (result.seeders > 0) Text("S:${result.seeders}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(result.source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    if (isDirect) Text("STREAM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }
            if (isDownloading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onDownload) {
                    Icon(
                        if (isDirect) Icons.Default.PlayArrow else Icons.Default.Download,
                        if (isDirect) "Play" else "Download",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
