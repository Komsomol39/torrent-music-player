package com.apia.musicplayer.domain.model

data class TorrentResult(
    val id: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val year: Int?,
    val seeders: Int,
    val leechers: Int,
    val sizeBytes: Long,
    val magnetLink: String,
    val source: String          // e.g. "rutracker", "1337x"
)