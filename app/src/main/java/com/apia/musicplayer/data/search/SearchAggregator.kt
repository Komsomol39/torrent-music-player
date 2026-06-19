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
                } catch (e: Exception) { /* source unavailable */ }
            }
        }.awaitAll()
    }

    suspend fun search(query: String, source: SearchSource): List<TorrentResult> =
        providerFor(source)?.search(query) ?: emptyList()

    suspend fun getMagnet(result: TorrentResult, source: SearchSource): String =
        providerFor(source)?.getMagnet(result) ?: result.magnetLink
}
