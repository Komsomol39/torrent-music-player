package com.apia.musicplayer.data.scanner

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.apia.musicplayer.data.db.TrackDao
import com.apia.musicplayer.data.torrent.TorrentEngine
import com.apia.musicplayer.domain.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackDao: TrackDao,
    private val torrentEngine: TorrentEngine
) {
    /**
     * Полное сканирование: MediaStore + папка загрузок торрентов
     */
    suspend fun scanAll() = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()
        tracks += scanMediaStore()
        tracks += scanTorrentDownloads()
        // Дедупликация по URI
        val unique = tracks.distinctBy { it.uri }
        trackDao.upsertTracks(unique)
        unique.size
    }

    /**
     * Сканирует системный MediaStore — все аудио файлы на устройстве
     */
    private fun scanMediaStore(): List<Track> {
        val tracks = mutableListOf<Track>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATA,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, selection, null, sortOrder
        )?.use { cursor ->
            val idCol       = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdCol  = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (cursor.moveToNext()) {
                val id       = cursor.getLong(idCol)
                val title    = cursor.getString(titleCol) ?: "Unknown"
                val artist   = cursor.getString(artistCol) ?: "Unknown Artist"
                val album    = cursor.getString(albumCol) ?: "Unknown Album"
                val duration = cursor.getLong(durationCol)
                val albumId  = cursor.getLong(albumIdCol)

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                )
                val artworkUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"), albumId
                ).toString()

                tracks += Track(
                    id       = "ms_$id",
                    title    = title,
                    artist   = artist,
                    album    = album,
                    duration = duration,
                    uri      = contentUri.toString(),
                    artworkUri = artworkUri
                )
            }
        }
        return tracks
    }

    /**
     * Сканирует папку загрузок торрентов
     */
    private fun scanTorrentDownloads(): List<Track> {
        val audioExts = setOf("mp3", "flac", "m4a", "ogg", "opus", "wav", "aac")
        val tracks = mutableListOf<Track>()

        fun scanDir(dir: File) {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    scanDir(file)
                } else if (audioExts.any { file.name.lowercase().endsWith(".$it") }) {
                    val (title, artist) = parseFileName(file.nameWithoutExtension)
                    tracks += Track(
                        id       = "dl_${file.absolutePath.hashCode()}",
                        title    = title,
                        artist   = artist,
                        album    = file.parentFile?.name ?: "Downloads",
                        duration = getFileDuration(file),
                        uri      = file.toURI().toString(),
                        artworkUri = null
                    )
                }
            }
        }

        if (torrentEngine.downloadDir.exists()) {
            scanDir(torrentEngine.downloadDir)
        }
        return tracks
    }

    private fun parseFileName(name: String): Pair<String, String> {
        val separators = listOf(" - ", " – ", "_-_")
        for (sep in separators) {
            if (name.contains(sep)) {
                return name.substringAfter(sep).trim() to name.substringBefore(sep).trim()
            }
        }
        return name to "Unknown Artist"
    }

    private fun getFileDuration(file: File): Long {
        return try {
            val mmr = android.media.MediaMetadataRetriever()
            mmr.setDataSource(file.absolutePath)
            val dur = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            mmr.release()
            dur?.toLongOrNull() ?: 0L
        } catch (e: Exception) { 0L }
    }
}
