package com.apia.musicplayer.data.search
import com.apia.musicplayer.domain.model.TorrentResult
import okhttp3.*
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FmaProvider @Inject constructor(private val client: OkHttpClient) : SearchProvider {
    override val name = "Free Music Archive"

    override suspend fun search(query: String): List<TorrentResult> {
        val enc = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "https://freemusicarchive.org/api/get/tracks.json?api_key=60BLHNQCAOUFPIBZ&search=$enc&limit=20"
        return try {
            val json = client.newCall(Request.Builder().url(url)
                .header("User-Agent","Mozilla/5.0").build())
                .execute().use { it.body?.string() ?: "" }
            val dataset = JSONObject(json).getJSONArray("dataset")
            (0 until dataset.length()).mapNotNull { i ->
                val t = dataset.getJSONObject(i)
                val url2 = t.optString("track_url","")
                if (url2.isBlank()) return@mapNotNull null
                TorrentResult("fma_${t.optString("track_id","$i")}",
                    t.optString("track_title",""), t.optString("artist_name",""),
                    t.optString("album_title",""), null,
                    0, 0, 0L, url2, "Free Music Archive")
            }
        } catch (e: Exception) { emptyList() }
    }
}