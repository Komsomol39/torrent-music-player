package com.apia.musicplayer.data.search
import com.apia.musicplayer.domain.model.TorrentResult
import okhttp3.*
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JamendoProvider @Inject constructor(private val client: OkHttpClient) : SearchProvider {
    override val name = "Jamendo"
    var apiKey: String = "d1c41421"  // публичный demo ключ, работает для поиска

    override suspend fun search(query: String): List<TorrentResult> {
        val enc = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "https://api.jamendo.com/v3.0/tracks/?client_id=$apiKey" +
            "&format=json&limit=20&namesearch=$enc&audioformat=mp32&include=musicinfo"
        return try {
            val json = client.newCall(Request.Builder().url(url)
                .header("User-Agent","Mozilla/5.0").build())
                .execute().use { it.body?.string() ?: "" }
            val results = JSONObject(json).getJSONArray("results")
            (0 until results.length()).map { i ->
                val t = results.getJSONObject(i)
                TorrentResult("jamendo_${t.getString("id")}", t.getString("name"),
                    t.getString("artist_name"), t.optString("album_name",""),
                    t.optString("releasedate","").take(4).toIntOrNull(),
                    0, 0, t.optLong("duration")*1000,
                    t.optString("audio",""), "Jamendo")
            }.filter { it.magnetLink.isNotBlank() }
        } catch (e: Exception) { emptyList() }
    }
}