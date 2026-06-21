package com.apia.musicplayer.data.search

import android.util.Log
import com.apia.musicplayer.domain.model.TorrentResult
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuTorProvider @Inject constructor(private val client: OkHttpClient) : SearchProvider {
    override val name = "RuTor"

    // VERIFIED by test: /search/0/0/000/0/<query>
    // magnets in href="magnet:..."
    // titles in href="/torrent/ID/slug">TITLE</a>
    // seeds in class="s">N</td>
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
        val magnetRegex = Regex("""href="(magnet:[^"]+)"""")
        val titleRegex  = Regex("""href="/torrent/\\d+/[^"]+">([^<]+)</a>"""")
        val seedRegex   = Regex("""class="s">(\\d+)</td>"""")
        val sizeRegex   = Regex("""class="ts">([^<]+)</td>"""")
        val magnets = magnetRegex.findAll(html).map { it.groupValues[1] }.toList()
        val titles  = titleRegex.findAll(html).map { it.groupValues[1].trim() }.toList()
        val seeds   = seedRegex.findAll(html).map { it.groupValues[1] }.toList()
        val sizes   = sizeRegex.findAll(html).map { it.groupValues[1].trim() }.toList()
        Log.d("RuTor", "magnets=${magnets.size} titles=${titles.size} seeds=${seeds.size}")
        return magnets.mapIndexed { i, magnet ->
            val title = titles.getOrElse(i) { "Unknown" }
            val seedCount = seeds.getOrElse(i) { "0" }.toIntOrNull() ?: 0
            val sizeStr = sizes.getOrElse(i) { "" }
            TorrentResult(
                id = "rutor_${magnet.hashCode()}",
                title = title,
                artist = title.substringBefore(" - ").trim().takeIf { title.contains(" - ") },
                album = null, year = null,
                seeders = seedCount, leechers = 0,
                sizeBytes = parseSize(sizeStr),
                magnetLink = magnet,
                source = "RuTor"
            )
        }.sortedByDescending { it.seeders }
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
            s.contains("GB") -> (n * 1_073_741_824).toLong()
            s.contains("MB") -> (n * 1_048_576).toLong()
            s.contains("KB") -> (n * 1024).toLong()
            else -> 0
        }
    }
}
