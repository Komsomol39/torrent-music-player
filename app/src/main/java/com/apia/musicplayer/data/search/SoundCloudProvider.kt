package com.apia.musicplayer.data.search
import com.apia.musicplayer.domain.model.TorrentResult
import okhttp3.*; import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundCloudProvider @Inject constructor(private val client: OkHttpClient) : SearchProvider {
    override val name = "SoundCloud"
    var clientId: String = ""

    override suspend fun search(query: String): List<TorrentResult> {
        val id = clientId.ifBlank { extractClientId() ?: return emptyList() }
        val enc = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "https://api-v2.soundcloud.com/search/tracks?q=$enc&client_id=$id&limit=20&filter.streamable=true"
        return try {
            val json = client.newCall(Request.Builder().url(url).header("User-Agent","Mozilla/5.0").build())
                .execute().use { it.body?.string() ?: "" }
            val coll = JSONObject(json).getJSONArray("collection")
            (0 until coll.length()).map { i ->
                val t = coll.getJSONObject(i)
                val streamUrl = "https://api.soundcloud.com/tracks/${t.getLong("id")}/stream?client_id=$id"
                TorrentResult("sc_${t.getLong("id")}", t.getString("title"),
                    t.getJSONObject("user").getString("username"), null, null,
                    t.optInt("favoritings_count"), 0, t.optLong("duration"),
                    streamUrl, "SoundCloud")
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun extractClientId(): String? = try {
        val html = client.newCall(Request.Builder().url("https://soundcloud.com").header("User-Agent","Mozilla/5.0").build())
            .execute().use { it.body?.string() ?: "" }
        Regex("client_id=([a-zA-Z0-9]+)").find(html)?.groupValues?.get(1)
    } catch (e: Exception) { null }
}
