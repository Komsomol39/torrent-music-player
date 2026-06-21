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
    // РФ — дефолтные без логина
    TORAPI, RUTOR,
    // РФ — требуют логин
    RUTRACKER, KINOZAL,
    // СНГ — требуют логин
    NNMCLUB,
    // Мировые — без логина
    TPB, NYAA, X1337, OPENRU,
    // Музыкальные — требуют токен
    VK, YOUTUBE, SOUNDCLOUD, YANDEX, ZAYCEV,
    // Отключены по умолчанию — плохой UX
    DEEZER,   // 30с превью без ARL
    ARCHIVE,  // медленно, нет прямого скачивания
    BANDCAMP, JAMENDO, FMA;

    val meta: SourceMeta get() = when (this) {
        TORAPI     -> SourceMeta("RU Torrents",   SourceCategory.RU,    "🇷🇺",
            "RuTracker+RuTor+Kinozal+NNM через TorAPI — без логина",
            "FLAC / MP3 320", AuthType.NONE, defaultEnabled = true)
        RUTOR      -> SourceMeta("RuTor",         SourceCategory.RU,    "🎵",
            "Без регистрации",
            "Mixed", AuthType.NONE, defaultEnabled = true)
        RUTRACKER  -> SourceMeta("RuTracker",     SourceCategory.RU,    "🎸",
            "Нужен логин и VPN",
            "FLAC / MP3 320", AuthType.LOGIN_PASS)
        KINOZAL    -> SourceMeta("Kinozal",       SourceCategory.RU,    "💿",
            "Высокое качество FLAC, нужен логин",
            "FLAC", AuthType.LOGIN_PASS)
        NNMCLUB    -> SourceMeta("NNM-Club",      SourceCategory.CIS,   "🌐",
            "СНГ трекер, нужен логин",
            "Mixed", AuthType.LOGIN_PASS)
        TPB        -> SourceMeta("Pirate Bay",    SourceCategory.WORLD, "🏴",
            "JSON API, без регистрации",
            "Mixed", AuthType.NONE, defaultEnabled = true)
        NYAA       -> SourceMeta("Nyaa.si",       SourceCategory.WORLD, "🌸",
            "RSS, аниме/джаз/классика",
            "FLAC / MP3", AuthType.NONE, defaultEnabled = true)
        X1337      -> SourceMeta("1337x",         SourceCategory.WORLD, "🔢",
            "Большой каталог",
            "Mixed", AuthType.NONE)
        OPENRU     -> SourceMeta("LimeTorrents",  SourceCategory.WORLD, "🧲",
            "LimeTorrents + MagnetDL",
            "Mixed", AuthType.NONE)
        VK         -> SourceMeta("VK Music",      SourceCategory.MUSIC, "💙",
            "Прямые MP3, нужен Kate Mobile токен",
            "MP3 320", AuthType.TOKEN)
        YOUTUBE    -> SourceMeta("YouTube",       SourceCategory.MUSIC, "▶️",
            "Поиск аудио",
            "Mixed", AuthType.OPTIONAL_TOKEN)
        SOUNDCLOUD -> SourceMeta("SoundCloud",    SourceCategory.MUSIC, "🟠",
            "Инди/электроника",
            "MP3 128", AuthType.OPTIONAL_TOKEN)
        YANDEX     -> SourceMeta("Яндекс.Музыка", SourceCategory.MUSIC, "🟡",
            "OAuth токен из браузера",
            "MP3 320", AuthType.TOKEN)
        ZAYCEV     -> SourceMeta("Зайцев.нет",    SourceCategory.MUSIC, "🐰",
            "Без регистрации",
            "MP3 128", AuthType.NONE)
        DEEZER     -> SourceMeta("Deezer",        SourceCategory.MUSIC, "🎧",
            "⚠ Только 30с превью без ARL cookie. Добавь ARL в настройках.",
            "Preview 30s", AuthType.OPTIONAL_TOKEN)
        ARCHIVE    -> SourceMeta("Archive.org",   SourceCategory.FREE,  "📚",
            "⚠ Медленно, нестабильно",
            "FLAC / MP3", AuthType.NONE)
        BANDCAMP   -> SourceMeta("Bandcamp",      SourceCategory.FREE,  "🎪",
            "Инди артисты",
            "MP3 / FLAC", AuthType.NONE)
        JAMENDO    -> SourceMeta("Jamendo",       SourceCategory.FREE,  "🎶",
            "CC музыка",
            "MP3 96", AuthType.OPTIONAL_TOKEN)
        FMA        -> SourceMeta("Free Music Archive", SourceCategory.FREE, "🆓",
            "Публичный API",
            "MP3 128", AuthType.OPTIONAL_TOKEN)
    }
}
