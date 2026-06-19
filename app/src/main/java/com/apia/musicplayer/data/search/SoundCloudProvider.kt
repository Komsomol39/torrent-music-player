package com.apia.musicplayer.data.search

import com.apia.musicplayer.domain.model.TorrentResult
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundCloudProvider @Inject constructor(private val client: OkHttpClient) : SearchProvider {
    override val name = "SoundCloud"
    var clientId: String = ""

    private fun extractClientId(): String {
        return try {
            val html = get("https://soundcloud.com") ?: return ""
            val scriptUrl = Regex("""src="(https://a-v2\.sndcdn\.com/assets/[^"]+\.js)""")
                .findAll(html).lastOrNull()?.groupValues?.get(1) ?: return ""
            val js = get(scriptUrl) ?: return ""
            Regex("""client_id:"([a-zA-Z0-9]+)"""").find(js)?.groupValues?.get(1) ?: ""
        } catch (e: Exception) { "" }
    }

    override suspend fun search(query: String): List<TorrentResult> {
        if (clientId.isBlank()) clientId = extractClientId()
        if (clientId.isBlank()) return emptyList()
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "https://api-v2.soundcloud.com/search/tracks?q=$encoded&limit=20&client_id=$clientId"
        val json = get(url) ?: return emptyList()
        return try {
            val items = JSONObject(json).getJSONArray("collection")
            (0 until items.length()).mapNotNull { i ->
                val track = items.getJSONObject(i)
                val streamUrl = "https://api.soundcloud.com/tracks/${track.optLong("id")}/stream?client_id=$clientId"
                TorrentResult("sc_${track.optLong("id")}", track.optString("title",""),
                    track.optJSONObject("user")?.optString("username"),
                    null, null, track.optInt("likes_count",0), track.optInt("playback_count",0),
                    track.optLong("duration",0), streamUrl, "SoundCloud")
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun get(url: String) = try {
        client.newCall(Request.Builder().url(url).header("User-Agent","Mozilla/5.0 (Android)").build())
            .execute().use { it.body?.string() }
    } catch (e: Exception) { null }
}
