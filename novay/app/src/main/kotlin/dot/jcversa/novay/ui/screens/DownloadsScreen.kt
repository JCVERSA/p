package dot.jcversa.novay.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import dot.jcversa.novay.data.AnimeEpisode
import dot.jcversa.novay.data.AnimeSeason
import dot.jcversa.novay.data.ResolvedStream
import dot.jcversa.novay.downloads.Downloads
import dot.jcversa.novay.downloads.SpeedMeter
import dot.jcversa.novay.downloads.formatEta
import dot.jcversa.novay.downloads.formatSpeed
import dot.jcversa.novay.ui.LocalBottomChromePadding
import dot.jcversa.novay.ui.components.AppBarTitle
import dot.jcversa.novay.ui.components.EmptyScreen
import dot.jcversa.novay.ui.components.LocalSlateMotion
import dot.jcversa.novay.ui.components.NovayAppBar
import dot.jcversa.novay.ui.components.Pill
import dot.jcversa.novay.ui.components.SlateMotion
import dot.jcversa.novay.ui.state.DownloadSection
import dot.jcversa.novay.ui.theme.header
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Écran File de téléchargements — Cinema Glassmorphism & Editorial Noir :
 * sections par anime, télémétrie monospace (vitesse + ETA), lignes distillées,
 * lecture hors-ligne et FAB Pause / Reprendre positionné au-dessus du chrome bas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    canBack: Boolean = true,
    onBack: () -> Unit = {},
    onPlay: (season: AnimeSeason, episode: AnimeEpisode, stream: ResolvedStream) -> Unit
) {
    val context = LocalContext.current
    val bottomChrome = LocalBottomChromePadding.current
    var infos by remember { mutableStateOf<List<Downloads.DownloadInfo>>(emptyList()) }
    var speeds by remember { mutableStateOf<Map<String, SpeedMeter.Reading>>(emptyMap()) }
    val meters = remember { mutableMapOf<String, SpeedMeter>() }
    var wifiOnly by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            wifiOnly = Downloads.isWifiOnly(context)
            runCatching { paused = Downloads.manager(context).downloadsPaused }
        }
    }

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
            speeds = infos.filter { it.isActive }.associate { info ->
                val meter = meters.getOrPut(info.id) { SpeedMeter() }
                info.id to meter.sample(info.bytesDownloaded, info.totalBytes)
            }
            delay(1000)
        }
    }

    val motion = LocalSlateMotion.current
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val sections = remember(infos) {
        infos.groupBy { it.meta?.animeTitle?.takeIf { t -> t.isNotEmpty() } ?: "Épisodes" }
            .map { (title, list) ->
                DownloadSection(
                    animeTitle = title,
                    items = list,
                    completedCount = list.count { it.isCompleted },
                )
            }
    }
    val pending = remember(infos) { infos.count { !it.isCompleted } }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            NovayAppBar(
                titleContent = {
                    AppBarTitle(title = "Téléchargements")
                    if (pending > 0) {
                        Pill(
                            text = "$pending",
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                },
                navigateUp = if (canBack) onBack else null,
                scrollBehavior = scrollBehavior,
            )

            AnimatedVisibility(visible = infos.isNotEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 840.dp)
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Wi-Fi uniquement",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = "Met en pause en données mobiles et reprend en Wi-Fi.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Switch(
                                checked = wifiOnly,
                                onCheckedChange = { checked ->
                                    wifiOnly = checked
                                    scope.launch(Dispatchers.IO) {
                                        Downloads.setWifiOnly(context, checked)
                                    }
                                },
                                modifier = Modifier.testTag("downloads_wifi_switch"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        }
                    }
                }
            }

            AnimatedContent(
                targetState = infos.isEmpty(),
                transitionSpec = { motion.stateCrossfade() },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                label = "downloads_body_state",
            ) { isEmpty ->
                if (isEmpty) {
                    EmptyScreen(
                        icon = Icons.Rounded.DownloadDone,
                        headingText = "Aucun téléchargement",
                        subtitleText = "Ouvre une saison et touche l'icône de téléchargement sur un épisode pour choisir sa résolution hors-ligne.",
                        modifier = Modifier.padding(bottom = bottomChrome),
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 840.dp),
                            contentPadding = PaddingValues(bottom = 104.dp + bottomChrome),
                        ) {
                            sections.forEach { section ->
                                item(
                                    key = "header_${section.animeTitle}",
                                    contentType = "download_section_header",
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .animateItem()
                                            .fillMaxWidth()
                                            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = section.animeTitle,
                                            style = MaterialTheme.typography.header,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Pill(
                                            text = "${section.completedCount}/${section.items.size}",
                                            color = MaterialTheme.colorScheme.tertiaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                        )
                                    }
                                }
                                items(
                                    section.items,
                                    key = { it.id },
                                    contentType = { "download_row" },
                                ) { info ->
                                    DownloadRow(
                                        modifier = Modifier.animateItem(),
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
        }

        AnimatedVisibility(
            visible = pending > 0,
            enter = if (motion.reduceMotion) {
                EnterTransition.None
            } else {
                fadeIn(tween(200, easing = SlateMotion.EaseOutExpo)) +
                    scaleIn(initialScale = 0.9f, animationSpec = tween(200, easing = SlateMotion.EaseOutExpo))
            },
            exit = if (motion.reduceMotion) {
                ExitTransition.None
            } else {
                fadeOut(tween(130, easing = SlateMotion.FastOutExit))
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .then(if (bottomChrome == 0.dp) Modifier.navigationBarsPadding() else Modifier)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + bottomChrome),
        ) {
            ExtendedFloatingActionButton(
                onClick = {
                    if (paused) {
                        Downloads.resumeAll(context)
                    } else {
                        Downloads.pauseAll(context)
                    }
                },
                shape = CircleShape,
                modifier = Modifier.testTag("downloads_pause_resume_fab"),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    contentDescription = null,
                )
                Spacer(Modifier.width(6.dp))
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
    modifier: Modifier = Modifier,
) {
    val meta = info.meta
    val title = meta?.episodeLabel?.takeIf { it.isNotEmpty() } ?: "Épisode"
    val subtitle = meta?.seasonName?.takeIf { it.isNotEmpty() && it != meta.animeTitle }
    val shape = MaterialTheme.shapes.medium

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, end = 6.dp, bottom = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
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
                            info.isCompleted -> "Prêt hors-ligne"
                            info.isFailed -> "Échec — relancer depuis la saison"
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
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            info.isCompleted -> MaterialTheme.colorScheme.tertiary
                            info.isFailed -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                if (info.isCompleted) {
                    IconButton(
                        onClick = onPlay,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("download_play_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayCircleOutline,
                            contentDescription = "Lire hors-ligne",
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("download_remove_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Supprimer le téléchargement",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!info.isCompleted) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (info.percent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp)
                        .height(4.dp)
                        .clip(MaterialTheme.shapes.extraSmall),
                    color = if (info.isFailed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
        }
    }
}
