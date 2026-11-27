package dot.jcversa.novay.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dot.jcversa.novay.R
import dot.jcversa.novay.data.AnimeDetails
import dot.jcversa.novay.data.AnimeEpisode
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.AnimeSeason
import dot.jcversa.novay.data.ScanChapter
import dot.jcversa.novay.data.ScanPanel
import dot.jcversa.novay.data.AnimeSource
import dot.jcversa.novay.data.GenreCatalog
import dot.jcversa.novay.data.ImageUrlSanitizer
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
import dot.jcversa.novay.ui.components.LocalNovayToast
import dot.jcversa.novay.ui.components.LocalSlateMotion
import dot.jcversa.novay.ui.components.NovayAppBar
import dot.jcversa.novay.ui.components.NovayIcons
import dot.jcversa.novay.ui.components.NovayToastTone
import dot.jcversa.novay.ui.components.OfflineStateView
import dot.jcversa.novay.ui.components.ShowScreenSkeleton
import dot.jcversa.novay.ui.components.SlateMotion
import dot.jcversa.novay.ui.components.isDeviceOnline
import dot.jcversa.novay.ui.theme.NovayTheme
import dot.jcversa.novay.ui.theme.NovayThemePreset
import dot.jcversa.novay.ui.theme.NovayThemeState
import dot.jcversa.novay.ui.theme.rememberPosterPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Écran Fiche Cinéma — Hero immersif avec parallaxe, métadonnées réelles,
 * sélecteur segmenté VF / VOSTFR, sections repliables, Split-Pane tablette
 * et préservation intégrale des transitions et animations NoVaY.
 */
/**
 * Libellé du groupe des panneaux de scans dans la liste des saisons
 * (V3.0 : les œuvres mangas listent leurs chapitres comme des saisons).
 */
internal const val SCANS_GROUP_LABEL = "Scans"

/** Une « saison » est-elle un panneau de scans (URL …/scan/…) ? */
internal fun isScanSeason(season: AnimeSeason): Boolean =
    season.url.contains("/scan/", ignoreCase = true)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ShowScreen(
    source: AnimeSource,
    anime: AnimeSearchResult,
    onBack: () -> Unit,
    onOpenEpisodes: (AnimeSeason) -> Unit,
    onOpenPlayer: (AnimeSeason, AnimeEpisode, ResolvedStream?) -> Unit = { _, _, _ -> },
    onResume: (WatchHistoryEntry) -> Unit,
    onOpenShow: (AnimeSearchResult) -> Unit = {},
    onOpenScanReader: (AnimeSeason, List<ScanChapter>, Int) -> Unit = { _, _, _ -> },
) {
    val context = LocalContext.current
    val motion = LocalSlateMotion.current
    val bottomChrome = LocalBottomChromePadding.current
    val toastController = LocalNovayToast.current
    var seasons by remember(anime.url) { mutableStateOf<List<AnimeSeason>?>(null) }
    var scanPanels by remember(anime.url) { mutableStateOf<List<ScanPanel>?>(null) }
    var details by remember(anime.url) {
        mutableStateOf(
            AnimeDetails(
                synopsis = anime.synopsis?.takeIf { it.isNotBlank() },
                genres = GenreCatalog.normalizeList(anime.genres),
                posterUrl = ImageUrlSanitizer.sanitizePosterUrl(anime.posterUrl),
            ),
        )
    }
    var error by remember(anime.url) { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }
    var inLibrary by remember { mutableStateOf(false) }
    var resumeEntry by remember { mutableStateOf<WatchHistoryEntry?>(null) }
    var downloadedBySeason by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var knownEpisodeCounts by remember(anime.url) { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var selectedSeason by remember(anime.url) { mutableStateOf<AnimeSeason?>(null) }
    var userSelectedSeason by remember(anime.url) { mutableStateOf(false) }
    var selectedLanguage by remember(anime.url) { mutableStateOf<String?>(null) }
    var userSelectedLanguage by remember(anime.url) { mutableStateOf(false) }
    var collapsedGroups by remember(anime.url) { mutableStateOf<Set<String>>(emptySet()) }
    var synopsisExpanded by remember(anime.url) { mutableStateOf(false) }
    var showGenres by remember(anime.url) { mutableStateOf(GenreCatalog.normalizeList(anime.genres)) }
    var resolvedPosterUrl by remember(anime.url, anime.posterUrl) {
        mutableStateOf(ImageUrlSanitizer.sanitizePosterUrl(anime.posterUrl))
    }
    val effectiveAnime = remember(anime, resolvedPosterUrl, details) {
        anime.copy(
            posterUrl = resolvedPosterUrl ?: details.posterUrl ?: anime.posterUrl,
            genres = showGenres,
            synopsis = details.synopsis ?: anime.synopsis,
        )
    }
    val posterPalette = rememberPosterPalette(
        posterUrl = effectiveAnime.posterUrl,
        syncGlobalDynamicTheme = NovayThemeState.preset == NovayThemePreset.POSTER_ADAPTIVE,
    )
    var showActionsSheet by remember { mutableStateOf(false) }
    var showDetailsSheet by remember { mutableStateOf(false) }

    LaunchedEffect(anime.url, retryKey) {
        error = null
        seasons = null
        try {
            val loaded = source.seasons(anime)
            seasons = loaded
            val ordered = orderByLanguage(loaded)
            if (selectedSeason == null || loaded.none { it.url == selectedSeason?.url }) {
                val resumed = resumeEntry?.let { entry ->
                    loaded.firstOrNull { it.url.trimEnd('/') == entry.seasonUrl.trimEnd('/') }
                }
                selectedSeason = resumed ?: ordered.firstOrNull()?.second?.firstOrNull()
            }
            val loadedDetails = runCatching { source.details(anime) }.getOrNull()
            if (loadedDetails != null) {
                details = loadedDetails
                if (resolvedPosterUrl == null && loadedDetails.posterUrl != null) {
                    resolvedPosterUrl = loadedDetails.posterUrl
                }
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
                loadedDetails?.genres.orEmpty()
                    .ifEmpty { runCatching { source.genres(anime) }.getOrDefault(emptyList()) }
                    .ifEmpty { anime.genres },
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
            val normalizedUrl = anime.url.trimEnd('/')
            val libraryEntries = Library.all(context)
            val libEntry = libraryEntries.firstOrNull { it.url.trimEnd('/') == normalizedUrl }
            val fav = libEntry != null
            val resume = WatchHistory.recent(context)
                .firstOrNull { it.animeUrl.trimEnd('/') == normalizedUrl }
            val dl = Downloads.snapshot(context)
                .filter { it.isCompleted }
                .groupBy { it.meta?.seasonUrl?.trimEnd('/') ?: "" }
                .mapValues { it.value.size }
            inLibrary = fav
            resumeEntry = resume
            downloadedBySeason = dl
            if (libEntry != null && libEntry.lastCounts.isNotEmpty()) {
                knownEpisodeCounts = libEntry.lastCounts.mapKeys { it.key.trimEnd('/') }
            }
        }
    }

    // V3.0 : panneaux de scans (manga) chargés en parallèle des saisons
    // vidéo — même page d'œuvre, appels panneauScan(...).
    LaunchedEffect(anime.url, retryKey) {
        scanPanels = null
        scanPanels = runCatching { source.scanPanels(anime) }.getOrDefault(emptyList())
    }

    val scanSeasons = remember(scanPanels) {
        scanPanels.orEmpty().map {
            AnimeSeason(name = it.name, url = it.url, language = it.language)
        }
    }
    val grouped = remember(seasons, scanSeasons) {
        seasons?.let(::orderByLanguage).orEmpty() +
            if (scanSeasons.isEmpty()) {
                emptyList()
            } else {
                listOf(SCANS_GROUP_LABEL to scanSeasons)
            }
    }

    LaunchedEffect(seasons, resumeEntry, userSelectedSeason) {
        val loaded = seasons ?: return@LaunchedEffect
        val entry = resumeEntry ?: return@LaunchedEffect
        val resumed = loaded.firstOrNull { it.url.trimEnd('/') == entry.seasonUrl.trimEnd('/') }
        if (resumed != null && !userSelectedSeason) {
            selectedSeason = resumed
        }
    }

    LaunchedEffect(grouped, resumeEntry, userSelectedLanguage) {
        if (grouped.isEmpty() || userSelectedLanguage) return@LaunchedEffect
        val resumeLang = resumeEntry?.let { entry ->
            seasons?.firstOrNull { it.url.trimEnd('/') == entry.seasonUrl.trimEnd('/') }
                ?.language?.uppercase()
        }
        selectedLanguage = when {
            resumeLang != null && grouped.any { it.first == resumeLang } -> resumeLang
            grouped.size == 1 -> grouped.first().first
            grouped.any { it.first == "VF" } -> "VF"
            else -> null
        }
    }

    val visibleGroups = remember(grouped, selectedLanguage) {
        if (selectedLanguage == null) {
            grouped
        } else {
            grouped.filter { it.first == selectedLanguage }.ifEmpty { grouped }
        }
    }

    val firstVisibleSeason = remember(visibleGroups, grouped) {
        visibleGroups.firstOrNull()?.second?.firstOrNull()
            ?: grouped.firstOrNull()?.second?.firstOrNull()
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
        toastController.show(
            message = if (inLibrary) "Ajouté à ta bibliothèque" else "Retiré de ta bibliothèque",
            tone = if (inLibrary) NovayToastTone.SUCCESS else NovayToastTone.INFO,
        )
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
        // Les panneaux de scans n'ont pas d'épisodes vidéo à marquer vus.
        if (!isScanSeason(season)) scope.launch {
            val loadedCount = withContext(Dispatchers.IO) {
                runCatching {
                    val eps = source.episodes(season)
                    Library.markSeen(context, season.url, eps.size)
                    eps.size
                }.getOrNull()
            }
            if (loadedCount != null) {
                knownEpisodeCounts = knownEpisodeCounts + (season.url.trimEnd('/') to loadedCount)
            }
        }
    }

    val hasAnyDetails = remember(effectiveAnime, details, showGenres, seasons) {
        !effectiveAnime.synopsis.isNullOrBlank() ||
            showGenres.isNotEmpty() ||
            !details.status.isNullOrBlank() ||
            !details.releaseYear.isNullOrBlank() ||
            !details.format.isNullOrBlank() ||
            !details.alternativeTitle.isNullOrBlank() ||
            !seasons.isNullOrEmpty() ||
            scanSeasons.isNotEmpty()
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
                    if (!seasons.isNullOrEmpty() || scanSeasons.isNotEmpty()) {
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
                seasonList == null || (seasonList.isEmpty() && scanPanels == null) -> "loading"
                seasonList.isEmpty() && scanSeasons.isEmpty() -> "empty"
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
                    "error" -> if (!isDeviceOnline(context)) {
                        OfflineStateView(
                            onRetry = { retryKey++ },
                            modifier = Modifier.padding(bottom = bottomChrome),
                        )
                    } else {
                        ErrorView(
                            message = error ?: "Erreur inconnue",
                            onRetry = { retryKey++ },
                            title = stringResource(R.string.error_catalog_title),
                            icon = NovayIcons.ErrorSeal,
                            modifier = Modifier.padding(bottom = bottomChrome),
                        )
                    }
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
                                        .widthIn(min = 300.dp, max = 460.dp)
                                        .fillMaxHeight()
                                        .testTag("show_seasons_list"),
                                    contentPadding = PaddingValues(bottom = 32.dp),
                                ) {
                                    showOverviewAndSeasons(
                                        anime = effectiveAnime,
                                        details = details,
                                        seasons = seasons.orEmpty(),
                                        grouped = grouped,
                                        visibleGroups = visibleGroups,
                                        selectedLanguage = selectedLanguage,
                                        collapsedGroups = collapsedGroups,
                                        synopsisExpanded = synopsisExpanded,
                                        genres = showGenres,
                                        firstSeason = firstVisibleSeason,
                                        resumeEntry = resumeEntry,
                                        inLibrary = inLibrary,
                                        hasDetails = hasAnyDetails,
                                        downloadedBySeason = downloadedBySeason,
                                        knownEpisodeCounts = knownEpisodeCounts,
                                        activeSeasonUrl = selectedSeason?.url,
                                        parallaxOffsetProvider = headerParallaxProvider,
                                        animateItems = !motion.reduceMotion,
                                        posterAccent = posterPalette?.accent,
                                        onResume = onResume,
                                        onToggleFavorite = ::toggleFavorite,
                                        onOpenDetails = { showDetailsSheet = true },
                                        onToggleSynopsis = { synopsisExpanded = !synopsisExpanded },
                                        onSelectLanguage = { lang ->
                                            userSelectedLanguage = true
                                            selectedLanguage = lang
                                        },
                                        onToggleGroup = { lang ->
                                            collapsedGroups = if (collapsedGroups.contains(lang)) {
                                                collapsedGroups - lang
                                            } else {
                                                collapsedGroups + lang
                                            }
                                        },
                                        onPosterResolved = { resolvedPosterUrl = it },
                                        onSelectSeason = { season ->
                                            userSelectedSeason = true
                                            selectedSeason = season
                                            markSeasonSeen(season)
                                        },
                                        onOpenShow = onOpenShow,
                                    )
                                }
                                VerticalDivider(
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                                val active = selectedSeason ?: firstVisibleSeason
                                if (active != null && isScanSeason(active)) {
                                    ScanChaptersPane(
                                        source = source,
                                        anime = effectiveAnime,
                                        panel = active,
                                        onOpenChapter = { chapters, index ->
                                            onOpenScanReader(active, chapters, chapters[index].id)
                                        },
                                        modifier = Modifier
                                            .weight(0.58f)
                                            .fillMaxHeight(),
                                        showHeader = true,
                                    )
                                } else if (active != null) {
                                    EpisodesPane(
                                        source = source,
                                        animeTitle = anime.title,
                                        season = active,
                                        showSeasonHeader = true,
                                        onLoadedCount = { count ->
                                            knownEpisodeCounts = knownEpisodeCounts +
                                                (active.url.trimEnd('/') to count)
                                        },
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
                                        .widthIn(max = 840.dp)
                                        .testTag("show_seasons_list"),
                                    contentPadding = PaddingValues(bottom = 96.dp + bottomChrome),
                                ) {
                                    showOverviewAndSeasons(
                                        anime = effectiveAnime,
                                        details = details,
                                        seasons = seasons.orEmpty(),
                                        grouped = grouped,
                                        visibleGroups = visibleGroups,
                                        selectedLanguage = selectedLanguage,
                                        collapsedGroups = collapsedGroups,
                                        synopsisExpanded = synopsisExpanded,
                                        genres = showGenres,
                                        firstSeason = firstVisibleSeason,
                                        resumeEntry = resumeEntry,
                                        inLibrary = inLibrary,
                                        hasDetails = hasAnyDetails,
                                        downloadedBySeason = downloadedBySeason,
                                        knownEpisodeCounts = knownEpisodeCounts,
                                        activeSeasonUrl = null,
                                        parallaxOffsetProvider = headerParallaxProvider,
                                        animateItems = !motion.reduceMotion,
                                        posterAccent = posterPalette?.accent,
                                        onResume = onResume,
                                        onToggleFavorite = ::toggleFavorite,
                                        onOpenDetails = { showDetailsSheet = true },
                                        onToggleSynopsis = { synopsisExpanded = !synopsisExpanded },
                                        onSelectLanguage = { lang ->
                                            userSelectedLanguage = true
                                            selectedLanguage = lang
                                        },
                                        onToggleGroup = { lang ->
                                            collapsedGroups = if (collapsedGroups.contains(lang)) {
                                                collapsedGroups - lang
                                            } else {
                                                collapsedGroups + lang
                                            }
                                        },
                                        onPosterResolved = { resolvedPosterUrl = it },
                                        onSelectSeason = { season ->
                                            markSeasonSeen(season)
                                            onOpenEpisodes(season)
                                        },
                                        onOpenShow = onOpenShow,
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
        ShowActionsBottomSheet(
            animeTitle = anime.title,
            inLibrary = inLibrary,
            firstSeason = firstVisibleSeason,
            resumeEntry = resumeEntry,
            hasSeasons = seasons.orEmpty().isNotEmpty(),
            hasDetails = hasAnyDetails,
            onDismiss = { showActionsSheet = false },
            onToggleFavorite = {
                toggleFavorite()
                showActionsSheet = false
            },
            onResumeOrOpenFirst = {
                showActionsSheet = false
                if (resumeEntry != null) {
                    onResume(resumeEntry!!)
                } else {
                    firstVisibleSeason?.let(onOpenEpisodes)
                }
            },
            onMarkAllSeen = {
                showActionsSheet = false
                seasons.orEmpty().forEach(markSeasonSeen)
                toastController.show("Saisons marquées comme vues")
            },
            onOpenDetails = {
                showActionsSheet = false
                showDetailsSheet = true
            },
        )
    }

    if (showDetailsSheet) {
        ShowDetailsBottomSheet(
            anime = effectiveAnime,
            details = details,
            genres = showGenres,
            seasons = seasons.orEmpty(),
            languages = grouped.map { it.first },
            totalDownloads = downloadedBySeason.values.sum(),
            onDismiss = { showDetailsSheet = false },
        )
    }
}
