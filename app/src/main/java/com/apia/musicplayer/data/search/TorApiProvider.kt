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
 * TorAPI — публичный агрегатор для RU торрент трекеров.
 * GitHub: https://github.com/Lifailon/TorAPI
 * Endpoint: https://torapi.vercel.app
 *
 * Поддерживает: RuTracker, Kinozal, RuTor, NoNameClub
 * БЕЗ авторизации — работает через прокси.
 *
 * API: GET /api/torrent/search?query=<query>&provider=<provider>
 */
@Singleton
class TorApiProvider @Inject constructor(private val client: OkHttpClient) : SearchProvider {

    override val name = "TorAPI (RU)"

    // Публичный инстанс TorAPI
    private val baseUrl = "https://torapi.vercel.app"

    // Провайдеры которые поддерживает TorAPI
    private val providers = listOf("rutracker", "rutor", "kinozal", "nnmclub")

    override suspend fun search(query: String): List<TorrentResult> {
        val results = mutableListOf<TorrentResult>()
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")

        for (provider in providers) {
            try {
                val url = "$baseUrl/api/torrent/search?query=$encoded&provider=$provider"
                Log.d("TorAPI", "Searching $provider: $url")
                val json = get(url) ?: continue
                val parsed = parseResponse(json, provider)
                Log.d("TorAPI", "$provider: ${parsed.size} results")
                results += parsed
            } catch (e: Exception) {
                Log.w("TorAPI", "$provider error: ${e.message}")
            }
        }
        return results.sortedByDescending { it.seeders }
    }

    private fun parseResponse(json: String, provider: String): List<TorrentResult> {
        return try {
            val obj = JSONObject(json)
            // TorAPI возвращает { "result": [...] } или напрямую массив
            val arr: JSONArray = when {
                obj.has("result") -> obj.getJSONArray("result")
                obj.has("data")   -> obj.getJSONArray("data")
                else -> return emptyList()
            }
            (0 until arr.length()).mapNotNull { i ->
                try {
                    val item = arr.getJSONObject(i)
                    val title = item.optString("Name", item.optString("title", "")).ifBlank { return@mapNotNull null }
                    val magnet = item.optString("Magnet", item.optString("magnet", ""))
                    val seeds  = item.optString("Seeds", item.optString("seeders", "0")).toIntOrNull() ?: 0
                    val leech  = item.optString("Peers", item.optString("leechers", "0")).toIntOrNull() ?: 0
                    val size   = item.optString("Size", item.optString("size", ""))
                    val url    = item.optString("Url",  item.optString("url",  ""))

                    TorrentResult(
                        id = "${provider}_${title.hashCode()}",
                        title  = title,
                        artist = title.substringBefore(" - ").takeIf { title.contains(" - ") },
                        album  = null, year = null,
                        seeders  = seeds,
                        leechers = leech,
                        sizeBytes = parseSize(size),
                        // Если есть magnet — используем, иначе URL страницы для резолва
                        magnetLink = magnet.ifBlank { url },
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
        } catch (e: Exception) {
            Log.e("TorAPI", "Parse error for $provider: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getMagnet(result: TorrentResult): String {
        // Если уже magnet — возвращаем сразу
        if (result.magnetLink.startsWith("magnet:")) return result.magnetLink

        // Иначе запрашиваем детали через TorAPI
        return try {
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
            obj.optString("Magnet", obj.optString("magnet", result.magnetLink))
        } catch (e: Exception) {
            result.magnetLink
        }
    }

    private fun get(url: String) = try {
        client.newCall(
            Request.Builder().url(url)
                .header("User-Agent", "TorrentMusicPlayer/1.0")
                .header("Accept", "application/json")
                .build()
        ).execute().use {
            if (it.isSuccessful) it.body?.string() else {
                Log.w("TorAPI", "HTTP ${it.code} for $url")
                null
            }
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
            s.contains("KB", true) || s.contains("КБ", true) -> (n * 1024).toLong()
            else -> n.toLong()
        }
    }
}
