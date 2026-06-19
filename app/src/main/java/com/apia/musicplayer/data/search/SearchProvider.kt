package com.apia.musicplayer.data.search

import com.apia.musicplayer.domain.model.TorrentResult

interface SearchProvider {
    val name: String
    suspend fun search(query: String): List<TorrentResult>
    suspend fun getMagnet(result: TorrentResult): String = result.magnetLink
}
