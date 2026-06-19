package com.apia.musicplayer.data.search

import com.apia.musicplayer.domain.model.TorrentResult
import okhttp3.*
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KinozalProvider @Inject constructor(private val client: OkHttpClient) : SearchProvider {
    override val name = "Kinozal"
    private val baseUrl = "https://kinozal.tv"
    private var cookie: String? = null

    suspend fun login(user: String, pass: String): Boolean {
        return try {
            val body = FormBody.Builder().add("username", user).add("password", pass).build()
            val req = Request.Builder().url("$baseUrl/takelogin.php").post(body)
                .header("User-Agent","Mozilla/5.0 (Android)").build()
            val resp = client.newCall(req).execute()
            cookie = resp.headers("Set-Cookie").firstOrNull { it.contains("uid") }?.substringBefore(";")
            resp.close(); cookie != null
        } catch (e: Exception) { false }
    }

    override suspend fun search(query: String): List<TorrentResult> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val rb = Request.Builder().url("$baseUrl/browse.php?s=$encoded&c=23")
            .header("User-Agent","Mozilla/5.0 (Android)")
        cookie?.let { rb.header("Cookie", it) }
        val html = client.newCall(rb.build()).execute().use { it.body?.string() ?: "" }
        val doc = Jsoup.parse(html)
        return doc.select("table.t_peer tr.bg").mapNotNull { row ->
            val a = row.selectFirst("td.nam a") ?: return@mapNotNull null
            val title = a.text()
            val id = Regex("id=(\\d+)").find(a.attr("href"))?.groupValues?.get(1) ?: return@mapNotNull null
            val seeds = row.select("td").getOrNull(5)?.text()?.toIntOrNull() ?: 0
            val size = row.select("td").getOrNull(3)?.text() ?: ""
            TorrentResult("kinozal_$id", title, title.substringBefore(" - ").takeIf { title.contains(" - ") },
                null, null, seeds, 0, parseSize(size), "$baseUrl/get_srv_details.php?id=$id&action=2", "Kinozal")
        }.sortedByDescending { it.seeders }
    }

    override suspend fun getMagnet(result: TorrentResult): String {
        val rb = Request.Builder().url(result.magnetLink).header("User-Agent","Mozilla/5.0 (Android)")
        cookie?.let { rb.header("Cookie", it) }
        val html = client.newCall(rb.build()).execute().use { it.body?.string() ?: "" }
        return Jsoup.parse(html).selectFirst("a[href^=magnet:]")?.attr("href") ?: result.magnetLink
    }

    private fun parseSize(s: String): Long {
        val n = Regex("[0-9.]+").find(s)?.value?.toDoubleOrNull() ?: return 0
        return when { s.contains("ГБ",true)||s.contains("GB",true) -> (n*1_073_741_824).toLong()
            s.contains("МБ",true)||s.contains("MB",true) -> (n*1_048_576).toLong()
            else -> (n*1024).toLong() }
    }
}
