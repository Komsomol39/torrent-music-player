package com.apia.musicplayer.domain.model

import com.apia.musicplayer.data.torrent.DownloadStatus

data class DownloadState(
    val id: String,
    val magnetLink: String = "",
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Float = 0f,
    val downloadRateBps: Long = 0L,
    val uploadRateBps: Long = 0L,
    val seeds: Int = 0,
    val peers: Int = 0,
    val totalBytes: Long = 0L,
    val eta: Long = 0L,
    val localPath: String? = null,
    val error: String? = null
)
