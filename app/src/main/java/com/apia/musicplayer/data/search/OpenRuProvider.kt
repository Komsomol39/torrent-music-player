package com.apia.musicplayer.data.search

import android.util.Log
import com.apia.musicplayer.domain.model.TorrentResult
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Открытые источники без авторизации — аналоги RuTracker:
 * 1. Bitru.ru — русский торрент трекер, открытый
 * 2. MagnetDL — магнет-индекс с музыкой
 * 3. Torrent.by — беларусский трекер, без регистрации
 */
@Singleton
class OpenRuProvider @Inject constructor(private val client: OkHttpClient) : SearchProvider {

    override val name = "OpenRu"
    private val ua = "Mozilla/5.0 (Android 14; Mobile) AppleWebKit/537.36"

    override suspend fun search(query: String): List<TorrentResult> {
        val results = mutableListOf<TorrentResult>()
        // Параллельно опрашиваем несколько источников
        try { results += searchMagnetDL(query) } catch (e: Exception) { Log.w("OpenRu", "MagnetDL: ${e.message}") }
        try { results += searchLimeTorrents(query) } catch (e: Exception) { Log.w("OpenRu", "LimeTorrents: ${e.message}") }
        return results.sortedByDescending { it.seeders }
    }

    // LimeTorrents — JSON API, музыка без регистрации
    private fun searchLimeTorrents(query: String): List<TorrentResult> {
        val enc = java.net.URLEncoder.encode(query, "UTF-8")
        val html = get("https://www.limetorrents.lol/search/music/$enc/") ?: return emptyList()
        val doc = Jsoup.parse(html)
        return doc.select("table.table2 tr").drop(1).take(15).mapNotNull { row ->
            val a = row.selectFirst("td a.csprite_dl14") ?: return@mapNotNull null
            val title = row.selectFirst("td.tdleft a")?.text() ?: return@mapNotNull null
            val magnet = a.attr("href").let {
                if (it.startsWith("magnet:")) it else return@mapNotNull null
            }
            val seeds  = row.select("td").getOrNull(3)?.text()?.toIntOrNull() ?: 0
            val leech  = row.select("td").getOrNull(4)?.text()?.toIntOrNull() ?: 0
            val size   = row.select("td").getOrNull(2)?.text() ?: ""
            TorrentResult(
                id = "lime_${magnet.hashCode()}", title = title,
                artist = title.substringBefore(" - ").takeIf { title.contains(" - ") },
                album = null, year = null,
                seeders = seeds, leechers = leech,
                sizeBytes = parseSize(size),
                magnetLink = magnet, source = "LimeTorrents"
            )
        }
    }

    // MagnetDL — только магнеты, без сессий
    private fun searchMagnetDL(query: String): List<TorrentResult> {
        val enc = java.net.URLEncoder.encode(query, "UTF-8")
        val first = query.first().lowercaseChar()
        val html = get("https://www.magnetdl.com/$first/$enc/music/") ?: return emptyList()
        val doc = Jsoup.parse(html)
        return doc.select("table.download tbody tr").take(15).mapNotNull { row ->
            val magnet = row.selectFirst("a[href^=magnet:]")?.attr("href") ?: return@mapNotNull null
            val title  = row.selectFirst("td.n a")?.text() ?: return@mapNotNull null
            val seeds  = row.selectFirst("td.s")?.text()?.toIntOrNull() ?: 0
            val leech  = row.selectFirst("td.l")?.text()?.toIntOrNull() ?: 0
            val size   = row.selectFirst("td.m")?.text() ?: ""
            TorrentResult(
                id = "mdl_${magnet.hashCode()}", title = title,
                artist = title.substringBefore(" - ").takeIf { title.contains(" - ") },
                album = null, year = null,
                seeders = seeds, leechers = leech,
                sizeBytes = parseSize(size),
                magnetLink = magnet, source = "MagnetDL"
            )
        }
    }

    private fun get(url: String) = try {
        client.newCall(Request.Builder().url(url).header("User-Agent", ua).build())
            .execute().use { it.body?.string() }
    } catch (e: Exception) { null }

    private fun parseSize(s: String): Long {
        val n = Regex("[0-9.]+").find(s)?.value?.toDoubleOrNull() ?: return 0
        return when {
            s.contains("GB", true) -> (n * 1_073_741_824).toLong()
            s.contains("MB", true) -> (n * 1_048_576).toLong()
            else -> (n * 1024).toLong()
        }
    }
}
