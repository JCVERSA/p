package dot.jcversa.novay.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Wifi
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dot.jcversa.novay.downloads.Downloads
import dot.jcversa.novay.ui.LocalBottomChromePadding
import dot.jcversa.novay.ui.components.AppBarTitle
import dot.jcversa.novay.ui.components.NovayAppBar
import dot.jcversa.novay.ui.theme.NovayThemeState
import dot.jcversa.novay.ui.theme.header
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val bottomChrome = LocalBottomChromePadding.current
    val scope = rememberCoroutineScope()
    var amoled by remember { mutableStateOf(NovayThemeState.amoled) }
    var wifiOnly by remember { mutableStateOf(false) }
    var activeCount by remember { mutableIntStateOf(0) }
    var totalCount by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            var infos = emptyList<Downloads.DownloadInfo>()
            var isPaused = false
            var isWifi = false
            withContext(Dispatchers.IO) {
                infos = runCatching { Downloads.snapshot(context) }.getOrDefault(emptyList())
                runCatching { isPaused = Downloads.manager(context).downloadsPaused }
                isWifi = Downloads.isWifiOnly(context)
            }
            activeCount = infos.count { it.isActive }
            totalCount = infos.count { !it.isCompleted }
            paused = isPaused
            wifiOnly = isWifi
            delay(2000)
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
                        icon = Icons.Rounded.Download,
                        modifier = Modifier.testTag("more_open_downloads"),
                        onPreferenceClick = onOpenDownloads,
                    )
                }

                Spacer(Modifier.height(18.dp))
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
                            icon = Icons.Rounded.DarkMode,
                            checked = amoled,
                            testTagName = "more_amoled_switch",
                            onCheckedChanged = { checked ->
                                amoled = checked
                                NovayThemeState.setAmoled(context, checked)
                            },
                        )
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        SwitchPreferenceWidget(
                            title = "Wi-Fi uniquement",
                            subtitle = "Met en pause en 4G/5G et reprend automatiquement en Wi-Fi.",
                            icon = Icons.Rounded.Wifi,
                            checked = wifiOnly,
                            testTagName = "more_wifi_switch",
                            onCheckedChanged = { checked ->
                                wifiOnly = checked
                                scope.launch(Dispatchers.IO) {
                                    Downloads.setWifiOnly(context, checked)
                                }
                            },
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                SectionLabel("À propos")
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "NoVaY — Cinema Midnight Noir",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
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
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.header,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
    )
}

@Composable
private fun TextPreferenceWidget(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onPreferenceClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onPreferenceClick)
            .minimumInteractiveComponentSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
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
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
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
    onCheckedChanged: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChanged(!checked) }
            .minimumInteractiveComponentSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
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
            onCheckedChange = onCheckedChanged,
            modifier = Modifier.testTag(testTagName),
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
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
