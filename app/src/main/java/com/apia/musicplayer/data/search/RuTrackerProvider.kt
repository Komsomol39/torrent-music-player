package com.apia.musicplayer.data.search

import android.util.Log
import com.apia.musicplayer.domain.model.TorrentResult
import okhttp3.*
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuTrackerProvider @Inject constructor(
    private val client: OkHttpClient
) : SearchProvider {

    override val name = "RuTracker"
    private val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/121.0.0.0 Safari/537.36"

    // Сессия — заполняется после login()
    private var sessionCookies = ""
    var isLoggedIn = false

    // ── Публичный поиск через RSS без авторизации ─────────────────
    // rutracker имеет RSS-фид поиска который не требует логина
    override suspend fun search(query: String): List<TorrentResult> {
        // Сначала пробуем RSS (без логина)
        val rssResults = searchViaRss(query)
        if (rssResults.isNotEmpty()) return rssResults

        // Если залогинены — через основной сайт
        if (isLoggedIn) return searchLoggedIn(query)

        return emptyList()
    }

    private fun searchViaRss(query: String): List<TorrentResult> {
        return try {
            val enc = java.net.URLEncoder.encode(query, "UTF-8")
            // RSS поиск — работает без авторизации
            val url = "https://rutracker.org/forum/tracker.php?nm=$enc&f=768,782,793,794,795,799,800&start=0"
            val req = Request.Builder().url(url)
                .header("User-Agent", ua)
                .apply { if (sessionCookies.isNotBlank()) header("Cookie", sessionCookies) }
                .build()
            val html = client.newCall(req).execute().use { it.body?.string() ?: "" }
            parseTrackerPage(html)
        } catch (e: Exception) {
            Log.w("RuTracker", "RSS search failed: ${e.message}")
            emptyList()
        }
    }

    private fun searchLoggedIn(query: String): List<TorrentResult> {
        return try {
            val enc = java.net.URLEncoder.encode(query, "UTF-8")
            val html = fetch("https://rutracker.org/forum/tracker.php?nm=$enc&f=768,782,793,794,795,799,800")
                ?: return emptyList()
            parseTrackerPage(html)
        } catch (e: Exception) { emptyList() }
    }

    private fun parseTrackerPage(html: String): List<TorrentResult> {
        val doc = Jsoup.parse(html)
        val results = mutableListOf<TorrentResult>()
        doc.select("tr.tCenter.hl-tr").forEach { row ->
            val a = row.selectFirst("td.t-title a.tLink") ?: return@forEach
            val title = a.text().ifBlank { return@forEach }
            val href = a.attr("href")
            val topicId = href.substringAfter("t=").substringBefore("&").trim()
            if (topicId.isBlank() || !topicId.all { it.isDigit() }) return@forEach
            val seeds  = row.selectFirst("td.seedmed b")?.text()?.toIntOrNull() ?: 0
            val leech  = row.selectFirst("td.leechmed b")?.text()?.toIntOrNull() ?: 0
            val size   = row.selectFirst("td.tor-size")?.text() ?: ""
            results += TorrentResult(
                id = "rutracker_$topicId", title = title,
                artist = title.substringBefore(" - ").takeIf { title.contains(" - ") },
                album  = null, year = null,
                seeders = seeds, leechers = leech,
                sizeBytes = parseSize(size),
                magnetLink = "https://rutracker.org/forum/viewtopic.php?t=$topicId",
                source = "RuTracker"
            )
        }
        Log.d("RuTracker", "Parsed ${results.size} results")
        return results.sortedByDescending { it.seeders }
    }

    suspend fun login(username: String, password: String): Boolean {
        return try {
            // Получаем форму для cap_sid
            val loginHtml = fetch("https://rutracker.org/forum/login.php") ?: throw Exception("Cannot reach rutracker.org")
            val doc = Jsoup.parse(loginHtml)
            val capSid = doc.selectFirst("input[name=cap_sid]")?.attr("value") ?: ""

            val body = FormBody.Builder()
                .add("login_username", username)
                .add("login_password", password)
                .add("login", "вход")
                .apply { if (capSid.isNotBlank()) add("cap_sid", capSid) }
                .build()

            val noRedir = client.newBuilder().followRedirects(false).build()
            val resp = noRedir.newCall(
                Request.Builder()
                    .url("https://rutracker.org/forum/login.php")
                    .post(body)
                    .header("User-Agent", ua)
                    .header("Referer", "https://rutracker.org/forum/login.php")
                    .build()
            ).execute()

            Log.d("RuTracker", "Login: HTTP ${resp.code}")
            val jar = mutableMapOf<String, String>()
            resp.headers("Set-Cookie").forEach { h ->
                val kv = h.substringBefore(";").trim()
                val eq = kv.indexOf('=')
                if (eq > 0) jar[kv.substring(0, eq)] = kv.substring(eq + 1)
            }
            resp.close()

            val session = jar["bb_session"] ?: ""
            Log.d("RuTracker", "bb_session=${session.take(20)}")

            if (session.isNotBlank() && session != "deleted") {
                sessionCookies = jar.entries.joinToString("; ") { "${it.key}=${it.value}" }
                isLoggedIn = true
                true
            } else {
                throw Exception("Login failed — wrong username or password")
            }
        } catch (e: Exception) {
            Log.e("RuTracker", "Login error: ${e.message}")
            isLoggedIn = false
            throw e
        }
    }

    override suspend fun getMagnet(result: TorrentResult): String {
        val html = fetch(result.magnetLink) ?: return result.magnetLink
        return Jsoup.parse(html).selectFirst("a.magnet-link, a[href^=magnet:]")
            ?.attr("href") ?: result.magnetLink
    }

    private fun fetch(url: String): String? = try {
        val req = Request.Builder().url(url).header("User-Agent", ua)
            .apply { if (sessionCookies.isNotBlank()) header("Cookie", sessionCookies) }
            .build()
        client.newCall(req).execute().use { it.body?.string() }
    } catch (e: Exception) { null }

    private fun parseSize(s: String): Long {
        val n = Regex("[0-9.]+").find(s)?.value?.toDoubleOrNull() ?: return 0
        return when {
            s.contains("GB", true) || s.contains("ГБ", true) -> (n * 1_073_741_824).toLong()
            s.contains("MB", true) || s.contains("МБ", true) -> (n * 1_048_576).toLong()
            else -> (n * 1024).toLong()
        }
    }
}
