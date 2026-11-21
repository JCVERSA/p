package dot.jcversa.novay.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import android.os.Build
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dot.jcversa.novay.R
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.AnimeSeason
import dot.jcversa.novay.data.AnimeSource
import dot.jcversa.novay.data.CatalogCard
import dot.jcversa.novay.data.CatalogHome
import dot.jcversa.novay.data.GenreCatalog
import dot.jcversa.novay.data.sama.SamaParsers
import dot.jcversa.novay.data.Library
import dot.jcversa.novay.data.SearchHistory
import dot.jcversa.novay.data.WatchHistory
import dot.jcversa.novay.ui.LocalBottomChromePadding
import dot.jcversa.novay.ui.components.AnimeComfortableGridItem
import dot.jcversa.novay.ui.components.AppBarTitle
import dot.jcversa.novay.ui.components.EmptyScreen
import dot.jcversa.novay.ui.components.ErrorView
import dot.jcversa.novay.ui.components.GenreFilterChipBar
import dot.jcversa.novay.ui.components.LocalSlateMotion
import dot.jcversa.novay.ui.components.NovayAppBar
import dot.jcversa.novay.ui.components.NovayIcons
import dot.jcversa.novay.ui.components.PosterGridSkeleton
import dot.jcversa.novay.ui.components.PosterImage
import dot.jcversa.novay.ui.components.ShimmerBox
import dot.jcversa.novay.ui.components.novaySharedPoster
import dot.jcversa.novay.ui.state.BrowseFeaturedState
import dot.jcversa.novay.ui.state.BrowseSearchController
import dot.jcversa.novay.ui.state.BrowseSearchDisplay
import dot.jcversa.novay.ui.state.BrowseSearchState
import dot.jcversa.novay.ui.theme.NovayTheme
import dot.jcversa.novay.ui.theme.header
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Onglet Catalogue — Smart Search Suggestions, Kowalski Motion & Parallax :
 *  - barre supérieure connectée via nestedScroll ;
 *  - suggestions intelligentes multi-sources (recherches récentes persistantes,
 *    bibliothèque, historique, À la une et catalogue en direct avec debounce 280ms) ;
 *  - transitions AnimatedContent (< 220ms) pilotées par [BrowseSearchDisplay] ;
 *  - parallaxe GPU liée au défilement sur la carte Spotlight héro.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BrowseScreen(
    source: AnimeSource,
    onOpenShow: (AnimeSearchResult) -> Unit,
    onOpenEpisodes: (animeTitle: String, posterUrl: String?, season: AnimeSeason) -> Unit = { _, _, _ -> },
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
    val controller = remember { BrowseSearchController() }

    LaunchedEffect(Unit) {
        controller.restoreSavedQueryIfNeeded(savedQuery)
        val (recents, lib, hist) = withContext(Dispatchers.IO) {
            Triple(
                SearchHistory.recent(context),
                Library.all(context),
                WatchHistory.recent(context),
            )
        }
        controller.updateLocalSources(recents, lib, hist)
    }

    // Home V3 (carrousel du site + derniers épisodes) ; repli featured().
    var home by remember { mutableStateOf<CatalogHome?>(null) }
    var randomSeed by remember { mutableIntStateOf(0) }

    LaunchedEffect(source) {
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

    val availableGenres = remember(home, controller.featuredState, controller.libraryEntries) {
        val featuredGenres = (controller.featuredState as? BrowseFeaturedState.Loaded)
            ?.items.orEmpty()
            .flatMap { it.genres }
        val heroGenres = home?.heroes.orEmpty().flatMap { it.genres }
        val libraryGenres = controller.libraryEntries.flatMap { it.genres }
        GenreCatalog.mergeGenresForBar(heroGenres + featuredGenres + libraryGenres)
    }

    BackHandler(enabled = controller.canHandleBack) {
        savedQuery = ""
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
            titleContent = { AppBarTitle(title = "Catalogue") },
            scrollBehavior = scrollBehavior,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            OutlinedTextField(
                value = controller.queryField,
                onValueChange = { next ->
                    controller.onQueryChange(next)
                    savedQuery = next.text
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
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
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }

        if (displayMode != BrowseSearchDisplay.FocusedEmptySuggestions &&
            displayMode != BrowseSearchDisplay.TypingSuggestions
        ) {
            GenreFilterChipBar(
                genres = availableGenres,
                selectedGenre = controller.selectedGenre,
                onSelectGenre = { genre ->
                    focusManager.clearFocus()
                    keyboard?.hide()
                    controller.selectGenre(genre)
                },
                allLabel = stringResource(R.string.filter_all),
                testTagPrefix = "browse_genre_chip",
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
                BrowseSearchDisplay.SpotlightIdle -> IdleBrowseSpotlight(
                    featured = controller.featuredState,
                    bottomChrome = bottomChrome,
                    home = home,
                    randomSeed = randomSeed,
                    onShuffle = { randomSeed++ },
                    onOpenShow = onOpenShow,
                    onOpenEpisodes = onOpenEpisodes,
                )

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
                    ErrorView(
                        message = errMessage,
                        onRetry = { submitSearch() },
                        title = stringResource(R.string.error_catalog_title),
                        icon = NovayIcons.ErrorSeal,
                        modifier = Modifier.padding(bottom = bottomChrome),
                    )
                }

                BrowseSearchDisplay.GenreError -> {
                    val errMessage = (controller.genreState as? BrowseSearchState.Error)
                        ?.message ?: "Erreur inconnue"
                    ErrorView(
                        message = errMessage,
                        onRetry = { controller.retryGenre() },
                        title = stringResource(R.string.error_catalog_title),
                        icon = NovayIcons.ErrorSeal,
                        modifier = Modifier.padding(bottom = bottomChrome),
                    )
                }

                BrowseSearchDisplay.EmptyResults -> EmptyScreen(
                    icon = NovayIcons.Search,
                    lottieRes = R.raw.lottie_explore,
                    headingText = "Aucun résultat",
                    subtitleText = "Rien trouvé pour « ${controller.trimmedQuery} » — vérifie l'orthographe ou essaie un mot-clé plus court.",
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
                    val results = if (display == BrowseSearchDisplay.GenreResults) {
                        val currentGenre = controller.selectedGenre
                        val submitted = controller.submittedState as? BrowseSearchState.Results
                        if (submitted != null && currentGenre != null && submitted.results.any { it.genres.isNotEmpty() }) {
                            submitted.results.filter { GenreCatalog.matches(it.genres, currentGenre) }
                        } else {
                            (controller.genreState as? BrowseSearchState.Results)?.results.orEmpty()
                        }
                    } else {
                        (controller.submittedState as? BrowseSearchState.Results)
                            ?.results.orEmpty()
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(140.dp),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 12.dp,
                            bottom = 24.dp + bottomChrome,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .imePadding(),
                    ) {
                        items(
                            items = results,
                            key = { it.url },
                            contentType = { "search_result" },
                        ) { result ->
                            val itemMod = if (motion.reduceMotion) Modifier else Modifier.animateItem()
                            AnimeComfortableGridItem(
                                title = result.title,
                                posterUrl = result.posterUrl,
                                modifier = itemMod,
                                animeUrl = result.url,
                                onClick = { onOpenShow(result) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IdleBrowseSpotlight(
    featured: BrowseFeaturedState,
    bottomChrome: androidx.compose.ui.unit.Dp,
    home: CatalogHome?,
    randomSeed: Int,
    onShuffle: () -> Unit,
    onOpenShow: (AnimeSearchResult) -> Unit,
    onOpenEpisodes: (animeTitle: String, posterUrl: String?, season: AnimeSeason) -> Unit,
) {
    val motion = LocalSlateMotion.current
    val listState = rememberLazyListState()
    val heroParallaxOffsetState = remember(listState, motion.reduceMotion) {
        derivedStateOf {
            if (motion.reduceMotion || listState.firstVisibleItemIndex > 1) {
                0f
            } else {
                listState.firstVisibleItemScrollOffset.toFloat() * 0.24f
            }
        }
    }
    val parallaxProvider = remember(heroParallaxOffsetState) { { heroParallaxOffsetState.value } }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 6.dp, bottom = 28.dp + bottomChrome),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        when (featured) {
            BrowseFeaturedState.Loading -> {
                item(key = "spotlight_skeleton", contentType = "skeleton") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(216.dp),
                            shape = MaterialTheme.shapes.large,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            repeat(3) {
                                ShimmerBox(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(176.dp),
                                    shape = MaterialTheme.shapes.small,
                                )
                            }
                        }
                    }
                }
            }

            BrowseFeaturedState.Unavailable -> {
                item(key = "idle_empty", contentType = "empty") {
                    EmptyScreen(
                        icon = NovayIcons.Search,
                        lottieRes = R.raw.lottie_explore,
                        headingText = "Explore le catalogue",
                        subtitleText = "Saisis au moins 2 lettres ou choisis un genre ci-dessus — la recherche gère automatiquement les variantes.",
                    )
                }
            }

            is BrowseFeaturedState.Loaded -> {
                val lead = featured.items.first()
                val rest = featured.items.drop(1)
                item(key = "spotlight_hero", contentType = "hero") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        SpotlightHeroCard(
                            anime = lead,
                            parallaxOffsetProvider = parallaxProvider,
                            modifier = Modifier.widthIn(max = 840.dp),
                            onClick = { onOpenShow(lead) },
                        )
                    }
                }
                if (rest.isNotEmpty()) {
                    item(key = "spotlight_rail_title", contentType = "header") {
                        Text(
                            text = "Sélection du catalogue",
                            style = MaterialTheme.typography.header,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    item(key = "spotlight_rail", contentType = "rail") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(
                                items = rest,
                                key = { it.url },
                                contentType = { "rail_card" },
                            ) { anime ->
                                val mod = if (motion.reduceMotion) Modifier.width(134.dp) else Modifier
                                    .width(134.dp)
                                    .animateItem()
                                Box(modifier = mod) {
                                    AnimeComfortableGridItem(
                                        title = anime.title,
                                        posterUrl = anime.posterUrl,
                                        animeUrl = anime.url,
                                        onClick = { onOpenShow(anime) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Home V3 : derniers épisodes ajoutés + aléatoire ──────────────
        val cards = home?.cards.orEmpty()
        if (cards.isNotEmpty()) {
            item(key = "recent_header", contentType = "header") {
                Text(
                    text = "Derniers épisodes",
                    style = MaterialTheme.typography.header,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item(key = "recent_rail", contentType = "rail") {
                HomeCardRail(
                    cards = cards.take(24),
                    onOpenEpisodes = onOpenEpisodes,
                )
            }
            item(key = "random_header", contentType = "header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Aléatoire",
                        style = MaterialTheme.typography.header,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onShuffle) {
                        Icon(
                            imageVector = NovayIcons.Refresh,
                            contentDescription = "Regénérer le tirage",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            item(key = "random_rail_" + randomSeed, contentType = "rail") {
                val random = remember(randomSeed, cards) {
                    if (cards.isEmpty()) emptyList()
                    else cards.shuffled(kotlin.random.Random(randomSeed.toLong())).take(12)
                }
                HomeCardRail(
                    cards = random,
                    onOpenEpisodes = onOpenEpisodes,
                )
            }
        }
    }
}

/** Rail de cartes de la home (leurs composants, leur style). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeCardRail(
    cards: List<CatalogCard>,
    onOpenEpisodes: (animeTitle: String, posterUrl: String?, season: AnimeSeason) -> Unit,
) {
    val motion = LocalSlateMotion.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = cards,
            key = { it.animeUrl },
            contentType = { "rail_card" },
        ) { card ->
            val mod = if (motion.reduceMotion) Modifier.width(134.dp) else Modifier
                .width(134.dp)
                .animateItem()
            Box(modifier = mod) {
                AnimeComfortableGridItem(
                    title = card.title,
                    posterUrl = card.posterUrl,
                    animeUrl = card.animeUrl,
                    onClick = {
                        onOpenEpisodes(
                            card.title,
                            card.posterUrl,
                            AnimeSeason(
                                name = SamaParsers.seasonLabelFromUrl(card.seasonUrl) ?: card.title,
                                url = card.seasonUrl,
                                language = card.language
                            )
                        )
                    },
                )
            }
        }
    }
}

/** Un héros de la home → entrée de fiche (pour LEUR SpotlightHeroCard). */
private fun dot.jcversa.novay.data.CatalogHero.toSearchResult(): AnimeSearchResult {
    val animeUrl = Regex("(.*?/catalogue/[^/]+/)").find(seasonUrl)?.groupValues?.get(1)
        ?: seasonUrl
    val subtitle = listOfNotNull(
        seasonLabel?.takeIf { it.isNotBlank() },
        genres.takeIf { it.isNotEmpty() }?.joinToString(" · ")?.takeIf { it.isNotBlank() }
    ).joinToString(" · ").takeIf { it.isNotBlank() }
    return AnimeSearchResult(
        title = title,
        url = animeUrl,
        slug = animeUrl.trimEnd('/').substringAfterLast('/', ""),
        language = language,
        subtitle = subtitle,
        posterUrl = posterUrl,
        genres = GenreCatalog.normalizeList(genres),
    )
}

@Composable
private fun SpotlightHeroCard(
    anime: AnimeSearchResult,
    parallaxOffsetProvider: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.large
    val overlays = NovayTheme.overlays
    val supportsBlur = Build.VERSION.SDK_INT >= 31
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(0.5.dp, overlays.glassBorder, shape)
            .semantics(mergeDescendants = true) {}
            .clickable(onClick = onClick)
            .testTag("browse_spotlight_hero"),
    ) {
        PosterImage(
            url = anime.posterUrl,
            contentDescription = null,
            animeUrl = anime.url,
            animeTitle = anime.title,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = 1.08f
                    scaleY = 1.08f
                    translationY = parallaxOffsetProvider()
                }
                .then(if (supportsBlur) Modifier.blur(18.dp) else Modifier)
                .alpha(if (supportsBlur) 0.32f else 0.16f),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        0f to MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = if (supportsBlur) 0.94f else 0.97f),
                        0.6f to MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = if (supportsBlur) 0.78f else 0.88f),
                        1f to MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PosterImage(
                url = anime.posterUrl,
                contentDescription = anime.title,
                animeUrl = anime.url,
                animeTitle = anime.title,
                modifier = Modifier
                    .size(width = 104.dp, height = 156.dp)
                    .novaySharedPoster(anime.url)
                    .clip(MaterialTheme.shapes.medium),
            )
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = NovayIcons.Spotlight,
                        contentDescription = null,
                        tint = overlays.crimsonOnContainer,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "À la une",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
                        color = overlays.crimsonOnContainer,
                    )
                }
                Text(
                    text = anime.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!anime.subtitle.isNullOrBlank()) {
                    Text(
                        text = anime.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = overlays.ctaContainer,
                        contentColor = overlays.onCtaContainer,
                    ),
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(
                        imageVector = NovayIcons.Play,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Ouvrir la fiche")
                }
            }
        }
    }
}
