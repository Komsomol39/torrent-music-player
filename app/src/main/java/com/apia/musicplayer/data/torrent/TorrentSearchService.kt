package com.apia.musicplayer.data.torrent

import com.apia.musicplayer.domain.model.TorrentResult
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TorrentSearchService @Inject constructor(
    private val client: OkHttpClient
) {
    // Поиск через несколько открытых источников
    suspend fun search(query: String): List<TorrentResult> {
        val results = mutableListOf<TorrentResult>()

        // Попытка 1: API поиска через nyaa.si (аниме/музыка, открытый API)
        try {
            results += searchNyaa(query)
        } catch (e: Exception) { /* skip */ }

        // Попытка 2: The Pirate Bay API (JSON API, публичный)
        try {
            results += searchTPB(query)
        } catch (e: Exception) { /* skip */ }

        // Попытка 3: 1337x scraping
        try {
            results += search1337x(query)
        } catch (e: Exception) { /* skip */ }

        return results.sortedByDescending { it.seeders }
    }

    private fun get(url: String): String {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP \${resp.code}")
            return resp.body?.string() ?: ""
        }
    }

    // The Pirate Bay — открытый JSON API
    private fun searchTPB(query: String): List<TorrentResult> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        // category 101 = music
        val json = get("https://apibay.org/q.php?q=$encoded&cat=101")
        val arr = org.json.JSONArray(json)
        val results = mutableListOf<TorrentResult>()
        for (i in 0 until minOf(arr.length(), 20)) {
            val obj = arr.getJSONObject(i)
            val name = obj.getString("name")
            if (name == "No results returned.") continue
            val infoHash = obj.getString("info_hash")
            val magnet = buildMagnet(infoHash, name)
            results += TorrentResult(
                id = "tpb_$infoHash",
                title = name,
                artist = parseArtist(name),
                album = parseAlbum(name),
                year = parseYear(name),
                seeders = obj.getInt("seeders"),
                leechers = obj.getInt("leechers"),
                sizeBytes = obj.getLong("size"),
                magnetLink = magnet,
                source = "TPB"
            )
        }
        return results
    }

    // Nyaa.si — RSS-based, хорошо для FLAC/лицензионной музыки
    private fun searchNyaa(query: String): List<TorrentResult> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val xml = get("https://nyaa.si/?page=rss&q=$encoded&c=3_0&f=0")
        val doc = Jsoup.parse(xml, "", org.jsoup.parser.Parser.xmlParser())
        return doc.select("item").take(10).mapIndexed { i, item ->
            val title = item.selectFirst("title")?.text() ?: ""
            val link  = item.selectFirst("link")?.text() ?: ""
            val seeders = item.selectFirst("nyaa|seeders")?.text()?.toIntOrNull() ?: 0
            val leechers = item.selectFirst("nyaa|leechers")?.text()?.toIntOrNull() ?: 0
            val sizeStr = item.selectFirst("nyaa|size")?.text() ?: "0"
            TorrentResult(
                id = "nyaa_$i",
                title = title,
                artist = parseArtist(title),
                album = null,
                year = parseYear(title),
                seeders = seeders,
                leechers = leechers,
                sizeBytes = parseSize(sizeStr),
                magnetLink = link,
                source = "Nyaa"
            )
        }
    }

    // 1337x scraping
    private fun search1337x(query: String): List<TorrentResult> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val html = get("https://1337x.to/category-search/$encoded/Music/1/")
        val doc = Jsoup.parse(html)
        return doc.select("tbody tr").take(10).mapIndexed { i, row ->
            val name     = row.selectFirst("td.name a:nth-child(2)")?.text() ?: return@mapIndexed null
            val href     = row.selectFirst("td.name a:nth-child(2)")?.attr("href") ?: ""
            val seeders  = row.selectFirst("td.seeds")?.text()?.toIntOrNull() ?: 0
            val leechers = row.selectFirst("td.leeches")?.text()?.toIntOrNull() ?: 0
            val size     = row.selectFirst("td.size")?.ownText() ?: "0"
            TorrentResult(
                id = "1337x_$i",
                title = name,
                artist = parseArtist(name),
                album = parseAlbum(name),
                year = parseYear(name),
                seeders = seeders,
                leechers = leechers,
                sizeBytes = parseSize(size),
                magnetLink = "https://1337x.to$href", // будет резолвиться отдельно
                source = "1337x"
            )
        }.filterNotNull()
    }

    // Получаем magnet с детальной страницы 1337x
    fun getMagnetFrom1337x(detailUrl: String): String? {
        return try {
            val html = get(detailUrl)
            val doc = Jsoup.parse(html)
            doc.selectFirst("a[href^=magnet:]")?.attr("href")
        } catch (e: Exception) { null }
    }

    private fun buildMagnet(infoHash: String, name: String): String {
        val trackers = listOf(
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://open.tracker.cl:1337/announce",
            "udp://tracker.openbittorrent.com:6969/announce"
        ).joinToString("&tr=") { java.net.URLEncoder.encode(it, "UTF-8") }
        val encodedName = java.net.URLEncoder.encode(name, "UTF-8")
        return "magnet:?xt=urn:btih:$infoHash&dn=$encodedName&tr=$trackers"
    }

    private fun parseArtist(title: String): String? {
        val separators = listOf(" - ", " – ", " — ")
        for (sep in separators) {
            if (title.contains(sep)) return title.substringBefore(sep).trim()
        }
        return null
    }

    private fun parseAlbum(title: String): String? {
        val separators = listOf(" - ", " – ", " — ")
        for (sep in separators) {
            if (title.contains(sep)) {
                val after = title.substringAfter(sep).trim()
                // Убираем год и теги в скобках
                return after.replace(Regex("\\(\\d{4}\\)"), "").replace(Regex("\\[.*?\\]"), "").trim()
            }
        }
        return null
    }

    private fun parseYear(title: String): Int? {
        return Regex("\\b(19|20)\\d{2}\\b").find(title)?.value?.toIntOrNull()
    }

    private fun parseSize(sizeStr: String): Long {
        val clean = sizeStr.trim()
        val num = Regex("[0-9.]+").find(clean)?.value?.toDoubleOrNull() ?: return 0L
        return when {
            clean.contains("GB", ignoreCase = true) -> (num * 1_073_741_824).toLong()
            clean.contains("MB", ignoreCase = true) -> (num * 1_048_576).toLong()
            clean.contains("KB", ignoreCase = true) -> (num * 1024).toLong()
            else -> num.toLong()
        }
    }
}
