package com.apia.musicplayer.data.search
import com.apia.musicplayer.domain.model.TorrentResult
import okhttp3.*; import org.jsoup.Jsoup; import javax.inject.Inject; import javax.inject.Singleton

@Singleton
class KinozalProvider @Inject constructor(private val client: OkHttpClient) : SearchProvider {
    override val name = "Kinozal"
    private val base = "https://kinozal.tv"
    private var cookie: String? = null

    suspend fun login(user: String, pass: String): Boolean = try {
        val body = FormBody.Builder().add("user", user).add("pass", pass).add("action", "login").build()
        val r = client.newCall(Request.Builder().url("$base/takelogin.php").post(body)
            .header("User-Agent","Mozilla/5.0").build()).execute()
        cookie = r.headers("Set-Cookie").firstOrNull { it.contains("uid") }?.substringBefore(";")
        r.close(); cookie != null
    } catch (e: Exception) { false }

    override suspend fun search(query: String): List<TorrentResult> {
        val enc = java.net.URLEncoder.encode(query, "UTF-8")
        val req = Request.Builder().url("$base/browse.php?s=$enc&c=8") // c=8 = музыка
            .header("User-Agent","Mozilla/5.0").apply { cookie?.let { header("Cookie", it) } }.build()
        val html = try { client.newCall(req).execute().use { it.body?.string() ?: "" } } catch (e: Exception) { return emptyList() }
        return Jsoup.parse(html).select("tr.first,tr.butt").mapNotNull { row ->
            val a = row.selectFirst("td.nam a") ?: return@mapNotNull null
            val id = a.attr("href").substringAfter("id=").substringBefore("&")
            TorrentResult("kinozal_$id", a.text(),
                a.text().substringBefore(" - ").takeIf { a.text().contains(" - ") },
                null, null,
                row.selectFirst("td.s")?.text()?.toIntOrNull() ?: 0,
                row.selectFirst("td.l")?.text()?.toIntOrNull() ?: 0,
                0L, "$base/get_srv_details.php?id=$id&action=2", "Kinozal")
        }
    }
    override suspend fun getMagnet(result: TorrentResult): String {
        val req = Request.Builder().url(result.magnetLink)
            .header("User-Agent","Mozilla/5.0").apply { cookie?.let { header("Cookie", it) } }.build()
        return try { val html = client.newCall(req).execute().use { it.body?.string() ?: "" }
            Jsoup.parse(html).selectFirst("a[href^=magnet:]")?.attr("href") ?: result.magnetLink
        } catch (e: Exception) { result.magnetLink }
    }
}