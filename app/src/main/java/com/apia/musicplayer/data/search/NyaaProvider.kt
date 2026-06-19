package com.apia.musicplayer.data.search

import com.apia.musicplayer.domain.model.TorrentResult
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NyaaProvider @Inject constructor(private val client: OkHttpClient) : SearchProvider {
    override val name = "Nyaa"

    override suspend fun search(query: String): List<TorrentResult> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        return try {
            val xml = get("https://nyaa.si/?page=rss&q=$encoded&c=3_0&f=0") ?: return emptyList()
            val doc = Jsoup.parse(xml, "", Parser.xmlParser())
            doc.select("item").take(15).map { item ->
                val title    = item.selectFirst("title")?.text() ?: ""
                val link     = item.selectFirst("link")?.text() ?: ""
                val seeders  = item.selectFirst("nyaa|seeders")?.text()?.toIntOrNull() ?: 0
                val leechers = item.selectFirst("nyaa|leechers")?.text()?.toIntOrNull() ?: 0
                val sizeStr  = item.selectFirst("nyaa|size")?.text() ?: ""
                TorrentResult(
                    id = "nyaa_${link.hashCode()}",
                    title = title,
                    artist = title.substringBefore(" - ").takeIf { title.contains(" - ") },
                    album = null, year = null,
                    seeders = seeders, leechers = leechers,
                    sizeBytes = parseSize(sizeStr),
                    magnetLink = link,
                    source = "Nyaa"
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun get(url: String) = try {
        client.newCall(Request.Builder().url(url).header("User-Agent","Mozilla/5.0").build())
            .execute().use { it.body?.string() }
    } catch (e: Exception) { null }

    private fun parseSize(s: String): Long {
        val n = Regex("[0-9.]+").find(s)?.value?.toDoubleOrNull() ?: return 0
        return when {
            s.contains("GiB",true) || s.contains("GB",true) -> (n*1_073_741_824).toLong()
            s.contains("MiB",true) || s.contains("MB",true) -> (n*1_048_576).toLong()
            else -> (n*1024).toLong()
        }
    }
}
