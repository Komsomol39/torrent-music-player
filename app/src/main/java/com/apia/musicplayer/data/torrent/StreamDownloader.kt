package com.apia.musicplayer.data.torrent

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Скачивает HTTP-стрим в фоне пока он играет.
 * Сохраняет в папку Music/Downloaded/
 */
@Singleton
class StreamDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient
) {
    private val downloadDir = File(
        context.getExternalFilesDir(null), "Music/Downloaded"
    ).also { it.mkdirs() }

    suspend fun downloadAsync(url: String, fileName: String): File? =
        withContext(Dispatchers.IO) {
            try {
                val safeName = fileName
                    .replace(Regex("[^a-zA-Zа-яА-Я0-9 ._-]"), "_")
                    .take(80)
                val ext = url.substringAfterLast(".").substringBefore("?").take(4)
                    .ifBlank { "mp3" }
                val file = File(downloadDir, "$safeName.$ext")

                // Не скачиваем повторно
                if (file.exists() && file.length() > 10_000) {
                    Log.d("StreamDownloader", "Already exists: ${file.name}")
                    return@withContext file
                }

                Log.d("StreamDownloader", "Downloading: $url -> ${file.name}")
                val req = Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0 (Android)")
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w("StreamDownloader", "HTTP ${resp.code} for $url")
                        return@withContext null
                    }
                    resp.body?.byteStream()?.use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                Log.d("StreamDownloader", "Saved: ${file.name} (${file.length()/1024}KB)")
                file
            } catch (e: Exception) {
                Log.e("StreamDownloader", "Download failed: ${e.message}")
                null
            }
        }

    fun getCachedFile(fileName: String): File? {
        return downloadDir.listFiles()?.firstOrNull {
            it.nameWithoutExtension.startsWith(
                fileName.replace(Regex("[^a-zA-Zа-яА-Я0-9 ]"), "_").take(20)
            )
        }
    }

    fun listDownloaded(): List<File> =
        downloadDir.listFiles()
            ?.filter { it.isFile && it.length() > 1000 }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
}
