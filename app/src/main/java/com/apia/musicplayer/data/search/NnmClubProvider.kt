package com.apia.musicplayer.data.search

import com.apia.musicplayer.domain.model.TorrentResult
import okhttp3.*
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NnmClubProvider @Inject constructor(private val client: OkHttpClient) : SearchProvider {

    override val name = "NNM-Club"
    private val baseUrl = "https://nnmclub.to"
    private var sessionCookie: String? = null

    suspend fun login(username: String, password: String): Boolean {
        return try {
            val body = FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .add("autologin", "on")
                .add("login", "Вход")
                .build()
            val req = Request.Builder()
                .url("$baseUrl/forum/login.php")
                .post(body)
                .header("User-Agent", "Mozilla/5.0 (Android)")
                .build()
            val resp = client.newCall(req).execute()
            sessionCookie = resp.headers("Set-Cookie")
                .firstOrNull { it.contains("phpbb2mysql_data") }
                ?.substringBefore(";")
            resp.close()
            sessionCookie != null
        } catch (e: Exception) { false }
    }

    override suspend fun search(query: String): List<TorrentResult> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "$baseUrl/forum/tracker.php?nm=$encoded&f=44,53,315,407" // музыка
        val reqBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android)")
        sessionCookie?.let { reqBuilder.header("Cookie", it) }

        val html = client.newCall(reqBuilder.build()).execute().use { it.body?.string() ?: "" }
        val doc = Jsoup.parse(html)
        val results = mutableListOf<TorrentResult>()

        doc.select("tr.tCenter").forEach { row ->
            val titleEl = row.selectFirst("a.gen") ?: return@forEach
            val title   = titleEl.text()
            val href    = titleEl.attr("href")
            val topicId = Regex("t=(\d+)").find(href)?.groupValues?.get(1) ?: return@forEach
            val seeders = row.selectFirst("span.seedmed")?.text()?.toIntOrNull() ?: 0
            val leechers = row.selectFirst("span.leechmed")?.text()?.toIntOrNull() ?: 0

            results += TorrentResult(
                id = "nnm_$topicId",
                title = title,
                artist = title.substringBefore(" - ").takeIf { title.contains(" - ") },
                album = null, year = null,
                seeders = seeders, leechers = leechers,
                sizeBytes = 0L,
                magnetLink = "$baseUrl/forum/viewtopic.php?t=$topicId",
                source = "NNM-Club"
            )
        }
        return results
    }

    override suspend fun getMagnet(result: TorrentResult): String {
        val reqBuilder = Request.Builder()
            .url(result.magnetLink)
            .header("User-Agent", "Mozilla/5.0 (Android)")
        sessionCookie?.let { reqBuilder.header("Cookie", it) }
        val html = client.newCall(reqBuilder.build()).execute().use { it.body?.string() ?: "" }
        return Jsoup.parse(html).selectFirst("a[href^=magnet:]")?.attr("href") ?: result.magnetLink
    }
}
