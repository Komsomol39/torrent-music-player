package com.apia.musicplayer.data.search

import com.apia.musicplayer.domain.model.TorrentResult
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeezerProvider @Inject constructor(private val client: OkHttpClient) : SearchProvider {
    override val name = "Deezer"
    // ARL cookie даёт полные треки, без него — 30-сек превью
    var arlCookie: String = ""

    override suspend fun search(query: String): List<TorrentResult> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val json = get("https://api.deezer.com/search?q=$encoded&limit=25") ?: return emptyList()
        return try {
            val items = JSONObject(json).getJSONArray("data")
            (0 until items.length()).map { i ->
                val t = items.getJSONObject(i)
                val preview = t.optString("preview", "")
                val durationSec = t.optLong("duration", 30L)
                // Без ARL — 30-сек превью, с ARL — полный трек
                val streamUrl = if (arlCookie.isNotBlank())
                    preview  // TODO: полный трек требует дополнительного API вызова
                else preview
                val quality = if (arlCookie.isNotBlank()) "MP3 320" else "Preview 30s"
                TorrentResult(
                    id = "deezer_${t.optLong("id")}",
                    title = t.optString("title", "") +
                        if (arlCookie.isBlank()) " [30s preview]" else "",
                    artist = t.optJSONObject("artist")?.optString("name"),
                    album = t.optJSONObject("album")?.optString("title"),
                    year = null,
                    seeders = t.optInt("rank", 0) / 10000,
                    leechers = 0,
                    sizeBytes = durationSec * 1000L,  // duration в мс для formatDuration
                    magnetLink = streamUrl,
                    source = "Deezer"
                )
            }.filter { it.magnetLink.isNotBlank() }
        } catch (e: Exception) { emptyList() }
    }

    private fun get(url: String) = try {
        val rb = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0")
        if (arlCookie.isNotBlank()) rb.header("Cookie", "arl=$arlCookie")
        client.newCall(rb.build()).execute().use { it.body?.string() }
    } catch (e: Exception) { null }
}
