package dot.jcversa.novay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import dot.jcversa.novay.downloads.SpeedMeter
import dot.jcversa.novay.downloads.formatEta
import dot.jcversa.novay.downloads.formatSpeed
import dot.jcversa.novay.ui.components.AnimateEntrance
import dot.jcversa.novay.ui.components.AppBarTitle
import dot.jcversa.novay.ui.components.EmptyScreen
import dot.jcversa.novay.ui.components.NovayAppBar
import dot.jcversa.novay.ui.components.Pill
import dot.jcversa.novay.ui.theme.header
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Écran File de téléchargements (V2.4, look DownloadQueueScreen Mihon ;
 * moteurs V2.3 inchangés) : sections par anime, progression + vitesse +
 * temps restant, pause/reprise GLOBALE en FAB, lecture hors-ligne,
 * Wi-Fi uniquement.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    canBack: Boolean = true,
    onBack: () -> Unit = {},
    onPlay: (season: AnimeSeason, episode: AnimeEpisode, stream: ResolvedStream) -> Unit
) {
    val context = LocalContext.current
    var infos by remember { mutableStateOf<List<Downloads.DownloadInfo>>(emptyList()) }
    // Vitesse lissée + temps restant du téléchargement actif (V2.3).
    var speeds by remember { mutableStateOf<Map<String, SpeedMeter.Reading>>(emptyMap()) }
    val meters = remember { mutableMapOf<String, SpeedMeter>() }
    // Préférence « Wi-Fi uniquement » (V2.3) + pause globale.
    var wifiOnly by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            wifiOnly = Downloads.isWifiOnly(context)
            runCatching { paused = Downloads.manager(context).downloadsPaused }
        }
    }

    // L'index media3 est une base SQLite : lecture hors du thread principal.
    LaunchedEffect(Unit) {
        while (true) {
            var snapshot = emptyList<Downloads.DownloadInfo>()
            var isPaused = false
            withContext(Dispatchers.IO) {
                snapshot = runCatching { Downloads.snapshot(context) }.getOrDefault(emptyList())
                runCatching { isPaused = Downloads.manager(context).downloadsPaused }
            }
            infos = snapshot
            paused = isPaused
            // Vitesse/ETA du (des) téléchargement(s) actif(s).
            speeds = infos.filter { it.isActive }.associate { info ->
                val meter = meters.getOrPut(info.id) { SpeedMeter() }
                info.id to meter.sample(info.bytesDownloaded, info.totalBytes)
            }
            delay(1000)
        }
    }

    // Sections par anime (DownloadQueueSection Mihon).
    val sections = infos.groupBy { it.meta?.animeTitle?.takeIf { t -> t.isNotEmpty() } ?: "Épisodes" }
    val pending = infos.count { !it.isCompleted }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            NovayAppBar(
                titleContent = {
                    AppBarTitle(title = "File de téléchargements")
                    if (pending > 0) {
                        Pill(
                            text = "$pending",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
                        )
                    }
                },
                navigateUp = if (canBack) onBack else null,
            )

            // Toggle Wi-Fi uniquement (validé V2.3).
            if (infos.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Wi-Fi uniquement",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "Pause auto en 4G, reprise auto en Wi-Fi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = wifiOnly,
                        onCheckedChange = { checked ->
                            wifiOnly = checked
                            scope.launch(Dispatchers.IO) {
                                Downloads.setWifiOnly(context, checked)
                            }
                        },
                    )
                }
            }

            if (infos.isEmpty()) {
                EmptyScreen(
                    icon = Icons.Rounded.DownloadDone,
                    headingText = "Aucun téléchargement",
                    subtitleText = "Ouvre un anime, puis l'icône de téléchargement d'un épisode — la résolution, c'est toi qui la choisis.",
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    sections.forEach { (animeTitle, sectionInfos) ->
                        item(key = "header_$animeTitle") {
                            val doneInSection = sectionInfos.count { it.isCompleted }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = animeTitle,
                                    style = MaterialTheme.typography.header,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Pill(
                                    text = "$doneInSection/${sectionInfos.size}",
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                        }
                        itemsIndexed(
                            sectionInfos,
                            key = { _, i -> i.id },
                        ) { index, info ->
                            AnimateEntrance(index = index) {
                                DownloadRow(
                                    info = info,
                                    reading = speeds[info.id],
                                    wifiOnly = wifiOnly,
                                    onPlay = {
                                        val meta = info.meta ?: return@DownloadRow
                                        val season = AnimeSeason(
                                            name = meta.seasonName,
                                            url = meta.seasonUrl
                                        )
                                        val episode = AnimeEpisode(
                                            number = meta.episodeNumber,
                                            url = meta.seasonUrl,
                                            label = meta.episodeLabel
                                        )
                                        onPlay(season, episode, meta.toStream())
                                    },
                                    onRemove = { Downloads.remove(context, info.id) },
                                )
                            }
                        }
                    }
                }
            }
        }

        // FAB pause/reprise globale (Mihon DownloadQueueScreen).
        if (pending > 0) {
            ExtendedFloatingActionButton(
                onClick = {
                    if (paused) {
                        Downloads.resumeAll(context)
                    } else {
                        Downloads.pauseAll(context)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Icon(
                    imageVector = if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    contentDescription = null,
                )
                Text(text = if (paused) "Reprendre" else "Mettre en pause")
            }
        }
    }
}

@Composable
private fun DownloadRow(
    info: Downloads.DownloadInfo,
    reading: SpeedMeter.Reading?,
    wifiOnly: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
) {
    val meta = info.meta
    val title = meta?.episodeLabel?.takeIf { it.isNotEmpty() } ?: "Épisode"
    val subtitle = meta?.seasonName?.takeIf { it.isNotEmpty() && it != meta.animeTitle }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = when {
                            info.isCompleted -> "Téléchargé — lecture hors-ligne"
                            info.isFailed -> "Échec — relance depuis la liste des épisodes"
                            info.isActive -> {
                                if (reading != null && reading.bytesPerSecond > 0) {
                                    "${info.percent.toInt()} % · ${formatSpeed(reading.bytesPerSecond)}" +
                                        (if (reading.etaMillis > 0) " · ${formatEta(reading.etaMillis)}" else "")
                                } else {
                                    "${info.percent.toInt()} %"
                                }
                            }
                            wifiOnly -> "En attente du Wi-Fi"
                            else -> "En pause"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (info.isCompleted) {
                            MaterialTheme.colorScheme.tertiary
                        } else if (info.isFailed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                if (info.isCompleted) {
                    IconButton(onClick = onPlay) {
                        Icon(
                            imageVector = Icons.Rounded.PlayCircleOutline,
                            contentDescription = "Lire hors-ligne",
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Supprimer",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!info.isCompleted) {
                LinearProgressIndicator(
                    progress = { (info.percent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = if (info.isFailed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        }
    }
}
