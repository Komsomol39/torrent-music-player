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
    // Попробуем несколько зеркал
    private val mirrors = listOf(
        "https://rutor.info",
        "https://rutor.is",
        "https://rutor.top"
    )

    override suspend fun search(query: String): List<TorrentResult> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        for (base in mirrors) {
            try {
                // category 2 = music
                val html = get("$base/search/0/2/000/0/$encoded") ?: continue
                val results = parseResults(html, base)
                if (results.isNotEmpty()) return results
            } catch (e: Exception) { continue }
        }
        return emptyList()
    }

    private fun parseResults(html: String, base: String): List<TorrentResult> {
        val doc = Jsoup.parse(html)
        val rows = doc.select("table#index tr").drop(1)
        if (rows.isEmpty()) return emptyList()

        return rows.mapNotNull { row ->
            val cells = row.select("td")
            if (cells.size < 4) return@mapNotNull null
            // Название — ищем ссылку на torrent
            val titleEl = cells.getOrNull(1)?.selectFirst("a") ?: return@mapNotNull null
            val title = titleEl.text().ifBlank { return@mapNotNull null }
            // Magnet
            val magnet = row.selectFirst("a[href^=magnet:]")?.attr("href") ?: return@mapNotNull null
            val seeders = cells.getOrNull(4)?.text()?.trim()?.toIntOrNull() ?: 0
            val leechers = cells.getOrNull(5)?.text()?.trim()?.toIntOrNull() ?: 0
            val size = cells.getOrNull(3)?.text() ?: ""
            TorrentResult(
                id = "rutor_${magnet.hashCode()}",
                title = title,
                artist = title.substringBefore(" - ").takeIf { title.contains(" - ") },
                album = null, year = null,
                seeders = seeders, leechers = leechers,
                sizeBytes = parseSize(size),
                magnetLink = magnet,
                source = "RuTor"
            )
        }.sortedByDescending { it.seeders }
    }

    private fun get(url: String): String? = try {
        client.newCall(Request.Builder().url(url)
            .header("User-Agent","Mozilla/5.0 (Android; Mobile; rv:120.0) Gecko/120.0 Firefox/120.0")
            .header("Accept-Language","ru-RU,ru;q=0.9")
            .build())
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
