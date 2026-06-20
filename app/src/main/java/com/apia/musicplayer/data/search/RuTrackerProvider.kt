package com.apia.musicplayer.data.search

import android.util.Log
import com.apia.musicplayer.domain.model.TorrentResult
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.jsoup.Jsoup
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuTrackerProvider @Inject constructor(
    private val client: OkHttpClient
) : SearchProvider {

    override val name = "RuTracker"
    private val baseUrl = "https://rutracker.org/forum"
    private var cookies: String? = null
    var isLoggedIn = false

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language" to "ru-RU,ru;q=0.9,en;q=0.8",
        "Origin" to "https://rutracker.org",
        "Referer" to "https://rutracker.org/forum/login.php"
    )

    suspend fun login(username: String, password: String): Boolean {
        return try {
            // Шаг 1: получаем страницу логина (нужно для cap_sid и формы)
            val loginPage = get("$baseUrl/login.php") ?: return false
            val doc = Jsoup.parse(loginPage)
            val capSid = doc.selectFirst("input[name=cap_sid]")?.attr("value") ?: ""
            val capCode = doc.selectFirst("input[name^=cap_code]")?.attr("name") ?: ""

            // Шаг 2: POST логин
            val formBody = FormBody.Builder()
                .add("login_username", username)
                .add("login_password", password)
                .add("login", "Вход")
                .apply {
                    if (capSid.isNotBlank()) add("cap_sid", capSid)
                    if (capCode.isNotBlank()) add(capCode, "")
                }
                .build()

            val req = Request.Builder()
                .url("$baseUrl/login.php")
                .post(formBody)
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()

            // Не следуем редиректу автоматически — нужно собрать куки
            val noRedirectClient = client.newBuilder()
                .followRedirects(false)
                .build()

            val resp = noRedirectClient.newCall(req).execute()
            val setCookies = resp.headers("Set-Cookie")
            Log.d("RuTracker", "Login response: ${resp.code}, cookies: $setCookies")

            // Собираем все куки
            val cookieMap = mutableMapOf<String, String>()
            setCookies.forEach { header ->
                val parts = header.split(";")[0].trim()
                val (name, value) = parts.split("=", limit = 2).let {
                    it[0] to (it.getOrElse(1) { "" })
                }
                cookieMap[name] = value
            }

            // bb_session — признак успешного логина
            val bbSession = cookieMap["bb_session"]
            Log.d("RuTracker", "bb_session: $bbSession")

            if (bbSession != null && bbSession.isNotBlank() && bbSession != "deleted") {
                cookies = cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
                isLoggedIn = true
                Log.d("RuTracker", "Login successful")
                true
            } else {
                // Проверяем тело ответа на ошибку
                val body = resp.body?.string() ?: ""
                val errMsg = Jsoup.parse(body).selectFirst(".login-form-errors")?.text()
                Log.w("RuTracker", "Login failed: $errMsg")
                false
            }
        } catch (e: Exception) {
            Log.e("RuTracker", "Login exception: ${e.message}")
            false
        }
    }

    override suspend fun search(query: String): List<TorrentResult> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        // f= список музыкальных форумов RuTracker
        val url = "$baseUrl/tracker.php?nm=$encoded&f=768,782,793,794,795,799,800,1260,1299"
        val html = get(url) ?: return emptyList()
        val doc = Jsoup.parse(html)
        val results = mutableListOf<TorrentResult>()

        doc.select("tr.tCenter.hl-tr").forEach { row ->
            val titleEl = row.selectFirst("td.t-title a.tLink") ?: return@forEach
            val title = titleEl.text()
            val href = titleEl.attr("href")
            val topicId = Regex("t=(\\d+)").find(href)?.groupValues?.get(1) ?: return@forEach
            val seeders = row.selectFirst("td.seedmed b")?.text()?.toIntOrNull() ?: 0
            val leechers = row.selectFirst("td.leechmed b")?.text()?.toIntOrNull() ?: 0
            val sizeText = row.selectFirst("td.tor-size")?.text() ?: ""
            results += TorrentResult(
                id = "rutracker_$topicId",
                title = title,
                artist = title.substringBefore(" - ").takeIf { title.contains(" - ") },
                album = title.substringAfter(" - ").takeIf { title.contains(" - ") },
                year = Regex("\\b(19|20)\\d{2}\\b").find(title)?.value?.toIntOrNull(),
                seeders = seeders, leechers = leechers,
                sizeBytes = parseSize(sizeText),
                magnetLink = "$baseUrl/viewtopic.php?t=$topicId",
                source = "RuTracker"
            )
        }
        Log.d("RuTracker", "Found ${results.size} results for '$query'")
        return results.sortedByDescending { it.seeders }
    }

    override suspend fun getMagnet(result: TorrentResult): String {
        val html = get(result.magnetLink) ?: return result.magnetLink
        val doc = Jsoup.parse(html)
        return doc.selectFirst("a.magnet-link")?.attr("href")
            ?: doc.selectFirst("a[href^=magnet:]")?.attr("href")
            ?: result.magnetLink
    }

    private fun get(url: String): String? {
        return try {
            val req = Request.Builder().url(url)
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .apply { cookies?.let { header("Cookie", it) } }
                .build()
            client.newCall(req).execute().use { it.body?.string() }
        } catch (e: Exception) {
            Log.e("RuTracker", "GET $url failed: ${e.message}")
            null
        }
    }

    private fun parseSize(s: String): Long {
        val n = Regex("[0-9.]+").find(s)?.value?.toDoubleOrNull() ?: return 0
        return when {
            s.contains("GB", true) || s.contains("ГБ", true) -> (n * 1_073_741_824).toLong()
            s.contains("MB", true) || s.contains("МБ", true) -> (n * 1_048_576).toLong()
            else -> (n * 1024).toLong()
        }
    }
}
