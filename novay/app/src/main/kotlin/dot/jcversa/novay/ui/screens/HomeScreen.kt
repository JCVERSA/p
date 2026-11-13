package dot.jcversa.novay.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dot.jcversa.novay.data.WatchHistory
import dot.jcversa.novay.data.WatchHistoryEntry
import dot.jcversa.novay.downloads.Downloads
import dot.jcversa.novay.ui.components.AnimateEntrance
import dot.jcversa.novay.ui.components.PosterImage
import dot.jcversa.novay.ui.components.bounceClick
import dot.jcversa.novay.ui.theme.Amber500
import dot.jcversa.novay.ui.theme.DarkCardBorder
import dot.jcversa.novay.ui.theme.DarkSurfaceCard
import dot.jcversa.novay.ui.theme.DarkSurfaceVariant
import dot.jcversa.novay.ui.theme.MintGreen
import dot.jcversa.novay.ui.theme.TextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Écran d'accueil (V2.1) : reprendre la lecture là où on s'était arrêté,
 * suivre les téléchargements en cours, et lancer une recherche.
 */
@Composable
fun HomeScreen(
    onOpenSearch: () -> Unit,
    onResume: (entry: WatchHistoryEntry) -> Unit
) {
    val context = LocalContext.current
    var history by remember { mutableStateOf<List<WatchHistoryEntry>>(emptyList()) }
    var infos by remember { mutableStateOf<List<Downloads.DownloadInfo>>(emptyList()) }

    LaunchedEffect(Unit) {
        history = withContext(Dispatchers.IO) { WatchHistory.recent(context) }
    }

    // Téléchargements en cours / récents (index SQLite : hors thread principal).
    LaunchedEffect(Unit) {
        while (true) {
            infos = withContext(Dispatchers.IO) {
                runCatching { Downloads.snapshot(context) }.getOrDefault(emptyList())
            }
            delay(1000)
        }
    }

    val active = infos.filter { it.isActive }
    val completed = infos.count { it.isCompleted }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "header") {
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "NoVaY",
                        style = MaterialTheme.typography.displayMedium
                    )
                    Text(
                        text = "Animes VF & VOSTFR, depuis ton téléphone",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            // Barre de recherche factice : un tap ouvre la vraie recherche.
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = DarkSurfaceCard,
                border = BorderStroke(1.dp, DarkCardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .bounceClick(onClick = onOpenSearch)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = TextMuted
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Rechercher un anime…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (history.isNotEmpty()) {
            item(key = "history-header") {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Reprendre la lecture",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(4.dp))
            }
            itemsIndexed(history, key = { _, e -> "${e.seasonUrl}#${e.episodeNumber}" }) { index, entry ->
                AnimateEntrance(index = index) {
                    ResumeRow(entry = entry, onClick = { onResume(entry) })
                }
            }
        }

        if (history.isEmpty() && active.isEmpty() && completed == 0) {
            item(key = "empty") {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Bienvenue — cherche un anime pour commencer.\n\nLes épisodes que tu regardes et télécharges apparaîtront ici, prêts à reprendre d'un seul tap.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
        }

        if (active.isNotEmpty() || completed > 0) {
            item(key = "downloads-header") {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Téléchargements",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(4.dp))
            }
            itemsIndexed(active, key = { _, i -> "dl-${i.id}" }) { _, info ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = DarkSurfaceCard,
                    border = BorderStroke(1.dp, DarkCardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .bounceClick(onClick = onOpenDownloads)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = info.meta?.animeTitle?.ifEmpty { null }
                                ?: info.meta?.episodeLabel
                                ?: "Épisode",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { (info.percent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${info.percent.toInt()} %",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }
            }
            if (completed > 0) {
                item(key = "downloads-done") {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = DarkSurfaceCard,
                        border = BorderStroke(1.dp, DarkCardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .bounceClick(onClick = onOpenDownloads)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$completed épisode(s) prêt(s) hors-ligne",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MintGreen,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = TextMuted
                            )
                        }
                    }
                }
            }
        }
        item(key = "footer") { Spacer(Modifier.height(24.dp)) }
    }
}

/** Une reprise de lecture : affiche, saison/épisode, bouton lecture. */
@Composable
private fun ResumeRow(entry: WatchHistoryEntry, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = DarkSurfaceCard,
        border = BorderStroke(1.dp, DarkCardBorder)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PosterImage(
                url = entry.posterUrl,
                contentDescription = entry.animeTitle,
                modifier = Modifier
                    .size(width = 52.dp, height = 68.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.animeTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${entry.seasonName} · ${entry.episodeLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Filled.PlayCircleOutline,
                contentDescription = "Reprendre",
                tint = Amber500,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
