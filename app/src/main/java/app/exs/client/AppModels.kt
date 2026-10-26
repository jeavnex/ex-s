package app.exs.client

enum class AppScreen { CATALOG, DETAIL, PLAYER, LOGIN }

enum class CatalogSection(val label: String, val path: String) {
    HOME("Главная", "/"),
    MOVIES("Фильмы", "/film/"),
    SERIES("Сериалы", "/serials/"),
    CARTOONS("Мультфильмы", "/multfilm/"),
    SHOWS("Передачи", "/tv-show/"),
    FAVORITES("Избранное", "/favorites/"),
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
)
