package dot.jcversa.novay.ui.state

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.LibraryEntry
import dot.jcversa.novay.data.SearchHistoryEntry
import dot.jcversa.novay.data.WatchHistoryEntry

val BROWSE_DISCOVERY_CHIPS = listOf(
    "Shonen",
    "Seinen",
    "Action",
    "Isekai",
    "Romance",
    "Fantastique",
)

/**
 * Contrôleur d'état @Stable inspiré de Jetsnack (`SearchState`) pour le
 * Catalogue NoVaY : sépare strictement la saisie/le focus des suggestions
 * instantanées, du live catalog et des résultats soumis.
 */
@Stable
class BrowseSearchController(
    private val discoveryChips: List<String> = BROWSE_DISCOVERY_CHIPS,
) {
    var queryField by mutableStateOf(TextFieldValue(""))
        private set

    var isFocused by mutableStateOf(false)
        private set

    var submittedState by mutableStateOf<BrowseSearchState>(BrowseSearchState.Idle)
        private set

    var featuredState by mutableStateOf<BrowseFeaturedState>(BrowseFeaturedState.Loading)
        internal set

    var recentSearches by mutableStateOf<List<SearchHistoryEntry>>(emptyList())
        internal set

    var libraryEntries by mutableStateOf<List<LibraryEntry>>(emptyList())
        internal set

    var watchHistory by mutableStateOf<List<WatchHistoryEntry>>(emptyList())
        internal set

    var liveCatalogResults by mutableStateOf<List<AnimeSearchResult>>(emptyList())
        private set

    var isLiveLoading by mutableStateOf(false)
        private set

    val trimmedQuery: String
        get() = queryField.text.trim()

    val searchDisplay: BrowseSearchDisplay by derivedStateOf {
        when (val submitted = submittedState) {
            BrowseSearchState.Loading -> BrowseSearchDisplay.LoadingResults
            is BrowseSearchState.Error -> BrowseSearchDisplay.Error
            is BrowseSearchState.Results -> {
                if (submitted.results.isEmpty()) {
                    BrowseSearchDisplay.EmptyResults
                } else {
                    BrowseSearchDisplay.Results
                }
            }
            BrowseSearchState.Idle -> when {
                queryField.text.isNotBlank() -> BrowseSearchDisplay.TypingSuggestions
                isFocused -> BrowseSearchDisplay.FocusedEmptySuggestions
                else -> BrowseSearchDisplay.SpotlightIdle
            }
        }
    }

    val canHandleBack: Boolean by derivedStateOf {
        isFocused || queryField.text.isNotEmpty() || submittedState !is BrowseSearchState.Idle
    }

    val suggestions: SmartSearchSuggestionsUi by derivedStateOf {
        val featuredItems = (featuredState as? BrowseFeaturedState.Loaded)?.items.orEmpty()
        SmartSuggestionEngine.buildSuggestions(
            query = queryField.text,
            recentSearches = recentSearches,
            libraryEntries = libraryEntries,
            watchHistory = watchHistory,
            featuredAnime = featuredItems,
            liveCatalog = liveCatalogResults,
            discoveryChips = discoveryChips,
            isLiveLoading = isLiveLoading,
        )
    }

    fun onQueryChange(newValue: TextFieldValue) {
        val textChanged = newValue.text != queryField.text
        queryField = newValue
        if (textChanged) {
            if (submittedState !is BrowseSearchState.Idle) {
                submittedState = BrowseSearchState.Idle
            }
            if (newValue.text.trim().length < 2) {
                liveCatalogResults = emptyList()
                isLiveLoading = false
            }
        }
    }

    fun onFocusChanged(focused: Boolean) {
        isFocused = focused
        if (focused && submittedState !is BrowseSearchState.Loading && submittedState !is BrowseSearchState.Idle) {
            submittedState = BrowseSearchState.Idle
        }
    }

    fun fillQuery(query: String) {
        val clean = query.trim()
        queryField = TextFieldValue(
            text = clean,
            selection = TextRange(clean.length),
        )
        submittedState = BrowseSearchState.Idle
        if (clean.length < 2) {
            liveCatalogResults = emptyList()
            isLiveLoading = false
        }
    }

    fun clearQueryOnly() {
        queryField = TextFieldValue("")
        submittedState = BrowseSearchState.Idle
        liveCatalogResults = emptyList()
        isLiveLoading = false
    }

    fun resetToSpotlight() {
        queryField = TextFieldValue("")
        submittedState = BrowseSearchState.Idle
        liveCatalogResults = emptyList()
        isLiveLoading = false
        isFocused = false
    }

    fun prepareSubmit(explicitQuery: String = queryField.text): String? {
        val q = explicitQuery.trim()
        if (q.length < 2) return null
        if (queryField.text != q) {
            queryField = TextFieldValue(text = q, selection = TextRange(q.length))
        }
        isFocused = false
        isLiveLoading = false
        submittedState = BrowseSearchState.Loading
        return q
    }

    fun finishSubmitSuccess(results: List<AnimeSearchResult>) {
        submittedState = BrowseSearchState.Results(results)
    }

    fun finishSubmitError(message: String) {
        submittedState = BrowseSearchState.Error(message)
    }

    fun setLiveLookupState(loading: Boolean, results: List<AnimeSearchResult>? = null) {
        isLiveLoading = loading
        if (results != null) {
            liveCatalogResults = results
        }
    }

    fun updateLocalSources(
        recent: List<SearchHistoryEntry>,
        library: List<LibraryEntry>,
        history: List<WatchHistoryEntry>,
    ) {
        recentSearches = recent
        libraryEntries = library
        watchHistory = history
    }

    fun restoreSavedQueryIfNeeded(savedQuery: String) {
        if (savedQuery.isNotEmpty() && queryField.text.isEmpty()) {
            queryField = TextFieldValue(text = savedQuery, selection = TextRange(savedQuery.length))
        }
    }
}
