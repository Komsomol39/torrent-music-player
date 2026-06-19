package com.apia.musicplayer.data.search

import com.apia.musicplayer.domain.model.TorrentResult
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class X1337Provider @Inject constructor(private val client: OkHttpClient) : SearchProvider {
    override val name = "1337x"

    override suspend fun search(query: String): List<TorrentResult> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        return try {
            val html = get("https://1337x.to/category-search/$encoded/Music/1/") ?: return emptyList()
            val doc  = Jsoup.parse(html)
            doc.select("tbody tr").take(15).mapNotNull { row ->
                val name     = row.selectFirst("td.name a:nth-child(2)")?.text() ?: return@mapNotNull null
                val href     = row.selectFirst("td.name a:nth-child(2)")?.attr("href") ?: ""
                val seeders  = row.selectFirst("td.seeds")?.text()?.toIntOrNull() ?: 0
                val leechers = row.selectFirst("td.leeches")?.text()?.toIntOrNull() ?: 0
                val size     = row.selectFirst("td.size")?.ownText() ?: ""
                TorrentResult(
                    id = "1337x_${href.hashCode()}",
                    title = name,
                    artist = name.substringBefore(" - ").takeIf { name.contains(" - ") },
                    album = null, year = null,
                    seeders = seeders, leechers = leechers,
                    sizeBytes = parseSize(size),
                    magnetLink = "https://1337x.to$href",
                    source = "1337x"
                )
            }.sortedByDescending { it.seeders }
        } catch (e: Exception) { emptyList() }
    }

    override suspend fun getMagnet(result: TorrentResult): String {
        if (!result.magnetLink.startsWith("https://1337x.to/torrent/")) return result.magnetLink
        return try {
            val html = get(result.magnetLink) ?: return result.magnetLink
            Jsoup.parse(html).selectFirst("a[href^=magnet:]")?.attr("href") ?: result.magnetLink
        } catch (e: Exception) { result.magnetLink }
    }

    private fun get(url: String) = try {
        client.newCall(Request.Builder().url(url).header("User-Agent","Mozilla/5.0 (Android)").build())
            .execute().use { it.body?.string() }
    } catch (e: Exception) { null }

    private fun parseSize(s: String): Long {
        val n = Regex("[0-9.]+").find(s)?.value?.toDoubleOrNull() ?: return 0
        return when {
            s.contains("GB",true) -> (n*1_073_741_824).toLong()
            s.contains("MB",true) -> (n*1_048_576).toLong()
            else -> (n*1024).toLong()
        }
    }
}
