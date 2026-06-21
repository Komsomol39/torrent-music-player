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

    // GET /search/0/2/000/0/<query>  — категория 2 = Music
    // GET /search/0/0/000/0/<query>  — все категории
    // Таблица #index, <tr> — строки
    // td[0]=дата, td[1]=название+magnet, td[2]=размер, td[3]=seeds, td[4]=leech

    private val mirrors = listOf(
        "https://rutor.info",
        "https://rutor.is"
    )

    override suspend fun search(query: String): List<TorrentResult> {
        val enc = java.net.URLEncoder.encode(query, "UTF-8")
        for (base in mirrors) {
            try {
                // Категория Music (2), потом все
                val html = get("$base/search/0/2/000/0/$enc")
                    ?: get("$base/search/0/0/000/0/$enc")
                    ?: continue
                val results = parseResults(html)
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

    private fun parseResults(html: String): List<TorrentResult> {
        val doc = Jsoup.parse(html)
        val rows = doc.select("table#index tr").drop(1)  // пропускаем заголовок
        Log.d("RuTor", "Rows: ${rows.size}")
        return rows.mapNotNull { row ->
            val cells = row.select("td")
            if (cells.size < 4) return@mapNotNull null
            // Magnet в td[1] внутри <a href="magnet:...">
            val magnetEl = cells[1].selectFirst("a[href^=magnet:]")
                ?: cells.getOrNull(0)?.selectFirst("a[href^=magnet:]")
            val magnet = magnetEl?.attr("href") ?: return@mapNotNull null
            // Название — текст <a> без magnet ссылки
            val titleEl = cells[1].selectFirst("a:not([href^=magnet:])") ?: cells[1]
            val title = titleEl.text().ifBlank { return@mapNotNull null }
            val size  = cells.getOrNull(2)?.text() ?: ""
            val seeds = cells.getOrNull(3)?.text()?.trim()?.toIntOrNull() ?: 0
            val leech = cells.getOrNull(4)?.text()?.trim()?.toIntOrNull() ?: 0
            TorrentResult(
                id = "rutor_${magnet.hashCode()}",
                title  = title,
                artist = title.substringBefore(" - ").takeIf { title.contains(" - ") },
                album  = null, year = null,
                seeders = seeds, leechers = leech,
                sizeBytes = parseSize(size),
                magnetLink = magnet,
                source = "RuTor"
            )
        }.sortedByDescending { it.seeders }
    }

    private fun get(url: String): String? = try {
        client.newCall(
            Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Accept-Language", "ru-RU,ru;q=0.9")
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
