package com.apia.musicplayer.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class Track(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,           // ms
    val uri: String,              // local file URI or stream URL
    val artworkUri: String? = null,
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
)