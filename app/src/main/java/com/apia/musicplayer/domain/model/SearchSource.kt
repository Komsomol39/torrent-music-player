package com.apia.musicplayer.domain.model

enum class SourceCategory(val label: String) {
    RUSSIAN("🇷🇺 Российские"),
    CIS("🌐 СНГ"),
    WORLD("🌍 Мировые"),
    MUSIC("🎵 Музыкальные сервисы"),
    LEGAL("✅ Легальные / бесплатные")
}

data class SourceInfo(
    val source: SearchSource,
    val category: SourceCategory,
    val description: String,
    val requiresLogin: Boolean = false,
    val requiresToken: Boolean = false,
    val quality: String = "",         // "FLAC", "MP3 320", "varies"
    val enabledByDefault: Boolean = false
)

enum class SearchSource(val displayName: String, val emoji: String) {
    // Российские торренты
    RUTRACKER("RuTracker",    "🔴"),
    RUTOR("RuTor",            "🟠"),
    KINOZAL("Kinozal",        "🟡"),

    // СНГ торренты
    NNMCLUB("NNM-Club",       "🟢"),
    UNIONPEER("UnionPeer",    "🔵"),

    // Мировые торренты
    TPB("Pirate Bay",         "⚫"),
    NYAA("Nyaa",              "🟣"),
    X1337("1337x",            "🟤"),

    // Музыкальные сервисы
    VK("VK Music",            "💙"),
    YOUTUBE("YouTube",        "❤️"),
    SOUNDCLOUD("SoundCloud",  "🟠"),
    DEEZER("Deezer",          "🟣"),
    YANDEX("Яндекс.Музыка",   "🟡"),
    ZAYCEV("Zaycev.net",      "🎵"),

    // Легальные / бесплатные
    ARCHIVE("Archive.org",   "📚"),
    BANDCAMP("Bandcamp",      "🎸"),
    JAMENDO("Jamendo",        "🎼"),
    FMA("Free Music Archive", "🆓");

    companion object {
        val ALL_SOURCES = listOf(
            SourceInfo(RUTRACKER, SourceCategory.RUSSIAN,
                "Крупнейший русскоязычный трекер. Огромная коллекция FLAC и MP3.",
                requiresLogin = true, quality = "FLAC / MP3", enabledByDefault = false),
            SourceInfo(RUTOR, SourceCategory.RUSSIAN,
                "Без регистрации. Magnet прямо в поиске.",
                quality = "MP3 / FLAC", enabledByDefault = true),
            SourceInfo(KINOZAL, SourceCategory.RUSSIAN,
                "Строгая модерация = высокое качество. Лучший FLAC на рунете.",
                requiresLogin = true, quality = "FLAC Hi-Res", enabledByDefault = false),

            SourceInfo(NNMCLUB, SourceCategory.CIS,
                "NNM-Club — крупный СНГ трекер с музыкой и видео.",
                requiresLogin = true, quality = "FLAC / MP3", enabledByDefault = false),
            SourceInfo(UNIONPEER, SourceCategory.CIS,
                "UnionPeer — СНГ трекер, без регистрации.",
                quality = "MP3", enabledByDefault = false),

            SourceInfo(TPB, SourceCategory.WORLD,
                "The Pirate Bay — открытый JSON API, без логина.",
                quality = "varies", enabledByDefault = true),
            SourceInfo(NYAA, SourceCategory.WORLD,
                "Nyaa.si — аниме и азиатская музыка. RSS API.",
                quality = "FLAC / MP3", enabledByDefault = true),
            SourceInfo(X1337, SourceCategory.WORLD,
                "1337x — большой каталог, magnet со страницы.",
                quality = "varies", enabledByDefault = false),

            SourceInfo(VK, SourceCategory.MUSIC,
                "VK Музыка — прямые MP3. Нужен Kate Mobile токен.",
                requiresToken = true, quality = "MP3 320", enabledByDefault = false),
            SourceInfo(YOUTUBE, SourceCategory.MUSIC,
                "YouTube — видео/аудио. Работает без ключа (scraping).",
                quality = "varies", enabledByDefault = false),
            SourceInfo(SOUNDCLOUD, SourceCategory.MUSIC,
                "SoundCloud — официальный API, инди и электроника.",
                requiresToken = true, quality = "MP3 128-256", enabledByDefault = false),
            SourceInfo(DEEZER, SourceCategory.MUSIC,
                "Deezer — высокое качество, неофициальный токен ARL.",
                requiresToken = true, quality = "MP3 320 / FLAC", enabledByDefault = false),
            SourceInfo(YANDEX, SourceCategory.MUSIC,
                "Яндекс.Музыка — лучший каталог для СНГ. Токен из браузера.",
                requiresToken = true, quality = "MP3 320 / FLAC", enabledByDefault = false),
            SourceInfo(ZAYCEV, SourceCategory.MUSIC,
                "Zaycev.net — старый русский сайт, без регистрации.",
                quality = "MP3 192", enabledByDefault = false),

            SourceInfo(ARCHIVE, SourceCategory.LEGAL,
                "Archive.org — миллионы легальных записей, без ключа.",
                quality = "varies (FLAC есть)", enabledByDefault = true),
            SourceInfo(BANDCAMP, SourceCategory.LEGAL,
                "Bandcamp — официальный API, инди артисты, часть бесплатно.",
                quality = "FLAC / MP3 320", enabledByDefault = false),
            SourceInfo(JAMENDO, SourceCategory.LEGAL,
                "Jamendo — 600K+ CC лицензированных треков, API бесплатный.",
                requiresToken = true, quality = "MP3 320", enabledByDefault = false),
            SourceInfo(FMA, SourceCategory.LEGAL,
                "Free Music Archive — тысячи свободных треков под CC.",
                quality = "MP3 / FLAC", enabledByDefault = false),
        )

        val DEFAULT_ENABLED = ALL_SOURCES.filter { it.enabledByDefault }.map { it.source }.toSet()
    }
}
