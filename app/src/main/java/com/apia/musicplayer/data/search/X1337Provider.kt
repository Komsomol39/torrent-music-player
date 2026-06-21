package com.apia.musicplayer.data.search

import android.util.Log
import com.apia.musicplayer.domain.model.TorrentResult
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class X1337Provider @Inject constructor(private val client: OkHttpClient) : SearchProvider {
    override val name = "1337x"

    // GET /category-search/<query>/Music/1/
    // Или /search/<query>/1/ для всех категорий
    private val mirrors = listOf(
        "https://1337x.to",
        "https://1337x.st",
        "https://x1337x.ws"
    )

    override suspend fun search(query: String): List<TorrentResult> {
        val enc = java.net.URLEncoder.encode(query, "UTF-8")
        for (base in mirrors) {
            try {
                // Сначала Music категория
                val html = get("$base/category-search/$enc/Music/1/")
                    ?: get("$base/search/$enc/1/")
                    ?: continue
                val results = parseResults(html, base)
                if (results.isNotEmpty()) {
                    Log.d("1337x", "Found ${results.size} from $base")
                    return results
                }
            } catch (e: Exception) {
                Log.w("1337x", "$base: ${e.message}")
            }
        }
        return emptyList()
    }

    private fun parseResults(html: String, base: String): List<TorrentResult> {
        val doc = Jsoup.parse(html)
        return doc.select("tbody tr").mapNotNull { row ->
            // Название — второй <a> в td.name
            val nameEl = row.selectFirst("td.name a:nth-child(2)") ?: return@mapNotNull null
            val title  = nameEl.text().ifBlank { return@mapNotNull null }
            val href   = nameEl.attr("href").ifBlank { return@mapNotNull null }
            val seeds  = row.selectFirst("td.seeds")?.text()?.toIntOrNull() ?: 0
            val leech  = row.selectFirst("td.leeches")?.text()?.toIntOrNull() ?: 0
            // Размер — td.size без <span>
            val size   = row.selectFirst("td.size")?.ownText() ?: ""
            TorrentResult(
                id = "1337x_${href.hashCode()}",
                title  = title,
                artist = title.substringBefore(" - ").takeIf { title.contains(" - ") },
                album  = null, year = null,
                seeders = seeds, leechers = leech,
                sizeBytes = parseSize(size),
                magnetLink = "$base$href",  // URL страницы — резолвим в getMagnet()
                source = "1337x"
            )
        }.sortedByDescending { it.seeders }.take(20)
    }

    // Открываем страницу раздачи и берём magnet
    override suspend fun getMagnet(result: TorrentResult): String {
        if (result.magnetLink.startsWith("magnet:")) return result.magnetLink
        return try {
            val html = get(result.magnetLink) ?: return result.magnetLink
            Jsoup.parse(html).selectFirst("a[href^=magnet:]")?.attr("href")
                ?: result.magnetLink
        } catch (e: Exception) { result.magnetLink }
    }

    private fun get(url: String): String? = try {
        client.newCall(
            Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
        ).execute().use { if (it.isSuccessful) it.body?.string() else null }
    } catch (e: Exception) { null }

    private fun parseSize(s: String): Long {
        val n = Regex("[0-9.]+").find(s)?.value?.toDoubleOrNull() ?: return 0
        return when {
            s.contains("GB", true) -> (n * 1_073_741_824).toLong()
            s.contains("MB", true) -> (n * 1_048_576).toLong()
            s.contains("KB", true) -> (n * 1024).toLong()
            else -> n.toLong()
        }
    }
}
