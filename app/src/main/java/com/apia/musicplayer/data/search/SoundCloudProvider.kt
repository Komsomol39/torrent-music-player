package com.apia.musicplayer.data.search

import android.util.Log
import com.apia.musicplayer.domain.model.TorrentResult
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundCloudProvider @Inject constructor(private val client: OkHttpClient) : SearchProvider {
    override val name = "SoundCloud"

    // VERIFIED: clientId extracted from soundcloud.com JS bundle
    // Fallback from test: iErh0hlIS7lC1NEeRzcimBG8NFFF045C
    private val fallbackClientId = "iErh0hlIS7lC1NEeRzcimBG8NFFF045C"
    var clientId: String = fallbackClientId

    override suspend fun search(query: String): List<TorrentResult> {
        var results = searchWith(query, clientId)
        if (results.isEmpty()) {
            val newId = extractClientId()
            if (newId != null && newId != clientId) {
                clientId = newId
                Log.d("SC", "New clientId: $clientId")
                results = searchWith(query, clientId)
            }
        }
        return results
    }

    private fun searchWith(query: String, cid: String): List<TorrentResult> {
        val enc = java.net.URLEncoder.encode(query, "UTF-8")
        val data = get("https://api-v2.soundcloud.com/search/tracks?q=$enc&limit=20&client_id=$cid") ?: return emptyList()
        return try {
            val items = JSONObject(data).getJSONArray("collection")
            (0 until items.length()).map { i ->
                val t = items.getJSONObject(i)
                val id = t.optLong("id")
                TorrentResult(
                    id = "sc_$id",
                    title = t.optString("title", ""),
                    artist = t.optJSONObject("user")?.optString("username"),
                    album = null, year = null,
                    seeders = t.optInt("likes_count", 0),
                    leechers = t.optInt("playback_count", 0),
                    sizeBytes = t.optLong("duration", 0),
                    magnetLink = "https://api.soundcloud.com/tracks/$id/stream?client_id=$cid",
                    source = "SoundCloud"
                )
            }.also { Log.d("SC", "Found ${it.size}") }
        } catch (e: Exception) { emptyList() }
    }

    private fun extractClientId(): String? = try {
        val html = get("https://soundcloud.com") ?: return null
        val scriptUrl = Regex("""src="(https://a-v2\.sndcdn\.com/assets/[^"]+\.js)""")
            .findAll(html).lastOrNull()?.groupValues?.get(1) ?: return null
        val js = get(scriptUrl) ?: return null
        Regex("""client_id:"([a-zA-Z0-9]+)"""").find(js)?.groupValues?.get(1)
    } catch (e: Exception) { null }

    private fun get(url: String): String? = try {
        client.newCall(Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .build()).execute().use { if (it.isSuccessful) it.body?.string() else null }
    } catch (e: Exception) { null }
}
