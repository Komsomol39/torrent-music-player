package com.apia.musicplayer.data.search
import com.apia.musicplayer.domain.model.TorrentResult
import okhttp3.*
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ZaycevProvider @Inject constructor(private val client: OkHttpClient) : SearchProvider {
    override val name = "Zaycev.net"

    override suspend fun search(query: String): List<TorrentResult> {
        val enc = java.net.URLEncoder.encode(query, "UTF-8")
        return try {
            val html = client.newCall(Request.Builder()
                .url("https://zaycev.net/search?keyword=$enc")
                .header("User-Agent","Mozilla/5.0").build())
                .execute().use { it.body?.string() ?: "" }
            val doc = Jsoup.parse(html)
            doc.select(".track-list .track").take(20).mapNotNull { el ->
                val title  = el.selectFirst(".track__info-name")?.text() ?: return@mapNotNull null
                val artist = el.selectFirst(".track__info-artist")?.text() ?: ""
                val src    = el.selectFirst("source[src]")?.attr("src") ?: ""
                if (src.isBlank()) return@mapNotNull null
                TorrentResult("zaycev_${src.hashCode()}", title, artist,
                    null, null, 0, 0, 0L, src, "Zaycev.net")
            }
        } catch (e: Exception) { emptyList() }
    }
}