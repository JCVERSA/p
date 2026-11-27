package dot.jcversa.novay.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import android.os.Build
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dot.jcversa.novay.R
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.GenreCatalog
import dot.jcversa.novay.data.Library
import dot.jcversa.novay.data.LibraryCollection
import dot.jcversa.novay.data.WatchHistory
import dot.jcversa.novay.data.WatchHistoryEntry
import dot.jcversa.novay.downloads.Downloads
import dot.jcversa.novay.ui.LocalBottomChromePadding
import dot.jcversa.novay.ui.components.AnimeComfortableGridItem
import dot.jcversa.novay.ui.components.AnimeCompactGridItem
import dot.jcversa.novay.ui.components.AnimeListItem
import dot.jcversa.novay.ui.components.AppBarAction
import dot.jcversa.novay.ui.components.AppBarTitle
import dot.jcversa.novay.ui.components.EmptyScreen
import dot.jcversa.novay.ui.components.GenreFilterChipBar
import dot.jcversa.novay.ui.components.LocalNovayToast
import dot.jcversa.novay.ui.components.LocalSlateMotion
import dot.jcversa.novay.ui.components.NovayAppBar
import dot.jcversa.novay.ui.components.NovayBrandSeal
import dot.jcversa.novay.ui.components.NovayIcons
import dot.jcversa.novay.ui.components.NovayToastTone
import dot.jcversa.novay.ui.components.Pill
import dot.jcversa.novay.ui.components.PosterGridSkeleton
import dot.jcversa.novay.ui.components.PosterImage
import dot.jcversa.novay.ui.components.RowListSkeleton
import dot.jcversa.novay.ui.components.SlateMotion
import dot.jcversa.novay.ui.components.novayCinemaGlass
import dot.jcversa.novay.ui.components.novaySharedPoster
import dot.jcversa.novay.ui.state.LibraryDisplay
import dot.jcversa.novay.ui.state.LibraryFilter
import dot.jcversa.novay.ui.state.LibraryItemUi
import dot.jcversa.novay.ui.state.LibrarySort
import dot.jcversa.novay.ui.state.LibraryUiState
import dot.jcversa.novay.ui.state.computeLibraryItems
import dot.jcversa.novay.ui.theme.NovayTheme
import dot.jcversa.novay.ui.theme.header
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Écran Bibliothèque — Now in Android UDF + Aniyomi Badges & Sheet :
 *  - état @Immutable mémoïsé (filtrage/tri hors recomposition) ;
 *  - barre supérieure repliable connectée via nestedScroll ;
 *  - feuille modale d'affichage & tri style Aniyomi ;
 *  - transitions AnimatedContent (< 220ms) et animateItem() sur grilles/listes.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    onOpenShow: (AnimeSearchResult) -> Unit,
    onResume: (WatchHistoryEntry) -> Unit,
    showBrandSeal: Boolean = false,
    onExploreCatalog: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val motion = LocalSlateMotion.current
    val toast = LocalNovayToast.current
    val bottomChrome = LocalBottomChromePadding.current

    var uiState by remember { mutableStateOf(LibraryUiState()) }
    var display by rememberSaveable { mutableStateOf(LibraryDisplay.COMFORTABLE_GRID) }
    var filter by rememberSaveable { mutableStateOf(LibraryFilter.ALL) }
    var selectedGenre by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCollectionId by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by rememberSaveable { mutableStateOf(LibrarySort.RECENT) }
    var showDisplaySheet by remember { mutableStateOf(false) }
    var showCreateCollectionDialog by remember { mutableStateOf(false) }
    var showAssignCollectionSheet by remember { mutableStateOf(false) }
    var newCollectionName by remember { mutableStateOf("") }

    var selectionMode by remember { mutableStateOf(false) }
    val selectedUrls = remember { mutableStateListOf<String>() }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshKey) {
        val loaded = withContext(Dispatchers.IO) {
            val lib = Library.all(context)
            val cols = Library.collections(context)
            val downloads = runCatching { Downloads.snapshot(context) }.getOrDefault(emptyList())
            val history = WatchHistory.recent(context)
            val libUrls = lib.map { it.url.trimEnd('/') }.toSet()
            val latest = history.firstOrNull { it.animeUrl.trimEnd('/') in libUrls } ?: history.firstOrNull()
            val completedByTitle = downloads.asSequence()
                .filter { it.isCompleted }
                .mapNotNull { it.meta?.animeTitle }
                .groupingBy { it }
                .eachCount()
            val resumeByUrl = buildMap(history.size) {
                for (h in history) {
                    putIfAbsent(h.animeUrl.trimEnd('/'), h)
                }
            }
            val libPosters = lib.mapNotNull { e ->
                e.posterUrl?.let { e.url.trimEnd('/') to it }
            }.toMap()
            val histPosters = history.mapNotNull { h ->
                h.posterUrl?.let { h.animeUrl.trimEnd('/') to it }
            }.toMap()
            val items = lib.map { entry ->
                val normalized = entry.url.trimEnd('/')
                val repairedPoster = entry.posterUrl ?: histPosters[normalized]
                val effectiveEntry = if (repairedPoster != null && entry.posterUrl == null) {
                    entry.copy(posterUrl = repairedPoster)
                } else {
                    entry
                }
                LibraryItemUi(
                    entry = effectiveEntry,
                    downloadedCount = completedByTitle[entry.title] ?: 0,
                    resume = resumeByUrl[normalized],
                )
            }
            val effectiveLatest = latest?.let { h ->
                val libPoster = libPosters[h.animeUrl.trimEnd('/')]
                if (h.posterUrl == null && libPoster != null) h.copy(posterUrl = libPoster) else h
            }
            LibraryUiState(
                isLoading = false,
                items = items,
                latestResume = effectiveLatest,
                hasVf = lib.any { e -> e.languages.any { it.equals("VF", true) } },
                hasVostfr = lib.any { e -> e.languages.any { it.equals("VOSTFR", true) } },
                availableGenres = GenreCatalog.mergeGenresForBar(lib.flatMap { it.genres }),
                collections = cols,
            )
        }
        uiState = loaded
    }

    val selectedCollection = remember(uiState.collections, selectedCollectionId) {
        uiState.collections.firstOrNull { it.id == selectedCollectionId }
    }
    val filtered = remember(uiState.items, filter, sort, selectedGenre, selectedCollection) {
        computeLibraryItems(uiState.items, filter, sort, selectedGenre, selectedCollection)
    }

    fun deleteSelected() {
        val urls = selectedUrls.toList()
        val current = Library.all(context)
        var next = current
        for (u in urls) next = Library.remove(next, u)
        Library.save(context, next)
        selectionMode = false
        selectedUrls.clear()
        refreshKey++
        if (urls.isNotEmpty()) {
            toast.show(
                message = "${urls.size} animé${if (urls.size > 1) "s" else ""} retiré${if (urls.size > 1) "s" else ""}",
                tone = NovayToastTone.INFO,
            )
        }
    }

    BackHandler(enabled = selectionMode) {
        selectionMode = false
        selectedUrls.clear()
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
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
                            modifier = Modifier.testTag("library_brand_seal"),
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    AppBarTitle(title = "Bibliothèque")
                    val count = uiState.items.size
                    if (count > 0) {
                        Pill(
                            text = "$count",
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                actions = {
                    AppBarAction(
                        contentDescription = "Affichage et tri",
                        icon = NovayIcons.QualitySliders,
                        modifier = Modifier.testTag("library_display_sheet_button"),
                        onClick = { showDisplaySheet = true },
                    )
                    AppBarAction(
                        contentDescription = when (display) {
                            LibraryDisplay.COMFORTABLE_GRID -> "Passer en liste"
                            LibraryDisplay.LIST -> "Passer en grille compacte"
                            LibraryDisplay.COMPACT_GRID -> "Passer en grille confortable"
                        },
                        icon = when (display) {
                            LibraryDisplay.COMPACT_GRID -> NovayIcons.GridCompact
                            LibraryDisplay.COMFORTABLE_GRID -> NovayIcons.GridComfortable
                            LibraryDisplay.LIST -> NovayIcons.ViewList
                        },
                        modifier = Modifier.testTag("library_display_toggle"),
                        onClick = {
                            display = when (display) {
                                LibraryDisplay.COMFORTABLE_GRID -> LibraryDisplay.LIST
                                LibraryDisplay.LIST -> LibraryDisplay.COMPACT_GRID
                                LibraryDisplay.COMPACT_GRID -> LibraryDisplay.COMFORTABLE_GRID
                            }
                        },
                    )
                },
            )

            if (uiState.hasVf && uiState.hasVostfr) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LibraryFilter.entries.forEach { f ->
                        val selected = filter == f
                        FilterChip(
                            selected = selected,
                            onClick = { filter = f },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("library_filter_${f.name.lowercase()}"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = NovayTheme.overlays.crimsonOnContainer,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = MaterialTheme.colorScheme.outlineVariant,
                                selectedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                            label = { Text(f.label) },
                        )
                    }
                }
            }

            if (uiState.items.isNotEmpty() && uiState.collections.isNotEmpty()) {
                LibraryCollectionsBar(
                    collections = uiState.collections,
                    items = uiState.items,
                    selectedCollectionId = selectedCollectionId,
                    onSelectCollection = { selectedCollectionId = it },
                    onCreateCollection = { showCreateCollectionDialog = true },
                )
            }

            if (uiState.items.isNotEmpty()) {
                GenreFilterChipBar(
                    genres = uiState.availableGenres.ifEmpty { GenreCatalog.CORE_GENRES },
                    selectedGenre = selectedGenre,
                    onSelectGenre = { selectedGenre = it },
                    allLabel = stringResource(R.string.filter_all),
                    testTagPrefix = "library_genre_chip",
                )
            }

            val viewKey = when {
                uiState.isLoading -> "loading_${display.name}"
                uiState.items.isEmpty() -> "empty"
                filtered.isEmpty() -> "filter_empty_${filter.name}_${selectedGenre.orEmpty()}_${selectedCollectionId.orEmpty()}"
                else -> "content_${display.name}"
            }

            AnimatedContent(
                targetState = viewKey,
                transitionSpec = { motion.stateCrossfade() },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                label = "library_body_state",
            ) { stateKey ->
                when {
                    stateKey.startsWith("loading_") -> {
                        if (display == LibraryDisplay.LIST) {
                            RowListSkeleton(modifier = Modifier.padding(bottom = bottomChrome))
                        } else {
                            PosterGridSkeleton(
                                minCellSize = if (display == LibraryDisplay.COMPACT_GRID) 104.dp else 140.dp,
                                compact = display == LibraryDisplay.COMPACT_GRID,
                                modifier = Modifier.padding(bottom = bottomChrome),
                            )
                        }
                    }
                    stateKey == "empty" -> EmptyScreen(
                        icon = NovayIcons.LibraryShelf,
                        lottieRes = R.raw.lottie_bookmark,
                        headingText = "Ta bibliothèque t'attend",
                        subtitleText = "Explore le catalogue et touche le cœur sur un anime pour le suivre ici et détecter ses nouveaux épisodes.",
                        actionLabel = if (onExploreCatalog != null) stringResource(R.string.action_explore_catalog) else null,
                        onAction = onExploreCatalog,
                        actionTestTag = "library_empty_browse_button",
                        modifier = Modifier.padding(bottom = bottomChrome),
                    )
                    stateKey.startsWith("filter_empty_") -> {
                        val activeGenre = selectedGenre
                        val activeCollection = selectedCollection
                        val emptyHeading = when {
                            activeCollection != null && activeCollection.animeUrls.isEmpty() ->
                                "Collection « ${activeCollection.name} » vide"
                            activeCollection != null ->
                                "Aucun animé dans « ${activeCollection.name} »"
                            activeGenre != null && filter != LibraryFilter.ALL ->
                                "Aucun anime $activeGenre en ${filter.label}"
                            activeGenre != null ->
                                "Aucun anime $activeGenre suivi"
                            else ->
                                "Aucun anime en ${filter.label}"
                        }
                        val emptySubtitle = when {
                            activeCollection != null && activeCollection.animeUrls.isEmpty() ->
                                "Appuie longuement sur un animé de ta bibliothèque pour l'ajouter à cette collection."
                            activeGenre != null ->
                                "Ouvre une fiche d'animé pour actualiser ses genres ou réinitialise les filtres pour tout afficher."
                            else ->
                                "Change de filtre pour afficher tes autres animés suivis."
                        }
                        EmptyScreen(
                            icon = NovayIcons.LibraryShelf,
                            lottieRes = R.raw.lottie_bookmark,
                            headingText = emptyHeading,
                            subtitleText = emptySubtitle,
                            actionLabel = stringResource(R.string.action_reset_filters),
                            onAction = {
                                filter = LibraryFilter.ALL
                                selectedGenre = null
                                selectedCollectionId = null
                            },
                            actionTestTag = "library_reset_filters_button",
                            modifier = Modifier.padding(bottom = bottomChrome),
                        )
                    }
                    else -> {
                        val grid = display != LibraryDisplay.LIST
                        val cellSize = if (display == LibraryDisplay.COMPACT_GRID) 104.dp else 140.dp
                        val selectionBarClearance = if (selectionMode) 68.dp else 0.dp
                        if (grid) {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(cellSize),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 8.dp,
                                    bottom = 24.dp + bottomChrome + selectionBarClearance,
                                ),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                uiState.latestResume?.let { hero ->
                                    item(
                                        key = "hero_resume",
                                        contentType = "resume_banner",
                                        span = { GridItemSpan(maxLineSpan) },
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            ResumeCinemaBanner(
                                                entry = hero,
                                                onClick = { onResume(hero) },
                                                modifier = Modifier.widthIn(max = 760.dp),
                                            )
                                        }
                                    }
                                }
                                items(
                                    items = filtered,
                                    key = { it.entry.url },
                                    contentType = { display.name },
                                ) { holder ->
                                    val placedMod = if (motion.reduceMotion) Modifier else Modifier.animateItem()
                                    if (display == LibraryDisplay.COMPACT_GRID) {
                                        AnimeCompactGridItem(
                                            title = holder.entry.title,
                                            posterUrl = holder.entry.posterUrl,
                                            modifier = placedMod,
                                            animeUrl = holder.entry.url,
                                            isSelected = selectionMode && selectedUrls.contains(holder.entry.url),
                                            newEpisodes = holder.entry.newEpisodes,
                                            downloadedCount = holder.downloadedCount,
                                            onClickContinue = holder.resume?.let { r -> { onResume(r) } },
                                            onClick = {
                                                if (selectionMode) {
                                                    toggleSelect(selectedUrls, holder.entry.url)
                                                } else {
                                                    onOpenShow(holder.entry.toSearchResult())
                                                }
                                            },
                                            onLongClick = {
                                                if (!selectionMode) {
                                                    selectionMode = true
                                                    selectedUrls.clear()
                                                }
                                                toggleSelect(selectedUrls, holder.entry.url)
                                            },
                                        )
                                    } else {
                                        AnimeComfortableGridItem(
                                            title = holder.entry.title,
                                            posterUrl = holder.entry.posterUrl,
                                            modifier = placedMod,
                                            animeUrl = holder.entry.url,
                                            isSelected = selectionMode && selectedUrls.contains(holder.entry.url),
                                            newEpisodes = holder.entry.newEpisodes,
                                            downloadedCount = holder.downloadedCount,
                                            onClickContinue = holder.resume?.let { r -> { onResume(r) } },
                                            onClick = {
                                                if (selectionMode) {
                                                    toggleSelect(selectedUrls, holder.entry.url)
                                                } else {
                                                    onOpenShow(holder.entry.toSearchResult())
                                                }
                                            },
                                            onLongClick = {
                                                if (!selectionMode) {
                                                    selectionMode = true
                                                    selectedUrls.clear()
                                                }
                                                toggleSelect(selectedUrls, holder.entry.url)
                                            },
                                        )
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    top = 8.dp,
                                    bottom = 24.dp + bottomChrome + selectionBarClearance,
                                ),
                            ) {
                                uiState.latestResume?.let { hero ->
                                    item(
                                        key = "hero_resume_list",
                                        contentType = "resume_banner",
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            ResumeCinemaBanner(
                                                entry = hero,
                                                onClick = { onResume(hero) },
                                                modifier = Modifier.widthIn(max = 760.dp),
                                            )
                                        }
                                    }
                                }
                                items(
                                    items = filtered,
                                    key = { it.entry.url },
                                    contentType = { "list_item" },
                                ) { holder ->
                                    val placedMod = if (motion.reduceMotion) Modifier else Modifier.animateItem()
                                    AnimeListItem(
                                        title = holder.entry.title,
                                        posterUrl = holder.entry.posterUrl,
                                        modifier = placedMod,
                                        animeUrl = holder.entry.url,
                                        isSelected = selectionMode && selectedUrls.contains(holder.entry.url),
                                        newEpisodes = holder.entry.newEpisodes,
                                        downloadedCount = holder.downloadedCount,
                                        subtitle = holder.resume?.let { "${it.seasonName} · ${it.episodeLabel}" },
                                        onClickContinue = holder.resume?.let { r -> { onResume(r) } },
                                        onClick = {
                                            if (selectionMode) {
                                                toggleSelect(selectedUrls, holder.entry.url)
                                            } else {
                                                onOpenShow(holder.entry.toSearchResult())
                                            }
                                        },
                                        onLongClick = {
                                            if (!selectionMode) {
                                                selectionMode = true
                                                selectedUrls.clear()
                                            }
                                            toggleSelect(selectedUrls, holder.entry.url)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = selectionMode,
            enter = if (motion.reduceMotion) fadeIn(motion.transitionSpec(0)) else (
                slideInVertically(
                    animationSpec = androidx.compose.animation.core.tween(220, easing = SlateMotion.EaseOutExpo)
                ) { it } + fadeIn(motion.enterSpec(180))
            ),
            exit = if (motion.reduceMotion) fadeOut(motion.transitionSpec(0)) else (
                slideOutVertically(
                    animationSpec = androidx.compose.animation.core.tween(140, easing = SlateMotion.FastOutExit)
                ) { it } + fadeOut(motion.exitSpec(120))
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = bottomChrome),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (bottomChrome == 0.dp) Modifier.navigationBarsPadding() else Modifier)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {
                                selectedUrls.clear()
                                filtered.forEach { selectedUrls.add(it.entry.url) }
                            },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Text("Tout")
                        }
                        OutlinedButton(
                            enabled = selectedUrls.isNotEmpty(),
                            onClick = { showAssignCollectionSheet = true },
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("library_assign_collection_button"),
                        ) {
                            Text(
                                text = stringResource(R.string.library_collection_add_action),
                                maxLines = 1,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        val overlays = NovayTheme.overlays
                        Button(
                            enabled = selectedUrls.isNotEmpty(),
                            onClick = { deleteSelected() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = overlays.ctaContainer,
                                contentColor = overlays.onCtaContainer,
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("library_delete_selected_button"),
                        ) {
                            Icon(
                                imageVector = NovayIcons.DeleteTrash,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Retirer (${selectedUrls.size})")
                        }
                        Spacer(Modifier.width(4.dp))
                        TextButton(
                            onClick = {
                                selectionMode = false
                                selectedUrls.clear()
                            },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Icon(
                                imageVector = NovayIcons.Close,
                                contentDescription = "Fermer la sélection",
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDisplaySheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showDisplaySheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Affichage",
                    style = MaterialTheme.typography.header,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LibraryDisplay.entries.forEach { mode ->
                        val active = display == mode
                        FilterChip(
                            selected = active,
                            onClick = { display = mode },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                            label = {
                                Text(
                                    text = when (mode) {
                                        LibraryDisplay.COMFORTABLE_GRID -> "Confort"
                                        LibraryDisplay.COMPACT_GRID -> "Compact"
                                        LibraryDisplay.LIST -> "Liste"
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = NovayIcons.Sort,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Trier par",
                        style = MaterialTheme.typography.header,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                LibrarySort.entries.forEach { option ->
                    val active = sort == option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(
                                if (active) {
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                }
                            )
                            .clickable {
                                sort = option
                                showDisplaySheet = false
                            }
                            .minimumInteractiveComponentSize()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (active) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.weight(1f),
                        )
                        if (active) {
                            Icon(
                                imageVector = NovayIcons.Check,
                                contentDescription = "Actif",
                                tint = NovayTheme.overlays.crimsonOnContainer,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAssignCollectionSheet) {
        val assignSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showAssignCollectionSheet = false },
            sheetState = assignSheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.library_collections_label),
                        style = MaterialTheme.typography.header,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            showAssignCollectionSheet = false
                            showCreateCollectionDialog = true
                        },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Text("+ Nouvelle")
                    }
                }
                uiState.collections.forEach { collection ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .clickable {
                                Library.addManyToCollection(
                                    context = context,
                                    collectionId = collection.id,
                                    animeUrls = selectedUrls.toList(),
                                )
                                showAssignCollectionSheet = false
                                selectionMode = false
                                val addedCount = selectedUrls.size
                                selectedUrls.clear()
                                refreshKey++
                                toast.show(
                                    message = "$addedCount animé${if (addedCount > 1) "s" else ""} ajouté${if (addedCount > 1) "s" else ""} à « ${collection.name} »",
                                    tone = NovayToastTone.SUCCESS,
                                )
                            }
                            .minimumInteractiveComponentSize()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("library_assign_collection_${collection.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CollectionCoverMosaic(
                            posterUrls = resolveCollectionPosters(collection, uiState.items),
                            size = 38.dp,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = collection.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${collection.animeUrls.size} animé${if (collection.animeUrls.size > 1) "s" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (collection.id !in setOf("coups_de_coeur", "a_voir")) {
                            IconButton(
                                onClick = {
                                    Library.removeCollection(context, collection.id)
                                    if (selectedCollectionId == collection.id) {
                                        selectedCollectionId = null
                                    }
                                    refreshKey++
                                    toast.show("Collection « ${collection.name} » supprimée", NovayToastTone.INFO)
                                },
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            ) {
                                Icon(
                                    imageVector = NovayIcons.DeleteTrash,
                                    contentDescription = "Supprimer ${collection.name}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateCollectionDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreateCollectionDialog = false
                newCollectionName = ""
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = {
                Text(
                    text = stringResource(R.string.library_collection_new),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                OutlinedTextField(
                    value = newCollectionName,
                    onValueChange = { newCollectionName = it },
                    singleLine = true,
                    placeholder = { Text("Ex. Chef-d'œuvre, Week-end…") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("library_collection_name_input"),
                )
            },
            confirmButton = {
                Button(
                    enabled = newCollectionName.trim().isNotEmpty(),
                    onClick = {
                        val created = Library.createCollection(context, newCollectionName)
                        if (created != null) {
                            if (selectionMode && selectedUrls.isNotEmpty()) {
                                Library.addManyToCollection(context, created.id, selectedUrls.toList())
                                selectionMode = false
                                selectedUrls.clear()
                            } else {
                                selectedCollectionId = created.id
                            }
                            refreshKey++
                            toast.show("Collection « ${created.name} » créée", NovayToastTone.SUCCESS)
                        }
                        showCreateCollectionDialog = false
                        newCollectionName = ""
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("library_collection_confirm_create"),
                ) {
                    Text("Créer")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCreateCollectionDialog = false
                        newCollectionName = ""
                    },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Text("Annuler")
                }
            },
        )
    }
}

/** Bannière rapide compacte « Reprendre la séance » sans bordure dure. */
@Composable
private fun ResumeCinemaBanner(
    entry: WatchHistoryEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.large
    val overlays = NovayTheme.overlays
    val motion = LocalSlateMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !motion.reduceMotion) 0.985f else 1f,
        animationSpec = motion.effectFloatSpec(),
        label = "resume_banner_scale",
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
            .border(0.5.dp, overlays.glassBorder, shape)
            .semantics(mergeDescendants = true) {}
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClickLabel = "Reprendre ${entry.animeTitle} ${entry.episodeLabel}",
                onClick = onClick,
            )
            .testTag("library_resume_hero_card"),
    ) {
        PosterImage(
            url = entry.posterUrl,
            contentDescription = null,
            animeUrl = entry.animeUrl,
            animeTitle = entry.animeTitle,
            decodeWidthDp = 72.dp,
            decodeHeightDp = 108.dp,
            modifier = Modifier
                .matchParentSize()
                .then(if (supportsBlur) Modifier.blur(16.dp) else Modifier)
                .alpha(if (supportsBlur) 0.22f else 0.14f),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        0f to MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = if (supportsBlur) 0.92f else 0.96f),
                        0.65f to MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = if (supportsBlur) 0.78f else 0.88f),
                        1f to MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PosterImage(
                url = entry.posterUrl,
                contentDescription = null,
                animeUrl = entry.animeUrl,
                animeTitle = entry.animeTitle,
                decodeWidthDp = 52.dp,
                decodeHeightDp = 76.dp,
                modifier = Modifier
                    .size(width = 52.dp, height = 76.dp)
                    .novaySharedPoster(entry.animeUrl)
                    .clip(MaterialTheme.shapes.small),
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Reprendre la séance",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
                    color = overlays.crimsonOnContainer,
                )
                Text(
                    text = entry.animeTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${entry.seasonName} · ${entry.episodeLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.durationMs > 0 && entry.positionMs > 0) {
                    Spacer(Modifier.height(2.dp))
                    LinearProgressIndicator(
                        progress = { (entry.positionMs.toFloat() / entry.durationMs).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(MaterialTheme.shapes.extraSmall),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = overlays.ctaContainer,
                contentColor = overlays.onCtaContainer,
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Box(
                    modifier = Modifier.size(44.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = NovayIcons.Play,
                        contentDescription = null,
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryCollectionsBar(
    collections: List<LibraryCollection>,
    items: List<LibraryItemUi>,
    selectedCollectionId: String?,
    onSelectCollection: (String?) -> Unit,
    onCreateCollection: () -> Unit,
) {
    val overlays = NovayTheme.overlays
    val motion = LocalSlateMotion.current

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("library_collections_row"),
    ) {
        item(key = "col_all") {
            val selected = selectedCollectionId == null
            val borderColor by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.primary else overlays.glassBorder,
                animationSpec = motion.colorSpec(180),
                label = "col_all_border",
            )
            val fill by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                animationSpec = motion.colorSpec(180),
                label = "col_all_bg",
            )
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = fill,
                border = BorderStroke(0.8.dp, borderColor),
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSelectCollection(null) }
                    .minimumInteractiveComponentSize()
                    .testTag("library_collection_all"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.library_collection_all),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) overlays.crimsonOnContainer else MaterialTheme.colorScheme.onSurface,
                    )
                    Pill(
                        text = "${items.size}",
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        items(collections, key = { it.id }) { collection ->
            val selected = selectedCollectionId == collection.id
            val posters = remember(collection.animeUrls, items) {
                resolveCollectionPosters(collection, items)
            }
            val borderColor by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.primary else overlays.glassBorder,
                animationSpec = motion.colorSpec(180),
                label = "col_border_${collection.id}",
            )
            val fill by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                animationSpec = motion.colorSpec(180),
                label = "col_bg_${collection.id}",
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .novayCinemaGlass(
                        shape = RoundedCornerShape(14.dp),
                        tint = fill,
                        borderColor = borderColor,
                        showBorder = true,
                    )
                    .clickable {
                        onSelectCollection(if (selected) null else collection.id)
                    }
                    .minimumInteractiveComponentSize()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("library_collection_chip_${collection.id}"),
            ) {
                CollectionCoverMosaic(posterUrls = posters, size = 34.dp)
                Column {
                    Text(
                        text = collection.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) overlays.crimsonOnContainer else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${collection.animeUrls.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item(key = "col_new") {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(0.6.dp, overlays.glassBorder),
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onCreateCollection)
                    .minimumInteractiveComponentSize()
                    .testTag("library_collection_new_button"),
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "+ Nouvelle",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionCoverMosaic(
    posterUrls: List<String>,
    size: androidx.compose.ui.unit.Dp,
) {
    val shape = RoundedCornerShape(8.dp)
    if (posterUrls.isEmpty()) {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(size),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = NovayIcons.LibraryShelf,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(size * 0.5f),
                )
            }
        }
    } else if (posterUrls.size == 1) {
        PosterImage(
            url = posterUrls.first(),
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(shape),
        )
    } else {
        Column(
            modifier = Modifier
                .size(size)
                .clip(shape),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                PosterImage(
                    url = posterUrls.getOrNull(0),
                    contentDescription = null,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                )
                PosterImage(
                    url = posterUrls.getOrNull(1) ?: posterUrls.getOrNull(0),
                    contentDescription = null,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                PosterImage(
                    url = posterUrls.getOrNull(2) ?: posterUrls.getOrNull(1) ?: posterUrls.getOrNull(0),
                    contentDescription = null,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                )
                PosterImage(
                    url = posterUrls.getOrNull(3) ?: posterUrls.getOrNull(0),
                    contentDescription = null,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                )
            }
        }
    }
}

private fun resolveCollectionPosters(
    collection: LibraryCollection,
    items: List<LibraryItemUi>,
): List<String> {
    if (collection.animeUrls.isEmpty() || items.isEmpty()) return emptyList()
    val posterByUrl = items.associate { it.entry.url.trimEnd('/') to it.entry.posterUrl }
    return collection.animeUrls.asSequence()
        .mapNotNull { posterByUrl[it.trimEnd('/')] }
        .take(4)
        .toList()
}

private fun toggleSelect(list: androidx.compose.runtime.snapshots.SnapshotStateList<String>, url: String) {
    if (list.contains(url)) list.remove(url) else list.add(url)
}
