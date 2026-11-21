package dot.jcversa.novay.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dot.jcversa.novay.R
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
import dot.jcversa.novay.ui.components.LocalNovayToast
import dot.jcversa.novay.ui.components.LocalSlateMotion
import dot.jcversa.novay.ui.components.NovayAppBar
import dot.jcversa.novay.ui.components.NovayIcons
import dot.jcversa.novay.ui.components.NovayLottieIcon
import dot.jcversa.novay.ui.components.NovayPlayPauseMorphIcon
import dot.jcversa.novay.ui.components.NovaySectionHeader
import dot.jcversa.novay.ui.components.NovayToastTone
import dot.jcversa.novay.ui.components.Pill
import dot.jcversa.novay.ui.components.SlateMotion
import dot.jcversa.novay.ui.components.novayCinemaGlass
import dot.jcversa.novay.ui.state.DownloadSection
import dot.jcversa.novay.ui.theme.NovayTheme
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
    val toast = LocalNovayToast.current
    val bottomChrome = LocalBottomChromePadding.current
    var infos by remember { mutableStateOf<List<Downloads.DownloadInfo>>(emptyList()) }
    var speeds by remember { mutableStateOf<Map<String, SpeedMeter.Reading>>(emptyMap()) }
    val meters = remember { mutableMapOf<String, SpeedMeter>() }
    var wifiOnly by remember { mutableStateOf(Downloads.isWifiOnly(context)) }
    var paused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(context) {
        Downloads.observeQueue(context).collect { queue ->
            infos = queue.items
            paused = queue.paused
            speeds = queue.items.filter { it.isActive }.associate { info ->
                val meter = meters.getOrPut(info.id) { SpeedMeter() }
                info.id to meter.sample(info.bytesDownloaded, info.totalBytes)
            }
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
                DownloadsWifiOnlyCard(
                    wifiOnly = wifiOnly,
                    onToggle = { checked ->
                        wifiOnly = checked
                        scope.launch(Dispatchers.IO) {
                            Downloads.setWifiOnly(context, checked)
                        }
                    },
                )
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
                        icon = NovayIcons.DownloadDone,
                        lottieRes = R.raw.lottie_download,
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
                            item(
                                key = "downloads_dashboard",
                                contentType = "downloads_dashboard",
                            ) {
                                DownloadsDashboardCard(
                                    infos = infos,
                                    speeds = speeds,
                                    paused = paused,
                                    wifiOnly = wifiOnly,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                )
                            }
                            sections.forEach { section ->
                                item(
                                    key = "header_${section.animeTitle}",
                                    contentType = "download_section_header",
                                ) {
                                    NovaySectionHeader(
                                        title = section.animeTitle,
                                        textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .animateItem()
                                            .fillMaxWidth()
                                            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
                                        trailing = {
                                            Pill(
                                                text = "${section.completedCount}/${section.items.size}",
                                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                            )
                                        },
                                    )
                                }
                                items(
                                    section.items,
                                    key = { it.id },
                                    contentType = { "download_row" },
                                ) { info ->
                                    val rowMod = if (motion.reduceMotion) Modifier else Modifier.animateItem()
                                    DownloadRow(
                                        modifier = rowMod,
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
                                        onRemove = {
                                            Downloads.remove(context, info.id)
                                            toast.show("Téléchargement supprimé", NovayToastTone.INFO)
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
            val overlays = NovayTheme.overlays
            ExtendedFloatingActionButton(
                onClick = {
                    if (paused) {
                        Downloads.resumeAll(context)
                        toast.show("Téléchargements repris", NovayToastTone.SUCCESS)
                    } else {
                        Downloads.pauseAll(context)
                        toast.show("Téléchargements mis en pause", NovayToastTone.INFO)
                    }
                },
                shape = CircleShape,
                modifier = Modifier.testTag("downloads_pause_resume_fab"),
                containerColor = overlays.ctaContainer,
                contentColor = overlays.onCtaContainer,
            ) {
                NovayPlayPauseMorphIcon(
                    isPlaying = !paused,
                    tint = overlays.onCtaContainer,
                    size = 20.dp,
                )
                Spacer(Modifier.width(8.dp))
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
            .padding(horizontal = 16.dp, vertical = 4.dp),
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
                    val codecBadge = meta?.videoCodec
                    Text(
                        text = when {
                            info.isCompleted && codecBadge != null -> "Prêt hors-ligne · $codecBadge"
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
                            info.isCompleted && codecBadge == "HEVC" ->
                                MaterialTheme.colorScheme.error
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
                        NovayLottieIcon(
                            res = R.raw.lottie_playpausecircle,
                            contentDescription = "Lire hors-ligne",
                            tint = MaterialTheme.colorScheme.tertiary,
                            size = 24.dp,
                        )
                    }
                }
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("download_remove_button"),
                ) {
                    NovayLottieIcon(
                        res = R.raw.lottie_trash2,
                        contentDescription = "Supprimer le téléchargement",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        size = 24.dp,
                    )
                }
            }
            if (!info.isCompleted) {
                Spacer(Modifier.height(8.dp))
                val motion = LocalSlateMotion.current
                val animatedProgress by animateFloatAsState(
                    targetValue = (info.percent / 100f).coerceIn(0f, 1f),
                    animationSpec = motion.enterSpec(220),
                    label = "download_row_progress",
                )
                LinearProgressIndicator(
                    progress = { animatedProgress },
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

@Composable
internal fun DownloadsDashboardCard(
    infos: List<Downloads.DownloadInfo>,
    speeds: Map<String, SpeedMeter.Reading>,
    paused: Boolean,
    wifiOnly: Boolean,
    modifier: Modifier = Modifier,
) {
    val overlays = NovayTheme.overlays
    val motion = LocalSlateMotion.current
    val completedCount = remember(infos) { infos.count { it.isCompleted } }
    val activeCount = remember(infos) { infos.count { it.isActive } }
    val failedCount = remember(infos) { infos.count { it.isFailed } }
    val totalSpeed = remember(speeds) { speeds.values.sumOf { it.bytesPerSecond } }
    val totalStoredBytes = remember(infos) {
        infos.sumOf { info ->
            if (info.isCompleted) info.totalBytes.coerceAtLeast(info.bytesDownloaded)
            else info.bytesDownloaded.coerceAtLeast(0L)
        }
    }
    val averagePercent = remember(infos) {
        if (infos.isEmpty()) 0f
        else (infos.sumOf { if (it.isCompleted) 100.0 else it.percent.toDouble() } / infos.size / 100.0)
            .toFloat()
            .coerceIn(0f, 1f)
    }
    val animatedAverage by animateFloatAsState(
        targetValue = averagePercent,
        animationSpec = motion.enterSpec(240),
        label = "downloads_dashboard_progress",
    )
    val statusLabel = when {
        activeCount > 0 && totalSpeed > 0 -> formatSpeed(totalSpeed)
        activeCount > 0 -> "$activeCount actif${if (activeCount > 1) "s" else ""}"
        paused -> "En pause"
        failedCount > 0 -> "$failedCount en échec"
        wifiOnly && completedCount < infos.size -> "Attente Wi-Fi"
        else -> "Tout est prêt"
    }
    val statusTint = when {
        activeCount > 0 -> MaterialTheme.colorScheme.primary
        failedCount > 0 -> MaterialTheme.colorScheme.error
        completedCount == infos.size && infos.isNotEmpty() -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .novayCinemaGlass(
                shape = RoundedCornerShape(18.dp),
                tint = overlays.glassSurface,
                borderColor = overlays.glassBorder,
                showBorder = true,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("downloads_dashboard_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.downloads_dashboard_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Pill(
                text = statusLabel,
                color = statusTint.copy(alpha = 0.16f),
                contentColor = statusTint,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DashboardMetric(
                label = "Hors-ligne",
                value = "$completedCount / ${infos.size}",
                modifier = Modifier.weight(1f),
            )
            DashboardMetric(
                label = "En file",
                value = "$activeCount",
                modifier = Modifier.weight(1f),
            )
            DashboardMetric(
                label = "Volume",
                value = formatStorageSize(totalStoredBytes),
                modifier = Modifier.weight(1f),
            )
        }

        LinearProgressIndicator(
            progress = { animatedAverage },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = if (completedCount == infos.size && infos.isNotEmpty()) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.primary
            },
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }
}

@Composable
private fun DashboardMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatStorageSize(bytes: Long): String {
    if (bytes <= 0L) return "0 Mo"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) {
        String.format(Locale.FRENCH, "%.1f Go", mb / 1024.0)
    } else {
        String.format(Locale.FRENCH, "%.0f Mo", mb.coerceAtLeast(1.0))
    }
}

@Composable
internal fun DownloadsWifiOnlyCard(
    wifiOnly: Boolean,
    onToggle: (Boolean) -> Unit,
) {
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
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(MaterialTheme.shapes.medium)
                .toggleable(
                    value = wifiOnly,
                    role = Role.Switch,
                    onValueChange = onToggle,
                )
                .minimumInteractiveComponentSize()
                .testTag("downloads_wifi_switch"),
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
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = NovayTheme.overlays.onCtaContainer,
                        checkedTrackColor = NovayTheme.overlays.ctaContainer,
                    ),
                )
            }
        }
    }
}

