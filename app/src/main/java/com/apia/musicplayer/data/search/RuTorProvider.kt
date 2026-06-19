package com.apia.musicplayer.data.search

import com.apia.musicplayer.domain.model.TorrentResult
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuTorProvider @Inject constructor(private val client: OkHttpClient) : SearchProvider {

    override val name = "RuTor"
    private val baseUrl = "https://rutor.info"

    override suspend fun search(query: String): List<TorrentResult> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        // category 2 = music
        val html = get("$baseUrl/search/0/2/000/0/$encoded")
        val doc = Jsoup.parse(html)
        val results = mutableListOf<TorrentResult>()

        doc.select("table#index tr").drop(1).forEach { row ->
            val titleEl = row.selectFirst("td a[href*='/torrent/']") ?: return@forEach
            val title   = titleEl.text()
            val href    = titleEl.attr("href")
            val magnet  = row.selectFirst("a[href^=magnet:]")?.attr("href") ?: return@forEach
            val seeders = row.select("td").getOrNull(4)?.text()?.trim()?.toIntOrNull() ?: 0
            val leechers = row.select("td").getOrNull(5)?.text()?.trim()?.toIntOrNull() ?: 0
            val size    = row.select("td").getOrNull(3)?.text() ?: ""

            results += TorrentResult(
                id = "rutor_${href.hashCode()}",
                title = title,
                artist = parseArtist(title),
                album = null,
                year = parseYear(title),
                seeders = seeders,
                leechers = leechers,
                sizeBytes = parseSize(size),
                magnetLink = magnet,
                source = "RuTor"
            )
        }
        return results.sortedByDescending { it.seeders }
    }

    private fun get(url: String): String {
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            .build()
        return client.newCall(req).execute().use { it.body?.string() ?: "" }
    }

    private fun parseArtist(title: String): String? {
        val sep = listOf(" - ", " – ")
        return sep.firstNotNullOfOrNull { if (title.contains(it)) title.substringBefore(it).trim() else null }
    }
    private fun parseYear(title: String) = Regex("\\b(19|20)\\d{2}\\b").find(title)?.value?.toIntOrNull()
    private fun parseSize(s: String): Long {
        val n = Regex("[0-9.]+").find(s)?.value?.toDoubleOrNull() ?: return 0
        return when {
            s.contains("GB", true) -> (n * 1_073_741_824).toLong()
            s.contains("MB", true) -> (n * 1_048_576).toLong()
            else -> (n * 1024).toLong()
        }
    }
}
