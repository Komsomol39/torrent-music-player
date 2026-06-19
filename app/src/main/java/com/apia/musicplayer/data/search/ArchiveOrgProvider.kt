package com.apia.musicplayer.data.search

import com.apia.musicplayer.domain.model.TorrentResult
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArchiveOrgProvider @Inject constructor(private val client: OkHttpClient) : SearchProvider {
    override val name = "Archive.org"

    override suspend fun search(query: String): List<TorrentResult> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "https://archive.org/advancedsearch.php?q=$encoded+mediatype:audio&fl=identifier,title,creator,year,downloads&rows=20&output=json"
        val json = try {
            client.newCall(Request.Builder().url(url).header("User-Agent","Mozilla/5.0").build())
                .execute().use { it.body?.string() ?: "" }
        } catch (e: Exception) { return emptyList() }
        return try {
            val docs = JSONObject(json).getJSONObject("response").getJSONArray("docs")
            (0 until docs.length()).map { i ->
                val d = docs.getJSONObject(i)
                val id = d.optString("identifier","")
                TorrentResult("archive_$id", d.optString("title",id),
                    d.optString("creator","").takeIf { it.isNotBlank() },
                    null, d.optString("year","").toIntOrNull(),
                    d.optInt("downloads",0), 0, 0L,
                    "https://archive.org/download/$id", "Archive.org")
            }
        } catch (e: Exception) { emptyList() }
    }
}
