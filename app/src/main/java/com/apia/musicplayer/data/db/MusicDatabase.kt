package com.apia.musicplayer.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.apia.musicplayer.domain.model.Track
import com.apia.musicplayer.domain.model.Playlist
import com.apia.musicplayer.domain.model.PlaylistTrack

@Database(entities = [Track::class, Playlist::class, PlaylistTrack::class], version = 1, exportSchema = false)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
}