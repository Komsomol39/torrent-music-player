package com.apia.musicplayer.data.search
import com.apia.musicplayer.domain.model.TorrentResult
import okhttp3.*
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YandexMusicProvider @Inject constructor(private val client: OkHttpClient) : SearchProvider {
    override val name = "Яндекс.Музыка"
    var token: String = ""

    override suspend fun search(query: String): List<TorrentResult> {
        if (token.isBlank()) return emptyList()
        val enc = java.net.URLEncoder.encode(query, "UTF-8")
        return try {
            val json = client.newCall(Request.Builder()
                .url("https://api.music.yandex.net/search?text=$enc&type=track&page=0&pageSize=20")
                .header("Authorization", "OAuth $token")
                .header("User-Agent", "Yandex-Music-API").build())
                .execute().use { it.body?.string() ?: "" }
            val tracks = JSONObject(json).getJSONObject("result")
                .getJSONObject("tracks").getJSONArray("results")
            (0 until tracks.length()).map { i ->
                val t = tracks.getJSONObject(i)
                val artists = t.getJSONArray("artists")
                val artist = if (artists.length() > 0) artists.getJSONObject(0).getString("name") else ""
                TorrentResult(
                    id = "ym_${t.getLong("id")}",
                    title = t.getString("title"),
                    artist = artist,
                    album = t.optJSONObject("albums")?.optJSONObject("0")?.optString("title"),
                    year = null,
                    seeders = 0, leechers = 0,
                    sizeBytes = t.optLong("durationMs"),
                    magnetLink = "yandex_music://${t.getLong("id")}",
                    source = "Яндекс.Музыка"
                )
            }
        } catch (e: Exception) { emptyList() }
    }
}