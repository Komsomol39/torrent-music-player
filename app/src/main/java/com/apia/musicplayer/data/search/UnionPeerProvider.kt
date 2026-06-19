package com.apia.musicplayer.data.search
import com.apia.musicplayer.domain.model.TorrentResult
import okhttp3.*; import org.jsoup.Jsoup; import javax.inject.Inject; import javax.inject.Singleton

@Singleton
class UnionPeerProvider @Inject constructor(private val client: OkHttpClient) : SearchProvider {
    override val name = "UnionPeer"
    override suspend fun search(query: String): List<TorrentResult> {
        val enc = java.net.URLEncoder.encode(query, "UTF-8")
        return try {
            val html = client.newCall(Request.Builder().url("https://unionpeer.org/browse.php?search=$enc&cat=8")
                .header("User-Agent","Mozilla/5.0").build()).execute().use { it.body?.string() ?: "" }
            Jsoup.parse(html).select("tr.prow1,tr.prow2").mapNotNull { row ->
                val a = row.selectFirst("a.gen") ?: return@mapNotNull null
                val magnet = row.selectFirst("a[href^=magnet:]")?.attr("href") ?: return@mapNotNull null
                TorrentResult("up_${a.attr("href").hashCode()}", a.text(),
                    a.text().substringBefore(" - ").takeIf { a.text().contains(" - ") },
                    null, null,
                    row.select("td").getOrNull(6)?.text()?.toIntOrNull() ?: 0, 0, 0L, magnet, "UnionPeer")
            }
        } catch (e: Exception) { emptyList() }
    }
}