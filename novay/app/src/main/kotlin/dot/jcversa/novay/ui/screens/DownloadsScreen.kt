package dot.jcversa.novay.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.outlined.ErrorOutline
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dot.jcversa.novay.data.AnimeEpisode
import dot.jcversa.novay.data.AnimeSeason
import dot.jcversa.novay.data.ResolvedStream
import dot.jcversa.novay.downloads.Downloads
import dot.jcversa.novay.ui.components.AnimateEntrance
import dot.jcversa.novay.ui.components.bounceClick
import dot.jcversa.novay.ui.theme.DarkCardBorder
import dot.jcversa.novay.ui.theme.DarkSurfaceCard
import dot.jcversa.novay.ui.theme.MintGreen
import dot.jcversa.novay.ui.theme.TextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Écran Téléchargements (V2) : chaque épisode téléchargé avec sa
 * progression, lecture hors-ligne, pause/reprise et suppression.
 * Un tap sur un épisode terminé le lit depuis le stockage offline.
 */
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onPlay: (season: AnimeSeason, episode: AnimeEpisode, stream: ResolvedStream) -> Unit
) {
    val context = LocalContext.current
    var infos by remember { mutableStateOf<List<Downloads.DownloadInfo>>(emptyList()) }

    // L'index media3 est une base SQLite : lecture hors du thread principal.
    LaunchedEffect(Unit) {
        while (true) {
            infos = withContext(Dispatchers.IO) {
                runCatching { Downloads.snapshot(context) }.getOrDefault(emptyList())
            }
            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Retour",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.bounceClick(onClick = onBack)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Téléchargements",
                style = MaterialTheme.typography.headlineMedium
            )
        }
        Spacer(Modifier.height(16.dp))

        if (infos.isEmpty()) {
            Text(
                text = "Aucun téléchargement pour l'instant — ouvre un anime, puis l'icône de téléchargement d'un épisode.",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(infos, key = { _, i -> i.id }) { index, info ->
                    AnimateEntrance(index = index) {
                        DownloadRow(
                            info = info,
                            onPlay = {
                                val meta = info.meta ?: return@DownloadRow
                                val season = AnimeSeason(name = meta.seasonName, url = meta.seasonUrl)
                                val episode = AnimeEpisode(
                                    number = meta.episodeNumber,
                                    url = meta.seasonUrl,
                                    label = meta.episodeLabel
                                )
                                onPlay(season, episode, meta.toStream())
                            },
                            onRemove = { Downloads.remove(context, info.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    info: Downloads.DownloadInfo,
    onPlay: () -> Unit,
    onRemove: () -> Unit
) {
    val meta = info.meta
    val animeTitle = meta?.animeTitle?.takeIf { it.isNotEmpty() }
    val title = animeTitle ?: meta?.seasonName?.takeIf { it.isNotEmpty() } ?: "Épisode"
    val subtitle = if (meta != null) {
        listOfNotNull(
            meta.seasonName.takeIf { it.isNotEmpty() && it != meta.animeTitle },
            meta.episodeLabel.takeIf { it.isNotEmpty() }
        ).joinToString(" · ")
    } else {
        ""
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = DarkSurfaceCard,
        border = BorderStroke(1.dp, DarkCardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle.isNotEmpty()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                when {
                    info.isCompleted -> Icon(
                        imageVector = Icons.Filled.PlayCircleOutline,
                        contentDescription = "Lire hors-ligne",
                        tint = MintGreen,
                        modifier = Modifier.bounceClick(onClick = onPlay)
                    )
                    info.isFailed -> Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = "Échec du téléchargement",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Supprimer",
                    tint = TextMuted,
                    modifier = Modifier.bounceClick(onClick = onRemove)
                )
            }
            if (!info.isCompleted) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { (info.percent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (info.isFailed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when {
                        info.isFailed -> "Échec — relance depuis la liste des épisodes"
                        info.isActive -> "${info.percent.toInt()} %"
                        else -> "En pause"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
    }
}
