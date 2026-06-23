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

    // CONFIRMED HTML (2025):
    // <tr class='gai'> or <tr class='tum'>
    //   <td>DATE</td>
    //   <td colspan = "2">  <- colspan с пробелами!
    //     <a href='//d.rutor.info/download/ID'>D</a>
    //     <a href='magnet:?xt=...&dn=rutor.info&tr=...'>M</a>
    //     <a href='/torrent/ID/slug'>TITLE TEXT</a>
    //   </td>
    //   <td>SIZE MB</td>
    //   <td><span class='green'>&nbsp;N</span>...<span class='red'>&nbsp;N</span></td>
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
        // Ищем строки с классами gai или tum — это строки с результатами
        val rows = doc.select("tr.gai, tr.tum")
        Log.d("RuTor", "Rows: ${rows.size}")
        return rows.mapNotNull { row ->
            // Ищем magnet в ЛЮБОМ месте строки (не привязываясь к индексу ячейки)
            val magnet = row.selectFirst("a[href^=magnet:]")?.attr("href")
                ?: return@mapNotNull null
            // Название — ссылка на /torrent/
            val title = row.selectFirst("a[href^=/torrent/]")?.text()?.trim()
                ?.ifBlank { null } ?: return@mapNotNull null
            // Размер — предпоследняя ячейка с текстом содержащим GB/MB
            val sizeText = row.select("td").map { it.text() }
                .firstOrNull { it.contains("GB") || it.contains("MB") || it.contains("KB") } ?: ""
            // Сиды — из span.green, берём последнее число
            val seedsRaw = row.selectFirst("span.green")?.text() ?: ""
            val seeds = Regex("\\d+").findAll(seedsRaw).lastOrNull()?.value?.toIntOrNull() ?: 0
            val leechRaw = row.selectFirst("span.red")?.text() ?: ""
            val leech = Regex("\\d+").findAll(leechRaw).lastOrNull()?.value?.toIntOrNull() ?: 0
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
        }.also { Log.d("RuTor", "Parsed ${it.size} results") }
         .sortedByDescending { it.seeders }
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
