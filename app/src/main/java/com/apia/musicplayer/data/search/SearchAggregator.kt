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
    val torapi:     TorApiProvider,
    val rutor:      RuTorProvider,
    val rutracker:  RuTrackerProvider,
    val kinozal:    KinozalProvider,
    val nnmclub:    NnmClubProvider,
    val tpb:        TpbProvider,
    val nyaa:       NyaaProvider,
    val x1337:      X1337Provider,
    val openru:     OpenRuProvider,
    val vk:         VkMusicProvider,
    val youtube:    YouTubeProvider,
    val soundcloud: SoundCloudProvider,
    val yandex:     YandexMusicProvider,
    val zaycev:     ZaycevProvider,
    val deezer:     DeezerProvider,
    val jamendo:    JamendoProvider,
    val fma:        FmaProvider,
    val bandcamp:   BandcampProvider,
    val archive:    ArchiveOrgProvider
) {
    val enabledSources = mutableSetOf(
        *SearchSource.entries.filter { it.meta.defaultEnabled }.toTypedArray()
    )

    fun providerFor(source: SearchSource): SearchProvider? = when (source) {
        SearchSource.TORAPI     -> torapi
        SearchSource.RUTOR      -> rutor
        SearchSource.RUTRACKER  -> rutracker
        SearchSource.KINOZAL    -> kinozal
        SearchSource.NNMCLUB    -> nnmclub
        SearchSource.TPB        -> tpb
        SearchSource.NYAA       -> nyaa
        SearchSource.X1337      -> x1337
        SearchSource.OPENRU     -> openru
        SearchSource.VK         -> vk
        SearchSource.YOUTUBE    -> youtube
        SearchSource.SOUNDCLOUD -> soundcloud
        SearchSource.YANDEX     -> yandex
        SearchSource.ZAYCEV     -> zaycev
        SearchSource.DEEZER     -> deezer
        SearchSource.JAMENDO    -> jamendo
        SearchSource.FMA        -> fma
        SearchSource.BANDCAMP   -> bandcamp
        SearchSource.ARCHIVE    -> archive
    }

    suspend fun searchAll(
        query: String,
        sources: Set<SearchSource> = enabledSources,
        onResult: (SourceResult) -> Unit
    ) = coroutineScope {
        Log.d("Aggregator", "Searching ${sources.size} sources for: $query")
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
                    Log.d("Aggregator", "${source.name}: ${results.size} in ${ms}ms")
                    onResult(SourceResult(source, results, durationMs = ms))
                } catch (e: Exception) {
                    val ms = System.currentTimeMillis() - t0
                    Log.w("Aggregator", "${source.name} error: ${e.message}")
                    onResult(SourceResult(source, error = e.message?.take(100), durationMs = ms))
                }
            }
        }.awaitAll()
    }

    suspend fun getMagnet(result: TorrentResult, source: SearchSource): String =
        withContext(Dispatchers.IO) {
            providerFor(source)?.getMagnet(result) ?: result.magnetLink
        }
}
