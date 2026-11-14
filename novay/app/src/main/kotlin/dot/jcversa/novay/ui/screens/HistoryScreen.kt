package dot.jcversa.novay.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dot.jcversa.novay.data.WatchHistory
import dot.jcversa.novay.data.WatchHistoryEntry
import dot.jcversa.novay.ui.LocalBottomChromePadding
import dot.jcversa.novay.ui.components.AppBarAction
import dot.jcversa.novay.ui.components.AppBarTitle
import dot.jcversa.novay.ui.components.EmptyScreen
import dot.jcversa.novay.ui.components.LocalSlateMotion
import dot.jcversa.novay.ui.components.NovayAppBar
import dot.jcversa.novay.ui.components.Pill
import dot.jcversa.novay.ui.components.PosterImage
import dot.jcversa.novay.ui.components.RowListSkeleton
import dot.jcversa.novay.ui.state.HistoryDayGroup
import dot.jcversa.novay.ui.theme.header
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Écran Historique — Cinema Glassmorphism & Editorial Noir :
 * épisodes visionnés groupés par jour, squelettes Shimmer, lignes distillées
 * sans bordures et reprise directe en un tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onResume: (WatchHistoryEntry) -> Unit
) {
    val context = LocalContext.current
    val bottomChrome = LocalBottomChromePadding.current
    var entries by remember { mutableStateOf<List<WatchHistoryEntry>?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey) {
        entries = WatchHistory.recent(context)
    }

    val motion = LocalSlateMotion.current
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val groups = remember(entries) {
        groupByDay(entries.orEmpty()).map { (day, list) -> HistoryDayGroup(day, list) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
    ) {
        NovayAppBar(
            titleContent = {
                AppBarTitle(title = "Historique")
                val count = entries.orEmpty().size
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
                if (!entries.orEmpty().isEmpty()) {
                    AppBarAction(
                        contentDescription = "Effacer tout l'historique",
                        icon = Icons.Rounded.DeleteOutline,
                        modifier = Modifier.testTag("history_clear_button"),
                        onClick = { confirmClear = true },
                    )
                }
            },
        )

        val viewKey = when {
            entries == null -> "loading"
            entries.orEmpty().isEmpty() -> "empty"
            else -> "list"
        }

        AnimatedContent(
            targetState = viewKey,
            transitionSpec = { motion.stateCrossfade() },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            label = "history_body_state",
        ) { stateKey ->
            when (stateKey) {
                "loading" -> RowListSkeleton(modifier = Modifier.padding(bottom = bottomChrome))
                "empty" -> EmptyScreen(
                    icon = Icons.Rounded.History,
                    headingText = "Aucun historique",
                    subtitleText = "Les épisodes que tu lances s'affichent ici avec ta position exacte pour reprendre instantanément.",
                    modifier = Modifier.padding(bottom = bottomChrome),
                )
                else -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 840.dp),
                        contentPadding = PaddingValues(bottom = 24.dp + bottomChrome),
                    ) {
                        groups.forEach { group ->
                            item(
                                key = "header_${group.dayLabel}",
                                contentType = "history_day_header",
                            ) {
                                Text(
                                    text = group.dayLabel,
                                    style = MaterialTheme.typography.header,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .animateItem()
                                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
                                )
                            }
                            items(
                                group.entries,
                                key = { "${it.seasonUrl}#${it.episodeNumber}" },
                                contentType = { "history_row" },
                            ) { entry ->
                                HistoryRow(
                                    modifier = Modifier.animateItem(),
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
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = {
                Text(
                    text = "Effacer l'historique ?",
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Text(
                    text = "Toutes les entrées et positions de reprise en cours seront supprimées.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        WatchHistory.clear(context)
                        confirmClear = false
                        refreshKey++
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) { Text("Effacer") }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmClear = false },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) { Text("Annuler") }
            },
        )
    }
}

@Composable
private fun HistoryRow(
    entry: WatchHistoryEntry,
    onResume: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.medium
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val containerColor by animateColorAsState(
        targetValue = if (hovered) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        label = "history_row_bg",
    )

    Surface(
        shape = shape,
        color = containerColor,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(shape)
            .semantics(mergeDescendants = true) {}
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onResume,
            ),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PosterImage(
                url = entry.posterUrl,
                contentDescription = entry.animeTitle,
                modifier = Modifier
                    .size(width = 58.dp, height = 84.dp)
                    .clip(MaterialTheme.shapes.extraSmall),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = entry.animeTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
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
                            .height(3.dp)
                            .clip(MaterialTheme.shapes.extraSmall),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    Text(
                        text = "Reprise à ${formatMillis(entry.positionMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            IconButton(
                onClick = onResume,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("history_resume_button"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayCircleOutline,
                    contentDescription = "Reprendre ${entry.episodeLabel}",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("history_remove_button"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Supprimer de l'historique",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

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
    val cal = Calendar.getInstance()
    cal.timeInMillis = epochMs
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

internal fun formatMillis(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(Locale.FRENCH, minutes, seconds)
}
