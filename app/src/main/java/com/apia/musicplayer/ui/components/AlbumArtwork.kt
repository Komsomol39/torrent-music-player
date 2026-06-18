package com.apia.musicplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import coil.compose.SubcomposeAsyncImage

@Composable
fun AlbumArtwork(uri: String?, modifier: Modifier = Modifier) {
    SubcomposeAsyncImage(
        model = uri,
        contentDescription = "Album art",
        modifier = modifier,
        loading = { ArtworkPlaceholder(modifier) },
        error = { ArtworkPlaceholder(modifier) }
    )
}

@Composable
private fun ArtworkPlaceholder(modifier: Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}