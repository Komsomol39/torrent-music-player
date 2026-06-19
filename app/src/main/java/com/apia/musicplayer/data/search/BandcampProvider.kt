package com.apia.musicplayer.data.search

import com.apia.musicplayer.domain.model.TorrentResult
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BandcampProvider @Inject constructor(private val client: OkHttpClient) : SearchProvider {
    override val name = "Bandcamp"

    override suspend fun search(query: String): List<TorrentResult> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val html = try {
            client.newCall(Request.Builder().url("https://bandcamp.com/search?q=$encoded&item_type=t")
                .header("User-Agent","Mozilla/5.0 (Android)").build())
                .execute().use { it.body?.string() ?: "" }
        } catch (e: Exception) { return emptyList() }
        val results = mutableListOf<TorrentResult>()
        val urlRegex = Regex("""https://[^.]+\.bandcamp\.com/track/[^"\s]+""")
        val titleRegex = Regex("""class="heading"[^>]*>\s*([^<]+)<""")
        val artistRegex = Regex("""class="subhead"[^>]*>\s*by ([^<]+)<""")
        val urls = urlRegex.findAll(html).map { it.value }.distinct().toList()
        val titles = titleRegex.findAll(html).map { it.groupValues[1].trim() }.toList()
        val artists = artistRegex.findAll(html).map { it.groupValues[1].trim() }.toList()
        urls.take(15).forEachIndexed { i, url ->
            results += TorrentResult(
                id = "bandcamp_${url.hashCode()}",
                title = titles.getOrElse(i) { url.substringAfterLast("/") },
                artist = artists.getOrNull(i),
                album = null, year = null, seeders = 0, leechers = 0, sizeBytes = 0L,
                magnetLink = url, source = "Bandcamp")
        }
        return results
    }
}
