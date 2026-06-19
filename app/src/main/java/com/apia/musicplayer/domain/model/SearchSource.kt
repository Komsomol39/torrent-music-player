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
    val quality: String,          // "MP3 128" / "MP3 320" / "FLAC" / "Mixed"
    val authType: AuthType,
    val defaultEnabled: Boolean = false
)

enum class AuthType {
    NONE,           // без регистрации
    LOGIN_PASS,     // логин + пароль
    TOKEN,          // один токен/ключ
    OPTIONAL_TOKEN  // без ключа работает, с ключом лучше
}

enum class SearchSource {
    // ── Российские ──────────────────────────────────────────
    RUTRACKER,   RUTOR,   KINOZAL,
    // ── СНГ ─────────────────────────────────────────────────
    NNMCLUB,     UNIONPEER,
    // ── Мировые ─────────────────────────────────────────────
    TPB,         NYAA,    X1337,
    // ── Музыкальные сервисы ──────────────────────────────────
    VK,          YOUTUBE, SOUNDCLOUD, DEEZER, YANDEX, ZAYCEV,
    // ── Легальные / Бесплатные ──────────────────────────────
    ARCHIVE,     BANDCAMP, JAMENDO,   FMA;

    val meta: SourceMeta get() = when (this) {
        RUTRACKER  -> SourceMeta("RuTracker",   SourceCategory.RU,    "🎸", "Крупнейший рус. трекер. Лучший FLAC каталог", "FLAC / MP3 320", AuthType.LOGIN_PASS)
        RUTOR      -> SourceMeta("RuTor",       SourceCategory.RU,    "🎵", "Без регистрации, большой каталог", "Mixed", AuthType.NONE, defaultEnabled = true)
        KINOZAL    -> SourceMeta("Kinozal",     SourceCategory.RU,    "💿", "Высокое качество, строгая модерация", "FLAC", AuthType.LOGIN_PASS)
        NNMCLUB    -> SourceMeta("NNM-Club",    SourceCategory.CIS,   "🌐", "СНГ трекер, активное сообщество", "Mixed", AuthType.LOGIN_PASS)
        UNIONPEER  -> SourceMeta("UnionPeer",   SourceCategory.CIS,   "🤝", "Без регистрации", "Mixed", AuthType.NONE)
        TPB        -> SourceMeta("Pirate Bay",  SourceCategory.WORLD, "🏴‍☠️", "JSON API, мгновенный magnet", "Mixed", AuthType.NONE, defaultEnabled = true)
        NYAA       -> SourceMeta("Nyaa.si",     SourceCategory.WORLD, "🌸", "RSS, лучший для аниме/джаз OST", "FLAC / MP3", AuthType.NONE, defaultEnabled = true)
        X1337      -> SourceMeta("1337x",       SourceCategory.WORLD, "🔢", "HTML scraping, большой каталог", "Mixed", AuthType.NONE)
        VK         -> SourceMeta("VK Music",    SourceCategory.MUSIC, "💙", "Прямые MP3, огромный рус. каталог", "MP3 320", AuthType.TOKEN)
        YOUTUBE    -> SourceMeta("YouTube",     SourceCategory.MUSIC, "▶️",  "Поиск клипов/аудио. Без ключа — scraping", "Mixed", AuthType.OPTIONAL_TOKEN)
        SOUNDCLOUD -> SourceMeta("SoundCloud",  SourceCategory.MUSIC, "🟠", "Официальный API, инди/электроника", "MP3 128", AuthType.OPTIONAL_TOKEN)
        DEEZER     -> SourceMeta("Deezer",      SourceCategory.MUSIC, "🎧", "Public API + ARL cookie для полных треков", "MP3 320", AuthType.OPTIONAL_TOKEN)
        YANDEX     -> SourceMeta("Яндекс.Музыка", SourceCategory.MUSIC, "🟡", "Неофициальный API, токен из браузера", "MP3 320", AuthType.TOKEN)
        ZAYCEV     -> SourceMeta("Зайцев.нет",  SourceCategory.MUSIC, "🐰", "Без регистрации, HTML scraping", "MP3 128", AuthType.NONE)
        ARCHIVE    -> SourceMeta("Archive.org", SourceCategory.FREE,  "📚", "Легальный, CC лицензии, REST API", "FLAC / MP3", AuthType.NONE, defaultEnabled = true)
        BANDCAMP   -> SourceMeta("Bandcamp",    SourceCategory.FREE,  "🎪", "Инди артисты, часть треков бесплатно", "MP3 / FLAC", AuthType.NONE)
        JAMENDO    -> SourceMeta("Jamendo",     SourceCategory.FREE,  "🎶", "CC музыка, встроенный demo ключ", "MP3 96", AuthType.OPTIONAL_TOKEN)
        FMA        -> SourceMeta("Free Music Archive", SourceCategory.FREE, "🆓", "Полностью легальный, публичный API", "MP3 128", AuthType.OPTIONAL_TOKEN)
    }
}
