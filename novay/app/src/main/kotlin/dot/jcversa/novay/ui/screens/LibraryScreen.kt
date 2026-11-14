package dot.jcversa.novay.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.Library
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
import dot.jcversa.novay.ui.components.LocalSlateMotion
import dot.jcversa.novay.ui.components.NovayAppBar
import dot.jcversa.novay.ui.components.Pill
import dot.jcversa.novay.ui.components.PosterGridSkeleton
import dot.jcversa.novay.ui.components.PosterImage
import dot.jcversa.novay.ui.components.RowListSkeleton
import dot.jcversa.novay.ui.components.SlateMotion
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
    onResume: (WatchHistoryEntry) -> Unit
) {
    val context = LocalContext.current
    val motion = LocalSlateMotion.current
    val bottomChrome = LocalBottomChromePadding.current

    var uiState by remember { mutableStateOf(LibraryUiState()) }
    var display by rememberSaveable { mutableStateOf(LibraryDisplay.COMFORTABLE_GRID) }
    var filter by rememberSaveable { mutableStateOf(LibraryFilter.ALL) }
    var sort by rememberSaveable { mutableStateOf(LibrarySort.RECENT) }
    var showDisplaySheet by remember { mutableStateOf(false) }

    var selectionMode by remember { mutableStateOf(false) }
    val selectedUrls = remember { mutableStateListOf<String>() }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshKey) {
        val loaded = withContext(Dispatchers.IO) {
            val lib = Library.all(context)
            val downloads = runCatching { Downloads.snapshot(context) }.getOrDefault(emptyList())
            val history = WatchHistory.recent(context)
            val libUrls = lib.map { it.url.trimEnd('/') }.toSet()
            val latest = history.firstOrNull { it.animeUrl.trimEnd('/') in libUrls } ?: history.firstOrNull()
            val items = lib.map { entry ->
                LibraryItemUi(
                    entry = entry,
                    downloadedCount = downloads.count { it.meta?.animeTitle == entry.title && it.isCompleted },
                    resume = history.firstOrNull { it.animeUrl.trimEnd('/') == entry.url.trimEnd('/') },
                )
            }
            LibraryUiState(
                isLoading = false,
                items = items,
                latestResume = latest,
                hasVf = lib.any { e -> e.languages.any { it.equals("VF", true) } },
                hasVostfr = lib.any { e -> e.languages.any { it.equals("VOSTFR", true) } },
            )
        }
        uiState = loaded
    }

    val filtered = remember(uiState.items, filter, sort) {
        computeLibraryItems(uiState.items, filter, sort)
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
                        icon = Icons.Rounded.Tune,
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
                            LibraryDisplay.COMPACT_GRID -> Icons.Rounded.Apps
                            LibraryDisplay.COMFORTABLE_GRID -> Icons.Rounded.GridView
                            LibraryDisplay.LIST -> Icons.AutoMirrored.Rounded.ViewList
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
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LibraryFilter.entries.forEach { f ->
                        val selected = filter == f
                        FilterChip(
                            selected = selected,
                            onClick = { filter = f },
                            modifier = Modifier.testTag("library_filter_${f.name.lowercase()}"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
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

            val viewKey = when {
                uiState.isLoading -> "loading_${display.name}"
                uiState.items.isEmpty() -> "empty"
                filtered.isEmpty() -> "filter_empty_${filter.name}"
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
                        icon = Icons.Rounded.VideoLibrary,
                        headingText = "Ta bibliothèque t'attend",
                        subtitleText = "Explore le catalogue et touche le cœur sur un anime pour le suivre ici et détecter ses nouveaux épisodes.",
                        modifier = Modifier.padding(bottom = bottomChrome),
                    )
                    stateKey.startsWith("filter_empty_") -> EmptyScreen(
                        icon = Icons.Rounded.VideoLibrary,
                        headingText = "Aucun anime en ${filter.label}",
                        subtitleText = "Change de filtre de langue pour afficher tes autres animés suivis.",
                        modifier = Modifier.padding(bottom = bottomChrome),
                    )
                    else -> {
                        val grid = display != LibraryDisplay.LIST
                        val cellSize = if (display == LibraryDisplay.COMPACT_GRID) 104.dp else 140.dp
                        val itemAnimMod = if (motion.reduceMotion) Modifier else Modifier
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
                                        ResumeCinemaBanner(
                                            entry = hero,
                                            onClick = { onResume(hero) },
                                        )
                                    }
                                }
                                items(
                                    items = filtered,
                                    key = { it.entry.url },
                                    contentType = { display.name },
                                ) { holder ->
                                    val placedMod = if (motion.reduceMotion) itemAnimMod else Modifier.animateItem()
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
                                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                            ResumeCinemaBanner(
                                                entry = hero,
                                                onClick = { onResume(hero) },
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
                        Spacer(Modifier.weight(1f))
                        Button(
                            enabled = selectedUrls.isNotEmpty(),
                            onClick = { deleteSelected() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("library_delete_selected_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Retirer (${selectedUrls.size})")
                        }
                        Spacer(Modifier.width(6.dp))
                        TextButton(
                            onClick = {
                                selectionMode = false
                                selectedUrls.clear()
                            },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
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
                        imageVector = Icons.AutoMirrored.Rounded.Sort,
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
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Actif",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Bannière rapide compacte « Reprendre la séance » sans bordure dure. */
@Composable
private fun ResumeCinemaBanner(
    entry: WatchHistoryEntry,
    onClick: () -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val overlays = NovayTheme.overlays
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(0.5.dp, overlays.glassBorder, shape)
            .semantics(mergeDescendants = true) {}
            .clickable(onClick = onClick)
            .testTag("library_resume_hero_card"),
    ) {
        PosterImage(
            url = entry.posterUrl,
            contentDescription = null,
            modifier = Modifier
                .matchParentSize()
                .blur(16.dp)
                .alpha(0.22f),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        0f to MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.92f),
                        0.65f to MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.78f),
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
                contentDescription = entry.animeTitle,
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
                    text = "REPRENDRE LA SÉANCE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
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
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Box(
                    modifier = Modifier.size(44.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Reprendre ${entry.episodeLabel}",
                    )
                }
            }
        }
    }
}

private fun toggleSelect(list: androidx.compose.runtime.snapshots.SnapshotStateList<String>, url: String) {
    if (list.contains(url)) list.remove(url) else list.add(url)
}
