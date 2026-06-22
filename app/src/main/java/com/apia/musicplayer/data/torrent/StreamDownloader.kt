package com.apia.musicplayer.data.torrent

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class HttpDownload(
    val id: String,
    val title: String,
    val url: String,
    val progress: Float = 0f,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val status: HttpDownloadStatus = HttpDownloadStatus.DOWNLOADING,
    val filePath: String = ""
)

enum class HttpDownloadStatus { DOWNLOADING, DONE, ERROR }

@Singleton
class StreamDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient
) {
    private val downloadDir = File(
        context.getExternalFilesDir(null), "Music/Downloaded"
    ).also { it.mkdirs() }

    private val _downloads = MutableStateFlow<Map<String, HttpDownload>>(emptyMap())
    val downloads: StateFlow<Map<String, HttpDownload>> = _downloads.asStateFlow()

    suspend fun downloadAsync(url: String, title: String): File? =
        withContext(Dispatchers.IO) {
            val id = url.hashCode().toString()
            val safeName = title
                .replace(Regex("[^a-zA-Zа-яА-Я0-9 ._-]"), "_")
                .take(80)
            val ext = url.substringAfterLast(".", "mp3")
                .substringBefore("?").take(4)
                .let { if (it.length in 2..4) it else "mp3" }
            val file = File(downloadDir, "$safeName.$ext")

            // Уже скачан
            if (file.exists() && file.length() > 10_000) {
                Log.d("Downloader", "Already exists: ${file.name}")
                _downloads.update { it + (id to HttpDownload(
                    id = id, title = title, url = url,
                    progress = 1f, bytesDownloaded = file.length(),
                    totalBytes = file.length(), status = HttpDownloadStatus.DONE,
                    filePath = file.absolutePath
                )) }
                return@withContext file
            }

            // Начинаем загрузку
            _downloads.update { it + (id to HttpDownload(
                id = id, title = title, url = url, status = HttpDownloadStatus.DOWNLOADING
            )) }

            try {
                val req = Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0 (Android)")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w("Downloader", "HTTP ${resp.code}")
                        _downloads.update { it + (id to (it[id]!!.copy(status = HttpDownloadStatus.ERROR))) }
                        return@withContext null
                    }
                    val total = resp.body?.contentLength() ?: -1L
                    var downloaded = 0L
                    resp.body?.byteStream()?.use { input ->
                        file.outputStream().use { output ->
                            val buf = ByteArray(8192)
                            var read: Int
                            while (input.read(buf).also { read = it } != -1) {
                                output.write(buf, 0, read)
                                downloaded += read
                                // Обновляем прогресс каждые 50KB
                                if (downloaded % 51200 < 8192) {
                                    val pct = if (total > 0) downloaded.toFloat() / total else 0f
                                    _downloads.update { map ->
                                        map + (id to (map[id]!!.copy(
                                            progress = pct,
                                            bytesDownloaded = downloaded,
                                            totalBytes = total
                                        )))
                                    }
                                }
                            }
                        }
                    }
                    Log.d("Downloader", "Done: ${file.name} (${file.length()/1024}KB)")
                    _downloads.update { it + (id to HttpDownload(
                        id = id, title = title, url = url,
                        progress = 1f, bytesDownloaded = downloaded,
                        totalBytes = downloaded, status = HttpDownloadStatus.DONE,
                        filePath = file.absolutePath
                    )) }
                    file
                }
            } catch (e: Exception) {
                Log.e("Downloader", "Error: ${e.message}")
                _downloads.update { it + (id to (it[id]?.copy(status = HttpDownloadStatus.ERROR)
                    ?: HttpDownload(id=id, title=title, url=url, status=HttpDownloadStatus.ERROR))) }
                null
            }
        }

    fun listDownloaded(): List<File> =
        downloadDir.listFiles()
            ?.filter { it.isFile && it.length() > 1000 }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
}
