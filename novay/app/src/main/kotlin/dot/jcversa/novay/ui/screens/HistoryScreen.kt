package dot.jcversa.novay.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import dot.jcversa.novay.data.WatchHistory
import dot.jcversa.novay.data.WatchHistoryEntry
import dot.jcversa.novay.ui.components.AppBarAction
import dot.jcversa.novay.ui.components.AppBarTitle
import dot.jcversa.novay.ui.components.EmptyScreen
import dot.jcversa.novay.ui.components.NovayAppBar
import dot.jcversa.novay.ui.components.Pill
import dot.jcversa.novay.ui.components.PosterImage
import dot.jcversa.novay.ui.theme.header
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Écran Historique (V2.4, design Mihon) : épisodes regardés groupés par
 * jour, reprise directe, suppression unitaire + tout effacer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onResume: (WatchHistoryEntry) -> Unit
) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf<List<WatchHistoryEntry>?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(refreshKey) {
        entries = WatchHistory.recent(context)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        NovayAppBar(
            titleContent = {
                AppBarTitle(title = "Historique")
                val count = entries.orEmpty().size
                if (count > 0) {
                    Pill(
                        text = "$count",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
                    )
                }
            },
            actions = {
                if (!entries.orEmpty().isEmpty()) {
                    AppBarAction(
                        contentDescription = "Effacer l'historique",
                        icon = Icons.Rounded.DeleteOutline,
                        onClick = { confirmClear = true },
                    )
                }
            },
        )

        when {
            entries == null -> Box(Modifier.fillMaxSize())
            entries.orEmpty().isEmpty() -> EmptyScreen(
                icon = Icons.Rounded.History,
                headingText = "Aucun historique",
                subtitleText = "Les épisodes que tu regardes apparaissent ici, prêts à reprendre.",
            )
            else -> {
                val groups = groupByDay(entries.orEmpty())
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
                ) {
                    groups.forEach { (day, dayEntries) ->
                        item(key = "header_$day") {
                            Text(
                                text = day,
                                style = MaterialTheme.typography.header,
                                modifier = Modifier.padding(
                                    start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp,
                                ),
                            )
                        }
                        itemsIndexed(dayEntries, key = { _, e -> "${e.seasonUrl}#${e.episodeNumber}" }) { _, entry ->
                            HistoryRow(
                                entry = entry,
                                onResume = { onResume(entry) },
                                onRemove = {
                                    WatchHistory.remove(
                                        context,
                                        entry.seasonUrl,
                                        entry.episodeNumber
                                    )
                                    refreshKey++
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Effacer l'historique ?") },
            text = { Text("Toutes les entrées seront supprimées. La position de lecture des épisodes en cours sera perdue.") },
            confirmButton = {
                TextButton(onClick = {
                    WatchHistory.clear(context)
                    confirmClear = false
                    refreshKey++
                }) { Text("Effacer") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Annuler") }
            },
        )
    }
}

/** Ligne d'historique : couverture, titres, progression, actions. */
@Composable
private fun HistoryRow(
    entry: WatchHistoryEntry,
    onResume: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PosterImage(
            url = entry.posterUrl,
            contentDescription = entry.animeTitle,
            modifier = Modifier.size(width = 56.dp, height = 80.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = entry.animeTitle,
                style = MaterialTheme.typography.bodyMedium,
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
                LinearProgressIndicator(
                    progress = { (entry.positionMs.toFloat() / entry.durationMs).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .padding(top = 2.dp),
                )
                Text(
                    text = "Reprise à ${formatMillis(entry.positionMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        IconButton(onClick = onResume) {
            Icon(
                imageVector = Icons.Rounded.PlayCircleOutline,
                contentDescription = "Reprendre",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = "Supprimer",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Groupe « Aujourd'hui / Hier / date » — pures, testables. */
internal fun groupByDay(entries: List<WatchHistoryEntry>): List<Pair<String, List<WatchHistoryEntry>>> {
    val dayFormat = SimpleDateFormat("EEEE d MMMM", Locale.FRENCH)
    val groups = linkedMapOf<String, MutableList<WatchHistoryEntry>>()
    val now = System.currentTimeMillis()
    for (e in entries) {
        val day = when {
            e.watchedAt >= startOfDay(now) -> "Aujourd'hui"
            e.watchedAt >= startOfDay(now) - 86_400_000L -> "Hier"
            else -> dayFormat.format(Date(e.watchedAt)).replaceFirstChar { it.uppercase(Locale.FRENCH) }
        }
        groups.getOrPut(day) { mutableListOf() }.add(e)
    }
    return groups.map { it.key to it.value.toList() }
}

internal fun startOfDay(epochMs: Long): Long {
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = epochMs
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

internal fun formatMillis(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(Locale.FRENCH, minutes, seconds)
}
