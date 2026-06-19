package com.apia.musicplayer.data.search

import com.apia.musicplayer.domain.model.TorrentResult
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ZaycevProvider @Inject constructor(private val client: OkHttpClient) : SearchProvider {
    override val name = "Зайцев.нет"

    override suspend fun search(query: String): List<TorrentResult> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val html = try {
            client.newCall(Request.Builder().url("https://zaycev.net/search/?query=$encoded")
                .header("User-Agent","Mozilla/5.0 (Android)").build())
                .execute().use { it.body?.string() ?: "" }
        } catch (e: Exception) { return emptyList() }
        val doc = Jsoup.parse(html)
        return doc.select("li.track-list__item").mapNotNull { el ->
            val title = el.selectFirst(".track-list__track-name")?.text() ?: return@mapNotNull null
            val artist = el.selectFirst(".track-list__track-author")?.text() ?: ""
            val mp3 = el.selectFirst("a[data-url]")?.attr("data-url") ?: return@mapNotNull null
            TorrentResult("zaycev_${mp3.hashCode()}", title, artist.ifBlank { null },
                null, null, 0, 0, 0L, mp3, "Зайцев")
        }
    }
}
