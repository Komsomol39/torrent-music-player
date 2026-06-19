package com.apia.musicplayer.data.search

import com.apia.musicplayer.domain.model.TorrentResult
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
        val url = "https://archive.org/advancedsearch.php?q=$encoded+mediatype:audio" +
            "&fl=identifier,title,creator,year,downloads,format" +
            "&rows=20&output=json"
        val json = try {
            client.newCall(Request.Builder().url(url)
                .header("User-Agent","Mozilla/5.0").build())
                .execute().use { it.body?.string() ?: "" }
        } catch (e: Exception) { return emptyList() }
        return try {
            val docs = JSONObject(json).getJSONObject("response").getJSONArray("docs")
            (0 until docs.length()).map { i ->
                val d = docs.getJSONObject(i)
                val id = d.optString("identifier","")
                TorrentResult(
                    id = "archive_$id",
                    title = d.optString("title", id),
                    artist = d.optString("creator","").takeIf { it.isNotBlank() },
                    album = null,
                    year = d.optString("year","").toIntOrNull(),
                    seeders = d.optInt("downloads",0),
                    leechers = 0,
                    sizeBytes = 0L,
                    // magnetLink хранит identifier, getMagnet() разрезолвит до реального MP3
                    magnetLink = "https://archive.org/download/$id",
                    source = "Archive.org"
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    /**
     * Получаем прямую ссылку на MP3/OGG файл из коллекции Archive.org
     */
    override suspend fun getMagnet(result: TorrentResult): String {
        val identifier = result.magnetLink
            .removePrefix("https://archive.org/download/")
            .substringBefore("/")
        return try {
            val metaUrl = "https://archive.org/metadata/$identifier"
            val json = client.newCall(Request.Builder().url(metaUrl)
                .header("User-Agent","Mozilla/5.0").build())
                .execute().use { it.body?.string() ?: "" }
            val files = JSONObject(json).getJSONArray("files")
            val audioExts = listOf(".mp3",".ogg",".flac",".m4a",".opus")
            // Ищем первый аудио файл
            for (i in 0 until files.length()) {
                val f = files.getJSONObject(i)
                val name = f.optString("name","")
                if (audioExts.any { name.lowercase().endsWith(it) }) {
                    return "https://archive.org/download/$identifier/$name"
                }
            }
            result.magnetLink
        } catch (e: Exception) { result.magnetLink }
    }
}
