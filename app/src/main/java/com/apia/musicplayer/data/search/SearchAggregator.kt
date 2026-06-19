package com.apia.musicplayer.data.search

import com.apia.musicplayer.domain.model.SearchSource
import com.apia.musicplayer.domain.model.TorrentResult
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchAggregator @Inject constructor(
    val rutracker: RuTrackerProvider,
    val rutor:     RuTorProvider,
    val nnmclub:   NnmClubProvider,
    val tpb:       TpbProvider,
    val nyaa:      NyaaProvider,
    val x1337:     X1337Provider,
    val vk:        VkMusicProvider,
    val youtube:   YouTubeProvider
) {
    // Активные источники (пользователь включает/отключает в настройках)
    val enabledSources = mutableSetOf(
        SearchSource.RUTOR,
        SearchSource.TPB,
        SearchSource.NYAA,
        SearchSource.X1337
    )

    fun providerFor(source: SearchSource): SearchProvider? = when (source) {
        SearchSource.RUTRACKER -> rutracker
        SearchSource.RUTOR     -> rutor
        SearchSource.NNMCLUB   -> nnmclub
        SearchSource.TPB       -> tpb
        SearchSource.NYAA      -> nyaa
        SearchSource.X1337     -> x1337
        SearchSource.VK        -> vk
        SearchSource.YOUTUBE   -> youtube
        else -> null
    }

    /**
     * Параллельный поиск по всем активным источникам.
     * Результаты стримятся по мере готовности через callback.
     */
    suspend fun searchAll(
        query: String,
        sources: Set<SearchSource> = enabledSources,
        onPartialResult: (source: SearchSource, results: List<TorrentResult>) -> Unit
    ) = coroutineScope {
        sources.map { source ->
            async {
                val provider = providerFor(source) ?: return@async
                try {
                    val results = provider.search(query)
                    if (results.isNotEmpty()) onPartialResult(source, results)
                } catch (e: Exception) {
                    // источник недоступен — просто пропускаем
                }
            }
        }.awaitAll()
    }

    /**
     * Поиск по одному источнику
     */
    suspend fun search(query: String, source: SearchSource): List<TorrentResult> {
        return providerFor(source)?.search(query) ?: emptyList()
    }

    /**
     * Получить magnet/URL для результата
     */
    suspend fun getMagnet(result: TorrentResult, source: SearchSource): String {
        return providerFor(source)?.getMagnet(result) ?: result.magnetLink
    }
}
