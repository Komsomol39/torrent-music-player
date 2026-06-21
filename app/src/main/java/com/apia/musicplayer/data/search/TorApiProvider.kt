package com.apia.musicplayer.data.search

import android.util.Log
import com.apia.musicplayer.domain.model.TorrentResult
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TorAPI — публичный агрегатор RU торрент трекеров.
 * https://github.com/Lifailon/TorAPI
 *
 * Публичные инстансы:
 * - https://torapi.vercel.app  (может блокировать)
 * - Пользователь может указать свой в настройках
 *
 * GET /api/torrent/search?query=<q>&provider=<p>
 * Ответ: { "RuTracker": [...], "RuTor": [...], ... }
 * Или массив напрямую.
 */
@Singleton
class TorApiProvider @Inject constructor(private val client: OkHttpClient) : SearchProvider {

    override val name = "TorAPI (RU)"

    // Публичный инстанс — можно переопределить в настройках
    var baseUrl = "https://torapi.vercel.app"

    // Провайдеры TorAPI для музыки
    private val musicProviders = listOf("rutracker", "rutor", "kinozal", "nnmclub")

    override suspend fun search(query: String): List<TorrentResult> {
        val enc = java.net.URLEncoder.encode(query, "UTF-8")
        val results = mutableListOf<TorrentResult>()

        // Пробуем каждый провайдер отдельно
        for (provider in musicProviders) {
            try {
                val url = "$baseUrl/api/torrent/search?query=$enc&provider=$provider"
                val json = get(url) ?: continue
                val parsed = parse(json, provider)
                Log.d("TorAPI", "$provider: ${parsed.size} results")
                results += parsed
            } catch (e: Exception) {
                Log.w("TorAPI", "$provider: ${e.message}")
            }
        }

        // Если всё заблокировано — пробуем all провайдеры одним запросом
        if (results.isEmpty()) {
            try {
                val url = "$baseUrl/api/torrent/search?query=$enc"
                val json = get(url) ?: return emptyList()
                results += parse(json, "all")
            } catch (e: Exception) {
                Log.e("TorAPI", "all: ${e.message}")
                throw Exception("TorAPI недоступен ($baseUrl). Проверь Settings → RU Torrents")
            }
        }

        return results.sortedByDescending { it.seeders }
    }

    private fun parse(json: String, provider: String): List<TorrentResult> {
        if (json.isBlank()) return emptyList()
        return try {
            val obj = JSONObject(json)

            // Формат 1: { "RuTracker": [...], "RuTor": [...] }
            val keys = listOf("RuTracker","RuTor","Kinozal","NoNameClub","rutracker","rutor","kinozal","nnmclub")
            val combined = mutableListOf<TorrentResult>()
            for (key in keys) {
                if (obj.has(key)) {
                    combined += parseArray(obj.getJSONArray(key), key)
                }
            }
            if (combined.isNotEmpty()) return combined

            // Формат 2: { "result": [...] }
            if (obj.has("result")) return parseArray(obj.getJSONArray("result"), provider)

            // Формат 3: { "data": [...] }
            if (obj.has("data")) return parseArray(obj.getJSONArray("data"), provider)

            emptyList()
        } catch (e: Exception) {
            // Формат 4: прямой массив [...]
            try {
                parseArray(org.json.JSONArray(json), provider)
            } catch (e2: Exception) {
                Log.e("TorAPI", "Parse failed: ${e.message}")
                emptyList()
            }
        }
    }

    private fun parseArray(arr: org.json.JSONArray, provider: String): List<TorrentResult> {
        return (0 until arr.length()).mapNotNull { i ->
            try {
                val item = arr.getJSONObject(i)
                // TorAPI использует разные имена полей
                val name = item.optString("Name", item.optString("name",
                    item.optString("title", ""))).ifBlank { return@mapNotNull null }
                val magnet = item.optString("Magnet", item.optString("magnet", ""))
                val torrentUrl = item.optString("Torrent", item.optString("torrent", ""))
                val pageUrl = item.optString("Url", item.optString("url", ""))
                val seeds = parseNum(item.optString("Seeds", item.optString("seeders","0")))
                val leech = parseNum(item.optString("Peers", item.optString("leechers","0")))
                val size  = item.optString("Size", item.optString("size",""))
                val id    = item.optString("Id", item.optString("id", "${name.hashCode()}"))

                val source = when {
                    provider.contains("rutracker", true) -> "RuTracker"
                    provider.contains("rutor", true)     -> "RuTor"
                    provider.contains("kinozal", true)   -> "Kinozal"
                    provider.contains("nnm", true) ||
                    provider.contains("noname", true)    -> "NNM-Club"
                    else -> provider.replaceFirstChar { it.uppercaseChar() }
                }

                // Приоритет ссылок: magnet > страница раздачи
                val link = magnet.ifBlank { pageUrl.ifBlank { torrentUrl } }
                if (link.isBlank()) return@mapNotNull null

                TorrentResult(
                    id = "${source.take(3)}_$id",
                    title  = name,
                    artist = name.substringBefore(" - ").takeIf { name.contains(" - ") },
                    album  = null, year = null,
                    seeders  = seeds,
                    leechers = leech,
                    sizeBytes = parseSize(size),
                    magnetLink = link,
                    source = source
                )
            } catch (e: Exception) { null }
        }
    }

    override suspend fun getMagnet(result: TorrentResult): String {
        // Уже magnet — возвращаем
        if (result.magnetLink.startsWith("magnet:")) return result.magnetLink

        // Страница раздачи — запрашиваем через TorAPI
        if (result.magnetLink.startsWith("http")) {
            try {
                val topicId = result.id.substringAfter("_")
                val provider = when (result.source) {
                    "RuTracker" -> "rutracker"
                    "RuTor"     -> "rutor"
                    "Kinozal"   -> "kinozal"
                    "NNM-Club"  -> "nnmclub"
                    else        -> return result.magnetLink
                }
                val url = "$baseUrl/api/torrent/info?id=$topicId&provider=$provider"
                val json = get(url) ?: return result.magnetLink
                val obj = JSONObject(json)
                val magnet = obj.optString("Magnet", obj.optString("magnet", ""))
                if (magnet.isNotBlank()) return magnet
            } catch (e: Exception) {
                Log.w("TorAPI", "getMagnet: ${e.message}")
            }
        }
        return result.magnetLink
    }

    private fun get(url: String): String? = try {
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/121.0.0.0 Safari/537.36")
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8")
            .header("Referer", "https://torapi.vercel.app/docs")
            .header("Origin", "https://torapi.vercel.app")
            .build()
        client.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.string()
            else { Log.w("TorAPI", "HTTP ${resp.code} for $url"); null }
        }
    } catch (e: Exception) { Log.e("TorAPI", "GET: ${e.message}"); null }

    private fun parseNum(s: String) = s.trim().toIntOrNull() ?: 0

    private fun parseSize(s: String): Long {
        if (s.isBlank()) return 0
        val n = Regex("[0-9.]+").find(s)?.value?.toDoubleOrNull() ?: return 0
        return when {
            s.contains("GB", true) || s.contains("ГБ", true) -> (n * 1_073_741_824).toLong()
            s.contains("MB", true) || s.contains("МБ", true) -> (n * 1_048_576).toLong()
            s.contains("KB", true) || s.contains("КБ", true) -> (n * 1024).toLong()
            else -> n.toLong()
        }
    }
}
