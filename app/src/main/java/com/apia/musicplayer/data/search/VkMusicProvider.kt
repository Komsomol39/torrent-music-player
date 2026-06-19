package com.apia.musicplayer.data.search

import com.apia.musicplayer.domain.model.TorrentResult
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * VK Audio API — требует токен Kate Mobile / VK Official
 * Пользователь вводит токен в настройках приложения.
 *
 * Endpoint: https://api.vk.com/method/audio.search
 * Документация: https://vodka2.github.io/vk-audio-token/method/audio_search/
 *
 * Возвращает прямые ссылки на MP3 — не torrent.
 * В TorrentResult.magnetLink кладём прямую ссылку на MP3.
 */
@Singleton
class VkMusicProvider @Inject constructor(
    private val client: OkHttpClient
) : SearchProvider {

    override val name = "VK Music"
    var token: String = ""
    // Kate Mobile User-Agent (необходим для работы audio API)
    private val userAgent = "KateMobileAndroid/56 lite-460 (Android 4.4.2; SDK 19; x86; unknown Android SDK built for x86; en)"

    override suspend fun search(query: String): List<TorrentResult> {
        if (token.isBlank()) return emptyList()
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "https://api.vk.com/method/audio.search" +
            "?q=$encoded&count=30&sort=0&autocomplete=1" +
            "&access_token=$token&v=5.131"

        val json = get(url) ?: return emptyList()
        val items = JSONObject(json)
            .getJSONObject("response")
            .getJSONArray("items")

        val results = mutableListOf<TorrentResult>()
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val artist   = item.optString("artist", "")
            val title    = item.optString("title", "")
            val duration = item.optLong("duration", 0) * 1000L
            val url2     = item.optString("url", "")
            if (url2.isBlank()) continue  // закрытый трек

            results += TorrentResult(
                id = "vk_${item.optLong("id")}",
                title = title,
                artist = artist,
                album = item.optString("album", null.toString()).takeIf { it != "null" },
                year = null,
                seeders = 0,
                leechers = 0,
                sizeBytes = duration,   // используем duration как "размер" для отображения
                magnetLink = url2,      // прямая ссылка на MP3
                source = "VK"
            )
        }
        return results
    }

    // VK audio = прямая ссылка, не magnet
    override suspend fun getMagnet(result: TorrentResult): String = result.magnetLink

    private fun get(url: String): String? {
        return try {
            val req = Request.Builder().url(url)
                .header("User-Agent", userAgent)
                .build()
            client.newCall(req).execute().use { it.body?.string() }
        } catch (e: Exception) { null }
    }
}
