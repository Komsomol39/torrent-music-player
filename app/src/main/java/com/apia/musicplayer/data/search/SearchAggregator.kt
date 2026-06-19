package com.apia.musicplayer.data.search

import android.util.Log
import com.apia.musicplayer.domain.model.SearchSource
import com.apia.musicplayer.domain.model.TorrentResult
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

data class SourceResult(
    val source: SearchSource,
    val results: List<TorrentResult> = emptyList(),
    val error: String? = null,
    val durationMs: Long = 0
)

@Singleton
class SearchAggregator @Inject constructor(
    val rutracker: RuTrackerProvider,
    val rutor:     RuTorProvider,
    val kinozal:   KinozalProvider,
    val nnmclub:   NnmClubProvider,
    val unionpeer: UnionPeerProvider,
    val tpb:       TpbProvider,
    val nyaa:      NyaaProvider,
    val x1337:     X1337Provider,
    val vk:        VkMusicProvider,
    val youtube:   YouTubeProvider,
    val soundcloud: SoundCloudProvider,
    val deezer:    DeezerProvider,
    val yandex:    YandexMusicProvider,
    val zaycev:    ZaycevProvider,
    val archive:   ArchiveOrgProvider,
    val bandcamp:  BandcampProvider,
    val jamendo:   JamendoProvider,
    val fma:       FmaProvider
) {
    val enabledSources = mutableSetOf(
        *SearchSource.entries.filter { it.meta.defaultEnabled }.toTypedArray()
    )

    fun providerFor(source: SearchSource): SearchProvider? = when (source) {
        SearchSource.RUTRACKER  -> rutracker
        SearchSource.RUTOR      -> rutor
        SearchSource.KINOZAL    -> kinozal
        SearchSource.NNMCLUB    -> nnmclub
        SearchSource.UNIONPEER  -> unionpeer
        SearchSource.TPB        -> tpb
        SearchSource.NYAA       -> nyaa
        SearchSource.X1337      -> x1337
        SearchSource.VK         -> vk
        SearchSource.YOUTUBE    -> youtube
        SearchSource.SOUNDCLOUD -> soundcloud
        SearchSource.DEEZER     -> deezer
        SearchSource.YANDEX     -> yandex
        SearchSource.ZAYCEV     -> zaycev
        SearchSource.ARCHIVE    -> archive
        SearchSource.BANDCAMP   -> bandcamp
        SearchSource.JAMENDO    -> jamendo
        SearchSource.FMA        -> fma
    }

    /**
     * Параллельный поиск. Возвращает SourceResult для каждого источника —
     * включая ошибки, чтобы UI мог показать что именно не сработало.
     */
    suspend fun searchAll(
        query: String,
        sources: Set<SearchSource> = enabledSources,
        onResult: (SourceResult) -> Unit
    ) = coroutineScope {
        Log.d("SearchAggregator", "Searching ${sources.size} sources for: $query")
        sources.map { source ->
            async(Dispatchers.IO) {
                val t0 = System.currentTimeMillis()
                val provider = providerFor(source)
                if (provider == null) {
                    onResult(SourceResult(source, error = "No provider"))
                    return@async
                }
                try {
                    val results = provider.search(query)
                    val ms = System.currentTimeMillis() - t0
                    Log.d("SearchAggregator", "${source.name}: ${results.size} results in ${ms}ms")
                    onResult(SourceResult(source, results, durationMs = ms))
                } catch (e: Exception) {
                    val ms = System.currentTimeMillis() - t0
                    val err = e.message ?: e.javaClass.simpleName
                    Log.w("SearchAggregator", "${source.name} failed: $err")
                    onResult(SourceResult(source, error = err, durationMs = ms))
                }
            }
        }.awaitAll()
    }

    suspend fun getMagnet(result: TorrentResult, source: SearchSource): String =
        providerFor(source)?.getMagnet(result) ?: result.magnetLink
}
