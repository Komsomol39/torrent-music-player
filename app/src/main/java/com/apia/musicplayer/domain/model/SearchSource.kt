package com.apia.musicplayer.domain.model

enum class SourceCategory(val label: String) {
    RU("🇷🇺 Российские"),
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
    // Российские — без авторизации
    TORAPI,    // TorAPI агрегатор: RuTracker+RuTor+Kinozal+NNM через публичный прокси
    RUTOR,     // Прямой RuTor без логина
    // С логином
    RUTRACKER,
    KINOZAL,
    NNMCLUB,
    // Мировые — без авторизации
    TPB,
    NYAA,
    X1337,
    OPENRU,
    // Музыкальные
    VK,
    YOUTUBE,
    SOUNDCLOUD,
    YANDEX,
    ZAYCEV,
    // С ограничениями
    DEEZER,    // 30с превью без ARL — выключен по умолчанию
    // Легальные
    JAMENDO,
    FMA,
    BANDCAMP,
    ARCHIVE;   // Нет прямого скачивания — выключен по умолчанию

    val meta: SourceMeta get() = when (this) {
        TORAPI     -> SourceMeta("RU Torrents",  SourceCategory.RU,    "🇷🇺", "RuTracker+RuTor+Kinozal+NNM без логина через TorAPI", "FLAC / MP3 320", AuthType.NONE, defaultEnabled = true)
        RUTOR      -> SourceMeta("RuTor",        SourceCategory.RU,    "🎵", "Прямой доступ, без регистрации", "Mixed", AuthType.NONE, defaultEnabled = true)
        RUTRACKER  -> SourceMeta("RuTracker",    SourceCategory.RU,    "🎸", "Прямой, требует логин+VPN", "FLAC / MP3 320", AuthType.LOGIN_PASS)
        KINOZAL    -> SourceMeta("Kinozal",      SourceCategory.RU,    "💿", "Высокое качество, требует логин", "FLAC", AuthType.LOGIN_PASS)
        NNMCLUB    -> SourceMeta("NNM-Club",     SourceCategory.RU,    "🌐", "Требует логин", "Mixed", AuthType.LOGIN_PASS)
        TPB        -> SourceMeta("Pirate Bay",   SourceCategory.WORLD, "🏴", "JSON API, без регистрации", "Mixed", AuthType.NONE, defaultEnabled = true)
        NYAA       -> SourceMeta("Nyaa.si",      SourceCategory.WORLD, "🌸", "RSS, аниме/джаз OST", "FLAC / MP3", AuthType.NONE, defaultEnabled = true)
        X1337      -> SourceMeta("1337x",        SourceCategory.WORLD, "🔢", "Большой каталог", "Mixed", AuthType.NONE, defaultEnabled = true)
        OPENRU     -> SourceMeta("LimeTorrents", SourceCategory.WORLD, "🧲", "LimeTorrents + MagnetDL", "Mixed", AuthType.NONE)
        VK         -> SourceMeta("VK Music",     SourceCategory.MUSIC, "💙", "Прямые MP3, Kate Mobile токен", "MP3 320", AuthType.TOKEN)
        YOUTUBE    -> SourceMeta("YouTube",      SourceCategory.MUSIC, "▶️", "Поиск аудио/клипов", "Mixed", AuthType.OPTIONAL_TOKEN)
        SOUNDCLOUD -> SourceMeta("SoundCloud",   SourceCategory.MUSIC, "🟠", "Инди/электроника, авто clientId", "MP3 128", AuthType.OPTIONAL_TOKEN)
        YANDEX     -> SourceMeta("Яндекс.Музыка",SourceCategory.MUSIC, "🟡", "OAuth токен из браузера", "MP3 320", AuthType.TOKEN)
        ZAYCEV     -> SourceMeta("Зайцев.нет",   SourceCategory.MUSIC, "🐰", "Без регистрации", "MP3 128", AuthType.NONE)
        DEEZER     -> SourceMeta("Deezer",       SourceCategory.MUSIC, "🎧", "⚠ Только 30с превью без ARL cookie", "30s preview", AuthType.OPTIONAL_TOKEN)
        JAMENDO    -> SourceMeta("Jamendo",      SourceCategory.FREE,  "🎶", "CC музыка, встроенный ключ", "MP3 96", AuthType.OPTIONAL_TOKEN, defaultEnabled = true)
        FMA        -> SourceMeta("Free Music Archive", SourceCategory.FREE, "🆓", "Публичный API, полные треки", "MP3 128", AuthType.NONE, defaultEnabled = true)
        BANDCAMP   -> SourceMeta("Bandcamp",     SourceCategory.FREE,  "🎪", "Инди артисты", "MP3 / FLAC", AuthType.NONE)
        ARCHIVE    -> SourceMeta("Archive.org",  SourceCategory.FREE,  "📚", "⚠ Только стриминг, нет скачивания", "FLAC / MP3", AuthType.NONE)
    }
}
