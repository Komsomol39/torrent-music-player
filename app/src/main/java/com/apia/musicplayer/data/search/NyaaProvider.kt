package com.apia.musicplayer.data.search

import android.util.Log
import com.apia.musicplayer.domain.model.TorrentResult
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NyaaProvider @Inject constructor(private val client: OkHttpClient) : SearchProvider {
    override val name = "Nyaa.si"

    // RSS API: GET /?page=rss&q=<query>&c=2_0&f=0
    // c=2_0 = Audio, c=0_0 = All
    // Возвращает RSS XML
    // <link> в item = ПРЯМАЯ magnet-ссылка
    // <nyaa:seeders>, <nyaa:leechers>, <nyaa:size>

    override suspend fun search(query: String): List<TorrentResult> {
        val enc = java.net.URLEncoder.encode(query, "UTF-8")
        return try {
            // Сначала ищем в категории Audio
            val url = "https://nyaa.si/?page=rss&q=$enc&c=2_0&f=0"
            Log.d("Nyaa", "GET $url")
            val xml = get(url) ?: return emptyList()
            val results = parseRss(xml)
            Log.d("Nyaa", "Found ${results.size} results")
            // Если мало — добавляем из всех категорий
            if (results.size < 5) {
                val xml2 = get("https://nyaa.si/?page=rss&q=$enc&c=0_0&f=0") ?: return results
                val all = parseRss(xml2)
                return (results + all).distinctBy { it.id }.sortedByDescending { it.seeders }
            }
            results
        } catch (e: Exception) {
            Log.e("Nyaa", "Search: ${e.message}")
            emptyList()
        }
    }

    private fun parseRss(xml: String): List<TorrentResult> {
        return try {
            val doc = Jsoup.parse(xml, "", Parser.xmlParser())
            doc.select("item").mapNotNull { item ->
                val title   = item.selectFirst("title")?.text() ?: return@mapNotNull null
                // link в Nyaa RSS — это magnet ссылка
                val magnet  = item.selectFirst("link")?.text()
                    ?: item.selectFirst("enclosure")?.attr("url")
                    ?: return@mapNotNull null
                if (!magnet.startsWith("magnet:") && !magnet.startsWith("http")) return@mapNotNull null
                val seeds   = item.selectFirst("nyaa|seeders, [nodeName=nyaa:seeders]")?.text()?.toIntOrNull() ?: 0
                val leech   = item.selectFirst("nyaa|leechers, [nodeName=nyaa:leechers]")?.text()?.toIntOrNull() ?: 0
                val sizeStr = item.selectFirst("nyaa|size, [nodeName=nyaa:size]")?.text() ?: ""
                TorrentResult(
                    id = "nyaa_${magnet.hashCode()}",
                    title  = title,
                    artist = title.substringBefore(" - ").takeIf { title.contains(" - ") },
                    album  = null, year = null,
                    seeders = seeds, leechers = leech,
                    sizeBytes = parseSize(sizeStr),
                    magnetLink = magnet,
                    source = "Nyaa"
                )
            }.sortedByDescending { it.seeders }
        } catch (e: Exception) {
            Log.e("Nyaa", "Parse: ${e.message}")
            emptyList()
        }
    }

    private fun get(url: String): String? = try {
        client.newCall(
            Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()
        ).execute().use { if (it.isSuccessful) it.body?.string() else null }
    } catch (e: Exception) { null }

    private fun parseSize(s: String): Long {
        val n = Regex("[0-9.]+").find(s)?.value?.toDoubleOrNull() ?: return 0
        return when {
            s.contains("GiB", true) || s.contains("GB", true) -> (n * 1_073_741_824).toLong()
            s.contains("MiB", true) || s.contains("MB", true) -> (n * 1_048_576).toLong()
            s.contains("KiB", true) || s.contains("KB", true) -> (n * 1024).toLong()
            else -> n.toLong()
        }
    }
}
