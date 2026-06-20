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

    // Куки после успешного логина
    private var sessionCookies = ""
    var isLoggedIn = false

    // Зеркала RuTracker (основной + зеркала)
    private val mirrors = listOf(
        "https://rutracker.org/forum",
        "https://rutracker.net/forum",
        "https://rutracker.nl/forum"
    )

    suspend fun login(username: String, password: String): Boolean {
        for (base in mirrors) {
            try {
                val result = tryLogin(base, username, password)
                if (result) return true
            } catch (e: Exception) {
                Log.w("RuTracker", "Login via $base failed: ${e.message}")
            }
        }
        throw Exception("Login failed on all mirrors. Check username/password.")
    }

    private fun tryLogin(base: String, username: String, password: String): Boolean {
        // Шаг 1: страница логина
        val html = fetch("$base/login.php", "") ?: throw Exception("Cannot reach $base")
        val capSid = Jsoup.parse(html)
            .selectFirst("input[name=cap_sid]")?.attr("value") ?: ""

        // Шаг 2: POST
        val body = FormBody.Builder()
            .add("login_username", username)
            .add("login_password", password)
            .add("login", "вход")
            .apply { if (capSid.isNotBlank()) add("cap_sid", capSid) }
            .build()

        val noRedir = client.newBuilder().followRedirects(false).build()
        val resp = noRedir.newCall(
            Request.Builder().url("$base/login.php").post(body)
                .header("User-Agent", ua)
                .header("Referer", "$base/login.php")
                .build()
        ).execute()

        Log.d("RuTracker", "Login $base: HTTP ${resp.code}")
        val jar = mutableMapOf<String, String>()
        resp.headers("Set-Cookie").forEach { h ->
            val kv = h.substringBefore(";").trim()
            val eq = kv.indexOf('=')
            if (eq > 0) jar[kv.substring(0, eq)] = kv.substring(eq + 1)
        }
        resp.close()

        val session = jar["bb_session"] ?: ""
        Log.d("RuTracker", "bb_session=${session.take(10)}...")

        return if (session.isNotBlank() && session != "deleted") {
            sessionCookies = jar.entries.joinToString("; ") { "${it.key}=${it.value}" }
            isLoggedIn = true
            Log.d("RuTracker", "Login OK via $base")
            true
        } else {
            false
        }
    }

    override suspend fun search(query: String): List<TorrentResult> {
        // Без логина — rutracker не даёт результаты, возвращаем пустой список
        // с понятной ошибкой
        if (!isLoggedIn) {
            throw Exception("Login required — enter credentials in Settings")
        }

        val enc = java.net.URLEncoder.encode(query, "UTF-8")
        // Музыкальные форумы: Русский рок, Поп, Электроника, Джаз и т.д.
        val forums = "768,782,793,794,795,799,800,1260,1299,1260"

        for (base in mirrors) {
            try {
                val html = fetch("$base/tracker.php?nm=$enc&f=$forums", sessionCookies)
                    ?: continue
                val results = parseResults(html, base)
                if (results.isNotEmpty()) return results
                // Если 0 результатов — возможно редиректнуло на логин, пробуем след зеркало
            } catch (e: Exception) {
                Log.w("RuTracker", "Search via $base: ${e.message}")
            }
        }
        return emptyList()
    }

    private fun parseResults(html: String, base: String): List<TorrentResult> {
        val doc = Jsoup.parse(html)
        // Если нас редиректнули на форму логина — возвращаем пустой список
        if (doc.selectFirst("form[action*=login]") != null &&
            doc.select("tr.tCenter.hl-tr").isEmpty()) {
            Log.w("RuTracker", "Got login page instead of results — session expired?")
            isLoggedIn = false
            return emptyList()
        }
        return doc.select("tr.tCenter.hl-tr").mapNotNull { row ->
            val a = row.selectFirst("td.t-title a.tLink") ?: return@mapNotNull null
            val title = a.text().ifBlank { return@mapNotNull null }
            val topicId = a.attr("href").substringAfter("t=").substringBefore("&").trim()
            if (topicId.isBlank() || !topicId.all { it.isDigit() }) return@mapNotNull null
            val seeds = row.selectFirst("td.seedmed b")?.text()?.toIntOrNull() ?: 0
            val leech = row.selectFirst("td.leechmed b")?.text()?.toIntOrNull() ?: 0
            val size  = row.selectFirst("td.tor-size")?.text() ?: ""
            TorrentResult(
                id = "rutracker_$topicId", title = title,
                artist = title.substringBefore(" - ").takeIf { title.contains(" - ") },
                album  = null, year = null,
                seeders = seeds, leechers = leech,
                sizeBytes = parseSize(size),
                magnetLink = "$base/viewtopic.php?t=$topicId",
                source = "RuTracker"
            )
        }.sortedByDescending { it.seeders }
    }

    override suspend fun getMagnet(result: TorrentResult): String {
        val html = fetch(result.magnetLink, sessionCookies) ?: return result.magnetLink
        return Jsoup.parse(html)
            .selectFirst("a.magnet-link, a[href^=magnet:]")
            ?.attr("href") ?: result.magnetLink
    }

    private fun fetch(url: String, cookies: String): String? = try {
        val req = Request.Builder().url(url).header("User-Agent", ua)
            .apply { if (cookies.isNotBlank()) header("Cookie", cookies) }
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
