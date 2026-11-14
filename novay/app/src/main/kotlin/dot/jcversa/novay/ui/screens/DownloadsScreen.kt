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
import dot.jcversa.novay.ui.components.bounceClick
import dot.jcversa.novay.ui.theme.DarkCardBorder
import dot.jcversa.novay.ui.theme.DarkSurfaceCard
import dot.jcversa.novay.ui.theme.MintGreen
import dot.jcversa.novay.ui.theme.TextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Écran Téléchargements (V2) : chaque épisode téléchargé avec sa
 * progression, lecture hors-ligne, pause/reprise et suppression.
 * Un tap sur un épisode terminé le lit depuis le stockage offline.
 */
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
    // Préférence « Wi-Fi uniquement ».
    var wifiOnly by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        wifiOnly = withContext(Dispatchers.IO) { Downloads.isWifiOnly(context) }
    }

    // L'index media3 est une base SQLite : lecture hors du thread principal.
    LaunchedEffect(Unit) {
        while (true) {
            infos = withContext(Dispatchers.IO) {
                runCatching { Downloads.snapshot(context) }.getOrDefault(emptyList())
            }
            // Vitesse/ETA du (des) téléchargement(s) actif(s).
            speeds = infos.filter { it.isActive }.associate { info ->
                val meter = meters.getOrPut(info.id) { SpeedMeter() }
                info.id to meter.sample(info.bytesDownloaded, info.totalBytes)
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
            // Racine d'onglet : pas de flèche retour.
            if (canBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Retour",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.bounceClick(onClick = onBack)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = "Téléchargements",
                style = MaterialTheme.typography.headlineMedium
            )
        }
        Spacer(Modifier.height(16.dp))

        // Progression de groupe (V2.3) : « 3/12 · 45 % ».
        val completedCount = infos.count { it.isCompleted }
        val activeInfo = infos.firstOrNull { it.isActive }
        if (infos.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = buildString {
                        append("$completedCount/${infos.size} téléchargés")
                        activeInfo?.let { append(" · ${it.percent.toInt()} %") }
                        speeds[activeInfo?.id]?.let { reading ->
                            if (reading.bytesPerSecond > 0) {
                                append(" · ${formatSpeed(reading.bytesPerSecond)}")
                                if (reading.etaMillis > 0) append(" · ${formatEta(reading.etaMillis)}")
                            }
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (completedCount == infos.size) MintGreen else TextMuted,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Wi-Fi uniquement",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                Switch(
                    checked = wifiOnly,
                    onCheckedChange = { checked ->
                        wifiOnly = checked
                        scope.launch(Dispatchers.IO) {
                            Downloads.setWifiOnly(context, checked)
                        }
                    }
                )
            }
            Spacer(Modifier.height(12.dp))
        }

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
                        info.isActive -> {
                            val reading = speeds[info.id]
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
                    color = TextMuted
                )
            }
        }
    }
}
