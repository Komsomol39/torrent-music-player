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
    private var cookies = ""
    var isLoggedIn = false

    // Работает без авторизации — открытый поиск
    override suspend fun search(query: String): List<TorrentResult> {
        val enc = java.net.URLEncoder.encode(query, "UTF-8")
        // Музыкальные форумы
        val url = "https://rutracker.org/forum/tracker.php?nm=$enc&f=768,782,793,794,795,799,800"
        val html = fetch(url) ?: throw Exception("Cannot reach rutracker.org — check internet connection")
        val doc = Jsoup.parse(html)

        // Если нас перебросило на логин вместо результатов
        if (doc.selectFirst("input[name=login_username]") != null) {
            // Попробуем без форумного фильтра
            val html2 = fetch("https://rutracker.org/forum/tracker.php?nm=$enc") ?: return emptyList()
            return parseResults(Jsoup.parse(html2))
        }
        return parseResults(doc)
    }

    private fun parseResults(doc: org.jsoup.nodes.Document): List<TorrentResult> {
        return doc.select("tr.tCenter.hl-tr").mapNotNull { row ->
            val a = row.selectFirst("td.t-title a.tLink") ?: return@mapNotNull null
            val title = a.text().ifBlank { return@mapNotNull null }
            val topicId = a.attr("href").substringAfter("t=").substringBefore("&").trim()
            if (topicId.isBlank() || !topicId.all { it.isDigit() }) return@mapNotNull null
            val seeds = row.selectFirst("td.seedmed b")?.text()?.toIntOrNull() ?: 0
            val leech = row.selectFirst("td.leechmed b")?.text()?.toIntOrNull() ?: 0
            val size  = row.selectFirst("td.tor-size")?.text() ?: ""
            TorrentResult(
                id = "rutracker_$topicId",
                title = title,
                artist = title.substringBefore(" - ").takeIf { title.contains(" - ") },
                album  = null, year = null,
                seeders = seeds, leechers = leech,
                sizeBytes = parseSize(size),
                magnetLink = "https://rutracker.org/forum/viewtopic.php?t=$topicId",
                source = "RuTracker"
            )
        }.also { Log.d("RuTracker", "Found ${it.size} results") }
         .sortedByDescending { it.seeders }
    }

    // Magnet доступен на странице раздачи без логина
    override suspend fun getMagnet(result: TorrentResult): String {
        val html = fetch(result.magnetLink) ?: return result.magnetLink
        val doc = Jsoup.parse(html)
        // Magnet на странице раздачи
        val magnet = doc.selectFirst("a.magnet-link, a[href^=magnet:]")?.attr("href")
        if (!magnet.isNullOrBlank()) return magnet
        // Если не нашли — строим из infoHash если он есть на странице
        val hash = doc.selectFirst("a[data-hash]")?.attr("data-hash")
            ?: doc.html().substringAfter("xt=urn:btih:").substringBefore("&").take(40)
        if (hash.isNotBlank() && hash.length >= 32) {
            return "magnet:?xt=urn:btih:$hash&tr=udp://tracker.opentrackr.org:1337/announce"
        }
        return result.magnetLink
    }

    // Опциональный логин для расширенного доступа
    suspend fun login(username: String, password: String): Boolean {
        return try {
            val loginHtml = fetch("https://rutracker.org/forum/login.php") ?: throw Exception("Cannot reach rutracker.org")
            val capSid = Jsoup.parse(loginHtml).selectFirst("input[name=cap_sid]")?.attr("value") ?: ""
            val body = FormBody.Builder()
                .add("login_username", username)
                .add("login_password", password)
                .add("login", "вход")
                .apply { if (capSid.isNotBlank()) add("cap_sid", capSid) }
                .build()
            val noRedir = client.newBuilder().followRedirects(false).build()
            val resp = noRedir.newCall(
                Request.Builder().url("https://rutracker.org/forum/login.php").post(body)
                    .header("User-Agent", ua)
                    .header("Referer", "https://rutracker.org/forum/login.php")
                    .build()
            ).execute()
            val jar = mutableMapOf<String, String>()
            resp.headers("Set-Cookie").forEach { h ->
                val kv = h.substringBefore(";").trim()
                val eq = kv.indexOf('=')
                if (eq > 0) jar[kv.substring(0, eq)] = kv.substring(eq + 1)
            }
            resp.close()
            val session = jar["bb_session"] ?: ""
            if (session.isNotBlank() && session != "deleted") {
                cookies = jar.entries.joinToString("; ") { "${it.key}=${it.value}" }
                isLoggedIn = true
                Log.d("RuTracker", "Login OK")
                true
            } else {
                throw Exception("Wrong username or password")
            }
        } catch (e: Exception) {
            Log.e("RuTracker", "Login: ${e.message}")
            throw e
        }
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
