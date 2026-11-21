@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package dot.jcversa.novay.ui.screens

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dot.jcversa.novay.R
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.AnimeSeason
import dot.jcversa.novay.data.CatalogCard
import dot.jcversa.novay.data.CatalogHero
import dot.jcversa.novay.data.CatalogHome
import dot.jcversa.novay.data.GenreCatalog
import dot.jcversa.novay.data.WatchHistoryEntry
import dot.jcversa.novay.data.sama.SamaParsers
import dot.jcversa.novay.ui.components.AnimeComfortableGridItem
import dot.jcversa.novay.ui.components.EmptyScreen
import dot.jcversa.novay.ui.components.LocalSlateMotion
import dot.jcversa.novay.ui.components.NovayBadge
import dot.jcversa.novay.ui.components.NovayIcons
import dot.jcversa.novay.ui.components.NovaySectionHeader
import dot.jcversa.novay.ui.components.PartialLoadingBanner
import dot.jcversa.novay.ui.components.PosterImage
import dot.jcversa.novay.ui.components.ShimmerBox
import dot.jcversa.novay.ui.components.SwiftSlateSpinner
import dot.jcversa.novay.ui.components.bounceClick
import dot.jcversa.novay.ui.components.novayCinemaGlass
import dot.jcversa.novay.ui.components.novaySharedPoster
import dot.jcversa.novay.ui.state.BrowseFeaturedState
import dot.jcversa.novay.ui.state.BrowseMood
import dot.jcversa.novay.ui.state.CatalogFilterEngine
import dot.jcversa.novay.ui.state.CatalogPageUiState
import dot.jcversa.novay.ui.state.CatalogSortOption
import dot.jcversa.novay.ui.state.filterItemsByMood
import dot.jcversa.novay.ui.theme.NovayTheme
import dot.jcversa.novay.ui.theme.NovayThemePreset
import dot.jcversa.novay.ui.theme.NovayThemeState
import dot.jcversa.novay.ui.theme.rememberPosterPalette
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
internal fun HybridBrowseCatalog(
    featured: BrowseFeaturedState,
    home: CatalogHome?,
    catalogPage: CatalogPageUiState,
    watchHistory: List<WatchHistoryEntry>,
    libraryUrls: Set<String>,
    randomSeed: Int,
    bottomChrome: Dp,
    onShuffle: () -> Unit,
    onRetryHome: () -> Unit,
    onRetryCatalog: () -> Unit,
    onLoadMoreCatalog: () -> Unit,
    onOpenShow: (AnimeSearchResult) -> Unit,
    onOpenEpisodes: (animeTitle: String, posterUrl: String?, season: AnimeSeason) -> Unit,
    onResumeHistory: (WatchHistoryEntry) -> Unit,
) {
    val motion = LocalSlateMotion.current
    val gridState = rememberLazyGridState()
    var selectedMood by rememberSaveable { mutableStateOf(BrowseMood.ALL) }

    val heroParallaxOffsetState = remember(gridState, motion.reduceMotion) {
        derivedStateOf {
            if (motion.reduceMotion || gridState.firstVisibleItemIndex > 1) {
                0f
            } else {
                gridState.firstVisibleItemScrollOffset.toFloat() * 0.24f
            }
        }
    }
    val parallaxProvider = remember(heroParallaxOffsetState) { { heroParallaxOffsetState.value } }

    val cards = home?.cards.orEmpty()
    val catalogItems = remember(catalogPage.items, featured) {
        if (catalogPage.items.isNotEmpty()) {
            catalogPage.items
        } else {
            (featured as? BrowseFeaturedState.Loaded)?.items.orEmpty()
        }
    }

    val continueItems = remember(watchHistory) {
        watchHistory
            .distinctBy { "${it.seasonUrl.trimEnd('/')}_${it.episodeNumber}" }
            .take(8)
    }

    val moodPool = remember(featured, catalogItems) {
        val feat = (featured as? BrowseFeaturedState.Loaded)?.items.orEmpty()
        CatalogFilterEngine.mergeUniqueItems(feat, catalogItems)
    }
    val moodItems = remember(moodPool, selectedMood) {
        filterItemsByMood(moodPool, selectedMood).take(14)
    }

    val trendingItems = remember(featured, cards, catalogItems) {
        val leadUrl = (featured as? BrowseFeaturedState.Loaded)?.items?.firstOrNull()
            ?.let { CatalogFilterEngine.normalizeUrlKey(it.url) }
        val homeCardKeys = cards.map { CatalogFilterEngine.normalizeUrlKey(it.animeUrl) }.toSet()
        val airingOrMatched = catalogItems.filter { item ->
            val key = CatalogFilterEngine.normalizeUrlKey(item.url)
            if (key == leadUrl) return@filter false
            item.status.equals("En cours", ignoreCase = true) || key in homeCardKeys
        }
        val featuredUrls = (featured as? BrowseFeaturedState.Loaded)?.items.orEmpty()
            .drop(1)
            .map { CatalogFilterEngine.normalizeUrlKey(it.url) }
            .toSet()
        airingOrMatched
            .filter { CatalogFilterEngine.normalizeUrlKey(it.url) !in featuredUrls }
            .distinctBy { CatalogFilterEngine.normalizeUrlKey(it.url) }
            .take(16)
    }

    LaunchedEffect(
        gridState,
        catalogItems.size,
        catalogPage.hasNextPage,
        catalogPage.isLoading,
        catalogPage.isLoadingMore,
        catalogPage.paginationError,
    ) {
        if (!catalogPage.hasNextPage ||
            catalogPage.isLoading ||
            catalogPage.isLoadingMore ||
            catalogPage.paginationError != null
        ) {
            return@LaunchedEffect
        }
        snapshotFlow {
            val layout = gridState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible to layout.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisible, totalCount) ->
                if (totalCount > 0 && lastVisible >= totalCount - 4) {
                    onLoadMoreCatalog()
                }
            }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(140.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("browse_hybrid_grid"),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 6.dp,
            bottom = 28.dp + bottomChrome,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── 0. Bandeau de rafraîchissement partiel non bloquant ────────────
        if (catalogPage.isLoading && catalogItems.isNotEmpty()) {
            item(
                key = "browse_partial_loading",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "partial_loading",
            ) {
                PartialLoadingBanner(
                    visible = true,
                    message = "Actualisation du catalogue en cours…",
                )
            }
        }

        // ── 1. Héros cinématique « À la une » ──────────────────────────────
        when (featured) {
            BrowseFeaturedState.Loading -> {
                item(
                    key = "spotlight_skeleton",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "skeleton",
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
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
                if (catalogItems.isEmpty() && !catalogPage.isLoading && cards.isEmpty()) {
                    item(
                        key = "idle_empty",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = "empty",
                    ) {
                        EmptyScreen(
                            icon = NovayIcons.Search,
                            lottieRes = R.raw.lottie_explore,
                            headingText = "Sélection indisponible",
                            subtitleText = "Impossible de charger la une du moment. Réessaie ou lance une recherche / un genre ci-dessus.",
                            actionLabel = stringResource(R.string.action_retry),
                            onAction = onRetryHome,
                            actionTestTag = "browse_home_retry_button",
                        )
                    }
                }
            }

            is BrowseFeaturedState.Loaded -> {
                val heroSlides = featured.items.take(5)
                val rest = featured.items.drop(1)
                item(
                    key = "spotlight_hero",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "hero",
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CinematicSpotlightCarousel(
                            heroes = heroSlides,
                            parallaxOffsetProvider = parallaxProvider,
                            modifier = Modifier.widthIn(max = 840.dp),
                            onOpenShow = onOpenShow,
                        )
                    }
                }
                if (rest.isNotEmpty()) {
                    item(
                        key = "spotlight_rail_title",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = "header",
                    ) {
                        NovaySectionHeader(
                            title = stringResource(R.string.catalog_section_featured),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item(
                        key = "spotlight_rail",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = "rail",
                    ) {
                        AnimeDiscoveryRail(
                            items = rest,
                            libraryUrls = libraryUrls,
                            onOpenShow = onOpenShow,
                        )
                    }
                }
            }
        }

        // ── 1b. Reprendre la lecture 2.0 ──────────────────────────────────
        if (continueItems.isNotEmpty()) {
            item(
                key = "continue_watching_section",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "continue_watching",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NovaySectionHeader(
                        title = stringResource(R.string.catalog_section_continue_watching),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ContinueWatchingRail(
                        entries = continueItems,
                        onResume = onResumeHistory,
                    )
                }
            }
        }

        // ── 2. Derniers épisodes ──────────────────────────────────────────
        if (cards.isNotEmpty()) {
            item(
                key = "recent_header",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "header",
            ) {
                NovaySectionHeader(
                    title = stringResource(R.string.catalog_section_latest),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item(
                key = "recent_rail",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "rail",
            ) {
                HomeCardRail(
                    cards = cards.take(24),
                    onOpenEpisodes = onOpenEpisodes,
                )
            }
        }

        // ── 2b. Ambiances & Découverte (Mood Discovery) ───────────────────
        if (moodPool.isNotEmpty()) {
            item(
                key = "mood_discovery_section",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "mood_discovery",
            ) {
                MoodDiscoverySection(
                    selectedMood = selectedMood,
                    moodItems = moodItems,
                    libraryUrls = libraryUrls,
                    onSelectMood = { next ->
                        selectedMood = if (selectedMood == next) BrowseMood.ALL else next
                    },
                    onOpenShow = onOpenShow,
                )
            }
        }

        // ── 3. Tendances & En cours (données réelles dédupliquées) ────────
        if (trendingItems.isNotEmpty()) {
            item(
                key = "trending_header",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "header",
            ) {
                NovaySectionHeader(
                    title = stringResource(R.string.catalog_section_trending),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item(
                key = "trending_rail",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "rail",
            ) {
                AnimeDiscoveryRail(
                    items = trendingItems,
                    libraryUrls = libraryUrls,
                    onOpenShow = onOpenShow,
                )
            }
        }

        // ── 4. Aléatoire ──────────────────────────────────────────────────
        if (cards.isNotEmpty()) {
            item(
                key = "random_header",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "header",
            ) {
                NovaySectionHeader(
                    title = stringResource(R.string.catalog_section_random),
                    modifier = Modifier.fillMaxWidth(),
                    trailing = {
                        IconButton(
                            onClick = onShuffle,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("browse_random_shuffle_button"),
                        ) {
                            Icon(
                                imageVector = NovayIcons.Refresh,
                                contentDescription = "Regénérer le tirage",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                )
            }
            item(
                key = "random_rail_$randomSeed",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "rail",
            ) {
                val random = remember(randomSeed, cards) {
                    cards.shuffled(kotlin.random.Random(randomSeed.toLong())).take(12)
                }
                HomeCardRail(
                    cards = random,
                    onOpenEpisodes = onOpenEpisodes,
                )
            }
        }

        // ── 5. Grille paginée « Catalogue complet » ───────────────────────
        if (catalogItems.isNotEmpty() || catalogPage.isLoading || catalogPage.error != null) {
            item(
                key = "complete_catalog_header",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "header",
            ) {
                NovaySectionHeader(
                    title = stringResource(R.string.catalog_section_complete),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .testTag("browse_complete_catalog_header"),
                    trailing = if (catalogItems.isNotEmpty()) {
                        {
                            CatalogCountBadge(count = catalogItems.size)
                        }
                    } else {
                        null
                    },
                )
            }
        }

        when {
            catalogPage.isLoading && catalogItems.isEmpty() -> {
                items(
                    count = 6,
                    key = { "catalog_grid_skeleton_$it" },
                    contentType = { "grid_skeleton" },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f),
                            shape = MaterialTheme.shapes.small,
                        )
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth(0.75f)
                                .height(14.dp),
                            shape = MaterialTheme.shapes.extraSmall,
                        )
                    }
                }
            }

            catalogPage.error != null && catalogItems.isEmpty() -> {
                item(
                    key = "complete_catalog_error",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "catalog_error",
                ) {
                    CatalogRetryRow(
                        message = catalogPage.error,
                        testTag = "browse_catalog_retry_button",
                        onRetry = onRetryCatalog,
                    )
                }
            }

            else -> {
                items(
                    items = catalogItems,
                    key = { "grid_${it.url}" },
                    contentType = { "catalog_grid_card" },
                ) { anime ->
                    val itemMod = if (motion.reduceMotion) Modifier else Modifier.animateItem()
                    val normUrl = CatalogFilterEngine.normalizeUrlKey(anime.url)
                    AnimeComfortableGridItem(
                        title = anime.title,
                        posterUrl = anime.posterUrl,
                        animeUrl = anime.url,
                        languageBadge = anime.cardLanguageBadge(),
                        metaBadge = anime.cardMetaBadge(),
                        inLibrary = normUrl in libraryUrls,
                        modifier = itemMod.testTag("browse_catalog_card_${anime.slug.ifBlank { anime.title }}"),
                        onClick = { onOpenShow(anime) },
                    )
                }

                if (catalogPage.isLoadingMore) {
                    item(
                        key = "pagination_loading",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = "pagination_footer",
                    ) {
                        CatalogPaginationLoadingFooter()
                    }
                } else if (catalogPage.paginationError != null) {
                    item(
                        key = "pagination_error",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = "pagination_footer",
                    ) {
                        CatalogRetryRow(
                            message = stringResource(R.string.catalog_pagination_error),
                            testTag = "browse_pagination_retry_button",
                            onRetry = onLoadMoreCatalog,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CinematicSpotlightCarousel(
    heroes: List<AnimeSearchResult>,
    parallaxOffsetProvider: () -> Float,
    onOpenShow: (AnimeSearchResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (heroes.size <= 1) {
        val only = heroes.firstOrNull() ?: return
        SpotlightHeroCard(
            anime = only,
            parallaxOffsetProvider = parallaxOffsetProvider,
            modifier = modifier,
            onClick = { onOpenShow(only) },
        )
        return
    }
    val pagerState = rememberPagerState(pageCount = { heroes.size })
    val motion = LocalSlateMotion.current
    val overlays = NovayTheme.overlays

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalPager(
            state = pagerState,
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val anime = heroes[page]
            SpotlightHeroCard(
                anime = anime,
                parallaxOffsetProvider = parallaxOffsetProvider,
                testTagName = if (page == pagerState.currentPage) "browse_spotlight_hero" else null,
                onClick = { onOpenShow(anime) },
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.testTag("browse_spotlight_indicators"),
        ) {
            heroes.indices.forEach { idx ->
                val selected = idx == pagerState.currentPage
                val widthDp by animateDpAsState(
                    targetValue = if (selected) 20.dp else 6.dp,
                    animationSpec = motion.dockDpSpec(),
                    label = "hero_dot_w_$idx",
                )
                val dotColor by animateColorAsState(
                    targetValue = if (selected) overlays.crimsonOnContainer else MaterialTheme.colorScheme.outlineVariant,
                    animationSpec = motion.colorSpec(180),
                    label = "hero_dot_c_$idx",
                )
                Box(
                    modifier = Modifier
                        .size(width = widthDp, height = 6.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingRail(
    entries: List<WatchHistoryEntry>,
    onResume: (WatchHistoryEntry) -> Unit,
) {
    val overlays = NovayTheme.overlays
    val motion = LocalSlateMotion.current

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.testTag("browse_continue_watching_rail"),
    ) {
        items(
            items = entries,
            key = { "${it.seasonUrl.trimEnd('/')}_${it.episodeNumber}" },
            contentType = { "continue_card" },
        ) { entry ->
            val progress = if (entry.durationMs > 0L) {
                (entry.positionMs.toFloat() / entry.durationMs.toFloat()).coerceIn(0.02f, 1f)
            } else {
                0f
            }
            val remainingMin = if (entry.durationMs > entry.positionMs && entry.durationMs > 0L) {
                ((entry.durationMs - entry.positionMs) / 60_000L).coerceAtLeast(1L)
            } else {
                0L
            }
            val cardShape = MaterialTheme.shapes.medium
            val mod = if (motion.reduceMotion) Modifier else Modifier.animateItem()

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = mod
                    .widthIn(min = 256.dp, max = 284.dp)
                    .novayCinemaGlass(
                        shape = cardShape,
                        tint = MaterialTheme.colorScheme.surfaceContainerLow,
                        borderColor = overlays.glassBorder,
                        showBorder = true,
                    )
                    .bounceClick(onClick = { onResume(entry) })
                    .padding(10.dp)
                    .testTag("browse_continue_card_${entry.episodeNumber}"),
            ) {
                PosterImage(
                    url = entry.posterUrl,
                    contentDescription = entry.animeTitle,
                    animeUrl = entry.animeUrl,
                    animeTitle = entry.animeTitle,
                    modifier = Modifier
                        .size(width = 52.dp, height = 74.dp)
                        .novaySharedPoster(entry.animeUrl)
                        .clip(MaterialTheme.shapes.extraSmall),
                )
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = entry.animeTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Ep. ${entry.episodeNumber} · ${entry.seasonName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = overlays.crimsonOnContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (progress > 0f) {
                        LinearProgressIndicator(
                            progress = { progress },
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            strokeCap = StrokeCap.Round,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                        )
                    }
                    if (remainingMin > 0L) {
                        Text(
                            text = "${remainingMin} min restantes",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = overlays.ctaContainer,
                    contentColor = overlays.onCtaContainer,
                    modifier = Modifier.size(34.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = NovayIcons.Play,
                            contentDescription = stringResource(R.string.action_resume_episode),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodDiscoverySection(
    selectedMood: BrowseMood,
    moodItems: List<AnimeSearchResult>,
    libraryUrls: Set<String>,
    onSelectMood: (BrowseMood) -> Unit,
    onOpenShow: (AnimeSearchResult) -> Unit,
) {
    val overlays = NovayTheme.overlays
    val motion = LocalSlateMotion.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("browse_mood_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        NovaySectionHeader(
            title = stringResource(R.string.catalog_section_moods),
            modifier = Modifier.fillMaxWidth(),
            trailing = if (selectedMood != BrowseMood.ALL) {
                {
                    Text(
                        text = "${moodItems.size} titre${if (moodItems.size > 1) "s" else ""}",
                        style = MaterialTheme.typography.labelMedium,
                        color = overlays.crimsonOnContainer,
                    )
                }
            } else {
                null
            },
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(
                items = BrowseMood.entries,
                key = { it.id },
            ) { mood ->
                val isSelected = selectedMood == mood
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    animationSpec = motion.colorSpec(180),
                    label = "mood_bg_${mood.id}",
                )
                val fgColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        overlays.crimsonOnContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    animationSpec = motion.colorSpec(180),
                    label = "mood_fg_${mood.id}",
                )
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectMood(mood) },
                    label = {
                        Text(
                            text = mood.label,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = bgColor,
                        labelColor = fgColor,
                        selectedContainerColor = bgColor,
                        selectedLabelColor = fgColor,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = MaterialTheme.colorScheme.outlineVariant,
                        selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("browse_mood_${mood.id}"),
                )
            }
        }
        AnimatedVisibility(
            visible = selectedMood != BrowseMood.ALL,
            enter = if (motion.reduceMotion) fadeIn() else expandVertically() + fadeIn(motion.enterSpec(200)),
            exit = if (motion.reduceMotion) fadeOut() else shrinkVertically() + fadeOut(motion.exitSpec(140)),
        ) {
            if (moodItems.isEmpty()) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(0.5.dp, overlays.glassBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Aucun animé déjà chargé ne correspond à « ${selectedMood.label} ».",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            } else {
                AnimeDiscoveryRail(
                    items = moodItems,
                    libraryUrls = libraryUrls,
                    onOpenShow = onOpenShow,
                )
            }
        }
    }
}

@Composable
internal fun CatalogResultsGrid(
    results: List<AnimeSearchResult>,
    libraryUrls: Set<String>,
    sortOption: CatalogSortOption,
    bottomChrome: Dp,
    isLoadingMore: Boolean = false,
    hasNextPage: Boolean = false,
    paginationError: String? = null,
    onLoadMore: (() -> Unit)? = null,
    onOpenShow: (AnimeSearchResult) -> Unit,
) {
    val motion = LocalSlateMotion.current
    val gridState = rememberLazyGridState()

    LaunchedEffect(
        gridState,
        results.size,
        hasNextPage,
        isLoadingMore,
        paginationError,
        onLoadMore,
    ) {
        if (onLoadMore == null || !hasNextPage || isLoadingMore || paginationError != null) {
            return@LaunchedEffect
        }
        snapshotFlow {
            val layout = gridState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible to layout.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisible, totalCount) ->
                if (totalCount > 0 && lastVisible >= totalCount - 4) {
                    onLoadMore()
                }
            }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(140.dp),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 10.dp,
            bottom = 24.dp + bottomChrome,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .testTag("browse_results_grid"),
    ) {
        item(
            key = "results_count_header",
            span = { GridItemSpan(maxLineSpan) },
            contentType = "results_header",
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedContent(
                    targetState = results.size,
                    transitionSpec = { motion.stateCrossfade() },
                    label = "results_count_anim",
                ) { count ->
                    Text(
                        text = if (count == 1) "1 animé" else "$count animés",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("browse_results_count"),
                    )
                }
                if (sortOption != CatalogSortOption.DEFAULT) {
                    Text(
                        text = sortOption.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = NovayTheme.overlays.crimsonOnContainer,
                    )
                }
            }
        }

        items(
            items = results,
            key = { it.url },
            contentType = { "search_result" },
        ) { result ->
            val itemMod = if (motion.reduceMotion) Modifier else Modifier.animateItem()
            val normUrl = CatalogFilterEngine.normalizeUrlKey(result.url)
            AnimeComfortableGridItem(
                title = result.title,
                posterUrl = result.posterUrl,
                animeUrl = result.url,
                languageBadge = result.cardLanguageBadge(),
                metaBadge = result.cardMetaBadge(),
                inLibrary = normUrl in libraryUrls,
                modifier = itemMod,
                onClick = { onOpenShow(result) },
            )
        }

        if (isLoadingMore) {
            item(
                key = "filtered_pagination_loading",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "pagination_footer",
            ) {
                CatalogPaginationLoadingFooter()
            }
        } else if (paginationError != null && onLoadMore != null) {
            item(
                key = "filtered_pagination_error",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "pagination_footer",
            ) {
                CatalogRetryRow(
                    message = stringResource(R.string.catalog_pagination_error),
                    testTag = "browse_pagination_retry_button",
                    onRetry = onLoadMore,
                )
            }
        }
    }
}

@Composable
private fun AnimeDiscoveryRail(
    items: List<AnimeSearchResult>,
    libraryUrls: Set<String>,
    onOpenShow: (AnimeSearchResult) -> Unit,
) {
    val motion = LocalSlateMotion.current
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = items,
            key = { it.url },
            contentType = { "rail_card" },
        ) { anime ->
            val mod = if (motion.reduceMotion) {
                Modifier.width(134.dp)
            } else {
                Modifier
                    .width(134.dp)
                    .animateItem()
            }
            val normUrl = CatalogFilterEngine.normalizeUrlKey(anime.url)
            Box(modifier = mod) {
                AnimeComfortableGridItem(
                    title = anime.title,
                    posterUrl = anime.posterUrl,
                    animeUrl = anime.url,
                    languageBadge = anime.cardLanguageBadge(),
                    metaBadge = anime.cardMetaBadge(),
                    inLibrary = normUrl in libraryUrls,
                    onClick = { onOpenShow(anime) },
                )
            }
        }
    }
}

/** Rail de cartes de la home (épisodes récents & tirage aléatoire). */
@Composable
private fun HomeCardRail(
    cards: List<CatalogCard>,
    onOpenEpisodes: (animeTitle: String, posterUrl: String?, season: AnimeSeason) -> Unit,
) {
    val motion = LocalSlateMotion.current
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = cards,
            key = { it.animeUrl },
            contentType = { "rail_card" },
        ) { card ->
            val mod = if (motion.reduceMotion) {
                Modifier.width(134.dp)
            } else {
                Modifier
                    .width(134.dp)
                    .animateItem()
            }
            Column(modifier = mod) {
                Box(modifier = Modifier.fillMaxWidth()) {
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
                                    language = card.language,
                                ),
                            )
                        },
                    )
                    card.language?.takeIf { it.isNotBlank() }?.let { lang ->
                        val isVf = lang.equals("VF", true)
                        NovayBadge(
                            text = lang.uppercase(),
                            color = if (isVf) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.tertiary
                            },
                            textColor = if (isVf) {
                                NovayTheme.overlays.crimsonOnContainer
                            } else {
                                MaterialTheme.colorScheme.onTertiary
                            },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp),
                        )
                    }
                }
                card.timeLabel?.takeIf { it.isNotBlank() }?.let { time ->
                    Text(
                        text = time,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogCountBadge(count: Int) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = if (count == 1) "1 animé" else "$count animés",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun CatalogPaginationLoadingFooter() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .testTag("browse_pagination_loading"),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SwiftSlateSpinner(size = 20.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.catalog_pagination_loading),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CatalogRetryRow(
    message: String,
    testTag: String,
    onRetry: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp),
            )
            OutlinedButton(
                onClick = onRetry,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag(testTag),
            ) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}

internal fun CatalogHero.toSearchResult(): AnimeSearchResult {
    val animeUrl = Regex("(.*?/catalogue/[^/]+/)").find(seasonUrl)?.groupValues?.get(1)
        ?: seasonUrl
    val subtitle = listOfNotNull(
        seasonLabel?.takeIf { it.isNotBlank() },
        genres.takeIf { it.isNotEmpty() }?.joinToString(" · ")?.takeIf { it.isNotBlank() },
    ).joinToString(" · ").takeIf { it.isNotBlank() }
    return AnimeSearchResult(
        title = title,
        url = animeUrl,
        slug = animeUrl.trimEnd('/').substringAfterLast('/', ""),
        language = language,
        subtitle = subtitle,
        isVf = language.equals("VF", ignoreCase = true),
        posterUrl = posterUrl,
        genres = GenreCatalog.normalizeList(genres),
        synopsis = synopsis,
    )
}

private fun AnimeSearchResult.cardLanguageBadge(): String? =
    language?.trim()?.takeIf { it.isNotEmpty() }
        ?: if (isVf) "VF" else null

private fun AnimeSearchResult.cardMetaBadge(): String? {
    val parts = listOfNotNull(
        releaseYear?.trim()?.takeIf { it.isNotEmpty() },
        format?.trim()?.takeIf { it.isNotEmpty() },
    )
    return if (parts.isNotEmpty()) parts.joinToString(" · ") else null
}

@Composable
internal fun SpotlightHeroCard(
    anime: AnimeSearchResult,
    parallaxOffsetProvider: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTagName: String? = "browse_spotlight_hero",
) {
    val shape = MaterialTheme.shapes.large
    val overlays = NovayTheme.overlays
    val motion = LocalSlateMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !motion.reduceMotion) 0.985f else 1f,
        animationSpec = motion.effectFloatSpec(),
        label = "spotlight_hero_scale",
    )
    val palette = rememberPosterPalette(
        posterUrl = anime.posterUrl,
        syncGlobalDynamicTheme = NovayThemeState.preset == NovayThemePreset.POSTER_ADAPTIVE,
    )
    val ambientAccent by animateColorAsState(
        targetValue = palette?.accent ?: MaterialTheme.colorScheme.primary,
        animationSpec = motion.colorSpec(240),
        label = "spotlight_ambient_accent",
    )
    val supportsBlur = dot.jcversa.novay.performance.LocalPerformanceProfile.current.allowAtmosphericBackdropBlur
    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(0.5.dp, ambientAccent.copy(alpha = 0.28f), shape)
            .semantics(mergeDescendants = true) {}
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick,
            )
            .let { if (testTagName != null) it.testTag(testTagName) else it },
    ) {
        PosterImage(
            url = anime.posterUrl,
            contentDescription = null,
            animeUrl = anime.url,
            animeTitle = anime.title,
            decodeWidthDp = 96.dp,
            decodeHeightDp = 144.dp,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = 1.08f
                    scaleY = 1.08f
                    translationY = parallaxOffsetProvider()
                }
                .then(if (supportsBlur) Modifier.blur(18.dp) else Modifier)
                .alpha(if (supportsBlur) 0.34f else 0.16f),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        0f to MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = if (supportsBlur) 0.94f else 0.97f),
                        0.6f to MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = if (supportsBlur) 0.78f else 0.88f),
                        1f to ambientAccent.copy(alpha = 0.20f),
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
                decodeWidthDp = 104.dp,
                decodeHeightDp = 156.dp,
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
