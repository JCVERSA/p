package dot.jcversa.novay.ui.state

import androidx.compose.runtime.Immutable
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.AnimeSeason
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
): List<LibraryItemUi> {
    val filtered = when (filter) {
        LibraryFilter.ALL -> allItems
        LibraryFilter.VF -> allItems.filter { h ->
            h.entry.languages.any { it.equals("VF", ignoreCase = true) }
        }
        LibraryFilter.VOSTFR -> allItems.filter { h ->
            h.entry.languages.any { it.equals("VOSTFR", ignoreCase = true) }
        }
    }
    return when (sort) {
        LibrarySort.RECENT -> filtered.sortedByDescending { it.entry.addedAt }
        LibrarySort.TITLE -> filtered.sortedBy { it.entry.title.lowercase() }
        LibrarySort.NEW_EPISODES -> filtered.sortedWith(
            compareByDescending<LibraryItemUi> { it.entry.newEpisodes }
                .thenByDescending { it.entry.addedAt }
        )
    }
}
