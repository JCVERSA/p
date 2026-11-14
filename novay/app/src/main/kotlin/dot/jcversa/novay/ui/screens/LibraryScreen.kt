package dot.jcversa.novay.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed as listItemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.ExperimentalFoundationApi
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.Library
import dot.jcversa.novay.data.WatchHistoryEntry
import dot.jcversa.novay.downloads.Downloads
import dot.jcversa.novay.ui.components.AnimeComfortableGridItem
import dot.jcversa.novay.ui.components.AnimeCompactGridItem
import dot.jcversa.novay.ui.components.AnimeListItem
import dot.jcversa.novay.ui.components.AppBarAction
import dot.jcversa.novay.ui.components.AppBarTitle
import dot.jcversa.novay.ui.components.EmptyScreen
import dot.jcversa.novay.ui.components.NovayAppBar
import dot.jcversa.novay.ui.components.Pill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Couplage UI : entrée bibliothèque + compteurs d'écran. */
private data class EntryHolder(
    val entry: dot.jcversa.novay.data.LibraryEntry,
    val downloaded: Int,
    val resume: WatchHistoryEntry?
)

/** Mode d'affichage de la bibliothèque (cycle par le bouton grille). */
private enum class LibraryDisplay { COMPACT_GRID, COMFORTABLE_GRID, LIST }

/** Filtre de langue (contrainte permanente : VF d'abord, VOSTFR ensuite). */
private enum class LibraryFilter { ALL, VF, VOSTFR }

/**
 * Écran Bibliothèque (V2.4, design Mihon) : grille des animés suivis,
 * badges « nouveaux épisodes » (primaire) et « téléchargés » (tertiaire),
 * bouton Continuer, multi-sélection pour retirer, filtres VF/VOSTFR.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    onOpenShow: (AnimeSearchResult) -> Unit,
    onResume: (WatchHistoryEntry) -> Unit
) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf<List<EntryHolder>?>(null) }
    var display by remember { mutableStateOf(LibraryDisplay.COMFORTABLE_GRID) }
    var filter by remember { mutableStateOf(LibraryFilter.ALL) }
    var selectionMode by remember { mutableStateOf(false) }
    val selectedUrls = remember { mutableStateListOf<String>() }
    var refreshKey by remember { mutableIntStateOf(0) }

    // Recharge bibliothèque + compteurs (téléchargés, reprise).
    LaunchedEffect(refreshKey) {
        val lib = withContext(Dispatchers.IO) { Library.all(context) }
        val downloads = withContext(Dispatchers.IO) {
            runCatching { Downloads.snapshot(context) }.getOrDefault(emptyList())
        }
        val history = withContext(Dispatchers.IO) { dot.jcversa.novay.data.WatchHistory.recent(context) }
        entries = lib.map { entry ->
            EntryHolder(
                entry = entry,
                downloaded = downloads.count { it.meta?.animeTitle == entry.title && it.isCompleted },
                resume = history.firstOrNull { it.animeUrl.trimEnd('/') == entry.url.trimEnd('/') }
            )
        }
    }

    // Les retraits en sélection rechargent la liste.
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

    val hasVf = entries.orEmpty().any { it.entry.languages.any { l -> l.equals("VF", true) } }
    val hasVostfr = entries.orEmpty().any { it.entry.languages.any { l -> l.equals("VOSTFR", true) } }

    val filtered = entries.orEmpty().filter { holder ->
        when (filter) {
            LibraryFilter.ALL -> true
            LibraryFilter.VF ->
                holder.entry.languages.any { it.equals("VF", true) }
            LibraryFilter.VOSTFR ->
                holder.entry.languages.any { it.equals("VOSTFR", true) }
        }
    }

    Scaffold(
        bottomBar = {
            if (selectionMode && selectedUrls.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${selectedUrls.size} sélectionné(s)",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { deleteSelected() }) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Retirer")
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            NovayAppBar(
                titleContent = {
                    AppBarTitle(title = "Bibliothèque")
                    val count = entries.orEmpty().size
                    if (count > 0) {
                        Pill(
                            text = "$count",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
                        )
                    }
                },
                actions = {
                    AppBarAction(
                        contentDescription = "Mode d'affichage",
                        icon = when (display) {
                            LibraryDisplay.COMPACT_GRID -> Icons.Rounded.GridView
                            LibraryDisplay.COMFORTABLE_GRID -> Icons.Rounded.GridView
                            LibraryDisplay.LIST -> Icons.Rounded.GridView
                        },
                        onClick = {
                            display = when (display) {
                                LibraryDisplay.COMPACT_GRID -> LibraryDisplay.COMFORTABLE_GRID
                                LibraryDisplay.COMFORTABLE_GRID -> LibraryDisplay.LIST
                                LibraryDisplay.LIST -> LibraryDisplay.COMPACT_GRID
                            }
                        },
                    )
                },
            )

            // Filtres de langue (VF d'abord — contrainte permanente).
            if (hasVf && hasVostfr) {
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
                            label = {
                                Text(
                                    when (f) {
                                        LibraryFilter.ALL -> "Tout"
                                        LibraryFilter.VF -> "VF"
                                        LibraryFilter.VOSTFR -> "VOSTFR"
                                    }
                                )
                            },
                        )
                    }
                }
            }

            when {
                entries == null -> Box(Modifier.fillMaxSize())
                entries.orEmpty().isEmpty() -> EmptyScreen(
                    icon = Icons.Rounded.VideoLibrary,
                    headingText = "Ta bibliothèque est vide",
                    subtitleText = "Ouvre un anime puis touche le cœur pour le suivre — les nouveaux épisodes s'afficheront ici.",
                )
                filtered.isEmpty() -> EmptyScreen(
                    icon = Icons.Rounded.VideoLibrary,
                    headingText = "Rien dans ce filtre",
                    subtitleText = "Aucun animé suivi ne correspond à cette langue.",
                )
                else -> {
                    val grid = display != LibraryDisplay.LIST
                    val cellSize = if (display == LibraryDisplay.COMPACT_GRID) 96.dp else 128.dp
                    if (grid) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(cellSize),
                            contentPadding = PaddingValues(
                                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            itemsIndexed(filtered, key = { _, h -> h.entry.url }) { _, holder ->
                                if (display == LibraryDisplay.COMPACT_GRID) {
                                    AnimeCompactGridItem(
                                        title = holder.entry.title,
                                        posterUrl = holder.entry.posterUrl,
                                        isSelected = selectionMode && selectedUrls.contains(holder.entry.url),
                                        newEpisodes = holder.entry.newEpisodes,
                                        downloadedCount = holder.downloaded,
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
                                        isSelected = selectionMode && selectedUrls.contains(holder.entry.url),
                                        newEpisodes = holder.entry.newEpisodes,
                                        downloadedCount = holder.downloaded,
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
                            contentPadding = PaddingValues(bottom = 24.dp),
                        ) {
                            listItemsIndexed(filtered, key = { _, h -> h.entry.url }) { _, holder ->
                                AnimeListItem(
                                    title = holder.entry.title,
                                    posterUrl = holder.entry.posterUrl,
                                    isSelected = selectionMode && selectedUrls.contains(holder.entry.url),
                                    newEpisodes = holder.entry.newEpisodes,
                                    downloadedCount = holder.downloaded,
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

    // Confirmer le retrait (appui long → sélection → Retirer).
    // (Barre d'action en bas ; pas de dialog supplémentaire — Mihon agit direct.)
}

private fun toggleSelect(list: androidx.compose.runtime.snapshots.SnapshotStateList<String>, url: String) {
    if (list.contains(url)) list.remove(url) else list.add(url)
}
