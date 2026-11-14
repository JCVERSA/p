package dot.jcversa.novay.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dot.jcversa.novay.downloads.Downloads
import dot.jcversa.novay.ui.components.AppBarTitle
import dot.jcversa.novay.ui.components.NovayAppBar
import dot.jcversa.novay.ui.components.bounceClick
import dot.jcversa.novay.ui.theme.NovayThemeState
import dot.jcversa.novay.ui.theme.header
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Onglet Plus (V2.4, design Mihon MoreScreen) : file de téléchargements
 * avec état en sous-texte, réglages (AMOLED, Wi-Fi uniquement) et À propos
 * avec les crédits des projets dont le design/code est porté.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onOpenDownloads: () -> Unit
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var amoled by remember { mutableStateOf(NovayThemeState.amoled) }
    var wifiOnly by remember { mutableStateOf(false) }
    var activeCount by remember { mutableIntStateOf(0) }
    var totalCount by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }

    // État de la file, rafraîchi doucement (sous-texte de la ligne).
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

    Column(modifier = Modifier.fillMaxSize()) {
        NovayAppBar(
            titleContent = { AppBarTitle(title = "Plus") },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Téléchargements",
                style = MaterialTheme.typography.header,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            TextPreferenceWidget(
                title = "File de téléchargements",
                subtitle = when {
                    activeCount > 0 -> "$activeCount en cours · ${totalCount - activeCount} en file"
                    paused && totalCount > 0 -> "En pause · $totalCount en attente"
                    totalCount > 0 -> "$totalCount en attente"
                    else -> "Aucun téléchargement"
                },
                icon = Icons.Rounded.Download,
                onPreferenceClick = onOpenDownloads,
            )

            Text(
                text = "Réglages",
                style = MaterialTheme.typography.header,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            SwitchPreferenceWidget(
                title = "Thème AMOLED",
                subtitle = "Noir pur — économise la batterie des écrans AMOLED.",
                icon = Icons.Rounded.DarkMode,
                checked = amoled,
                onCheckedChanged = { checked ->
                    amoled = checked
                    NovayThemeState.setAmoled(context, checked)
                },
            )
            SwitchPreferenceWidget(
                title = "Wi-Fi uniquement",
                subtitle = "Les téléchargements se mettent en pause en 4G et reprennent en Wi-Fi.",
                icon = Icons.Rounded.Wifi,
                checked = wifiOnly,
                onCheckedChanged = { checked ->
                    wifiOnly = checked
                    scope.launch(Dispatchers.IO) {
                        Downloads.setWifiOnly(context, checked)
                    }
                },
            )

            Text(
                text = "À propos",
                style = MaterialTheme.typography.header,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AboutText("NoVaY 2.4 — streaming anime, lecteur media3/ExoPlayer.")
                AboutText(
                    "Interface portée de Mihon (fork de Tachiyomi, " +
                        "github.com/mihonapp/mihon) — licence Apache-2.0."
                )
                AboutText(
                    "SpeedMeter adapté de Downpour (github.com/AlirezaJavan/Downpour, " +
                        "Apache-2.0). Pauses/reprises inspirées de Ketch et TurboDownloader."
                )
            }
        }
    }
}

@Composable
private fun TextPreferenceWidget(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onPreferenceClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(onClick = onPreferenceClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
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
}

@Composable
private fun SwitchPreferenceWidget(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChanged: (Boolean) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
            Switch(checked = checked, onCheckedChange = onCheckedChanged)
        }
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
