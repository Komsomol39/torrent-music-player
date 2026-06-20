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
    private var cookies: String? = null
    var isLoggedIn = false

    private val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    suspend fun login(username: String, password: String): Boolean {
        return try {
            // Шаг 1: загружаем страницу логина — получаем cap_sid
            val loginPageReq = Request.Builder()
                .url("$baseUrl/login.php")
                .header("User-Agent", ua)
                .build()
            val loginHtml = client.newCall(loginPageReq).execute().use { it.body?.string() ?: "" }
            val loginDoc = Jsoup.parse(loginHtml)
            val capSid = loginDoc.selectFirst("input[name=cap_sid]")?.attr("value") ?: ""
            val capCodeName = loginDoc.selectFirst("input[name^=cap_code_")?.attr("name") ?: ""
            Log.d("RuTracker", "cap_sid=$capSid capCodeName=$capCodeName")

            // Шаг 2: POST с кредами — без авторедиректа чтобы перехватить куки
            val formBody = FormBody.Builder()
                .add("login_username", username)
                .add("login_password", password)
                .add("login", "%E2%E2%EE%E4")
                .apply {
                    if (capSid.isNotBlank()) add("cap_sid", capSid)
                    if (capCodeName.isNotBlank()) add(capCodeName, "")
                }
                .build()

            val noRedirect = client.newBuilder().followRedirects(false).build()
            val resp = noRedirect.newCall(
                Request.Builder()
                    .url("$baseUrl/login.php")
                    .post(formBody)
                    .header("User-Agent", ua)
                    .header("Referer", "$baseUrl/login.php")
                    .header("Origin", "https://rutracker.org")
                    .build()
            ).execute()

            Log.d("RuTracker", "Login HTTP ${resp.code}")
            val setCookies = resp.headers("Set-Cookie")
            Log.d("RuTracker", "Cookies: $setCookies")

            // Собираем куки
            val cookieMap = mutableMapOf<String, String>()
            setCookies.forEach { h ->
                val pair = h.split(";")[0].trim()
                val idx = pair.indexOf('=')
                if (idx > 0) {
                    cookieMap[pair.substring(0, idx)] = pair.substring(idx + 1)
                }
            }
            resp.close()

            val session = cookieMap["bb_session"]
            Log.d("RuTracker", "bb_session=$session")

            if (!session.isNullOrBlank() && session != "deleted") {
                cookies = cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
                isLoggedIn = true
                true
            } else {
                // Читаем ошибку из тела
                val errBody = client.newCall(
                    Request.Builder().url("$baseUrl/login.php")
                        .header("User-Agent", ua)
                        .apply { cookies?.let { header("Cookie", it) } }
                        .build()
                ).execute().use { it.body?.string() ?: "" }
                val errMsg = Jsoup.parse(errBody).selectFirst(".login-form-errors, .mrg_16")?.text() ?: "Unknown error"
                Log.w("RuTracker", "Login failed: $errMsg")
                false
            }
        } catch (e: Exception) {
            Log.e("RuTracker", "Login exception: ${e.message}")
            throw Exception("RuTracker: ${e.message}")
        }
    }

    override suspend fun search(query: String): List<TorrentResult> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "$baseUrl/tracker.php?nm=$encoded&f=768,782,793,794,795,799,800,1260,1299"
        val html = get(url) ?: return emptyList()
        val doc = Jsoup.parse(html)
        val results = mutableListOf<TorrentResult>()
        doc.select("tr.tCenter.hl-tr").forEach { row ->
            val titleEl = row.selectFirst("td.t-title a.tLink") ?: return@forEach
            val title = titleEl.text()
            val href = titleEl.attr("href")
            val topicId = href.substringAfter("t=").substringBefore("&").trim()
            if (topicId.isBlank()) return@forEach
            val seeders  = row.selectFirst("td.seedmed b")?.text()?.toIntOrNull() ?: 0
            val leechers = row.selectFirst("td.leechmed b")?.text()?.toIntOrNull() ?: 0
            val sizeText = row.selectFirst("td.tor-size")?.text() ?: ""
            results += TorrentResult(
                id = "rutracker_$topicId",
                title = title,
                artist = title.substringBefore(" - ").takeIf { title.contains(" - ") },
                album  = title.substringAfter(" - ").takeIf  { title.contains(" - ") },
                year   = null,
                seeders = seeders, leechers = leechers,
                sizeBytes = parseSize(sizeText),
                magnetLink = "$baseUrl/viewtopic.php?t=$topicId",
                source = "RuTracker"
            )
        }
        Log.d("RuTracker", "Found ${results.size} for '$query'")
        return results.sortedByDescending { it.seeders }
    }

    override suspend fun getMagnet(result: TorrentResult): String {
        val html = get(result.magnetLink) ?: return result.magnetLink
        return Jsoup.parse(html)
            .selectFirst("a.magnet-link, a[href^=magnet:]")
            ?.attr("href") ?: result.magnetLink
    }

    private fun get(url: String): String? = try {
        client.newCall(
            Request.Builder().url(url)
                .header("User-Agent", ua)
                .apply { cookies?.let { header("Cookie", it) } }
                .build()
        ).execute().use { it.body?.string() }
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
