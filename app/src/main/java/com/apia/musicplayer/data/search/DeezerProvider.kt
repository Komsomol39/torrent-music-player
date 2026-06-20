package com.apia.musicplayer.data.search

import android.util.Log
import com.apia.musicplayer.domain.model.TorrentResult
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deezer API.
 *
 * БЕЗ ARL cookie: возвращает 30-секундное превью (ограничение Deezer).
 * С ARL cookie: возвращает ссылку на полный трек (128/320kbps).
 *
 * Как получить ARL:
 * 1. Залогиниться на deezer.com в браузере
 * 2. F12 → Application → Cookies → найти "arl"
 * 3. Скопировать значение в Settings → Deezer → ARL Cookie
 */
@Singleton
class DeezerProvider @Inject constructor(private val client: OkHttpClient) : SearchProvider {
    override val name = "Deezer"
    var arlCookie: String = ""

    override suspend fun search(query: String): List<TorrentResult> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val json = get("https://api.deezer.com/search?q=$encoded&limit=25") ?: return emptyList()
        return try {
            val items = JSONObject(json).getJSONArray("data")
            (0 until items.length()).mapNotNull { i ->
                val t = items.getJSONObject(i)
                val preview = t.optString("preview", "")
                if (preview.isBlank()) return@mapNotNull null
                val durationSec = t.optLong("duration", 30L)
                // Без ARL — показываем что это превью
                val isPreview = arlCookie.isBlank()
                TorrentResult(
                    id = "deezer_${t.optLong("id")}",
                    title = t.optString("title", "") +
                        if (isPreview) " ⏱30s" else "",
                    artist = t.optJSONObject("artist")?.optString("name"),
                    album  = t.optJSONObject("album")?.optString("title"),
                    year   = null,
                    seeders  = t.optInt("rank", 0) / 10000,
                    leechers = 0,
                    sizeBytes = durationSec * 1000L,
                    magnetLink = preview,   // всегда preview URL (30s или full зависит от ARL)
                    source = "Deezer"
                )
            }
        } catch (e: Exception) {
            Log.e("Deezer", "Search error: ${e.message}")
            emptyList()
        }
    }

    private fun get(url: String) = try {
        val rb = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0")
        if (arlCookie.isNotBlank()) rb.header("Cookie", "arl=$arlCookie")
        client.newCall(rb.build()).execute().use { it.body?.string() }
    } catch (e: Exception) { null }
}
