package dot.jcversa.novay.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dot.jcversa.novay.R
import dot.jcversa.novay.downloads.Downloads
import dot.jcversa.novay.performance.LocalPerformanceProfile
import dot.jcversa.novay.performance.NovayPerformanceState
import dot.jcversa.novay.performance.PerformanceMode
import dot.jcversa.novay.ui.LocalBottomChromePadding
import dot.jcversa.novay.ui.components.AppBarTitle
import dot.jcversa.novay.ui.components.LocalNovayToast
import dot.jcversa.novay.ui.components.LocalSlateMotion
import dot.jcversa.novay.ui.components.MotionPreference
import dot.jcversa.novay.ui.components.NovayAppBar
import dot.jcversa.novay.ui.components.NovayBrandEditorialHeader
import dot.jcversa.novay.ui.components.NovayIcons
import dot.jcversa.novay.ui.components.NovayLottieIcon
import dot.jcversa.novay.ui.components.NovayMotionState
import dot.jcversa.novay.ui.components.NovaySectionHeader
import dot.jcversa.novay.ui.components.NovayToastTone
import dot.jcversa.novay.ui.theme.LocalDynamicPosterAccent
import dot.jcversa.novay.ui.theme.NovayTheme
import dot.jcversa.novay.ui.theme.NovayThemePreset
import dot.jcversa.novay.ui.theme.NovayThemeState
import dot.jcversa.novay.ui.theme.previewThemeFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Onglet Plus — Cinema Midnight Noir :
 * cartes groupées (File de téléchargements, Apparence AMOLED, Wi-Fi uniquement,
 * À propos & Crédits) centrées sur tablettes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onOpenDownloads: () -> Unit
) {
    val context = LocalContext.current
    val toast = LocalNovayToast.current
    val bottomChrome = LocalBottomChromePadding.current
    val scope = rememberCoroutineScope()
    val amoled = NovayThemeState.amoled
    val activePreset = NovayThemeState.preset
    val dynamicAccent = LocalDynamicPosterAccent.current
    val motionPreference = NovayMotionState.preference
    val performanceMode = NovayPerformanceState.mode
    val activePerformanceProfile = LocalPerformanceProfile.current
    var wifiOnly by remember { mutableStateOf(Downloads.isWifiOnly(context)) }
    var activeCount by remember { mutableIntStateOf(0) }
    var totalCount by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }

    LaunchedEffect(context) {
        Downloads.observeQueue(context).collect { queue ->
            activeCount = queue.items.count { it.isActive }
            totalCount = queue.items.count { !it.isCompleted }
            paused = queue.paused
        }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
    ) {
        NovayAppBar(
            titleContent = { AppBarTitle(title = "Plus") },
            scrollBehavior = scrollBehavior,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .padding(bottom = 28.dp + bottomChrome),
            ) {
                SectionLabel("Téléchargements")
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextPreferenceWidget(
                        title = "File de téléchargements",
                        subtitle = when {
                            activeCount > 0 -> "$activeCount en cours · ${totalCount - activeCount} en file"
                            paused && totalCount > 0 -> "En pause · $totalCount en attente"
                            totalCount > 0 -> "$totalCount en attente"
                            else -> "Aucun téléchargement actif"
                        },
                        icon = NovayIcons.Download,
                        lottieRes = R.raw.lottie_download,
                        modifier = Modifier.testTag("more_open_downloads"),
                        onPreferenceClick = onOpenDownloads,
                    )
                }

                Spacer(Modifier.height(18.dp))
                SectionLabel(stringResource(R.string.more_appearance_title))
                ThemePresetPreviewCarousel(
                    activePreset = activePreset,
                    dynamicAccent = dynamicAccent,
                    onSelectPreset = { selected ->
                        NovayThemeState.setPreset(context, selected)
                        toast.show("Thème : ${selected.label}", NovayToastTone.SUCCESS)
                    },
                )

                Spacer(Modifier.height(14.dp))
                SectionLabel("Réglages")
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        SwitchPreferenceWidget(
                            title = "Noir pur AMOLED",
                            subtitle = "Remplace le Midnight Noir par un noir absolu sur écrans OLED.",
                            icon = NovayIcons.AmoledMoon,
                            checked = amoled,
                            testTagName = "more_amoled_switch",
                            onCheckedChanged = { checked ->
                                NovayThemeState.setAmoled(context, checked)
                                toast.show(
                                    if (checked) "Mode OLED Noir pur activé" else "Mode Noir cinéma rétabli",
                                    NovayToastTone.INFO,
                                )
                            },
                        )
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        SwitchPreferenceWidget(
                            title = "Wi-Fi uniquement",
                            subtitle = "Met en pause en 4G/5G et reprend automatiquement en Wi-Fi.",
                            icon = NovayIcons.WifiSignal,
                            lottieRes = R.raw.lottie_wifi,
                            restartKey = wifiOnly,
                            checked = wifiOnly,
                            testTagName = "more_wifi_switch",
                            onCheckedChanged = { checked ->
                                wifiOnly = checked
                                scope.launch(Dispatchers.IO) {
                                    Downloads.setWifiOnly(context, checked)
                                }
                                toast.show(
                                    if (checked) "Téléchargements limités au Wi-Fi" else "Téléchargements autorisés sur réseau mobile",
                                    NovayToastTone.INFO,
                                )
                            },
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                SectionLabel(stringResource(R.string.more_motion_title))
                MotionPreferenceCard(
                    selected = motionPreference,
                    onSelect = { pref ->
                        NovayMotionState.setPreference(context, pref)
                        toast.show("Animations : ${pref.label}", NovayToastTone.INFO)
                    },
                )

                Spacer(Modifier.height(18.dp))
                SectionLabel(stringResource(R.string.more_performance_title))
                PerformancePreferenceCard(
                    selected = performanceMode,
                    activeReason = activePerformanceProfile.effectiveReason,
                    onSelect = { mode ->
                        NovayPerformanceState.setMode(context, mode)
                        toast.show("Performance : ${mode.label}", NovayToastTone.INFO)
                    },
                )

                Spacer(Modifier.height(18.dp))
                SectionLabel("À propos")
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        NovayBrandEditorialHeader()
                        AboutText("Streaming anime VF/VOSTFR et lecture hors-ligne avec Media3 ExoPlayer.")
                        AboutText(
                            "Architecture d'interface inspirée de Mihon (github.com/mihonapp/mihon, Apache-2.0)."
                        )
                        AboutText(
                            "SpeedMeter adapté de Downpour (github.com/AlirezaJavan/Downpour, Apache-2.0). " +
                                "Gestes du lecteur inspirés de NextPlayer."
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemePresetPreviewCarousel(
    activePreset: NovayThemePreset,
    dynamicAccent: Color?,
    onSelectPreset: (NovayThemePreset) -> Unit,
) {
    val motion = LocalSlateMotion.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("more_theme_presets_row"),
    ) {
        items(NovayThemePreset.entries, key = { it.id }) { preset ->
            val selected = preset == activePreset
            val (previewScheme, previewOverlays) = remember(preset, dynamicAccent) {
                previewThemeFor(preset, dynamicAccent)
            }
            val borderColor by animateColorAsState(
                targetValue = if (selected) previewScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                animationSpec = motion.colorSpec(180),
                label = "theme_preview_border_${preset.id}",
            )
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = previewScheme.surfaceContainerLow,
                border = BorderStroke(if (selected) 1.5.dp else 0.7.dp, borderColor),
                modifier = Modifier
                    .width(154.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .semantics(mergeDescendants = true) {}
                    .clickable { onSelectPreset(preset) }
                    .minimumInteractiveComponentSize()
                    .testTag("more_theme_preset_${preset.id}"),
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Mini aperçu visuel de l'écran et des surfaces du thème
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(
                                    0f to previewScheme.background,
                                    1f to previewScheme.surfaceContainerHigh,
                                )
                            )
                            .padding(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 24.dp, height = 36.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                previewScheme.primary.copy(alpha = 0.85f),
                                                previewOverlays.ctaContainer,
                                            )
                                        )
                                    ),
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.85f)
                                        .height(6.dp)
                                        .clip(CircleShape)
                                        .background(previewScheme.onSurface.copy(alpha = 0.85f)),
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.55f)
                                        .height(5.dp)
                                        .clip(CircleShape)
                                        .background(previewScheme.onSurfaceVariant.copy(alpha = 0.6f)),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp, 6.dp)
                                            .clip(CircleShape)
                                            .background(previewOverlays.ctaContainer),
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp, 6.dp)
                                            .clip(CircleShape)
                                            .background(previewScheme.secondary),
                                    )
                                }
                            }
                        }
                        if (selected) {
                            Surface(
                                shape = CircleShape,
                                color = previewOverlays.ctaContainer,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(18.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = NovayIcons.Check,
                                        contentDescription = "Actif",
                                        tint = previewOverlays.onCtaContainer,
                                        modifier = Modifier.size(12.dp),
                                    )
                                }
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = preset.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) previewScheme.primary else previewScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = preset.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = previewScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MotionPreferenceCard(
    selected: MotionPreference,
    onSelect: (MotionPreference) -> Unit,
) {
    val overlays = NovayTheme.overlays
    val motion = LocalSlateMotion.current
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MotionPreference.entries.forEach { option ->
                    val active = option == selected
                    val fill by animateColorAsState(
                        targetValue = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        animationSpec = motion.colorSpec(180),
                        label = "motion_fill_${option.id}",
                    )
                    val borderColor by animateColorAsState(
                        targetValue = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        animationSpec = motion.colorSpec(180),
                        label = "motion_border_${option.id}",
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = fill,
                        border = BorderStroke(if (active) 1.dp else 0.5.dp, borderColor),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(option) }
                            .minimumInteractiveComponentSize()
                            .testTag("more_motion_${option.id}"),
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (active) overlays.crimsonOnContainer else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Text(
                text = selected.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PerformancePreferenceCard(
    selected: PerformanceMode,
    activeReason: String,
    onSelect: (PerformanceMode) -> Unit,
) {
    val overlays = NovayTheme.overlays
    val motion = LocalSlateMotion.current
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PerformanceMode.entries.forEach { option ->
                    val active = option == selected
                    val fill by animateColorAsState(
                        targetValue = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        animationSpec = motion.colorSpec(180),
                        label = "perf_fill_${option.id}",
                    )
                    val borderColor by animateColorAsState(
                        targetValue = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        animationSpec = motion.colorSpec(180),
                        label = "perf_border_${option.id}",
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = fill,
                        border = BorderStroke(if (active) 1.dp else 0.5.dp, borderColor),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(option) }
                            .minimumInteractiveComponentSize()
                            .testTag("more_performance_${option.id}"),
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = option.shortLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (active) overlays.crimsonOnContainer else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Text(
                text = selected.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Profil actif : $activeReason",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("more_performance_active_reason"),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    NovaySectionHeader(
        title = text,
        textColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
    )
}

@Composable
private fun TextPreferenceWidget(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    // V2.7 : icône animée Lottie en lieu et place de l'ImageVector.
    lottieRes: Int? = null,
    onPreferenceClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onPreferenceClick)
            .minimumInteractiveComponentSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (lottieRes != null) {
            NovayLottieIcon(
                res = lottieRes,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                size = 24.dp,
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = NovayIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SwitchPreferenceWidget(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    testTagName: String,
    // V2.7 : icône animée Lottie en lieu et place de l'ImageVector.
    lottieRes: Int? = null,
    restartKey: Any? = null,
    onCheckedChanged: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChanged,
            )
            .minimumInteractiveComponentSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(testTagName),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (lottieRes != null) {
            NovayLottieIcon(
                res = lottieRes,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                size = 24.dp,
                restartKey = restartKey,
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = NovayTheme.overlays.onCtaContainer,
                checkedTrackColor = NovayTheme.overlays.ctaContainer,
            ),
        )
    }
}

@Composable
private fun AboutText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
