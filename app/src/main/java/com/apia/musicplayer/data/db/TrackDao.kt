package com.apia.musicplayer.data.db

import androidx.room.*
import com.apia.musicplayer.domain.model.Track
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY title ASC")
    fun getAllTracks(): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE isFavorite = 1 ORDER BY title ASC")
    fun getFavorites(): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun getTrackById(id: String): Track?

    @Query("SELECT * FROM tracks ORDER BY playCount DESC LIMIT 20")
    fun getMostPlayed(): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%'")
    fun search(query: String): Flow<List<Track>>

    @Upsert
    suspend fun upsertTrack(track: Track)

    @Upsert
    suspend fun upsertTracks(tracks: List<Track>)

    @Query("UPDATE tracks SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)

    @Query("UPDATE tracks SET playCount = playCount + 1 WHERE id = :id")
    suspend fun incrementPlayCount(id: String)

    @Delete
    suspend fun deleteTrack(track: Track)
}