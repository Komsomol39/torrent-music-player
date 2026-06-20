package com.apia.musicplayer.domain.model

enum class SourceCategory(val label: String) {
    RU("🇷🇺 Российские"),
    CIS("🌐 СНГ"),
    WORLD("🌍 Мировые"),
    MUSIC("🎵 Музыкальные"),
    FREE("✅ Легальные / Бесплатные")
}

data class SourceMeta(
    val displayName: String,
    val category: SourceCategory,
    val emoji: String,
    val description: String,
    val quality: String,
    val authType: AuthType,
    val defaultEnabled: Boolean = false
)

enum class AuthType { NONE, LOGIN_PASS, TOKEN, OPTIONAL_TOKEN }

enum class SearchSource {
    // Российские
    TORAPI,      // TorAPI агрегатор (RuTracker+RuTor+Kinozal+NNM без логина)
    RUTRACKER,   // Прямой RuTracker (требует логин)
    RUTOR,
    KINOZAL,
    // СНГ
    NNMCLUB, UNIONPEER,
    // Мировые
    TPB, NYAA, X1337, OPENRU,
    // Музыкальные
    VK, YOUTUBE, SOUNDCLOUD, DEEZER, YANDEX, ZAYCEV,
    // Легальные
    ARCHIVE, BANDCAMP, JAMENDO, FMA;

    val meta: SourceMeta get() = when (this) {
        TORAPI     -> SourceMeta("RU Torrents",    SourceCategory.RU,    "🇷🇺", "RuTracker+RuTor+Kinozal+NNM через публичный TorAPI — без логина", "FLAC / MP3 320", AuthType.NONE, defaultEnabled = true)
        RUTRACKER  -> SourceMeta("RuTracker",      SourceCategory.RU,    "🎸", "Прямой доступ — требует логин и VPN", "FLAC / MP3 320", AuthType.LOGIN_PASS)
        RUTOR      -> SourceMeta("RuTor",          SourceCategory.RU,    "🎵", "Без регистрации", "Mixed", AuthType.NONE, defaultEnabled = true)
        KINOZAL    -> SourceMeta("Kinozal",        SourceCategory.RU,    "💿", "Высокое качество FLAC", "FLAC", AuthType.LOGIN_PASS)
        NNMCLUB    -> SourceMeta("NNM-Club",       SourceCategory.CIS,   "🌐", "СНГ трекер", "Mixed", AuthType.LOGIN_PASS)
        UNIONPEER  -> SourceMeta("UnionPeer",      SourceCategory.CIS,   "🤝", "Без регистрации", "Mixed", AuthType.NONE)
        TPB        -> SourceMeta("Pirate Bay",     SourceCategory.WORLD, "🏴", "JSON API без регистрации", "Mixed", AuthType.NONE, defaultEnabled = true)
        NYAA       -> SourceMeta("Nyaa.si",        SourceCategory.WORLD, "🌸", "RSS, аниме/джаз", "FLAC / MP3", AuthType.NONE, defaultEnabled = true)
        X1337      -> SourceMeta("1337x",          SourceCategory.WORLD, "🔢", "Большой каталог", "Mixed", AuthType.NONE)
        OPENRU     -> SourceMeta("LimeTorrents+",  SourceCategory.WORLD, "🧲", "LimeTorrents + MagnetDL", "Mixed", AuthType.NONE)
        VK         -> SourceMeta("VK Music",       SourceCategory.MUSIC, "💙", "Прямые MP3", "MP3 320", AuthType.TOKEN)
        YOUTUBE    -> SourceMeta("YouTube",        SourceCategory.MUSIC, "▶️", "Поиск аудио", "Mixed", AuthType.OPTIONAL_TOKEN)
        SOUNDCLOUD -> SourceMeta("SoundCloud",     SourceCategory.MUSIC, "🟠", "Инди/электроника", "MP3 128", AuthType.OPTIONAL_TOKEN)
        DEEZER     -> SourceMeta("Deezer",         SourceCategory.MUSIC, "🎧", "30с без ARL, полные с ARL", "MP3 320", AuthType.OPTIONAL_TOKEN)
        YANDEX     -> SourceMeta("Яндекс.Музыка",  SourceCategory.MUSIC, "🟡", "OAuth токен из браузера", "MP3 320", AuthType.TOKEN)
        ZAYCEV     -> SourceMeta("Зайцев.нет",     SourceCategory.MUSIC, "🐰", "Без регистрации", "MP3 128", AuthType.NONE)
        ARCHIVE    -> SourceMeta("Archive.org",    SourceCategory.FREE,  "📚", "CC лицензии, REST API", "FLAC / MP3", AuthType.NONE, defaultEnabled = true)
        BANDCAMP   -> SourceMeta("Bandcamp",       SourceCategory.FREE,  "🎪", "Инди, часть треков бесплатно", "MP3 / FLAC", AuthType.NONE)
        JAMENDO    -> SourceMeta("Jamendo",        SourceCategory.FREE,  "🎶", "CC музыка, demo ключ встроен", "MP3 96", AuthType.OPTIONAL_TOKEN)
        FMA        -> SourceMeta("Free Music Archive", SourceCategory.FREE, "🆓", "Публичный API", "MP3 128", AuthType.OPTIONAL_TOKEN)
    }
}
