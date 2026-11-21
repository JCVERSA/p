package dot.jcversa.novay.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dot.jcversa.novay.R
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.AnimeSeason
import dot.jcversa.novay.data.AnimeSource
import dot.jcversa.novay.data.CatalogHome
import dot.jcversa.novay.data.GenreCatalog
import dot.jcversa.novay.data.Library
import dot.jcversa.novay.data.SearchHistory
import dot.jcversa.novay.data.WatchHistory
import dot.jcversa.novay.data.WatchHistoryEntry
import dot.jcversa.novay.ui.LocalBottomChromePadding
import dot.jcversa.novay.ui.components.AppBarTitle
import dot.jcversa.novay.ui.components.EmptyScreen
import dot.jcversa.novay.ui.components.ErrorView
import dot.jcversa.novay.ui.components.GenreFilterChipBar
import dot.jcversa.novay.ui.components.LocalSlateMotion
import dot.jcversa.novay.ui.components.NoResultsStateView
import dot.jcversa.novay.ui.components.NovayAppBar
import dot.jcversa.novay.ui.components.NovayBrandSeal
import dot.jcversa.novay.ui.components.NovayIcons
import dot.jcversa.novay.ui.components.OfflineStateView
import dot.jcversa.novay.ui.components.PosterGridSkeleton
import dot.jcversa.novay.ui.components.isDeviceOnline
import dot.jcversa.novay.ui.state.BrowseFeaturedState
import dot.jcversa.novay.ui.state.BrowseSearchController
import dot.jcversa.novay.ui.state.BrowseSearchDisplay
import dot.jcversa.novay.ui.state.BrowseSearchState
import dot.jcversa.novay.ui.state.CatalogFilterEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Onglet Catalogue Hybride — Découverte + Grille complète paginée + Filtres avancés :
 *  - barre supérieure connectée via nestedScroll ;
 *  - suggestions intelligentes multi-sources (recherches récentes, bibliothèque, historique,
 *    À la une et catalogue en direct avec debounce 280ms) ;
 *  - filtres avancés combinés dans une feuille modale + compteur + puces amovibles ;
 *  - sections de découverte horizontales au-dessus de la grille paginée complète ;
 *  - transitions AnimatedContent (< 220ms) et parallaxe GPU préservées.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    source: AnimeSource,
    onOpenShow: (AnimeSearchResult) -> Unit,
    onOpenEpisodes: (animeTitle: String, posterUrl: String?, season: AnimeSeason) -> Unit = { _, _, _ -> },
    onResume: (WatchHistoryEntry) -> Unit = {},
    onOpenDownloads: (() -> Unit)? = null,
    showBrandSeal: Boolean = false,
) {
    val motion = LocalSlateMotion.current
    val bottomChrome = LocalBottomChromePadding.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    var savedQuery by rememberSaveable { mutableStateOf("") }
    var savedFilterRaw by rememberSaveable { mutableStateOf("") }
    val controller = remember {
        BrowseSearchController().apply {
            restoreSavedStateIfNeeded(savedQuery, savedFilterRaw)
        }
    }
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(controller.filterState) {
        savedFilterRaw = controller.filterState.toSavedString()
    }

    LaunchedEffect(Unit) {
        val (recents, lib, hist) = withContext(Dispatchers.IO) {
            Triple(
                SearchHistory.recent(context),
                Library.all(context),
                WatchHistory.recent(context),
            )
        }
        controller.updateLocalSources(recents, lib, hist)
    }

    var home by remember { mutableStateOf<CatalogHome?>(null) }
    var randomSeed by remember { mutableIntStateOf(0) }
    var homeRetryKey by remember { mutableIntStateOf(0) }
    var catalogReloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(source, homeRetryKey) {
        controller.featuredState = BrowseFeaturedState.Loading
        val h = withContext(Dispatchers.IO) {
            runCatching { source.home() }.getOrNull()
        }
        if (h != null && (h.heroes.isNotEmpty() || h.cards.isNotEmpty())) {
            home = h
            val heroesAsResults = h.heroes.map { it.toSearchResult() }.distinctBy { it.url }
            controller.featuredState = if (heroesAsResults.isNotEmpty()) {
                BrowseFeaturedState.Loaded(heroesAsResults)
            } else {
                BrowseFeaturedState.Unavailable
            }
        } else {
            val loaded = withContext(Dispatchers.IO) {
                runCatching { source.featured() }.getOrDefault(emptyList())
            }
            controller.featuredState = if (loaded.isNotEmpty()) {
                BrowseFeaturedState.Loaded(loaded)
            } else {
                BrowseFeaturedState.Unavailable
            }
        }
    }

    val serverSignature = controller.catalogServerSignature
    LaunchedEffect(source, homeRetryKey, catalogReloadKey, serverSignature) {
        val activeFilter = controller.filterState
        controller.startCatalogPageLoad(page = 1)
        val result = withContext(Dispatchers.IO) {
            runCatching {
                source.catalogPage(
                    page = 1,
                    genres = activeFilter.genres,
                    language = activeFilter.language,
                    format = activeFilter.format,
                )
            }
        }
        result.fold(
            onSuccess = { pageData ->
                controller.finishCatalogPageSuccess(pageData, resetExisting = true)
            },
            onFailure = { err ->
                controller.finishCatalogPageError(
                    message = err.message ?: "Erreur inconnue",
                    isInitialPage = true,
                )
            },
        )
    }

    fun loadMoreCatalogPage() {
        val pageState = controller.catalogPageState
        if (!pageState.hasNextPage || pageState.isLoading || pageState.isLoadingMore) return
        val nextPage = (pageState.page + 1).coerceAtLeast(2)
        val activeFilter = controller.filterState
        controller.startCatalogPageLoad(page = nextPage)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    source.catalogPage(
                        page = nextPage,
                        genres = activeFilter.genres,
                        language = activeFilter.language,
                        format = activeFilter.format,
                    )
                }
            }
            result.fold(
                onSuccess = { pageData ->
                    controller.finishCatalogPageSuccess(pageData, resetExisting = false)
                },
                onFailure = { err ->
                    controller.finishCatalogPageError(
                        message = err.message ?: "Erreur inconnue",
                        isInitialPage = false,
                    )
                },
            )
        }
    }

    val displayMode = controller.searchDisplay
    val liveLookupQuery = controller.trimmedQuery
    LaunchedEffect(source, displayMode, liveLookupQuery) {
        if (displayMode != BrowseSearchDisplay.TypingSuggestions || liveLookupQuery.length < 2) {
            controller.setLiveLookupState(
                loading = false,
                results = if (liveLookupQuery.length < 2) emptyList() else null,
            )
            return@LaunchedEffect
        }
        controller.setLiveLookupState(loading = true)
        delay(280L)
        val live = withContext(Dispatchers.IO) {
            runCatching { source.search(liveLookupQuery) }.getOrDefault(emptyList())
        }
        controller.setLiveLookupState(loading = false, results = live)
    }

    val activeGenre = controller.selectedGenre
    val activeGenreState = controller.genreState
    LaunchedEffect(source, activeGenre, activeGenreState) {
        if (activeGenre != null && activeGenreState is BrowseSearchState.Loading) {
            val result = withContext(Dispatchers.IO) {
                runCatching { source.catalogByGenre(activeGenre) }
            }
            result.fold(
                onSuccess = { controller.finishGenreSuccess(it) },
                onFailure = { controller.finishGenreError(it.message ?: "Erreur inconnue") },
            )
        }
    }

    val featuredItems = remember(controller.featuredState) {
        (controller.featuredState as? BrowseFeaturedState.Loaded)?.items.orEmpty()
    }
    val allCatalogKnownItems = remember(featuredItems, controller.catalogPageState.items) {
        CatalogFilterEngine.mergeUniqueItems(controller.catalogPageState.items, featuredItems)
    }
    val libraryGenres = remember(controller.libraryEntries) {
        controller.libraryEntries.flatMap { it.genres }
    }
    val availableGenres = remember(home, allCatalogKnownItems, libraryGenres) {
        val heroGenres = home?.heroes.orEmpty().flatMap { it.genres }
        val knownGenres = allCatalogKnownItems.flatMap { it.genres }
        GenreCatalog.mergeGenresForBar(heroGenres + knownGenres + libraryGenres)
    }
    val availableFacets = remember(allCatalogKnownItems, availableGenres) {
        CatalogFilterEngine.extractAvailableFacets(
            items = allCatalogKnownItems,
            extraGenres = availableGenres,
        )
    }
    val libraryUrls = remember(controller.libraryEntries) {
        controller.libraryEntries.map { CatalogFilterEngine.normalizeUrlKey(it.url) }.toSet()
    }
    val recentOrderUrls = remember(home, controller.watchHistory) {
        buildMap {
            var idx = 0
            home?.cards.orEmpty().forEach { card ->
                val key = CatalogFilterEngine.normalizeUrlKey(card.animeUrl)
                if (!containsKey(key)) put(key, idx++)
            }
            controller.watchHistory.forEach { entry ->
                val key = CatalogFilterEngine.normalizeUrlKey(entry.animeUrl)
                if (!containsKey(key)) put(key, idx++)
            }
        }
    }

    BackHandler(enabled = controller.canHandleBack) {
        savedQuery = ""
        savedFilterRaw = ""
        focusManager.clearFocus()
        keyboard?.hide()
        controller.resetToSpotlight()
    }

    fun submitSearch(explicitQuery: String = controller.queryField.text) {
        val q = controller.prepareSubmit(explicitQuery) ?: return
        savedQuery = q
        focusManager.clearFocus()
        keyboard?.hide()
        scope.launch {
            val updatedRecents = withContext(Dispatchers.IO) {
                SearchHistory.record(context, q)
            }
            controller.recentSearches = updatedRecents
            val result = withContext(Dispatchers.IO) {
                runCatching { source.search(q) }
            }
            result.fold(
                onSuccess = { controller.finishSubmitSuccess(it) },
                onFailure = { controller.finishSubmitError(it.message ?: "Erreur inconnue") },
            )
        }
    }

    fun openFromSuggestion(anime: AnimeSearchResult) {
        val recordText = anime.title.ifBlank { controller.trimmedQuery }
        focusManager.clearFocus()
        keyboard?.hide()
        if (recordText.isNotBlank()) {
            scope.launch {
                val updatedRecents = withContext(Dispatchers.IO) {
                    SearchHistory.record(context, recordText)
                }
                controller.recentSearches = updatedRecents
            }
        }
        onOpenShow(anime)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
    ) {
        NovayAppBar(
            titleContent = {
                if (showBrandSeal) {
                    NovayBrandSeal(
                        size = 28.dp,
                        accessibleLabel = null,
                        modifier = Modifier.testTag("browse_brand_seal"),
                    )
                    Spacer(Modifier.width(10.dp))
                }
                AppBarTitle(title = stringResource(R.string.nav_browse))
            },
            scrollBehavior = scrollBehavior,
        )

        val isSearchFocused = controller.isFocused
        val searchHorizontalPad by animateDpAsState(
            targetValue = if (isSearchFocused) 12.dp else 16.dp,
            animationSpec = motion.dockDpSpec(),
            label = "browse_search_pad",
        )
        val searchIconScale by animateFloatAsState(
            targetValue = if (isSearchFocused) 1.1f else 1f,
            animationSpec = motion.spatialFloatSpec(),
            label = "browse_search_icon_scale",
        )
        val searchBorderColor by animateColorAsState(
            targetValue = if (isSearchFocused) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
            animationSpec = motion.colorSpec(180),
            label = "browse_search_border",
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = searchHorizontalPad, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = controller.queryField,
                    onValueChange = { next ->
                        controller.onQueryChange(next)
                        savedQuery = next.text
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            controller.onFocusChanged(focusState.isFocused)
                            if (focusState.isFocused) {
                                scope.launch {
                                    val (recents, lib, hist) = withContext(Dispatchers.IO) {
                                        Triple(
                                            SearchHistory.recent(context),
                                            Library.all(context),
                                            WatchHistory.recent(context),
                                        )
                                    }
                                    controller.updateLocalSources(recents, lib, hist)
                                }
                            }
                        }
                        .testTag("browse_search_input"),
                    placeholder = { Text(text = "Rechercher un anime (ex. Frieren, Naruto)…") },
                    leadingIcon = {
                        IconButton(
                            onClick = { submitSearch() },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Icon(
                                imageVector = NovayIcons.Search,
                                contentDescription = "Lancer la recherche",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = searchIconScale
                                    scaleY = searchIconScale
                                },
                            )
                        }
                    },
                    trailingIcon = {
                        if (controller.queryField.text.isNotEmpty() || controller.isFocused) {
                            IconButton(
                                onClick = {
                                    if (controller.queryField.text.isNotEmpty()) {
                                        savedQuery = ""
                                        controller.clearQueryOnly()
                                    } else {
                                        savedQuery = ""
                                        focusManager.clearFocus()
                                        keyboard?.hide()
                                        controller.resetToSpotlight()
                                    }
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("browse_clear_button"),
                            ) {
                                Icon(
                                    imageVector = NovayIcons.Close,
                                    contentDescription = if (controller.queryField.text.isNotEmpty()) {
                                        "Effacer la recherche"
                                    } else {
                                        "Fermer les suggestions"
                                    },
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = searchBorderColor,
                        unfocusedBorderColor = searchBorderColor,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )

                CatalogFilterButton(
                    activeCount = controller.activeFilterCount,
                    onClick = {
                        focusManager.clearFocus()
                        keyboard?.hide()
                        showFilterSheet = true
                    },
                )
            }
        }

        val showFilterBars = displayMode != BrowseSearchDisplay.FocusedEmptySuggestions &&
            displayMode != BrowseSearchDisplay.TypingSuggestions
        if (showFilterBars) {
            GenreFilterChipBar(
                genres = availableGenres,
                selectedGenre = controller.selectedGenre ?: controller.filterState.genres.singleOrNull(),
                onSelectGenre = { genre ->
                    focusManager.clearFocus()
                    keyboard?.hide()
                    controller.selectGenre(genre)
                },
                allLabel = stringResource(R.string.filter_all),
                testTagPrefix = "browse_genre_chip",
            )
            ActiveFilterChipsRow(
                chips = controller.removableChips,
                onRemoveChip = { chip -> controller.removeFilterChip(chip) },
                onResetAll = { controller.resetAllFilters() },
            )
        }

        AnimatedContent(
            targetState = displayMode,
            transitionSpec = { motion.stateCrossfade() },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            label = "browse_search_state",
        ) { display ->
            when (display) {
                BrowseSearchDisplay.SpotlightIdle -> HybridBrowseCatalog(
                    featured = controller.featuredState,
                    home = home,
                    catalogPage = controller.catalogPageState,
                    watchHistory = controller.watchHistory,
                    libraryUrls = libraryUrls,
                    randomSeed = randomSeed,
                    bottomChrome = bottomChrome,
                    onShuffle = { randomSeed++ },
                    onRetryHome = { homeRetryKey++ },
                    onRetryCatalog = { catalogReloadKey++ },
                    onLoadMoreCatalog = ::loadMoreCatalogPage,
                    onOpenShow = onOpenShow,
                    onOpenEpisodes = onOpenEpisodes,
                    onResumeHistory = onResume,
                )

                BrowseSearchDisplay.FilteredCatalog -> {
                    val pageState = controller.catalogPageState
                    val baseItems = remember(controller.filterState, pageState.items, allCatalogKnownItems) {
                        if (controller.filterState.hasServerFilter) {
                            pageState.items
                        } else {
                            allCatalogKnownItems
                        }
                    }
                    val filteredResults = remember(baseItems, controller.filterState, recentOrderUrls) {
                        CatalogFilterEngine.filterAndSort(
                            items = baseItems,
                            filter = controller.filterState,
                            recentOrderUrls = recentOrderUrls,
                        )
                    }
                    when {
                        pageState.isLoading && baseItems.isEmpty() -> PosterGridSkeleton(
                            minCellSize = 140.dp,
                            itemCount = 9,
                            modifier = Modifier.padding(bottom = bottomChrome),
                        )

                        pageState.error != null && baseItems.isEmpty() -> ErrorView(
                            message = pageState.error,
                            onRetry = { catalogReloadKey++ },
                            title = stringResource(R.string.error_catalog_title),
                            icon = NovayIcons.ErrorSeal,
                            modifier = Modifier.padding(bottom = bottomChrome),
                        )

                        filteredResults.isEmpty() -> EmptyScreen(
                            icon = NovayIcons.Search,
                            lottieRes = R.raw.lottie_explore,
                            headingText = stringResource(R.string.catalog_empty_filtered_title),
                            subtitleText = stringResource(R.string.catalog_empty_filtered_subtitle),
                            actionLabel = stringResource(R.string.action_reset_filters),
                            onAction = { controller.resetAllFilters() },
                            actionTestTag = "browse_filter_reset_button",
                            modifier = Modifier.padding(bottom = bottomChrome),
                        )

                        else -> CatalogResultsGrid(
                            results = filteredResults,
                            libraryUrls = libraryUrls,
                            sortOption = controller.filterState.sort,
                            bottomChrome = bottomChrome,
                            isLoadingMore = pageState.isLoadingMore,
                            hasNextPage = pageState.hasNextPage,
                            paginationError = pageState.paginationError,
                            onLoadMore = ::loadMoreCatalogPage,
                            onOpenShow = onOpenShow,
                        )
                    }
                }

                BrowseSearchDisplay.FocusedEmptySuggestions,
                BrowseSearchDisplay.TypingSuggestions -> BrowseSuggestionsContent(
                    isTyping = display == BrowseSearchDisplay.TypingSuggestions,
                    query = controller.queryField.text,
                    suggestions = controller.suggestions,
                    bottomChrome = bottomChrome,
                    onSelectQuery = { selectedQuery -> submitSearch(selectedQuery) },
                    onFillQuery = { filled ->
                        savedQuery = filled
                        controller.fillQuery(filled)
                        focusRequester.requestFocus()
                    },
                    onRemoveRecent = { recentQuery ->
                        scope.launch {
                            controller.recentSearches = withContext(Dispatchers.IO) {
                                SearchHistory.remove(context, recentQuery)
                            }
                        }
                    },
                    onClearHistory = {
                        scope.launch {
                            controller.recentSearches = withContext(Dispatchers.IO) {
                                SearchHistory.clear(context)
                            }
                        }
                    },
                    onOpenShow = ::openFromSuggestion,
                )

                BrowseSearchDisplay.LoadingResults,
                BrowseSearchDisplay.GenreLoading -> PosterGridSkeleton(
                    minCellSize = 140.dp,
                    itemCount = 9,
                    modifier = Modifier.padding(bottom = bottomChrome),
                )

                BrowseSearchDisplay.Error -> {
                    val errMessage = (controller.submittedState as? BrowseSearchState.Error)
                        ?.message ?: "Erreur inconnue"
                    if (!isDeviceOnline(context)) {
                        OfflineStateView(
                            onRetry = { submitSearch() },
                            onOpenDownloads = onOpenDownloads,
                            modifier = Modifier.padding(bottom = bottomChrome),
                        )
                    } else {
                        ErrorView(
                            message = errMessage,
                            onRetry = { submitSearch() },
                            title = stringResource(R.string.error_catalog_title),
                            icon = NovayIcons.ErrorSeal,
                            modifier = Modifier.padding(bottom = bottomChrome),
                        )
                    }
                }

                BrowseSearchDisplay.GenreError -> {
                    val errMessage = (controller.genreState as? BrowseSearchState.Error)
                        ?.message ?: "Erreur inconnue"
                    if (!isDeviceOnline(context)) {
                        OfflineStateView(
                            onRetry = { controller.retryGenre() },
                            onOpenDownloads = onOpenDownloads,
                            modifier = Modifier.padding(bottom = bottomChrome),
                        )
                    } else {
                        ErrorView(
                            message = errMessage,
                            onRetry = { controller.retryGenre() },
                            title = stringResource(R.string.error_catalog_title),
                            icon = NovayIcons.ErrorSeal,
                            modifier = Modifier.padding(bottom = bottomChrome),
                        )
                    }
                }

                BrowseSearchDisplay.EmptyResults -> NoResultsStateView(
                    title = stringResource(R.string.state_no_results_title),
                    subtitle = stringResource(R.string.state_no_results_subtitle, controller.trimmedQuery),
                    suggestions = listOf("Frieren", "Solo Leveling", "One Piece", "Jujutsu Kaisen"),
                    onSelectSuggestion = { suggestion ->
                        savedQuery = suggestion
                        controller.fillQuery(suggestion)
                        submitSearch(suggestion)
                    },
                    resetLabel = if (controller.filterState.hasAnyActiveFilter) {
                        stringResource(R.string.action_reset_filters)
                    } else {
                        "Effacer la recherche"
                    },
                    onReset = {
                        if (controller.filterState.hasAnyActiveFilter) {
                            controller.resetAllFilters()
                        } else {
                            savedQuery = ""
                            controller.clearQueryOnly()
                        }
                    },
                    modifier = Modifier.padding(bottom = bottomChrome),
                )

                BrowseSearchDisplay.GenreEmpty -> EmptyScreen(
                    icon = NovayIcons.Search,
                    lottieRes = R.raw.lottie_explore,
                    headingText = "Aucun anime en ${controller.selectedGenre ?: "ce genre"}",
                    subtitleText = "Aucun anime trouvé pour ce genre. Réinitialise le filtre ou essaie une autre catégorie.",
                    actionLabel = stringResource(R.string.action_show_all),
                    onAction = { controller.clearGenre() },
                    actionTestTag = "browse_genre_reset_button",
                    modifier = Modifier.padding(bottom = bottomChrome),
                )

                BrowseSearchDisplay.Results,
                BrowseSearchDisplay.GenreResults -> {
                    val rawResults = if (display == BrowseSearchDisplay.GenreResults) {
                        val currentGenre = controller.selectedGenre
                        val submitted = controller.submittedState as? BrowseSearchState.Results
                        if (submitted != null && currentGenre != null && submitted.results.any { it.genres.isNotEmpty() }) {
                            submitted.results.filter { GenreCatalog.matches(it.genres, currentGenre) }
                        } else {
                            (controller.genreState as? BrowseSearchState.Results)?.results.orEmpty()
                        }
                    } else {
                        (controller.submittedState as? BrowseSearchState.Results)?.results.orEmpty()
                    }
                    val filteredResults = remember(rawResults, controller.filterState, recentOrderUrls, display) {
                        if (display == BrowseSearchDisplay.GenreResults) {
                            CatalogFilterEngine.filterAndSort(
                                items = rawResults,
                                filter = controller.filterState.copy(genres = emptySet()),
                                recentOrderUrls = recentOrderUrls,
                            )
                        } else {
                            CatalogFilterEngine.filterAndSort(
                                items = rawResults,
                                filter = controller.filterState,
                                recentOrderUrls = recentOrderUrls,
                            )
                        }
                    }
                    if (filteredResults.isEmpty() && controller.filterState.hasAnyActiveFilter) {
                        EmptyScreen(
                            icon = NovayIcons.Search,
                            lottieRes = R.raw.lottie_explore,
                            headingText = stringResource(R.string.catalog_empty_filtered_title),
                            subtitleText = stringResource(R.string.catalog_empty_filtered_subtitle),
                            actionLabel = stringResource(R.string.action_reset_filters),
                            onAction = { controller.resetAllFilters() },
                            actionTestTag = "browse_filter_reset_button",
                            modifier = Modifier.padding(bottom = bottomChrome),
                        )
                    } else {
                        CatalogResultsGrid(
                            results = filteredResults,
                            libraryUrls = libraryUrls,
                            sortOption = controller.filterState.sort,
                            bottomChrome = bottomChrome,
                            onOpenShow = onOpenShow,
                        )
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        val previewPool = remember(controller.submittedState, allCatalogKnownItems) {
            val submitted = (controller.submittedState as? BrowseSearchState.Results)?.results
            if (!submitted.isNullOrEmpty()) submitted else allCatalogKnownItems
        }
        CatalogFilterBottomSheet(
            initialFilter = controller.filterState,
            facets = availableFacets,
            previewItems = previewPool,
            recentOrderUrls = recentOrderUrls,
            onDismiss = { showFilterSheet = false },
            onApply = { applied ->
                controller.updateFilter(applied)
            },
        )
    }
}
