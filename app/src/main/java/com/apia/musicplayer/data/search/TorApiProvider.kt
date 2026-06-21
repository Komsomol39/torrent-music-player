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
 * TorAPI — публичный агрегатор для RU трекеров без авторизации.
 * GitHub: https://github.com/Lifailon/TorAPI
 *
 * Публичные инстансы (пробуем по очереди):
 * - https://torapi.vercel.app
 * - https://torapi.onrender.com
 *
 * Endpoints:
 * GET /api/torrent/search?query=<q>&provider=<p>&limit=20
 * Провайдеры: rutracker, rutor, kinozal, nnmclub
 */
@Singleton
class TorApiProvider @Inject constructor(private val client: OkHttpClient) : SearchProvider {

    override val name = "TorAPI (RU)"

    // Пробуем несколько публичных инстансов
    private val instances = listOf(
        "https://torapi.vercel.app",
        "https://torapi.onrender.com"
    )

    private val providers = listOf("rutracker", "rutor", "kinozal", "nnmclub")

    override suspend fun search(query: String): List<TorrentResult> {
        val enc = java.net.URLEncoder.encode(query, "UTF-8")
        val results = mutableListOf<TorrentResult>()

        for (provider in providers) {
            var found = false
            for (base in instances) {
                try {
                    val url = "$base/api/torrent/search?query=$enc&provider=$provider&limit=20"
                    Log.d("TorAPI", "GET $url")
                    val json = get(url) ?: continue
                    Log.d("TorAPI", "$provider raw: ${json.take(200)}")
                    val parsed = parseAny(json, provider)
                    if (parsed.isNotEmpty()) {
                        Log.d("TorAPI", "$provider via $base: ${parsed.size} results")
                        results += parsed
                        found = true
                        break
                    }
                } catch (e: Exception) {
                    Log.w("TorAPI", "$provider@$base: ${e.message}")
                }
            }
            if (!found) Log.w("TorAPI", "$provider: no results from any instance")
        }
        return results.sortedByDescending { it.seeders }
    }

    /**
     * Парсим любой формат который может вернуть TorAPI.
     * Поддерживаем несколько вариантов структуры.
     */
    private fun parseAny(json: String, provider: String): List<TorrentResult> {
        if (json.isBlank() || json == "null") return emptyList()
        return try {
            when {
                // Массив напрямую
                json.trimStart().startsWith("[") -> {
                    parseArray(JSONArray(json), provider)
                }
                // Объект с полем result/data/items/torrents
                json.trimStart().startsWith("{") -> {
                    val obj = JSONObject(json)
                    val arr = obj.optJSONArray("result")
                        ?: obj.optJSONArray("data")
                        ?: obj.optJSONArray("items")
                        ?: obj.optJSONArray("torrents")
                        ?: return emptyList()
                    parseArray(arr, provider)
                }
                else -> emptyList()
            }
        } catch (e: Exception) {
            Log.e("TorAPI", "Parse error: ${e.message}
JSON: ${json.take(300)}")
            emptyList()
        }
    }

    private fun parseArray(arr: JSONArray, provider: String): List<TorrentResult> {
        return (0 until arr.length()).mapNotNull { i ->
            try {
                val item = arr.getJSONObject(i)
                // TorAPI использует разные ключи в зависимости от провайдера
                val title = item.optString("Name")
                    .ifBlank { item.optString("title") }
                    .ifBlank { item.optString("name") }
                    .ifBlank { return@mapNotNull null }

                val magnet = item.optString("Magnet")
                    .ifBlank { item.optString("magnet") }
                    .ifBlank { item.optString("MagnetLink") }

                val pageUrl = item.optString("Url")
                    .ifBlank { item.optString("url") }
                    .ifBlank { item.optString("Link") }

                // Нужен хотя бы один из них
                if (magnet.isBlank() && pageUrl.isBlank()) return@mapNotNull null

                val seeds = item.optString("Seeds").toIntOrNull()
                    ?: item.optString("seeders").toIntOrNull()
                    ?: item.optInt("Seeds", 0)

                val leech = item.optString("Peers").toIntOrNull()
                    ?: item.optString("leechers").toIntOrNull()
                    ?: item.optInt("Peers", 0)

                val size = item.optString("Size").ifBlank { item.optString("size") }

                TorrentResult(
                    id = "${provider}_${(title + magnet).hashCode()}",
                    title  = title,
                    artist = title.substringBefore(" - ").takeIf { title.contains(" - ") },
                    album  = null, year = null,
                    seeders  = seeds,
                    leechers = leech,
                    sizeBytes = parseSize(size),
                    magnetLink = magnet.ifBlank { pageUrl },
                    source = when (provider) {
                        "rutracker" -> "RuTracker"
                        "rutor"     -> "RuTor"
                        "kinozal"   -> "Kinozal"
                        "nnmclub"   -> "NNM-Club"
                        else        -> provider
                    }
                )
            } catch (e: Exception) { null }
        }
    }

    override suspend fun getMagnet(result: TorrentResult): String {
        if (result.magnetLink.startsWith("magnet:")) return result.magnetLink
        // Если это URL страницы — пробуем получить magnet через TorAPI info
        val pageUrl = result.magnetLink
        val provider = when (result.source) {
            "RuTracker" -> "rutracker"
            "RuTor"     -> "rutor"
            "Kinozal"   -> "kinozal"
            "NNM-Club"  -> "nnmclub"
            else        -> return pageUrl
        }
        // Извлекаем ID из URL
        val id = pageUrl.substringAfterLast("t=").substringBefore("&").trim()
            .ifBlank { pageUrl.substringAfterLast("/").substringBefore("?") }
        if (id.isBlank()) return pageUrl

        for (base in instances) {
            try {
                val url = "$base/api/torrent/info?id=$id&provider=$provider"
                val json = get(url) ?: continue
                val obj = JSONObject(json)
                val magnet = obj.optString("Magnet").ifBlank { obj.optString("magnet") }
                if (magnet.isNotBlank() && magnet.startsWith("magnet:")) return magnet
            } catch (e: Exception) { continue }
        }
        return pageUrl
    }

    private fun get(url: String): String? = try {
        client.newCall(
            Request.Builder().url(url)
                .header("User-Agent", "TorrentMusicPlayer/1.0")
                .header("Accept", "application/json")
                .build()
        ).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.string()
            else { Log.w("TorAPI", "HTTP ${resp.code} for $url"); null }
        }
    } catch (e: Exception) {
        Log.e("TorAPI", "GET failed: ${e.message}")
        null
    }

    private fun parseSize(s: String): Long {
        if (s.isBlank()) return 0
        val n = Regex("[0-9.]+").find(s)?.value?.toDoubleOrNull() ?: return 0
        return when {
            s.contains("GB", true) || s.contains("ГБ", true) -> (n * 1_073_741_824).toLong()
            s.contains("MB", true) || s.contains("МБ", true) -> (n * 1_048_576).toLong()
            s.contains("KB", true) || s.contains("КБ", true) -> (n * 1_024).toLong()
            else -> n.toLong()
        }
    }
}
