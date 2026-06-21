package com.apia.musicplayer.data.search

import android.util.Log
import com.apia.musicplayer.domain.model.TorrentResult
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TpbProvider @Inject constructor(private val client: OkHttpClient) : SearchProvider {
    override val name = "The Pirate Bay"

    // apibay.org — официальный JSON API TPB
    // GET /q.php?q=<query>&cat=101  -> [{name, info_hash, seeders, leechers, size, ...}]
    private val mirrors = listOf(
        "https://apibay.org",
        "https://piratebayproxy.live",
    )

    override suspend fun search(query: String): List<TorrentResult> {
        val enc = java.net.URLEncoder.encode(query, "UTF-8")
        for (base in mirrors) {
            try {
                // cat=101 = Music, cat=0 = все — пробуем оба
                val json = get("$base/q.php?q=$enc&cat=101") ?: continue
                val results = parseResults(json)
                if (results.isNotEmpty()) {
                    Log.d("TPB", "Found ${results.size} from $base")
                    return results
                }
                // Если музыка не нашлась — ищем по всем категориям
                val json2 = get("$base/q.php?q=$enc&cat=0") ?: continue
                return parseResults(json2)
            } catch (e: Exception) {
                Log.w("TPB", "$base: ${e.message}")
            }
        }
        return emptyList()
    }

    private fun parseResults(json: String): List<TorrentResult> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.getJSONObject(i)
                val name = obj.optString("name", "")
                if (name == "No results returned." || name.isBlank()) return@mapNotNull null
                val hash = obj.optString("info_hash", "").lowercase()
                if (hash.isBlank()) return@mapNotNull null
                val seeds = obj.optString("seeders", "0").toIntOrNull() ?: 0
                val leech = obj.optString("leechers", "0").toIntOrNull() ?: 0
                val size  = obj.optString("size", "0").toLongOrNull() ?: 0L
                TorrentResult(
                    id = "tpb_$hash",
                    title  = name,
                    artist = name.substringBefore(" - ").takeIf { name.contains(" - ") },
                    album  = null, year = null,
                    seeders = seeds, leechers = leech,
                    sizeBytes = size,
                    magnetLink = buildMagnet(hash, name),
                    source = "TPB"
                )
            }.sortedByDescending { it.seeders }
        } catch (e: Exception) {
            Log.e("TPB", "Parse: ${e.message}")
            emptyList()
        }
    }

    private fun buildMagnet(hash: String, name: String): String {
        val dn = java.net.URLEncoder.encode(name, "UTF-8")
        return "magnet:?xt=urn:btih:$hash&dn=$dn" +
            "&tr=udp://tracker.opentrackr.org:1337/announce" +
            "&tr=udp://open.tracker.cl:1337/announce" +
            "&tr=udp://tracker.openbittorrent.com:6969/announce"
    }

    private fun get(url: String): String? = try {
        client.newCall(
            Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()
        ).execute().use { if (it.isSuccessful) it.body?.string() else null }
    } catch (e: Exception) { null }
}
