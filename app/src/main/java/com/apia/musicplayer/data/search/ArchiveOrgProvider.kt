package com.apia.musicplayer.data.search

import android.util.Log
import com.apia.musicplayer.domain.model.TorrentResult
import com.apia.musicplayer.ui.screens.torrent.ArchiveFile
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArchiveOrgProvider @Inject constructor(private val client: OkHttpClient) : SearchProvider {
    override val name = "Archive.org"

    override suspend fun search(query: String): List<TorrentResult> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "https://archive.org/advancedsearch.php" +
            "?q=$encoded+mediatype:audio" +
            "&fl=identifier,title,creator,year,downloads" +
            "&rows=20&output=json"
        val json = try {
            get(url) ?: return emptyList()
        } catch (e: Exception) { return emptyList() }

        return try {
            val docs = JSONObject(json).getJSONObject("response").getJSONArray("docs")
            (0 until docs.length()).map { i ->
                val d = docs.getJSONObject(i)
                val id = d.optString("identifier", "")
                TorrentResult(
                    id = "archive_$id",
                    title  = d.optString("title", id),
                    artist = d.optString("creator", "").takeIf { it.isNotBlank() },
                    album  = null,
                    year   = d.optString("year", "").toIntOrNull(),
                    seeders  = d.optInt("downloads", 0),
                    leechers = 0,
                    sizeBytes = 0L,
                    // Сохраняем identifier в magnetLink, getMagnet() резолвит до MP3
                    magnetLink = id,
                    source = "Archive.org"
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    /**
     * Резолвим identifier до прямого URL первого аудиофайла.
     * Вызывается перед воспроизведением.
     */
    override suspend fun getMagnet(result: TorrentResult): String {
        val identifier = result.magnetLink.removePrefix("https://archive.org/download/")
        return resolveFirstAudio(identifier) ?: "https://archive.org/download/$identifier"
    }

    private fun resolveFirstAudio(identifier: String): String? {
        return try {
            val json = get("https://archive.org/metadata/$identifier") ?: return null
            val files = JSONObject(json).optJSONArray("files") ?: return null
            val audioExts = listOf("mp3", "ogg", "flac", "m4a", "opus", "aac")
            // Предпочитаем mp3 для совместимости
            val preferred = listOf("mp3", "ogg", "m4a", "aac", "flac", "opus")
            for (ext in preferred) {
                for (i in 0 until files.length()) {
                    val f = files.getJSONObject(i)
                    val name = f.optString("name", "")
                    if (name.lowercase().endsWith(".$ext")) {
                        val url = "https://archive.org/download/$identifier/$name"
                        Log.d("Archive", "Resolved: $url")
                        return url
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e("Archive", "Resolve failed: ${e.message}")
            null
        }
    }

    /** Список всех аудиофайлов — для диалога выбора */
    suspend fun getFileList(identifier: String): List<ArchiveFile> {
        return try {
            val json = get("https://archive.org/metadata/$identifier") ?: return emptyList()
            val files = JSONObject(json).optJSONArray("files") ?: return emptyList()
            val audioExts = listOf("mp3", "ogg", "flac", "m4a", "opus", "aac")
            (0 until files.length()).mapNotNull { i ->
                val f = files.getJSONObject(i)
                val name = f.optString("name", "")
                if (audioExts.any { name.lowercase().endsWith(".$it") }) {
                    ArchiveFile(
                        name = name,
                        url  = "https://archive.org/download/$identifier/$name",
                        size = f.optString("size", "0").toLongOrNull() ?: 0L
                    )
                } else null
            }.sortedBy { it.name }
        } catch (e: Exception) { emptyList() }
    }

    private fun get(url: String) = try {
        client.newCall(
            Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
        ).execute().use { it.body?.string() }
    } catch (e: Exception) { null }
}
