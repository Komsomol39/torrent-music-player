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
    var arlCookie: String = ""

    override suspend fun search(query: String): List<TorrentResult> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val json = get("https://api.deezer.com/search?q=$encoded&limit=25") ?: return emptyList()
        return try {
            val items = JSONObject(json).getJSONArray("data")
            (0 until items.length()).map { i ->
                val t = items.getJSONObject(i)
                val preview = t.optString("preview","")
                TorrentResult("deezer_${t.optLong("id")}",
                    t.optString("title",""),
                    t.optJSONObject("artist")?.optString("name"),
                    t.optJSONObject("album")?.optString("title"),
                    null, t.optInt("rank",0)/10000, 0,
                    t.optLong("duration",0)*1000, preview, "Deezer")
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun get(url: String) = try {
        val rb = Request.Builder().url(url).header("User-Agent","Mozilla/5.0")
        if (arlCookie.isNotBlank()) rb.header("Cookie","arl=$arlCookie")
        client.newCall(rb.build()).execute().use { it.body?.string() }
    } catch (e: Exception) { null }
}
