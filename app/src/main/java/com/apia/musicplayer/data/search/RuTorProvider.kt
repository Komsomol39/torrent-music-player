package com.apia.musicplayer.data.search

import android.util.Log
import com.apia.musicplayer.domain.model.TorrentResult
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuTorProvider @Inject constructor(private val client: OkHttpClient) : SearchProvider {
    override val name = "RuTor"

    // VERIFIED HTML structure (2025):
    // <tr class='gai'/'tum'>
    //   <td>DATE</td>
    //   <td colspan=2>
    //     <a href='//d.rutor.info/download/ID'>D</a>
    //     <a href='magnet:?xt=...'>M</a>
    //     <a href='/torrent/ID/slug'>TITLE</a>
    //   </td>
    //   <td>SIZE</td>
    //   <td><span class='green'>...&nbsp;SEEDS</span>...<span class='red'>&nbsp;LEECH</span></td>
    // </tr>

    private val mirrors = listOf("https://rutor.info", "https://rutor.is")

    override suspend fun search(query: String): List<TorrentResult> {
        val enc = java.net.URLEncoder.encode(query, "UTF-8")
        for (base in mirrors) {
            try {
                val html = get("$base/search/0/0/000/0/$enc") ?: continue
                val results = parse(html)
                if (results.isNotEmpty()) {
                    Log.d("RuTor", "Found ${results.size} from $base")
                    return results
                }
            } catch (e: Exception) {
                Log.w("RuTor", "$base: ${e.message}")
            }
        }
        return emptyList()
    }

    private fun parse(html: String): List<TorrentResult> {
        val doc = Jsoup.parse(html)
        // Таблица с результатами имеет id='index' или строки с классами gai/tum
        val rows = doc.select("tr.gai, tr.tum")
        Log.d("RuTor", "Rows found: ${rows.size}")
        return rows.mapNotNull { row ->
            val cells = row.select("td")
            if (cells.size < 3) return@mapNotNull null
            // td[1] содержит magnet и название
            val contentCell = cells.getOrNull(1) ?: cells.getOrNull(0) ?: return@mapNotNull null
            val magnet = contentCell.selectFirst("a[href^=magnet:]")?.attr("href")
                ?: return@mapNotNull null
            val titleEl = contentCell.selectFirst("a[href^=/torrent/]")
                ?: return@mapNotNull null
            val title = titleEl.text().trim().ifBlank { return@mapNotNull null }
            // Размер
            val sizeText = cells.getOrNull(cells.size - 2)?.text() ?: ""
            // Сиды — в span.green, текст типа '↑ 2' или просто '2'
            val seedsText = row.selectFirst("span.green")?.text() ?: ""
            val seeds = Regex("\\d+").findAll(seedsText).lastOrNull()?.value?.toIntOrNull() ?: 0
            val leechText = row.selectFirst("span.red")?.text() ?: ""
            val leech = Regex("\\d+").findAll(leechText).lastOrNull()?.value?.toIntOrNull() ?: 0
            TorrentResult(
                id = "rutor_${magnet.hashCode()}",
                title = title,
                artist = title.substringBefore(" - ").trim().takeIf { title.contains(" - ") },
                album = null, year = null,
                seeders = seeds, leechers = leech,
                sizeBytes = parseSize(sizeText),
                magnetLink = magnet,
                source = "RuTor"
            )
        }.sortedByDescending { it.seeders }
    }

    private fun get(url: String): String? = try {
        client.newCall(Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .header("Accept-Language", "ru-RU,ru;q=0.9")
            .build()).execute().use { if (it.isSuccessful) it.body?.string() else null }
    } catch (e: Exception) { null }

    private fun parseSize(s: String): Long {
        val n = Regex("[0-9.]+").find(s)?.value?.toDoubleOrNull() ?: return 0
        return when {
            s.contains("GB") || s.contains("ГБ") -> (n * 1_073_741_824).toLong()
            s.contains("MB") || s.contains("МБ") -> (n * 1_048_576).toLong()
            s.contains("KB") || s.contains("КБ") -> (n * 1024).toLong()
            else -> 0
        }
    }
}
