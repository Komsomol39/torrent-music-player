package com.apia.musicplayer.data.search

import android.util.Log
import com.apia.musicplayer.domain.model.TorrentResult
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Провайдер для Ryuk-me/Torrent-Api-py
 * GitHub: https://github.com/Ryuk-me/Torrent-Api-py
 *
 * API: GET /api/v1/search?site=<site>&query=<q>&limit=20
 * API: GET /api/v1/category?site=<site>&query=<q>&category=music&limit=20
 *
 * Ответ: {"data":[{"name","size","seeders","leechers","url","magnet","hash"}]}
 *
 * Поддерживаемые сайты с категорией music: 1337x, limetorrent, torrentfunk, kickass, tgx
 * Без категории (все): piratebay, nyaasi, bitsearch, glodls
 *
 * Публичные инстансы (пробуем по очереди):
 * - Можно поднять свой: docker run -p 8009:8009 ryukme/torrent-api-py
 */
@Singleton
class TorApiProvider @Inject constructor(private val client: OkHttpClient) : SearchProvider {

    override val name = "TorAPI"

    // Список публичных инстансов — пробуем по очереди
    // Основной умер, но форки живут
    var customInstance: String = ""  // пользователь может задать свой

    private val defaultInstances = listOf(
        "https://torrent-api-py-nx0x.onrender.com",
        "https://torrents-api.ryukme.repl.co",   // старый
    )

    private val instances get() = buildList {
        if (customInstance.isNotBlank()) add(customInstance.trimEnd('/'))
        addAll(defaultInstances)
    }

    // Сайты с поддержкой категории music
    private val musicSites = listOf("1337x", "limetorrent", "kickass", "torrentfunk")
    // Сайты без категорий — поиск по всем
    private val allSites   = listOf("piratebay", "nyaasi", "bitsearch", "glodls", "tgx")

    override suspend fun search(query: String): List<TorrentResult> {
        val enc = java.net.URLEncoder.encode(query, "UTF-8")
        val results = mutableListOf<TorrentResult>()

        for (base in instances) {
            var anySuccess = false
            // Сначала с категорией music
            for (site in musicSites) {
                try {
                    val url = "$base/api/v1/category?site=$site&query=$enc&category=music&limit=15"
                    val data = fetchData(url) ?: continue
                    val parsed = parseData(data, site)
                    if (parsed.isNotEmpty()) { results += parsed; anySuccess = true }
                } catch (e: Exception) {
                    Log.w("TorAPI", "$site: ${e.message}")
                }
            }
            // Потом без категории
            for (site in allSites) {
                try {
                    val url = "$base/api/v1/search?site=$site&query=$enc&limit=10"
                    val data = fetchData(url) ?: continue
                    val parsed = parseData(data, site)
                    if (parsed.isNotEmpty()) { results += parsed; anySuccess = true }
                } catch (e: Exception) {
                    Log.w("TorAPI", "$site: ${e.message}")
                }
            }
            if (anySuccess) break  // нашли рабочий инстанс — хватит
        }
        Log.d("TorAPI", "Total: ${results.size}")
        return results.sortedByDescending { it.seeders }
    }

    private fun fetchData(url: String): JSONArray? {
        Log.d("TorAPI", "GET $url")
        val json = get(url) ?: return null
        val obj = JSONObject(json)
        return obj.optJSONArray("data")
    }

    private fun parseData(arr: JSONArray, site: String): List<TorrentResult> {
        return (0 until arr.length()).mapNotNull { i ->
            try {
                val item = arr.getJSONObject(i)
                val name    = item.optString("name", "").ifBlank { return@mapNotNull null }
                val magnet  = item.optString("magnet", "").ifBlank { item.optString("url", "") }
                if (magnet.isBlank()) return@mapNotNull null
                val seeds   = item.optString("seeders", "0").toIntOrNull() ?: 0
                val leech   = item.optString("leechers", "0").toIntOrNull() ?: 0
                val size    = item.optString("size", "")
                TorrentResult(
                    id = "${site}_${name.hashCode()}",
                    title  = name,
                    artist = name.substringBefore(" - ").takeIf { name.contains(" - ") },
                    album  = null, year = null,
                    seeders = seeds, leechers = leech,
                    sizeBytes = parseSize(size),
                    magnetLink = magnet,
                    source = siteName(site)
                )
            } catch (e: Exception) { null }
        }
    }

    override suspend fun getMagnet(result: TorrentResult): String {
        // Если url — страница а не magnet, открываем детали
        if (result.magnetLink.startsWith("magnet:")) return result.magnetLink
        val enc = java.net.URLEncoder.encode(result.magnetLink, "UTF-8")
        for (base in instances) {
            try {
                val json = get("$base/api/v1/search?site=1337x&query=${result.title.take(30).let { java.net.URLEncoder.encode(it, "UTF-8") }}&limit=5") ?: continue
                val arr = JSONObject(json).optJSONArray("data") ?: continue
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    if (item.optString("name","").contains(result.title.take(20), ignoreCase = true)) {
                        val m = item.optString("magnet","")
                        if (m.startsWith("magnet:")) return m
                    }
                }
            } catch (e: Exception) { continue }
        }
        return result.magnetLink
    }

    private fun siteName(site: String) = when (site) {
        "1337x"      -> "1337x"
        "piratebay"  -> "TPB"
        "nyaasi"     -> "Nyaa"
        "limetorrent"-> "LimeTorrents"
        "kickass"    -> "KickAss"
        "tgx"        -> "TorrentGalaxy"
        "bitsearch"  -> "BitSearch"
        "glodls"     -> "Glodls"
        "torrentfunk"-> "TorrentFunk"
        else         -> site
    }

    private fun get(url: String): String? = try {
        client.newCall(
            Request.Builder().url(url)
                .header("User-Agent", "TorrentMusicPlayer/1.0")
                .build()
        ).execute().use { if (it.isSuccessful) it.body?.string() else null }
    } catch (e: Exception) { null }

    private fun parseSize(s: String): Long {
        val n = Regex("[0-9.]+").find(s)?.value?.toDoubleOrNull() ?: return 0
        return when {
            s.contains("GB", true) || s.contains("GiB", true) -> (n * 1_073_741_824).toLong()
            s.contains("MB", true) || s.contains("MiB", true) -> (n * 1_048_576).toLong()
            s.contains("KB", true) || s.contains("KiB", true) -> (n * 1024).toLong()
            else -> n.toLong()
        }
    }
}
