package com.apia.musicplayer.data.search

import com.apia.musicplayer.domain.model.TorrentResult
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnionPeerProvider @Inject constructor(private val client: OkHttpClient) : SearchProvider {
    override val name = "UnionPeer"

    override suspend fun search(query: String): List<TorrentResult> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val html = try {
            client.newCall(Request.Builder().url("https://unionpeer.org/browse.php?search=$encoded&cat=8")
                .header("User-Agent","Mozilla/5.0 (Android)").build())
                .execute().use { it.body?.string() ?: "" }
        } catch (e: Exception) { return emptyList() }
        val doc = Jsoup.parse(html)
        return doc.select("table.forum_news tr").drop(1).mapNotNull { row ->
            val a = row.selectFirst("td a[href*=topic]") ?: return@mapNotNull null
            val magnet = row.selectFirst("a[href^=magnet:]")?.attr("href") ?: return@mapNotNull null
            val seeds = row.select("td").getOrNull(3)?.text()?.toIntOrNull() ?: 0
            TorrentResult("unionpeer_${magnet.hashCode()}",
                a.text(), a.text().substringBefore(" - ").takeIf { a.text().contains(" - ") },
                null, null, seeds, 0, 0L, magnet, "UnionPeer")
        }.sortedByDescending { it.seeders }
    }
}
