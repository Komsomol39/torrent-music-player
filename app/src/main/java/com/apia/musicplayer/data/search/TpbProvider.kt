package com.apia.musicplayer.data.search

import com.apia.musicplayer.domain.model.TorrentResult
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TpbProvider @Inject constructor(private val client: OkHttpClient) : SearchProvider {
    override val name = "The Pirate Bay"

    override suspend fun search(query: String): List<TorrentResult> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        return try {
            val json = get("https://apibay.org/q.php?q=$encoded&cat=101") ?: return emptyList()
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.getJSONObject(i)
                val name = obj.getString("name")
                if (name == "No results returned.") return@mapNotNull null
                val hash = obj.getString("info_hash")
                TorrentResult(
                    id = "tpb_$hash",
                    title = name,
                    artist = name.substringBefore(" - ").takeIf { name.contains(" - ") },
                    album = null,
                    year = Regex("\\b(19|20)\\d{2}\\b").find(name)?.value?.toIntOrNull(),
                    seeders = obj.optInt("seeders"),
                    leechers = obj.optInt("leechers"),
                    sizeBytes = obj.optLong("size"),
                    magnetLink = buildMagnet(hash, name),
                    source = "TPB"
                )
            }.sortedByDescending { it.seeders }
        } catch (e: Exception) { emptyList() }
    }

    private fun get(url: String) = try {
        client.newCall(Request.Builder().url(url).header("User-Agent","Mozilla/5.0").build())
            .execute().use { it.body?.string() }
    } catch (e: Exception) { null }

    private fun buildMagnet(hash: String, name: String): String {
        val trackers = "udp://tracker.opentrackr.org:1337/announce&tr=udp://open.tracker.cl:1337/announce"
        val n = java.net.URLEncoder.encode(name, "UTF-8")
        return "magnet:?xt=urn:btih:$hash&dn=$n&tr=$trackers"
    }
}
