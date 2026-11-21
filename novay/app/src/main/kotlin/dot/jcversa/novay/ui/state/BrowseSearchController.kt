package dot.jcversa.novay.ui.state

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.CatalogPage
import dot.jcversa.novay.data.GenreCatalog
import dot.jcversa.novay.data.LibraryEntry
import dot.jcversa.novay.data.SearchHistoryEntry
import dot.jcversa.novay.data.WatchHistoryEntry

val BROWSE_DISCOVERY_CHIPS = GenreCatalog.CORE_GENRES

/**
 * Contrôleur d'état @Stable inspiré de Jetsnack (`SearchState`) pour le
 * Catalogue NoVaY : sépare strictement la saisie/le focus des suggestions
 * instantanées, du live catalog, du filtrage avancé multi-critères, de la
 * pagination et des résultats soumis.
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

    var selectedGenre by mutableStateOf<String?>(null)
        private set

    var genreState by mutableStateOf<BrowseSearchState>(BrowseSearchState.Idle)
        private set

    var filterState by mutableStateOf(CatalogFilterState())
        private set

    var catalogPageState by mutableStateOf(CatalogPageUiState())
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

    val catalogServerSignature: String by derivedStateOf {
        filterState.serverQuerySignature()
    }

    val removableChips: List<CatalogFilterChipItem> by derivedStateOf {
        CatalogFilterEngine.toRemovableChips(filterState)
    }

    val activeFilterCount: Int by derivedStateOf {
        filterState.activeCount
    }

    val searchDisplay: BrowseSearchDisplay by derivedStateOf {
        val activeGenre = selectedGenre
        val useSingleGenreFlow = activeGenre != null && !filterState.hasAdvancedCriteriaBeyondSingleGenre
        when (val submitted = submittedState) {
            BrowseSearchState.Loading -> BrowseSearchDisplay.LoadingResults
            is BrowseSearchState.Error -> BrowseSearchDisplay.Error
            is BrowseSearchState.Results -> {
                if (useSingleGenreFlow) {
                    val genre = activeGenre
                    val hasSearchGenres = submitted.results.any { it.genres.isNotEmpty() }
                    if (hasSearchGenres) {
                        val filtered = submitted.results.filter { GenreCatalog.matches(it.genres, genre) }
                        if (filtered.isEmpty()) BrowseSearchDisplay.GenreEmpty else BrowseSearchDisplay.GenreResults
                    } else {
                        when (val g = genreState) {
                            BrowseSearchState.Loading, BrowseSearchState.Idle -> BrowseSearchDisplay.GenreLoading
                            is BrowseSearchState.Error -> BrowseSearchDisplay.GenreError
                            is BrowseSearchState.Results ->
                                if (g.results.isEmpty()) BrowseSearchDisplay.GenreEmpty else BrowseSearchDisplay.GenreResults
                        }
                    }
                } else if (submitted.results.isEmpty()) {
                    BrowseSearchDisplay.EmptyResults
                } else {
                    BrowseSearchDisplay.Results
                }
            }
            BrowseSearchState.Idle -> when {
                queryField.text.isNotBlank() -> BrowseSearchDisplay.TypingSuggestions
                isFocused -> BrowseSearchDisplay.FocusedEmptySuggestions
                useSingleGenreFlow -> when (val g = genreState) {
                    BrowseSearchState.Loading, BrowseSearchState.Idle -> BrowseSearchDisplay.GenreLoading
                    is BrowseSearchState.Error -> BrowseSearchDisplay.GenreError
                    is BrowseSearchState.Results ->
                        if (g.results.isEmpty()) BrowseSearchDisplay.GenreEmpty else BrowseSearchDisplay.GenreResults
                }
                filterState.hasAnyActiveFilter -> BrowseSearchDisplay.FilteredCatalog
                else -> BrowseSearchDisplay.SpotlightIdle
            }
        }
    }

    val canHandleBack: Boolean by derivedStateOf {
        isFocused ||
            queryField.text.isNotEmpty() ||
            submittedState !is BrowseSearchState.Idle ||
            selectedGenre != null ||
            filterState.hasAnyActiveFilter
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
        selectedGenre = null
        genreState = BrowseSearchState.Idle
        filterState = CatalogFilterState()
        liveCatalogResults = emptyList()
        isLiveLoading = false
        isFocused = false
    }

    fun selectGenre(genre: String?) {
        val normalized = genre?.let(GenreCatalog::normalize)?.takeIf { it.isNotEmpty() }
        val activeSingle = selectedGenre ?: filterState.genres.singleOrNull()
        if (normalized == null || activeSingle?.let(GenreCatalog::foldKey) == GenreCatalog.foldKey(normalized)) {
            clearGenre()
            return
        }
        isFocused = false
        selectedGenre = normalized
        filterState = filterState.copy(genres = setOf(normalized))
        if (!filterState.hasAdvancedCriteriaBeyondSingleGenre) {
            val submitted = submittedState
            val canFilterSearchInMemory = submitted is BrowseSearchState.Results &&
                submitted.results.any { it.genres.isNotEmpty() }
            genreState = if (canFilterSearchInMemory) {
                BrowseSearchState.Results(
                    submitted.results.filter { GenreCatalog.matches(it.genres, normalized) }
                )
            } else {
                BrowseSearchState.Loading
            }
        } else {
            genreState = BrowseSearchState.Idle
        }
    }

    fun clearGenre() {
        selectedGenre = null
        genreState = BrowseSearchState.Idle
        if (filterState.genres.isNotEmpty()) {
            filterState = filterState.copy(genres = emptySet())
        }
    }

    fun updateFilter(next: CatalogFilterState) {
        val cleanGenres = next.genres
            .map(GenreCatalog::normalize)
            .filter { it.isNotEmpty() }
            .toSet()
        val normalized = next.copy(
            genres = cleanGenres,
            language = next.language?.trim()?.uppercase()?.takeIf { it.isNotEmpty() },
            format = next.format?.trim()?.takeIf { it.isNotEmpty() },
            releaseYear = next.releaseYear?.trim()?.takeIf { it.isNotEmpty() },
            status = next.status?.trim()?.takeIf { it.isNotEmpty() },
        )
        isFocused = false
        filterState = normalized
        if (normalized.genres.size == 1 && !normalized.hasAdvancedCriteriaBeyondSingleGenre) {
            val single = normalized.genres.first()
            if (selectedGenre?.let(GenreCatalog::foldKey) != GenreCatalog.foldKey(single)) {
                selectedGenre = single
                genreState = BrowseSearchState.Loading
            }
        } else {
            selectedGenre = if (normalized.genres.size == 1 && submittedState is BrowseSearchState.Results) {
                normalized.genres.first()
            } else {
                null
            }
            genreState = BrowseSearchState.Idle
        }
    }

    fun removeFilterChip(chip: CatalogFilterChipItem) {
        updateFilter(CatalogFilterEngine.removeChip(filterState, chip))
    }

    fun resetAllFilters() {
        selectedGenre = null
        genreState = BrowseSearchState.Idle
        filterState = CatalogFilterState()
    }

    fun finishGenreSuccess(results: List<AnimeSearchResult>) {
        if (selectedGenre != null) {
            genreState = BrowseSearchState.Results(results)
        }
    }

    fun finishGenreError(message: String) {
        if (selectedGenre != null) {
            genreState = BrowseSearchState.Error(message)
        }
    }

    fun retryGenre() {
        if (selectedGenre != null) {
            genreState = BrowseSearchState.Loading
        }
    }

    fun startCatalogPageLoad(page: Int) {
        catalogPageState = if (page <= 1) {
            catalogPageState.copy(
                isLoading = true,
                isLoadingMore = false,
                error = null,
                paginationError = null,
            )
        } else {
            catalogPageState.copy(
                isLoadingMore = true,
                paginationError = null,
            )
        }
    }

    fun finishCatalogPageSuccess(
        catalogPage: CatalogPage,
        resetExisting: Boolean,
    ) {
        val mergedItems = if (resetExisting) {
            CatalogFilterEngine.mergeUniqueItems(emptyList(), catalogPage.items)
        } else {
            CatalogFilterEngine.mergeUniqueItems(catalogPageState.items, catalogPage.items)
        }
        catalogPageState = CatalogPageUiState(
            items = mergedItems,
            page = catalogPage.page,
            hasNextPage = catalogPage.hasNextPage,
            isLoading = false,
            isLoadingMore = false,
            error = null,
            paginationError = null,
        )
    }

    fun finishCatalogPageError(
        message: String,
        isInitialPage: Boolean,
    ) {
        catalogPageState = if (isInitialPage) {
            catalogPageState.copy(
                isLoading = false,
                isLoadingMore = false,
                error = message,
            )
        } else {
            catalogPageState.copy(
                isLoading = false,
                isLoadingMore = false,
                paginationError = message,
            )
        }
    }

    fun prepareSubmit(explicitQuery: String = queryField.text): String? {
        val q = explicitQuery.trim()
        if (q.length < 2) return null
        if (queryField.text != q) {
            queryField = TextFieldValue(text = q, selection = TextRange(q.length))
        }
        isFocused = false
        isLiveLoading = false
        selectedGenre = null
        genreState = BrowseSearchState.Idle
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

    fun restoreSavedStateIfNeeded(savedQuery: String, savedFilterRaw: String) {
        if (savedQuery.isNotEmpty() && queryField.text.isEmpty()) {
            queryField = TextFieldValue(text = savedQuery, selection = TextRange(savedQuery.length))
        }
        if (savedFilterRaw.isNotBlank() && !filterState.hasAnyActiveFilter) {
            val restored = CatalogFilterState.fromSavedString(savedFilterRaw)
            if (restored.hasAnyActiveFilter) {
                updateFilter(restored)
            }
        }
    }

    fun restoreSavedQueryIfNeeded(savedQuery: String) {
        restoreSavedStateIfNeeded(savedQuery, "")
    }
}
