package com.apia.musicplayer.data.search

import com.apia.musicplayer.domain.model.TorrentResult
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FmaProvider @Inject constructor(private val client: OkHttpClient) : SearchProvider {
    override val name = "Free Music Archive"
    private val apiKey = "60BLHNQCAOUFPIBZ"

    override suspend fun search(query: String): List<TorrentResult> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "https://freemusicarchive.org/api/get/tracks.json?api_key=$apiKey&title=$encoded&limit=20&page=1"
        val json = try {
            client.newCall(Request.Builder().url(url).header("User-Agent","Mozilla/5.0").build())
                .execute().use { it.body?.string() ?: "" }
        } catch (e: Exception) { return emptyList() }
        return try {
            val dataset = JSONObject(json).getJSONArray("dataset")
            (0 until dataset.length()).mapNotNull { i ->
                val t = dataset.getJSONObject(i)
                val mp3 = t.optString("track_file","").ifBlank { return@mapNotNull null }
                TorrentResult("fma_${t.optString("track_id")}",
                    t.optString("track_title",""), t.optString("artist_name","").takeIf { it.isNotBlank() },
                    t.optString("album_title","").takeIf { it.isNotBlank() },
                    t.optString("track_date_created","").take(4).toIntOrNull(),
                    t.optInt("track_listens",0)/1000, 0, 0L, mp3, "FMA")
            }
        } catch (e: Exception) { emptyList() }
    }
}
