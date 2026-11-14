package dot.jcversa.novay.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.Library
import dot.jcversa.novay.data.LibraryEntry
import dot.jcversa.novay.data.WatchHistoryEntry
import dot.jcversa.novay.ui.components.AppBarAction
import dot.jcversa.novay.ui.components.AppBarTitle
import dot.jcversa.novay.ui.components.EmptyScreen
import dot.jcversa.novay.ui.components.LoadingView
import dot.jcversa.novay.ui.components.NovayAppBar
import dot.jcversa.novay.ui.components.NovayBadge
import dot.jcversa.novay.ui.components.Pill
import dot.jcversa.novay.ui.components.PosterImage
import dot.jcversa.novay.ui.components.SwiftSlateSpinner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Écran Mises à jour (V2.4, design Mihon) : animés suivis ayant de
 * nouveaux épisodes. « Vérifier » recompte les épisodes de chaque saison
 * de chaque favori (réseau) et met à jour les badges — les nouveaux
 * épisodes se consomment en ouvrant la saison.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(
    source: dot.jcversa.novay.data.AnimeSource,
    onOpenShow: (AnimeSearchResult) -> Unit,
    onResume: (WatchHistoryEntry) -> Unit
) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf<List<LibraryEntry>?>(null) }
    var checking by remember { mutableStateOf(false) }
    var checkError by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(refreshKey) {
        entries = withContext(Dispatchers.IO) { Library.all(context) }
    }

    /** Recompte les épisodes de chaque favori, saison par saison. */
    fun checkAll() {
        if (checking) return
        checkError = null
        val favorites = Library.all(context)
        if (favorites.isEmpty()) {
            refreshKey++
            return
        }
        checking = true
        scope.launch {
            try {
                for (entry in favorites) {
                    val counts = mutableMapOf<String, Int>()
                    val seasons = runCatching {
                        source.seasons(entry.toSearchResult())
                    }.getOrDefault(emptyList())
                    for (season in seasons) {
                        val eps = runCatching {
                            source.episodes(season)
                        }.getOrDefault(emptyList())
                        counts[season.url.trimEnd('/')] = eps.size
                    }
                    Library.recordCounts(context, entry.url, counts)
                }
            } catch (e: Exception) {
                checkError = e.message ?: "Vérification impossible"
            } finally {
                checking = false
                refreshKey++
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        NovayAppBar(
            titleContent = {
                AppBarTitle(title = "Mises à jour")
                val news = entries.orEmpty().sumOf { it.newEpisodes }
                if (news > 0) {
                    Pill(
                        text = "$news",
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            },
            actions = {
                if (checking) {
                    SwiftSlateSpinner(size = 20.dp, modifier = Modifier.padding(end = 16.dp))
                } else {
                    AppBarAction(
                        contentDescription = "Vérifier les nouveaux épisodes",
                        icon = Icons.Rounded.Refresh,
                        onClick = { checkAll() },
                    )
                }
            },
        )

        when {
            checking && (entries == null || entries.orEmpty().isEmpty()) ->
                LoadingView(message = "Vérification des animés suivis…")
            entries == null -> Box(Modifier.fillMaxSize())
            entries.orEmpty().isEmpty() -> EmptyScreen(
                icon = Icons.Rounded.NewReleases,
                headingText = "Aucun animé suivi",
                subtitleText = "Ajoute des animés en bibliothèque (le cœur sur leur fiche) puis vérifie ici les nouveaux épisodes.",
            )
            else -> {
                val withNews = entries.orEmpty().filter { it.newEpisodes > 0 }
                    .sortedByDescending { it.addedAt }
                if (withNews.isEmpty()) {
                    EmptyScreen(
                        icon = Icons.Rounded.NewReleases,
                        headingText = "Tout est à jour",
                        subtitleText = if (checkError != null) {
                            "Dernière vérification incomplète : ${checkError}"
                        } else {
                            "Aucun nouvel épisode détecté. Touche ⟳ pour revérifier."
                        },
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        itemsIndexed(withNews, key = { _, e -> e.url }) { _, entry ->
                            UpdateRow(
                                entry = entry,
                                onClick = { onOpenShow(entry.toSearchResult()) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateRow(
    entry: LibraryEntry,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PosterImage(
            url = entry.posterUrl,
            contentDescription = entry.title,
            modifier = Modifier.size(width = 56.dp, height = 80.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Saisons vérifiées : ${entry.lastCounts.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        NovayBadge(text = "${entry.newEpisodes}")
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Rounded.NewReleases,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
        )
    }
}
