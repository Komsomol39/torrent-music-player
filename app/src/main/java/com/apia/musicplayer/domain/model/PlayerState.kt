package com.apia.musicplayer.domain.model

data class PlayerState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val progress: Float = 0f,           // 0..1
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isShuffleOn: Boolean = false,
    val queue: List<Track> = emptyList()
)

enum class RepeatMode { OFF, ONE, ALL }