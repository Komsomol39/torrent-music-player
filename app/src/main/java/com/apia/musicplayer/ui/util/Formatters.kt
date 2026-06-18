package com.apia.musicplayer.ui.util

fun Long.formatDuration(): String {
    val totalSeconds = this / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

fun Long.formatSize(): String {
    return when {
        this >= 1_073_741_824 -> "%.1f GB".format(this / 1_073_741_824.0)
        this >= 1_048_576 -> "%.1f MB".format(this / 1_048_576.0)
        else -> "%.0f KB".format(this / 1024.0)
    }
}