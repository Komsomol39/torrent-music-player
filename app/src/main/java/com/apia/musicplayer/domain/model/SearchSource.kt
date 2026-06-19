package com.apia.musicplayer.domain.model

enum class SearchSource(
    val displayName: String,
    val flag: String,
    val requiresLogin: Boolean = false
) {
    // Российские
    RUTRACKER("RuTracker",  "RU", requiresLogin = true),
    RUTOR("RuTor",          "RU"),
    KINOZAL("Kinozal",      "RU", requiresLogin = true),

    // СНГ
    NNMCLUB("NNM-Club",     "СНГ", requiresLogin = true),
    UNIONPEER("UnionPeer",  "СНГ"),

    // Мировые торренты
    TPB("Pirate Bay",       "🌍"),
    NYAA("Nyaa",            "🌍"),
    X1337("1337x",          "🌍"),

    // Музыкальные
    VK("VK Music",          "VK", requiresLogin = true),
    YOUTUBE("YouTube",      "YT"),
}
