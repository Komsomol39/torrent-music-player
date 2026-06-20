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
    private val baseUrl = "https://rutracker.org/forum"
    private var cookies = ""
    var isLoggedIn = false

    private val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"

    suspend fun login(username: String, password: String): Boolean {
        return try {
            // Шаг 1: загружаем форму логина — получаем cap_sid
            val loginHtml = fetch("$baseUrl/login.php")
                ?: throw Exception("Cannot reach rutracker.org")
            val doc = Jsoup.parse(loginHtml)
            val capSid = doc.selectFirst("input[name=cap_sid]")?.attr("value") ?: ""

            // Шаг 2: POST — не следуем редиректу чтобы поймать Set-Cookie
            val body = FormBody.Builder()
                .add("login_username", username)
                .add("login_password", password)
                .add("login", "вход")
                .apply { if (capSid.isNotBlank()) add("cap_sid", capSid) }
                .build()

            val req = Request.Builder()
                .url("$baseUrl/login.php")
                .post(body)
                .header("User-Agent", ua)
                .header("Referer", "$baseUrl/login.php")
                .build()

            val noRedir = client.newBuilder().followRedirects(false).build()
            val resp = noRedir.newCall(req).execute()
            Log.d("RuTracker", "Login HTTP ${resp.code}")

            // Собираем все Set-Cookie заголовки
            val jar = mutableMapOf<String, String>()
            resp.headers("Set-Cookie").forEach { h ->
                val kv = h.substringBefore(";").trim()
                val eq = kv.indexOf('=')
                if (eq > 0) jar[kv.substring(0, eq)] = kv.substring(eq + 1)
            }
            resp.close()
            Log.d("RuTracker", "Cookies after login: ${jar.keys}")

            val session = jar["bb_session"] ?: ""
            if (session.isBlank() || session == "deleted") {
                // Пробуем прочитать ошибку — следующий GET с куками
                val errPage = fetch("$baseUrl/login.php")
                val errText = Jsoup.parse(errPage ?: "")
                    .selectFirst(".login-form-errors, .mrg_16, #login-form")
                    ?.text()?.take(120) ?: "Wrong login or password"
                Log.w("RuTracker", "Login failed: $errText")
                throw Exception(errText)
            }

            cookies = jar.entries.joinToString("; ") { "${it.key}=${it.value}" }
            isLoggedIn = true
            Log.d("RuTracker", "Login OK, session=$session")
            true
        } catch (e: Exception) {
            Log.e("RuTracker", "Login error: ${e.message}")
            throw e   // пробрасываем чтобы SettingsViewModel показал ошибку
        }
    }

    override suspend fun search(query: String): List<TorrentResult> {
        val enc = java.net.URLEncoder.encode(query, "UTF-8")
        val html = fetch("$baseUrl/tracker.php?nm=$enc&f=768,782,793,794,795,799,800")
            ?: return emptyList()
        val results = mutableListOf<TorrentResult>()
        Jsoup.parse(html).select("tr.tCenter.hl-tr").forEach { row ->
            val a  = row.selectFirst("td.t-title a.tLink") ?: return@forEach
            val id = a.attr("href").substringAfter("t=").substringBefore("&").trim()
            if (id.isBlank()) return@forEach
            val seeds = row.selectFirst("td.seedmed b")?.text()?.toIntOrNull() ?: 0
            val leech = row.selectFirst("td.leechmed b")?.text()?.toIntOrNull() ?: 0
            val size  = row.selectFirst("td.tor-size")?.text() ?: ""
            val title = a.text()
            results += TorrentResult(
                id = "rutracker_$id", title = title,
                artist = title.substringBefore(" - ").takeIf { title.contains(" - ") },
                album  = null, year = null,
                seeders = seeds, leechers = leech,
                sizeBytes = parseSize(size),
                magnetLink = "$baseUrl/viewtopic.php?t=$id",
                source = "RuTracker"
            )
        }
        return results.sortedByDescending { it.seeders }
    }

    override suspend fun getMagnet(result: TorrentResult): String {
        val html = fetch(result.magnetLink) ?: return result.magnetLink
        return Jsoup.parse(html)
            .selectFirst("a.magnet-link, a[href^=magnet:]")
            ?.attr("href") ?: result.magnetLink
    }

    private fun fetch(url: String): String? = try {
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
