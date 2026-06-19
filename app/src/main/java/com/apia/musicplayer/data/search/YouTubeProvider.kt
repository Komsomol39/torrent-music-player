package com.apia.musicplayer.data.search

import com.apia.musicplayer.domain.model.TorrentResult
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * YouTube поиск через YouTube Data API v3 (бесплатно, 10K req/день).
 * Пользователь вводит API ключ в настройках.
 *
 * Результаты открываются через ExoPlayer напрямую через
 * специальный MediaSource (android.media.MediaExtractor или
 * media3-datasource-cronet).
 *
 * Для воспроизведения YouTube используем:
 *   youtube-dl / yt-dlp android port — получаем direct audio URL
 * или
 *   android.webkit.WebView + JS bridge
 */
@Singleton
class YouTubeProvider @Inject constructor(
    private val client: OkHttpClient
) : SearchProvider {

    override val name = "YouTube"
    var apiKey: String = ""

    override suspend fun search(query: String): List<TorrentResult> {
        if (apiKey.isBlank()) return searchNoAuth(query)

        val encoded = java.net.URLEncoder.encode("$query music audio", "UTF-8")
        val url = "https://www.googleapis.com/youtube/v3/search" +
            "?part=snippet&q=$encoded&type=video&videoCategoryId=10" + // 10 = Music
            "&maxResults=20&key=$apiKey"

        return try {
            val json = get(url) ?: return emptyList()
            val items = JSONObject(json).getJSONArray("items")
            (0 until items.length()).mapNotNull { i ->
                val item = items.getJSONObject(i)
                val snippet = item.getJSONObject("snippet")
                val videoId = item.getJSONObject("id").optString("videoId") ?: return@mapNotNull null
                val title = snippet.getString("title")
                val channel = snippet.getString("channelTitle")

                TorrentResult(
                    id = "yt_$videoId",
                    title = title,
                    artist = channel,
                    album = null,
                    year = null,
                    seeders = 0,
                    leechers = 0,
                    sizeBytes = 0L,
                    magnetLink = "https://www.youtube.com/watch?v=$videoId",
                    source = "YouTube"
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    /**
     * Поиск без ключа через YouTube suggest / scraping
     * (ограниченный, но работает без API ключа)
     */
    private fun searchNoAuth(query: String): List<TorrentResult> {
        return try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://www.youtube.com/results?search_query=$encoded+music"
            val html = get(url) ?: return emptyList()

            // Извлекаем videoId из ytInitialData JSON
            val jsonStr = html.substringAfter("var ytInitialData = ")
                .substringBefore(";</script>")
                .ifBlank { return emptyList() }

            val data = JSONObject(jsonStr)
            val contents = data
                .getJSONObject("contents")
                .getJSONObject("twoColumnSearchResultsRenderer")
                .getJSONObject("primaryContents")
                .getJSONObject("sectionListRenderer")
                .getJSONArray("contents")
                .getJSONObject(0)
                .getJSONObject("itemSectionRenderer")
                .getJSONArray("contents")

            val results = mutableListOf<TorrentResult>()
            for (i in 0 until minOf(contents.length(), 15)) {
                try {
                    val renderer = contents.getJSONObject(i)
                        .optJSONObject("videoRenderer") ?: continue
                    val videoId = renderer.getString("videoId")
                    val title = renderer.getJSONObject("title")
                        .getJSONArray("runs").getJSONObject(0).getString("text")
                    val channel = renderer.optJSONObject("ownerText")
                        ?.getJSONArray("runs")?.getJSONObject(0)?.getString("text") ?: ""

                    results += TorrentResult(
                        id = "yt_$videoId",
                        title = title,
                        artist = channel,
                        album = null, year = null,
                        seeders = 0, leechers = 0, sizeBytes = 0L,
                        magnetLink = "https://www.youtube.com/watch?v=$videoId",
                        source = "YouTube"
                    )
                } catch (e: Exception) { continue }
            }
            results
        } catch (e: Exception) { emptyList() }
    }

    private fun get(url: String): String? = try {
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Android)")
            .build()
        client.newCall(req).execute().use { it.body?.string() }
    } catch (e: Exception) { null }
}
