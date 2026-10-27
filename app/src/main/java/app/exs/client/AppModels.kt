package app.exs.client

enum class AppScreen { CATALOG, DETAIL, PLAYER, LOGIN }

enum class UpdateStatus { CHECKING, CURRENT, OUTDATED, UNAVAILABLE }

enum class CatalogSection(val label: String, val path: String, val primary: Boolean = false) {
    HOME("Главная", "/", true),
    MOVIES("Фильмы", "/film/", true),
    SERIES("Сериалы", "/serials/", true),
    CARTOONS("Мультфильмы", "/multfilm/", true),
    SHOWS("Передачи и шоу", "/tv-show/", true),
    FAVORITES("Избранное", "/favorites/", true),

    YEAR_2026("2026 год", "/year/2026/"),
    YEAR_2025("2025 год", "/year/2025/"),
    YEAR_2024("2024 год", "/year/2024/"),
    YEAR_2023("2023 год", "/year/2023/"),
    COUNTRY_USA("США", "/country/США/"),
    COUNTRY_UK("Великобритания", "/country/Великобритания/"),
    COUNTRY_RUSSIA("Россия", "/country/Россия/"),
    COUNTRY_UKRAINE("Украина", "/country/Украина/"),
    COUNTRY_TURKEY("Турция", "/country/Турция/"),
    COUNTRY_KOREA("Корея", "/country/Корея_Южная/"),
    COUNTRY_INDIA("Индия", "/country/Индия/"),
    COUNTRY_CANADA("Канада", "/country/Канада/"),
    COUNTRY_FRANCE("Франция", "/country/Франция/"),
    COUNTRY_GERMANY("Германия", "/country/Германия/"),
    COUNTRY_JAPAN("Япония", "/country/Япония/"),
    COUNTRY_USSR("СССР", "/country/СССР/"),
    UKRAINIAN("На украинском", "/genre/на_украинском/"),
    SCI_FI("Фантастика", "/genre/фантастика/"),
    ANIME("Аниме", "/genre/аниме/"),
    BIOGRAPHY("Биография", "/genre/биография/"),
    ACTION("Боевик", "/genre/боевик/"),
    WESTERN("Вестерн", "/genre/вестерн/"),
    WAR("Военный", "/genre/военный/"),
    DETECTIVE("Детектив", "/genre/детектив/"),
    CHILDREN("Детский", "/genre/детский/"),
    ADULT("Для взрослых", "/genre/для_взрослых/"),
    DOCUMENTARY("Документальный", "/genre/документальный/"),
    DRAMA("Драма", "/genre/драма/"),
    HISTORY("История", "/genre/история/"),
    COMEDY("Комедия", "/genre/комедия/"),
    SHORTS("Короткометражка", "/genre/короткометражка/"),
    CRIME("Криминал", "/genre/криминал/"),
    ROMANCE("Мелодрама", "/genre/мелодрама/"),
    MUSIC("Музыка", "/genre/музыка/"),
    MUSICAL("Мюзикл", "/genre/мюзикл/"),
    NEWS("Новости", "/genre/новости/"),
    ADVENTURE("Приключения", "/genre/приключения/"),
    REALITY("Реальное ТВ", "/genre/реальное_ТВ/"),
    FAMILY("Семейный", "/genre/семейный/"),
    SPORT("Спорт", "/genre/спорт/"),
    TALK_SHOW("Ток-шоу", "/genre/ток-шоу/"),
    THRILLER("Триллер", "/genre/триллер/"),
    HORROR("Ужасы", "/genre/ужасы/"),
    NOIR("Фильм-Нуар", "/genre/фильм-нуар/"),
    GAME("Игра", "/genre/игра/"),
    FANTASY("Фэнтези", "/genre/фэнтези/"),
}

data class MediaCard(
    val url: String,
    val title: String,
    val poster: String = "",
    val meta: String = "",
    val badge: String = "",
)

data class PlayerChoice(val index: Int, val label: String)

data class MediaDetail(
    val card: MediaCard,
    val originalTitle: String = "",
    val description: String = "",
    val facts: String = "",
    val rating: String = "",
    val players: List<PlayerChoice> = emptyList(),
)

data class ExsState(
    val screen: AppScreen = AppScreen.CATALOG,
    val section: CatalogSection = CatalogSection.HOME,
    val cards: List<MediaCard> = emptyList(),
    val detail: MediaDetail? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val query: String = "",
    val loggedIn: Boolean = false,
    val accountName: String = "",
    val playerReady: Boolean = false,
    val updateStatus: UpdateStatus = UpdateStatus.CHECKING,
    val latestVersion: String = "",
)
