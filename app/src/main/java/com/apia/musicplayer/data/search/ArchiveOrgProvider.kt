package com.apia.musicplayer.data.search
import com.apia.musicplayer.domain.model.TorrentResult
import okhttp3.*
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArchiveOrgProvider @Inject constructor(private val client: OkHttpClient) : SearchProvider {
    override val name = "Archive.org"

    override suspend fun search(query: String): List<TorrentResult> {
        val enc = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "https://archive.org/advancedsearch.php?q=$enc+AND+mediatype:audio" +
            "&fl[]=identifier,title,creator,year,format,downloads" +
            "&rows=20&page=1&output=json"
        return try {
            val json = client.newCall(Request.Builder().url(url)
                .header("User-Agent","Mozilla/5.0").build())
                .execute().use { it.body?.string() ?: "" }
            val docs = JSONObject(json).getJSONObject("response").getJSONArray("docs")
            (0 until docs.length()).map { i ->
                val d = docs.getJSONObject(i)
                val id = d.getString("identifier")
                TorrentResult(
                    id = "archive_$id",
                    title = d.optString("title", id),
                    artist = d.optString("creator", ""),
                    album = null,
                    year = d.optString("year","").toIntOrNull(),
                    seeders = d.optInt("downloads"), leechers = 0, sizeBytes = 0L,
                    magnetLink = "https://archive.org/download/$id",
                    source = "Archive.org"
                )
            }
        } catch (e: Exception) { emptyList() }
    }
}