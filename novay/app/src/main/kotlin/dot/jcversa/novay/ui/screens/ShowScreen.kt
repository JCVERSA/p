package dot.jcversa.novay.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import android.os.Build
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dot.jcversa.novay.R
import dot.jcversa.novay.data.AnimeEpisode
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.AnimeSeason
import dot.jcversa.novay.data.AnimeSource
import dot.jcversa.novay.data.GenreCatalog
import dot.jcversa.novay.data.Library
import dot.jcversa.novay.data.LibraryEntry
import dot.jcversa.novay.data.ResolvedStream
import dot.jcversa.novay.data.WatchHistory
import dot.jcversa.novay.data.WatchHistoryEntry
import dot.jcversa.novay.downloads.Downloads
import dot.jcversa.novay.ui.LocalBottomChromePadding
import dot.jcversa.novay.ui.components.AppBarAction
import dot.jcversa.novay.ui.components.AppBarTitle
import dot.jcversa.novay.ui.components.EmptyScreen
import dot.jcversa.novay.ui.components.ErrorView
import dot.jcversa.novay.ui.components.LangPill
import dot.jcversa.novay.ui.components.LocalSlateMotion
import dot.jcversa.novay.ui.components.NovayAppBar
import dot.jcversa.novay.ui.components.NovayBadge
import dot.jcversa.novay.ui.components.NovayFavoriteMorphIcon
import dot.jcversa.novay.ui.components.NovayIcons
import dot.jcversa.novay.ui.components.PosterImage
import dot.jcversa.novay.ui.components.ShowScreenSkeleton
import dot.jcversa.novay.ui.components.SlateMotion
import dot.jcversa.novay.ui.components.novaySharedPoster
import dot.jcversa.novay.ui.theme.NovayTheme
import dot.jcversa.novay.ui.theme.header
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Écran Fiche — Aniyomi Split-Pane & Actions, Kowalski Parallax, NiA UDF :
 *  - barre supérieure repliable câblée sur nestedScroll ;
 *  - parallaxe GPU liée au défilement sur l'en-tête d'affiche ;
 *  - cœur morphing Suivre / Suivi + feuille d'actions rapides Aniyomi ;
 *  - tablettes (>= 840dp) : List-Detail côte à côte (Fiche + Saisons | Épisodes).
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ShowScreen(
    source: AnimeSource,
    anime: AnimeSearchResult,
    onBack: () -> Unit,
    onOpenEpisodes: (AnimeSeason) -> Unit,
    onOpenPlayer: (AnimeSeason, AnimeEpisode, ResolvedStream?) -> Unit = { _, _, _ -> },
    onResume: (WatchHistoryEntry) -> Unit
) {
    val context = LocalContext.current
    val motion = LocalSlateMotion.current
    val bottomChrome = LocalBottomChromePadding.current
    var seasons by remember(anime.url) { mutableStateOf<List<AnimeSeason>?>(null) }
    var error by remember(anime.url) { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }
    var inLibrary by remember { mutableStateOf(false) }
    var resumeEntry by remember { mutableStateOf<WatchHistoryEntry?>(null) }
    var downloadedBySeason by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var selectedSeason by remember(anime.url) { mutableStateOf<AnimeSeason?>(null) }
    var showGenres by remember(anime.url) { mutableStateOf(GenreCatalog.normalizeList(anime.genres)) }
    var resolvedPosterUrl by remember(anime.url, anime.posterUrl) {
        mutableStateOf(dot.jcversa.novay.data.ImageUrlSanitizer.sanitizePosterUrl(anime.posterUrl))
    }
    val effectiveAnime = remember(anime, resolvedPosterUrl) {
        if (resolvedPosterUrl != anime.posterUrl) anime.copy(posterUrl = resolvedPosterUrl) else anime
    }
    var showActionsSheet by remember { mutableStateOf(false) }

    LaunchedEffect(anime.url, retryKey) {
        error = null
        seasons = null
        try {
            val loaded = source.seasons(anime)
            seasons = loaded
            val ordered = orderByLanguage(loaded)
            if (selectedSeason == null || loaded.none { it.url == selectedSeason?.url }) {
                selectedSeason = ordered.firstOrNull()?.second?.firstOrNull()
            }
            if (resolvedPosterUrl == null) {
                val canonicalPoster = runCatching { source.resolvePosterUrl(anime) }.getOrNull()
                if (canonicalPoster != null) {
                    resolvedPosterUrl = canonicalPoster
                    withContext(Dispatchers.IO) {
                        Library.updatePoster(context, anime.url, canonicalPoster)
                        WatchHistory.updatePoster(context, anime.url, canonicalPoster)
                    }
                }
            }
            val resolvedGenres = GenreCatalog.normalizeList(
                runCatching { source.genres(anime) }.getOrDefault(emptyList())
                    .ifEmpty { anime.genres }
            )
            if (resolvedGenres.isNotEmpty()) {
                showGenres = resolvedGenres
                withContext(Dispatchers.IO) {
                    Library.updateGenres(context, anime.url, resolvedGenres)
                }
            }
        } catch (e: Exception) {
            error = e.message ?: "Erreur inconnue"
        }
    }

    LaunchedEffect(anime.url) {
        withContext(Dispatchers.IO) {
            val fav = Library.isInLibrary(context, anime.url)
            val resume = WatchHistory.recent(context)
                .firstOrNull { it.animeUrl.trimEnd('/') == anime.url.trimEnd('/') }
            val dl = Downloads.snapshot(context)
                .filter { it.isCompleted }
                .groupBy { it.meta?.seasonUrl?.trimEnd('/') ?: "" }
                .mapValues { it.value.size }
            inLibrary = fav
            resumeEntry = resume
            downloadedBySeason = dl
        }
    }

    val grouped = remember(seasons) {
        seasons?.let(::orderByLanguage).orEmpty()
    }
    val firstSeason = remember(grouped) {
        grouped.firstOrNull()?.second?.firstOrNull()
    }

    fun toggleFavorite() {
        val entry = LibraryEntry(
            title = effectiveAnime.title,
            url = effectiveAnime.url,
            slug = effectiveAnime.slug,
            posterUrl = effectiveAnime.posterUrl,
            languages = seasons.orEmpty().mapNotNull { it.language?.uppercase() }.distinct(),
            addedAt = System.currentTimeMillis(),
            genres = showGenres,
        )
        inLibrary = Library.toggle(context, entry)
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val headerParallaxOffsetState = remember(listState, motion.reduceMotion) {
        derivedStateOf {
            if (motion.reduceMotion || listState.firstVisibleItemIndex > 0) {
                0f
            } else {
                listState.firstVisibleItemScrollOffset.toFloat() * 0.25f
            }
        }
    }
    val headerParallaxProvider = remember(headerParallaxOffsetState) {
        { headerParallaxOffsetState.value }
    }

    val markSeasonSeen: (AnimeSeason) -> Unit = { season ->
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val eps = source.episodes(season)
                    Library.markSeen(context, season.url, eps.size)
                }
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isSplitPane = maxWidth >= 720.dp || (maxWidth >= 600.dp && maxWidth > maxHeight)
        val overlays = NovayTheme.overlays

        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            NovayAppBar(
                titleContent = { AppBarTitle(title = anime.title) },
                navigateUp = onBack,
                scrollBehavior = scrollBehavior,
                actions = {
                    if (!seasons.isNullOrEmpty()) {
                        AppBarAction(
                            contentDescription = "Actions rapides",
                            icon = NovayIcons.Overflow,
                            modifier = Modifier.testTag("show_actions_sheet_button"),
                            onClick = { showActionsSheet = true },
                        )
                    }
                },
            )

            val seasonList = seasons
            val viewKey = when {
                error != null -> "error"
                seasonList == null -> "loading"
                seasonList.isEmpty() -> "empty"
                else -> "loaded"
            }

            AnimatedContent(
                targetState = viewKey,
                transitionSpec = { motion.stateCrossfade() },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                label = "show_body_state",
            ) { stateKey ->
                when (stateKey) {
                    "error" -> ErrorView(
                        message = error ?: "Erreur inconnue",
                        onRetry = { retryKey++ },
                        title = stringResource(R.string.error_catalog_title),
                        icon = NovayIcons.ErrorSeal,
                        modifier = Modifier.padding(bottom = bottomChrome),
                    )
                    "loading" -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        ShowScreenSkeleton()
                    }
                    "empty" -> EmptyScreen(
                        icon = NovayIcons.SeasonReel,
                        headingText = "Aucune saison disponible",
                        subtitleText = "Aucune saison n'est listée pour cet animé pour le moment.",
                        modifier = Modifier.padding(bottom = bottomChrome),
                    )
                    else -> {
                        if (isSplitPane) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .weight(0.42f)
                                        .widthIn(min = 300.dp, max = 440.dp)
                                        .fillMaxHeight(),
                                    contentPadding = PaddingValues(bottom = 32.dp),
                                ) {
                                    showOverviewAndSeasons(
                                        anime = effectiveAnime,
                                        seasons = seasons.orEmpty(),
                                        grouped = grouped,
                                        firstSeason = firstSeason,
                                        resumeEntry = resumeEntry,
                                        inLibrary = inLibrary,
                                        downloadedBySeason = downloadedBySeason,
                                        activeSeasonUrl = selectedSeason?.url,
                                        parallaxOffsetProvider = headerParallaxProvider,
                                        animateItems = !motion.reduceMotion,
                                        onResume = onResume,
                                        onToggleFavorite = ::toggleFavorite,
                                        onPosterResolved = { resolvedPosterUrl = it },
                                        onSelectSeason = { season ->
                                            selectedSeason = season
                                            markSeasonSeen(season)
                                        },
                                    )
                                }
                                VerticalDivider(
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                                val active = selectedSeason ?: firstSeason
                                if (active != null) {
                                    EpisodesPane(
                                        source = source,
                                        animeTitle = anime.title,
                                        season = active,
                                        showSeasonHeader = true,
                                        onOpenPlayer = { ep, offline ->
                                            onOpenPlayer(active, ep, offline)
                                        },
                                        modifier = Modifier
                                            .weight(0.58f)
                                            .fillMaxHeight(),
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.TopCenter,
                            ) {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .widthIn(max = 840.dp),
                                    contentPadding = PaddingValues(bottom = 96.dp + bottomChrome),
                                ) {
                                    showOverviewAndSeasons(
                                        anime = effectiveAnime,
                                        seasons = seasons.orEmpty(),
                                        grouped = grouped,
                                        firstSeason = firstSeason,
                                        resumeEntry = resumeEntry,
                                        inLibrary = inLibrary,
                                        downloadedBySeason = downloadedBySeason,
                                        activeSeasonUrl = null,
                                        parallaxOffsetProvider = headerParallaxProvider,
                                        animateItems = !motion.reduceMotion,
                                        onResume = onResume,
                                        onToggleFavorite = ::toggleFavorite,
                                        onPosterResolved = { resolvedPosterUrl = it },
                                        onSelectSeason = { season ->
                                            markSeasonSeen(season)
                                            onOpenEpisodes(season)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val fabVisible = resumeEntry != null && !isSplitPane
        val idle by remember { derivedStateOf { !listState.isScrollInProgress } }
        AnimatedVisibility(
            visible = fabVisible && idle,
            enter = if (motion.reduceMotion) {
                EnterTransition.None
            } else {
                fadeIn(tween(190, easing = SlateMotion.EaseOutExpo)) +
                    scaleIn(
                        initialScale = 0.88f,
                        animationSpec = tween(200, easing = SlateMotion.EaseOutExpo),
                    )
            },
            exit = if (motion.reduceMotion) {
                ExitTransition.None
            } else {
                fadeOut(tween(120, easing = SlateMotion.FastOutExit)) +
                    scaleOut(targetScale = 0.88f, animationSpec = tween(120, easing = SlateMotion.FastOutExit))
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .then(if (bottomChrome == 0.dp) Modifier.navigationBarsPadding() else Modifier)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + bottomChrome),
        ) {
            ExtendedFloatingActionButton(
                onClick = { resumeEntry?.let(onResume) },
                containerColor = overlays.ctaContainer,
                contentColor = overlays.onCtaContainer,
                modifier = Modifier.testTag("show_resume_fab"),
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = NovayIcons.Play,
                    contentDescription = null,
                )
                Spacer(Modifier.width(6.dp))
                Text(text = "Continuer")
            }
        }
    }

    if (showActionsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showActionsSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = anime.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                ShowSheetActionRow(
                    label = if (inLibrary) "Retirer de la bibliothèque" else "Ajouter à la bibliothèque",
                    icon = NovayIcons.Play,
                    customLeading = {
                        NovayFavoriteMorphIcon(
                            inLibrary = inLibrary,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    onClick = {
                        toggleFavorite()
                        showActionsSheet = false
                    },
                )
                firstSeason?.let { first ->
                    ShowSheetActionRow(
                        label = resumeEntry?.let { "Reprendre Épisode ${it.episodeNumber}" }
                            ?: "Ouvrir ${first.name}",
                        icon = NovayIcons.Play,
                        onClick = {
                            showActionsSheet = false
                            if (resumeEntry != null) {
                                onResume(resumeEntry!!)
                            } else {
                                onOpenEpisodes(first)
                            }
                        },
                    )
                }
                if (seasons.orEmpty().isNotEmpty()) {
                    ShowSheetActionRow(
                        label = "Marquer les saisons comme vues",
                        icon = NovayIcons.MarkAllSeen,
                        onClick = {
                            showActionsSheet = false
                            seasons.orEmpty().forEach(markSeasonSeen)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ShowSheetActionRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    customLeading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (customLeading != null) {
            customLeading()
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.showOverviewAndSeasons(
    anime: AnimeSearchResult,
    seasons: List<AnimeSeason>,
    grouped: List<Pair<String, List<AnimeSeason>>>,
    firstSeason: AnimeSeason?,
    resumeEntry: WatchHistoryEntry?,
    inLibrary: Boolean,
    downloadedBySeason: Map<String, Int>,
    activeSeasonUrl: String?,
    parallaxOffsetProvider: () -> Float,
    animateItems: Boolean,
    onResume: (WatchHistoryEntry) -> Unit,
    onToggleFavorite: () -> Unit,
    onPosterResolved: (String) -> Unit,
    onSelectSeason: (AnimeSeason) -> Unit,
) {
    item(key = "header", contentType = "hero") {
        CinemaShowHeader(
            anime = anime,
            seasonCount = seasons.size,
            languages = grouped.map { it.first },
            parallaxOffsetProvider = parallaxOffsetProvider,
            onPosterResolved = onPosterResolved,
        )
    }

    item(key = "actions", contentType = "actions") {
        val overlays = NovayTheme.overlays
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = {
                    if (resumeEntry != null) {
                        onResume(resumeEntry)
                    } else if (firstSeason != null) {
                        onSelectSeason(firstSeason)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = overlays.ctaContainer,
                    contentColor = overlays.onCtaContainer,
                ),
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("show_primary_cta"),
            ) {
                Icon(
                    imageVector = NovayIcons.Play,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (resumeEntry != null) {
                        "Reprendre Ep. ${resumeEntry.episodeNumber}"
                    } else {
                        "Commencer"
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            OutlinedButton(
                onClick = onToggleFavorite,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (inLibrary) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (inLibrary) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    contentColor = if (inLibrary) {
                        overlays.crimsonOnContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("show_favorite_button"),
            ) {
                NovayFavoriteMorphIcon(
                    inLibrary = inLibrary,
                    tint = if (inLibrary) {
                        overlays.crimsonOnContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    size = 18.dp,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (inLibrary) "Suivi" else "Suivre",
                    maxLines = 1,
                )
            }
        }
    }

    grouped.forEach { (lang, langSeasons) ->
        stickyHeader(key = "sticky_${lang}", contentType = "lang_header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.94f)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 3.dp, height = 16.dp)
                            .clip(CircleShape)
                            .background(
                                if (lang == "VF") {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.tertiary
                                }
                            ),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "$lang · ${langSeasons.size} saison${if (langSeasons.size > 1) "s" else ""}",
                        style = MaterialTheme.typography.header,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                )
            }
        }
        items(
            items = langSeasons,
            key = { it.url },
            contentType = { "season_row" },
        ) { season ->
            val dl = downloadedBySeason[season.url.trimEnd('/')] ?: 0
            val active = activeSeasonUrl != null && season.url == activeSeasonUrl
            val rowMod = if (animateItems) Modifier.animateItem() else Modifier
            SeasonRow(
                season = season,
                downloaded = dl,
                isActive = active,
                modifier = rowMod,
                onClick = { onSelectSeason(season) },
            )
        }
    }
}

@Composable
private fun CinemaShowHeader(
    anime: AnimeSearchResult,
    seasonCount: Int,
    languages: List<String>,
    parallaxOffsetProvider: () -> Float,
    onPosterResolved: (String) -> Unit,
) {
    val posterShape = MaterialTheme.shapes.medium
    val overlays = NovayTheme.overlays
    Box(modifier = Modifier.fillMaxWidth()) {
        val supportsBlur = Build.VERSION.SDK_INT >= 31
        PosterImage(
            url = anime.posterUrl,
            contentDescription = null,
            animeUrl = anime.url,
            animeTitle = anime.title,
            onPosterResolved = onPosterResolved,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = 1.08f
                    scaleY = 1.08f
                    translationY = parallaxOffsetProvider()
                }
                .then(if (supportsBlur) Modifier.blur(16.dp) else Modifier)
                .alpha(if (supportsBlur) 0.28f else 0.16f),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.background.copy(alpha = if (supportsBlur) 0.22f else 0.44f),
                        0.6f to MaterialTheme.colorScheme.background.copy(alpha = if (supportsBlur) 0.80f else 0.88f),
                        1f to MaterialTheme.colorScheme.background,
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            PosterImage(
                url = anime.posterUrl,
                contentDescription = anime.title,
                animeUrl = anime.url,
                animeTitle = anime.title,
                onPosterResolved = onPosterResolved,
                modifier = Modifier
                    .size(width = 112.dp, height = 168.dp)
                    .novaySharedPoster(anime.url)
                    .clip(posterShape)
                    .border(0.5.dp, overlays.glassBorder, posterShape),
            )
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = anime.title,
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 3,
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    languages.forEach { lang ->
                        LangPill(text = lang, vf = lang.equals("VF", true))
                    }
                    Text(
                        text = "$seasonCount saison${if (seasonCount > 1) "s" else ""}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SeasonRow(
    season: AnimeSeason,
    downloaded: Int,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    val motion = LocalSlateMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val containerColor by animateColorAsState(
        targetValue = when {
            isActive -> MaterialTheme.colorScheme.secondaryContainer
            hovered -> MaterialTheme.colorScheme.surfaceContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = motion.colorSpec(180),
        label = "season_row_bg",
    )

    Surface(
        shape = shape,
        color = containerColor,
        border = if (isActive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(shape)
            .semantics(mergeDescendants = true) {}
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick,
            )
            .minimumInteractiveComponentSize(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = season.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isActive) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (downloaded > 0) {
                        Spacer(Modifier.width(8.dp))
                        NovayBadge(
                            text = "$downloaded DL",
                            color = MaterialTheme.colorScheme.tertiary,
                            textColor = MaterialTheme.colorScheme.onTertiary,
                        )
                    }
                }
                season.language?.let { lang ->
                    Text(
                        text = lang.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                imageVector = NovayIcons.ChevronRight,
                contentDescription = "Voir les épisodes de ${season.name}",
                tint = if (isActive) {
                    NovayTheme.overlays.crimsonOnContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/** VF d'abord, VOSTFR ensuite (contrainte permanente NoVaY). */
internal fun orderByLanguage(seasons: List<AnimeSeason>): List<Pair<String, List<AnimeSeason>>> {
    val groups = linkedMapOf<String, MutableList<AnimeSeason>>()
    for (s in seasons) {
        val lang = s.language?.uppercase() ?: when {
            s.url.contains("/vf/") -> "VF"
            s.url.contains("/vostfr/") -> "VOSTFR"
            else -> "AUTRE"
        }
        groups.getOrPut(lang) { mutableListOf() }.add(s)
    }
    val ordered = groups.entries.sortedWith(
        compareBy { (lang, _) ->
            when (lang) {
                "VF" -> 0
                "VOSTFR" -> 1
                else -> 2
            }
        }
    )
    return ordered.map { it.key to it.value.toList() }
}
