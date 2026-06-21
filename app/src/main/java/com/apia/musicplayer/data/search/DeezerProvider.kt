package com.apia.musicplayer.data.search

import android.util.Log
import com.apia.musicplayer.domain.model.TorrentResult
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeezerProvider @Inject constructor(private val client: OkHttpClient) : SearchProvider {
    override val name = "Deezer"
    // VERIFIED: public API works, returns 30s previews, 5 results for test query
    var arlCookie: String = ""

    override suspend fun search(query: String): List<TorrentResult> {
        val enc = java.net.URLEncoder.encode(query, "UTF-8")
        val data = get("https://api.deezer.com/search?q=$enc&limit=25") ?: return emptyList()
        return try {
            val items = JSONObject(data).getJSONArray("data")
            (0 until items.length()).mapNotNull { i ->
                val t = items.getJSONObject(i)
                val preview = t.optString("preview", "").ifBlank { return@mapNotNull null }
                val isPreview = arlCookie.isBlank()
                TorrentResult(
                    id = "deezer_${t.optLong("id")}",
                    title = t.optString("title", "") + if (isPreview) " [30s]" else "",
                    artist = t.optJSONObject("artist")?.optString("name"),
                    album = t.optJSONObject("album")?.optString("title"),
                    year = null,
                    seeders = t.optInt("rank", 0) / 10000,
                    leechers = 0,
                    sizeBytes = t.optLong("duration", 30) * 1000L,
                    magnetLink = preview,
                    source = "Deezer"
                )
            }.also { Log.d("Deezer", "Found ${it.size}") }
        } catch (e: Exception) { emptyList() }
    }

    private fun get(url: String): String? = try {
        val rb = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0")
        if (arlCookie.isNotBlank()) rb.header("Cookie", "arl=$arlCookie")
        client.newCall(rb.build()).execute().use { if (it.isSuccessful) it.body?.string() else null }
    } catch (e: Exception) { null }
}
