package dot.jcversa.novay.ui.screens

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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.AnimeSeason
import dot.jcversa.novay.data.AnimeSource
import dot.jcversa.novay.data.CatalogCard
import dot.jcversa.novay.data.CatalogHome
import dot.jcversa.novay.data.sama.SamaParsers
import dot.jcversa.novay.ui.LocalBottomChromePadding
import dot.jcversa.novay.ui.components.AnimeComfortableGridItem
import dot.jcversa.novay.ui.components.AppBarTitle
import dot.jcversa.novay.ui.components.EmptyScreen
import dot.jcversa.novay.ui.components.ErrorView
import dot.jcversa.novay.ui.components.LocalSlateMotion
import dot.jcversa.novay.ui.components.NovayAppBar
import dot.jcversa.novay.ui.components.PosterGridSkeleton
import dot.jcversa.novay.ui.components.PosterImage
import dot.jcversa.novay.ui.components.ShimmerBox
import dot.jcversa.novay.ui.components.novaySharedPoster
import dot.jcversa.novay.ui.state.BrowseFeaturedState
import dot.jcversa.novay.ui.state.BrowseSearchState
import dot.jcversa.novay.ui.theme.NovayTheme
import dot.jcversa.novay.ui.theme.header
import kotlinx.coroutines.launch

private val DISCOVERY_CHIPS = listOf("Shonen", "Seinen", "Action", "Isekai", "Romance", "Fantastique")

/**
 * Onglet Catalogue — Kowalski Motion, Parallax & Live Spotlight :
 *  - barre supérieure connectée via nestedScroll ;
 *  - transitions AnimatedContent (< 220ms) entre Spotlight, chargement et résultats ;
 *  - parallaxe GPU liée au défilement sur la carte Spotlight héro ;
 *  - placement fluide animateItem() sur les résultats et le carrousel.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BrowseScreen(
    source: AnimeSource,
    onOpenShow: (AnimeSearchResult) -> Unit,
    onOpenEpisodes: (animeTitle: String, posterUrl: String?, season: AnimeSeason) -> Unit = { _, _, _ -> }
) {
    val motion = LocalSlateMotion.current
    val bottomChrome = LocalBottomChromePadding.current
    var query by rememberSaveable { mutableStateOf("") }
    var state by remember { mutableStateOf<BrowseSearchState>(BrowseSearchState.Idle) }
    var featured by remember { mutableStateOf<BrowseFeaturedState>(BrowseFeaturedState.Loading) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    // Home V3 (carrousel du site + derniers épisodes) ; repli featured().
    var home by remember { mutableStateOf<CatalogHome?>(null) }
    var randomSeed by remember { mutableStateOf(0) }

    LaunchedEffect(source) {
        featured = BrowseFeaturedState.Loading
        val h = runCatching { source.home() }.getOrNull()
        if (h != null && (h.heroes.isNotEmpty() || h.cards.isNotEmpty())) {
            home = h
            val heroesAsResults = h.heroes.map { it.toSearchResult() }.distinctBy { it.url }
            featured = if (heroesAsResults.isNotEmpty()) {
                BrowseFeaturedState.Loaded(heroesAsResults)
            } else {
                BrowseFeaturedState.Unavailable
            }
        } else {
            featured = runCatching { source.featured() }
                .getOrDefault(emptyList())
                .let { list ->
                    if (list.isNotEmpty()) BrowseFeaturedState.Loaded(list) else BrowseFeaturedState.Unavailable
                }
        }
    }

    fun runSearch(explicitQuery: String = query) {
        val q = explicitQuery.trim()
        if (q.length < 2) return
        if (explicitQuery != query) query = explicitQuery
        keyboard?.hide()
        scope.launch {
            state = BrowseSearchState.Loading
            state = try {
                BrowseSearchState.Results(source.search(q))
            } catch (e: Exception) {
                BrowseSearchState.Error(e.message ?: "Erreur inconnue")
            }
        }
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
                value = query,
                onValueChange = {
                    query = it
                    if (it.isBlank()) state = BrowseSearchState.Idle
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .testTag("browse_search_input"),
                placeholder = { Text(text = "Rechercher un anime (ex. Frieren, Naruto)…") },
                leadingIcon = {
                    IconButton(
                        onClick = { runSearch() },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Lancer la recherche",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                query = ""
                                state = BrowseSearchState.Idle
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("browse_clear_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Effacer la recherche",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSearch() }),
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

        AnimatedContent(
            targetState = state,
            transitionSpec = { motion.stateCrossfade() },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            label = "browse_search_state",
        ) { s ->
            when (s) {
                BrowseSearchState.Idle -> IdleBrowseSpotlight(
                    featured = featured,
                    bottomChrome = bottomChrome,
                    home = home,
                    randomSeed = randomSeed,
                    onShuffle = { randomSeed++ },
                    onOpenShow = onOpenShow,
                    onOpenEpisodes = onOpenEpisodes,
                    onQuickQuery = { chip -> runSearch(chip) },
                )
                BrowseSearchState.Loading -> PosterGridSkeleton(
                    minCellSize = 140.dp,
                    itemCount = 9,
                    modifier = Modifier.padding(bottom = bottomChrome),
                )
                is BrowseSearchState.Error -> ErrorView(
                    message = s.message,
                    onRetry = { runSearch() },
                    modifier = Modifier.padding(bottom = bottomChrome),
                )
                is BrowseSearchState.Results -> {
                    if (s.results.isEmpty()) {
                        EmptyScreen(
                            icon = Icons.Rounded.Search,
                            headingText = "Aucun résultat",
                            subtitleText = "Rien trouvé pour « ${query.trim()} » — vérifie l'orthographe ou essaie un mot-clé plus court.",
                            modifier = Modifier.padding(bottom = bottomChrome),
                        )
                    } else {
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
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(
                                items = s.results,
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
                                    onLongClick = { },
                                )
                            }
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
    onQuickQuery: (String) -> Unit,
) {
    val motion = LocalSlateMotion.current
    val listState = rememberLazyListState()
    val heroParallaxOffset by remember {
        derivedStateOf {
            if (motion.reduceMotion || listState.firstVisibleItemIndex > 1) {
                0f
            } else {
                listState.firstVisibleItemScrollOffset.toFloat() * 0.24f
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 6.dp, bottom = 28.dp + bottomChrome),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(key = "quick_chips", contentType = "chips") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(DISCOVERY_CHIPS, key = { it }) { chip ->
                    FilterChip(
                        selected = false,
                        onClick = { onQuickQuery(chip) },
                        modifier = Modifier.testTag("browse_chip_${chip.lowercase()}"),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            labelColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = false,
                            borderColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                        label = { Text(chip) },
                    )
                }
            }
        }

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
                        icon = Icons.Rounded.Search,
                        headingText = "Explore le catalogue",
                        subtitleText = "Saisis au moins 2 lettres ou choisis un thème ci-dessus — la recherche gère automatiquement les variantes.",
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
                            parallaxOffsetPx = heroParallaxOffset,
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
                                        onLongClick = {},
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
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = "Regénérer le tirage",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            item(key = "random_rail_${randomSeed}", contentType = "rail") {
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
                    onLongClick = {},
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
        posterUrl = posterUrl
    )
}

@Composable
private fun SpotlightHeroCard(
    anime: AnimeSearchResult,
    parallaxOffsetPx: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.large
    val overlays = NovayTheme.overlays
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
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = 1.08f
                    scaleY = 1.08f
                    translationY = parallaxOffsetPx
                }
                .blur(18.dp)
                .alpha(0.32f),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        0f to MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.94f),
                        0.6f to MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.78f),
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
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "À LA UNE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
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
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
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
