package dot.jcversa.novay.ui.state

import androidx.compose.runtime.Immutable
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.AnimeSeason
import dot.jcversa.novay.data.LibraryCollection
import dot.jcversa.novay.data.LibraryEntry
import dot.jcversa.novay.data.WatchHistoryEntry
import dot.jcversa.novay.downloads.Downloads
import dot.jcversa.novay.ui.components.MorphTabKind

/**
 * Contrats d'état UDF inspirés de Now in Android :
 * modèles @Immutable calculés en dehors du cycle de composition afin
 * d'éviter les filtrages/tris/regroupements à chaque recomposition.
 */

enum class TopLevelDestination(
    val morphKind: MorphTabKind,
    val contentDescription: String,
    val testTagKey: String,
) {
    LIBRARY(
        morphKind = MorphTabKind.LIBRARY,
        contentDescription = "Bibliothèque",
        testTagKey = "tab_library",
    ),
    UPDATES(
        morphKind = MorphTabKind.UPDATES,
        contentDescription = "Mises à jour",
        testTagKey = "tab_updates",
    ),
    HISTORY(
        morphKind = MorphTabKind.HISTORY,
        contentDescription = "Historique",
        testTagKey = "tab_history",
    ),
    BROWSE(
        morphKind = MorphTabKind.BROWSE,
        contentDescription = "Catalogue",
        testTagKey = "tab_browse",
    ),
    MORE(
        morphKind = MorphTabKind.MORE,
        contentDescription = "Plus",
        testTagKey = "tab_more",
    ),
}

enum class LibraryDisplay(val label: String) {
    COMFORTABLE_GRID("Grille confortable"),
    COMPACT_GRID("Grille compacte"),
    LIST("Liste"),
}

enum class LibraryFilter(val label: String) {
    ALL("Tout"),
    VF("VF"),
    VOSTFR("VOSTFR"),
}

enum class LibrarySort(val label: String) {
    RECENT("Ajoutés récemment"),
    TITLE("Ordre alphabétique"),
    NEW_EPISODES("Nouveaux épisodes"),
}

@Immutable
data class LibraryItemUi(
    val entry: LibraryEntry,
    val downloadedCount: Int,
    val resume: WatchHistoryEntry?,
)

@Immutable
data class LibraryUiState(
    val isLoading: Boolean = true,
    val items: List<LibraryItemUi> = emptyList(),
    val latestResume: WatchHistoryEntry? = null,
    val hasVf: Boolean = false,
    val hasVostfr: Boolean = false,
    val availableGenres: List<String> = emptyList(),
    val collections: List<LibraryCollection> = emptyList(),
)

@Immutable
sealed interface BrowseFeaturedState {
    data object Loading : BrowseFeaturedState
    data class Loaded(val items: List<AnimeSearchResult>) : BrowseFeaturedState
    data object Unavailable : BrowseFeaturedState
}

@Immutable
sealed interface BrowseSearchState {
    data object Idle : BrowseSearchState
    data object Loading : BrowseSearchState
    data class Error(val message: String) : BrowseSearchState
    data class Results(val results: List<AnimeSearchResult>) : BrowseSearchState
}

enum class BrowseSearchDisplay {
    SpotlightIdle,
    FilteredCatalog,
    FocusedEmptySuggestions,
    TypingSuggestions,
    LoadingResults,
    Results,
    EmptyResults,
    Error,
    GenreLoading,
    GenreResults,
    GenreEmpty,
    GenreError,
}

enum class SuggestionQuerySource {
    RECENT,
    POPULAR,
}

enum class AnimeSuggestionOrigin(val label: String) {
    LIBRARY("Bibliothèque"),
    HISTORY("Historique"),
    FEATURED("À la une"),
    LIVE_CATALOG("Catalogue"),
}

@Immutable
data class HighlightSpan(
    val start: Int,
    val endExclusive: Int,
)

@Immutable
data class QuerySuggestionUi(
    val query: String,
    val source: SuggestionQuerySource,
    val spans: List<HighlightSpan> = emptyList(),
)

@Immutable
data class AnimeSuggestionUi(
    val anime: AnimeSearchResult,
    val origin: AnimeSuggestionOrigin,
    val spans: List<HighlightSpan> = emptyList(),
)

@Immutable
data class SmartSearchSuggestionsUi(
    val querySuggestions: List<QuerySuggestionUi> = emptyList(),
    val localAnime: List<AnimeSuggestionUi> = emptyList(),
    val liveAnime: List<AnimeSuggestionUi> = emptyList(),
    val popularTags: List<String> = emptyList(),
    val isLiveLoading: Boolean = false,
)

@Immutable
data class HistoryDayGroup(
    val dayLabel: String,
    val entries: List<WatchHistoryEntry>,
)

@Immutable
data class DownloadSection(
    val animeTitle: String,
    val items: List<Downloads.DownloadInfo>,
    val completedCount: Int,
)

@Immutable
data class ShowLanguageGroup(
    val language: String,
    val seasons: List<AnimeSeason>,
)

fun computeLibraryItems(
    allItems: List<LibraryItemUi>,
    filter: LibraryFilter,
    sort: LibrarySort,
    selectedGenre: String? = null,
    selectedCollection: LibraryCollection? = null,
): List<LibraryItemUi> {
    val collectionFiltered = if (selectedCollection == null) {
        allItems
    } else {
        val urls = selectedCollection.animeUrls.map { it.trimEnd('/') }.toSet()
        allItems.filter { it.entry.url.trimEnd('/') in urls }
    }
    val langFiltered = when (filter) {
        LibraryFilter.ALL -> collectionFiltered
        LibraryFilter.VF -> collectionFiltered.filter { h ->
            h.entry.languages.any { it.equals("VF", ignoreCase = true) }
        }
        LibraryFilter.VOSTFR -> collectionFiltered.filter { h ->
            h.entry.languages.any { it.equals("VOSTFR", ignoreCase = true) }
        }
    }
    val genreFiltered = if (selectedGenre.isNullOrBlank()) {
        langFiltered
    } else {
        langFiltered.filter { h ->
            dot.jcversa.novay.data.GenreCatalog.matches(h.entry.genres, selectedGenre)
        }
    }
    return when (sort) {
        LibrarySort.RECENT -> genreFiltered.sortedByDescending { it.entry.addedAt }
        LibrarySort.TITLE -> genreFiltered.sortedBy { it.entry.title.lowercase() }
        LibrarySort.NEW_EPISODES -> genreFiltered.sortedWith(
            compareByDescending<LibraryItemUi> { it.entry.newEpisodes }
                .thenByDescending { it.entry.addedAt }
        )
    }
}

enum class BrowseMood(
    val id: String,
    val label: String,
    val matchingGenres: Set<String>,
) {
    ALL(
        id = "all",
        label = "Tout",
        matchingGenres = emptySet(),
    ),
    ACTION(
        id = "action",
        label = "Action intense",
        matchingGenres = setOf("Action", "Combat", "Arts Martiaux", "Shonen", "Sport"),
    ),
    MYSTERY(
        id = "mystery",
        label = "Frissons & Mystère",
        matchingGenres = setOf("Mystère", "Horreur", "Psychologique", "Thriller", "Seinen", "Surnaturel"),
    ),
    ROMANCE(
        id = "romance",
        label = "Romance & Émotion",
        matchingGenres = setOf("Romance", "Drame", "Tranche de vie", "Shojo", "Musique"),
    ),
    ISEKAI(
        id = "isekai",
        label = "Isekai & Évasion",
        matchingGenres = setOf("Isekai", "Fantastique", "Fantasy", "Aventure", "Magie"),
    ),
    SCIFI(
        id = "scifi",
        label = "Sci-Fi & Futur",
        matchingGenres = setOf("Sci-Fi", "Science-Fiction", "Mecha", "Cyberpunk", "Espace"),
    ),
}

fun filterItemsByMood(
    items: List<AnimeSearchResult>,
    mood: BrowseMood,
): List<AnimeSearchResult> {
    if (mood == BrowseMood.ALL || items.isEmpty()) return items
    return items.filter { anime ->
        anime.genres.any { genre ->
            mood.matchingGenres.any { target ->
                dot.jcversa.novay.data.GenreCatalog.matches(listOf(genre), target)
            }
        }
    }
}

