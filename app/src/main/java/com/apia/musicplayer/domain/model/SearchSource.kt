package com.apia.musicplayer.domain.model

enum class SourceCategory(val label: String) {
    RU("🇷🇺 Российские"),
    CIS("🌐 СНГ"),
    WORLD("🌍 Мировые"),
    MUSIC("🎵 Музыкальные"),
    FREE("✅ Легальные")
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
    TORAPI,
    RUTRACKER, RUTOR, KINOZAL,
    NNMCLUB, UNIONPEER,
    TPB, NYAA, X1337, OPENRU,
    VK, YOUTUBE, SOUNDCLOUD, DEEZER, YANDEX, ZAYCEV,
    ARCHIVE, BANDCAMP, JAMENDO, FMA;

    val meta: SourceMeta get() = when (this) {
        TORAPI     -> SourceMeta("RU Torrents",    SourceCategory.RU,    "🇷🇺",
            "RuTracker+RuTor+Kinozal+NNM — без логина через TorAPI",
            "FLAC / MP3 320", AuthType.NONE, defaultEnabled = true)
        RUTRACKER  -> SourceMeta("RuTracker",      SourceCategory.RU,    "🎸",
            "Прямой — нужен логин и VPN",
            "FLAC / MP3 320", AuthType.LOGIN_PASS)
        RUTOR      -> SourceMeta("RuTor",          SourceCategory.RU,    "🎵",
            "Без регистрации",
            "Mixed", AuthType.NONE, defaultEnabled = true)
        KINOZAL    -> SourceMeta("Kinozal",        SourceCategory.RU,    "💿",
            "Высокое качество FLAC, нужен логин",
            "FLAC", AuthType.LOGIN_PASS)
        NNMCLUB    -> SourceMeta("NNM-Club",       SourceCategory.CIS,   "🌐",
            "СНГ трекер, нужен логин",
            "Mixed", AuthType.LOGIN_PASS)
        UNIONPEER  -> SourceMeta("UnionPeer",      SourceCategory.CIS,   "🤝",
            "Без регистрации",
            "Mixed", AuthType.NONE)
        TPB        -> SourceMeta("Pirate Bay",     SourceCategory.WORLD, "🏴",
            "JSON API, без регистрации",
            "Mixed", AuthType.NONE, defaultEnabled = true)
        NYAA       -> SourceMeta("Nyaa.si",        SourceCategory.WORLD, "🌸",
            "RSS, аниме/джаз/классика",
            "FLAC / MP3", AuthType.NONE, defaultEnabled = true)
        X1337      -> SourceMeta("1337x",          SourceCategory.WORLD, "🔢",
            "Большой каталог, scraping",
            "Mixed", AuthType.NONE)
        OPENRU     -> SourceMeta("LimeTorrents",   SourceCategory.WORLD, "🧲",
            "LimeTorrents + MagnetDL",
            "Mixed", AuthType.NONE)
        VK         -> SourceMeta("VK Music",       SourceCategory.MUSIC, "💙",
            "Прямые MP3, нужен Kate Mobile токен",
            "MP3 320", AuthType.TOKEN)
        YOUTUBE    -> SourceMeta("YouTube",        SourceCategory.MUSIC, "▶️",
            "Поиск аудио, API ключ опционален",
            "Mixed", AuthType.OPTIONAL_TOKEN)
        SOUNDCLOUD -> SourceMeta("SoundCloud",     SourceCategory.MUSIC, "🟠",
            "Инди/электроника, работает без ключа",
            "MP3 128", AuthType.OPTIONAL_TOKEN)
        DEEZER     -> SourceMeta("Deezer",         SourceCategory.MUSIC, "🎧",
            "⚠ Только 30с превью без ARL cookie",
            "Preview 30s", AuthType.OPTIONAL_TOKEN)
        YANDEX     -> SourceMeta("Яндекс.Музыка",  SourceCategory.MUSIC, "🟡",
            "Нужен OAuth токен",
            "MP3 320", AuthType.TOKEN)
        ZAYCEV     -> SourceMeta("Зайцев.нет",     SourceCategory.MUSIC, "🐰",
            "Без регистрации",
            "MP3 128", AuthType.NONE)
        ARCHIVE    -> SourceMeta("Archive.org",    SourceCategory.FREE,  "📚",
            "⚠ Медленно, нестабильно",
            "FLAC / MP3", AuthType.NONE)
        BANDCAMP   -> SourceMeta("Bandcamp",       SourceCategory.FREE,  "🎪",
            "Инди артисты",
            "MP3 / FLAC", AuthType.NONE)
        JAMENDO    -> SourceMeta("Jamendo",        SourceCategory.FREE,  "🎶",
            "CC музыка, demo ключ встроен",
            "MP3 96", AuthType.OPTIONAL_TOKEN)
        FMA        -> SourceMeta("Free Music Archive", SourceCategory.FREE, "🆓",
            "Публичный API",
            "MP3 128", AuthType.OPTIONAL_TOKEN)
    }
}
