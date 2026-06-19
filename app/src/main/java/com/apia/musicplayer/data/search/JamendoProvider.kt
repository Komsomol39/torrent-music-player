package com.apia.musicplayer.data.search

import com.apia.musicplayer.domain.model.TorrentResult
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JamendoProvider @Inject constructor(private val client: OkHttpClient) : SearchProvider {
    override val name = "Jamendo"
    var clientId: String = "d1c41421"

    override suspend fun search(query: String): List<TorrentResult> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "https://api.jamendo.com/v3.0/tracks/?client_id=$clientId&format=json&limit=20&namesearch=$encoded&audioformat=mp32"
        val json = try {
            client.newCall(Request.Builder().url(url).header("User-Agent","Mozilla/5.0").build())
                .execute().use { it.body?.string() ?: "" }
        } catch (e: Exception) { return emptyList() }
        return try {
            val results = JSONObject(json).getJSONArray("results")
            (0 until results.length()).mapNotNull { i ->
                val t = results.getJSONObject(i)
                val dl = t.optString("audiodownload","").ifBlank { return@mapNotNull null }
                TorrentResult("jamendo_${t.optString("id")}",
                    t.optString("name",""), t.optString("artist_name","").takeIf { it.isNotBlank() },
                    t.optString("album_name","").takeIf { it.isNotBlank() },
                    t.optString("releasedate","").take(4).toIntOrNull(),
                    t.optInt("sharecount",0), t.optInt("listencount",0),
                    t.optLong("duration",0)*1000, dl, "Jamendo")
            }
        } catch (e: Exception) { emptyList() }
    }
}
