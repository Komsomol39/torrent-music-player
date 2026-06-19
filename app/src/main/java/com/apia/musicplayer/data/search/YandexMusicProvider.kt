package com.apia.musicplayer.data.search

import com.apia.musicplayer.domain.model.TorrentResult
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YandexMusicProvider @Inject constructor(private val client: OkHttpClient) : SearchProvider {
    override val name = "Яндекс.Музыка"
    var token: String = ""

    override suspend fun search(query: String): List<TorrentResult> {
        if (token.isBlank()) return emptyList()
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val json = get("https://api.music.yandex.net/search?text=$encoded&type=track&page=0&pageSize=20") ?: return emptyList()
        return try {
            val tracks = JSONObject(json).getJSONObject("result").getJSONObject("tracks").getJSONArray("results")
            (0 until tracks.length()).map { i ->
                val t = tracks.getJSONObject(i)
                val id = t.optLong("id")
                TorrentResult("yandex_$id", t.optString("title",""),
                    t.optJSONArray("artists")?.optJSONObject(0)?.optString("name"),
                    t.optJSONArray("albums")?.optJSONObject(0)?.optString("title"),
                    t.optJSONArray("albums")?.optJSONObject(0)?.optInt("year"),
                    0, 0, 0L, "yandex_music://$id", "Яндекс")
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun get(url: String) = try {
        client.newCall(Request.Builder().url(url)
            .header("Authorization","OAuth $token")
            .header("X-Yandex-Music-Client","WindowsPhone/3.17").build())
            .execute().use { it.body?.string() }
    } catch (e: Exception) { null }
}
