package com.apia.musicplayer.ui.screens.torrent

import com.apia.musicplayer.data.torrent.TorrentSearchService
import com.apia.musicplayer.domain.model.TorrentResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TorrentRepository @Inject constructor(
    private val searchService: TorrentSearchService
) {
    suspend fun search(query: String): List<TorrentResult> =
        withContext(Dispatchers.IO) {
            searchService.search(query)
        }

    suspend fun resolveMagnet(result: TorrentResult): String =
        withContext(Dispatchers.IO) {
            // Для 1337x — получаем магнет с детальной страницы
            if (result.source == "1337x" && result.magnetLink.startsWith("https://")) {
                searchService.getMagnetFrom1337x(result.magnetLink)
                    ?: result.magnetLink
            } else {
                result.magnetLink
            }
        }

    suspend fun startDownload(result: TorrentResult) {
        // TODO: интегрировать torrent-клиент
        // Вариант A: TorrentStream-Android — стриминг без полной загрузки
        //   implementation("com.github.TorrentStream:TorrentStream-Android:2.7.0")
        // Вариант B: libtorrent4j — полный клиент (требует NDK)
        //   implementation("org.libtorrent4j:libtorrent4j-android-arm64:2.1.0-26")
    }
}
