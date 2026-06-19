package com.apia.musicplayer.data.search

import com.apia.musicplayer.domain.model.TorrentResult
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuTrackerProvider @Inject constructor(
    private val client: OkHttpClient
) : SearchProvider {

    override val name = "RuTracker"
    private val baseUrl = "https://rutracker.org"
    private var sessionCookie: String? = null

    // Логин через POST — нужны учётные данные пользователя
    suspend fun login(username: String, password: String): Boolean {
        return try {
            val body = FormBody.Builder()
                .add("login_username", username)
                .add("login_password", password)
                .add("login", "Вход")
                .build()
            val req = Request.Builder()
                .url("$baseUrl/forum/login.php")
                .post(body)
                .header("Referer", "$baseUrl/forum/index.php")
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:109.0)")
                .build()
            val resp = client.newCall(req).execute()
            // Сохраняем cookie сессии
            val cookies = resp.headers("Set-Cookie")
            sessionCookie = cookies.firstOrNull { it.contains("bb_session") }
                ?.substringBefore(";")
            resp.close()
            sessionCookie != null
        } catch (e: Exception) { false }
    }

    override suspend fun search(query: String): List<TorrentResult> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "$baseUrl/forum/tracker.php?nm=$encoded&f=768,782,793,794,795,799,800" // музыкальные форумы
        val reqBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:109.0)")
        sessionCookie?.let { reqBuilder.header("Cookie", it) }

        val html = client.newCall(reqBuilder.build()).execute().use { it.body?.string() ?: "" }
        val doc = Jsoup.parse(html)
        val results = mutableListOf<TorrentResult>()

        doc.select("tr.tCenter.hl-tr").forEach { row ->
            val titleEl = row.selectFirst("td.t-title a.tLink") ?: return@forEach
            val title   = titleEl.text()
            val href    = titleEl.attr("href")
            val topicId = href.substringAfter("t=").substringBefore("&")
            val seeders = row.selectFirst("td.seedmed b")?.text()?.toIntOrNull() ?: 0
            val leechers = row.selectFirst("td.leechmed b")?.text()?.toIntOrNull() ?: 0
            val sizeText = row.selectFirst("td.tor-size")?.text() ?: ""

            results += TorrentResult(
                id = "rutracker_$topicId",
                title = title,
                artist = parseArtist(title),
                album = parseAlbum(title),
                year = parseYear(title),
                seeders = seeders,
                leechers = leechers,
                sizeBytes = parseSize(sizeText),
                magnetLink = "$baseUrl/forum/dl.php?t=$topicId",
                source = "RuTracker"
            )
        }
        return results.sortedByDescending { it.seeders }
    }

    override suspend fun getMagnet(result: TorrentResult): String {
        // Для RuTracker достаём magnet со страницы раздачи
        val topicId = result.id.removePrefix("rutracker_")
        val reqBuilder = Request.Builder()
            .url("$baseUrl/forum/viewtopic.php?t=$topicId")
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:109.0)")
        sessionCookie?.let { reqBuilder.header("Cookie", it) }

        val html = client.newCall(reqBuilder.build()).execute().use { it.body?.string() ?: "" }
        val doc = Jsoup.parse(html)
        return doc.selectFirst("a.magnet-link")?.attr("href")
            ?: doc.selectFirst("a[href^=magnet:]")?.attr("href")
            ?: result.magnetLink
    }

    private fun parseArtist(title: String): String? {
        val sep = listOf(" - ", " – ")
        return sep.firstNotNullOfOrNull { if (title.contains(it)) title.substringBefore(it).trim() else null }
    }
    private fun parseAlbum(title: String): String? {
        val sep = listOf(" - ", " – ")
        return sep.firstNotNullOfOrNull { if (title.contains(it)) title.substringAfter(it).trim().replace(Regex("\\(\\d{4}\\).*"), "").trim() else null }
    }
    private fun parseYear(title: String) = Regex("\\b(19|20)\\d{2}\\b").find(title)?.value?.toIntOrNull()
    private fun parseSize(s: String): Long {
        val n = Regex("[0-9.]+").find(s)?.value?.toDoubleOrNull() ?: return 0
        return when {
            s.contains("GB", true) || s.contains("ГБ", true) -> (n * 1_073_741_824).toLong()
            s.contains("MB", true) || s.contains("МБ", true) -> (n * 1_048_576).toLong()
            else -> (n * 1024).toLong()
        }
    }
}
