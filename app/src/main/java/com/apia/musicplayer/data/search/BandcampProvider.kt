package com.apia.musicplayer.data.search
import com.apia.musicplayer.domain.model.TorrentResult
import okhttp3.*
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BandcampProvider @Inject constructor(private val client: OkHttpClient) : SearchProvider {
    override val name = "Bandcamp"

    override suspend fun search(query: String): List<TorrentResult> {
        val enc = java.net.URLEncoder.encode(query, "UTF-8")
        return try {
            val html = client.newCall(Request.Builder()
                .url("https://bandcamp.com/search?q=$enc&item_type=t")
                .header("User-Agent","Mozilla/5.0").build())
                .execute().use { it.body?.string() ?: "" }
            val json = Regex("BandcampPlayer\.initRelationalFilters\((.+?)\);").find(html)
                ?.groupValues?.get(1) ?: return emptyList()
            val items = JSONObject(json).getJSONArray("tralbums")
            (0 until minOf(items.length(), 15)).mapNotNull { i ->
                val t = items.getJSONObject(i)
                val streamUrl = t.optJSONArray("tracks")?.getJSONObject(0)
                    ?.optJSONObject("file")?.optString("mp3-128") ?: return@mapNotNull null
                TorrentResult("bc_${t.optLong("id")}", t.optString("title",""),
                    t.optString("artist",""), t.optString("album",""), null,
                    0, 0, 0L, streamUrl, "Bandcamp")
            }
        } catch (e: Exception) { emptyList() }
    }
}