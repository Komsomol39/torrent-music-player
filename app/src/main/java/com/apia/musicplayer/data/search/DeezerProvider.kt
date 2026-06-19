package com.apia.musicplayer.data.search
import com.apia.musicplayer.domain.model.TorrentResult
import okhttp3.*
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeezerProvider @Inject constructor(private val client: OkHttpClient) : SearchProvider {
    override val name = "Deezer"
    var arlToken: String = ""

    override suspend fun search(query: String): List<TorrentResult> {
        val enc = java.net.URLEncoder.encode(query, "UTF-8")
        return try {
            val reqBuilder = Request.Builder()
                .url("https://api.deezer.com/search/track?q=$enc&limit=25")
                .header("User-Agent", "Mozilla/5.0")
            if (arlToken.isNotBlank()) reqBuilder.header("Cookie", "arl=$arlToken")
            val json = client.newCall(reqBuilder.build()).execute().use { it.body?.string() ?: "" }
            val data = JSONObject(json).getJSONArray("data")
            (0 until data.length()).map { i ->
                val t = data.getJSONObject(i)
                TorrentResult(
                    id = "deezer_${t.getLong("id")}",
                    title = t.getString("title"),
                    artist = t.getJSONObject("artist").getString("name"),
                    album = t.getJSONObject("album").getString("title"),
                    year = null,
                    seeders = 0, leechers = 0,
                    sizeBytes = t.optLong("duration") * 1000,
                    magnetLink = t.optString("preview", ""),
                    source = "Deezer"
                )
            }.filter { it.magnetLink.isNotBlank() }
        } catch (e: Exception) { emptyList() }
    }
}