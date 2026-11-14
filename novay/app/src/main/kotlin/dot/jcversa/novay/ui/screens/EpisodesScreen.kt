package dot.jcversa.novay.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dot.jcversa.novay.data.AnimeEpisode
import dot.jcversa.novay.data.AnimeSeason
import dot.jcversa.novay.data.AnimeSource
import dot.jcversa.novay.data.QualityOption
import dot.jcversa.novay.data.ResolvedStream
import dot.jcversa.novay.data.sama.SamaParsers
import dot.jcversa.novay.downloads.Downloads
import dot.jcversa.novay.ui.components.AnimateEntrance
import dot.jcversa.novay.ui.components.ErrorView
import dot.jcversa.novay.ui.components.LoadingView
import dot.jcversa.novay.ui.components.SwiftSlateSpinner
import dot.jcversa.novay.ui.components.bounceClick
import dot.jcversa.novay.ui.theme.Amber500
import dot.jcversa.novay.ui.theme.DarkCardBorder
import dot.jcversa.novay.ui.theme.DarkSurfaceCard
import dot.jcversa.novay.ui.theme.DarkSurfaceVariant
import dot.jcversa.novay.ui.theme.MintGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** États de téléchargement d'un épisode, pour l'UI. */
private enum class EpDl { NONE, RESOLVING, QUEUED, DOWNLOADING, DONE, FAILED }

/** Dialog de résolution : les épisodes visés + les options disponibles. */
private data class QualityDialogState(
    val episodes: List<AnimeEpisode>,
    val options: List<QualityOption>,
    /** Lecteurs déjà résolus (numéro d'épisode -> flux par lecteur). */
    val streamsByEpisode: Map<Int, List<ResolvedStream>>
)

/**
 * Écran Épisodes (V2.3) : liste de la saison, téléchargement à la
 * résolution CHOISIE par l'utilisateur (l'appli ne pré-choisit rien) et
 * multi-sélection (appui long) pour télécharger plusieurs épisodes d'un
 * coup. Un épisode téléchargé se lit hors-ligne.
 */
@Composable
fun EpisodesScreen(
    source: AnimeSource,
    animeTitle: String,
    season: AnimeSeason,
    onBack: () -> Unit,
    onOpenPlayer: (episode: AnimeEpisode, offlineStream: ResolvedStream?) -> Unit
) {
    val context = LocalContext.current
    var episodes by remember { mutableStateOf<List<AnimeEpisode>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }
    var toast by remember { mutableStateOf<String?>(null) }

    // État de téléchargement par épisode (polling de l'index media3).
    var infos by remember { mutableStateOf<List<Downloads.DownloadInfo>>(emptyList()) }
    var resolving by remember { mutableStateOf<Int?>(null) }
    var resolvingBatch by remember { mutableStateOf(false) }
    var enqueueLabel by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Multi-sélection (V2.3) : appui long sur un épisode.
    var selectionMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<Int>() }

    // Dialog de résolution — AUCUNE présélection : le tap de l'utilisateur
    // sur une résolution lance le téléchargement.
    var dialog by remember { mutableStateOf<QualityDialogState?>(null) }

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(season.url, retryKey) {
        error = null
        episodes = null
        try {
            episodes = source.episodes(season)
        } catch (e: Exception) {
            error = e.message ?: "Erreur inconnue"
        }
    }

    // Le message d'erreur s'efface tout seul après 4 s.
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(4000)
            toast = null
        }
    }

    // Polling des téléchargements (1 s) : progression et états à jour.
    // L'index media3 est une base SQLite : lecture hors du thread principal.
    LaunchedEffect(season.url) {
        while (true) {
            infos = withContext(Dispatchers.IO) {
                runCatching { Downloads.snapshot(context) }.getOrDefault(emptyList())
            }
            delay(1000)
        }
    }

    val seasonUrl = season.url.trimEnd('/')
    val seasonInfos = infos.filter { it.meta?.seasonUrl?.trimEnd('/') == seasonUrl }

    fun infoFor(episode: AnimeEpisode): Downloads.DownloadInfo? =
        seasonInfos.firstOrNull { it.meta?.episodeNumber == episode.number }

    fun ensurePermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** Un épisode : résout tous ses lecteurs puis propose les résolutions. */
    fun download(episode: AnimeEpisode) {
        if (resolving != null || resolvingBatch) return
        ensurePermission()
        scope.launch {
            resolving = episode.number
            try {
                val streams = source.streams(episode)
                val options = SamaParsers.mergePlayerQualities(streams)
                dialog = QualityDialogState(
                    episodes = listOf(episode),
                    options = options,
                    streamsByEpisode = mapOf(episode.number to streams)
                )
            } catch (e: Exception) {
                toast = e.message ?: "Résolution du flux impossible"
            } finally {
                resolving = null
            }
        }
    }

    /** Un lot d'épisodes : on analyse le premier, les autres suivent. */
    fun downloadSelected() {
        val eps = episodes?.filter { selected.contains(it.number) } ?: return
        if (eps.isEmpty() || resolvingBatch || resolving != null) return
        ensurePermission()
        scope.launch {
            resolvingBatch = true
            try {
                val streams = source.streams(eps.first())
                val options = SamaParsers.mergePlayerQualities(streams)
                dialog = QualityDialogState(
                    episodes = eps,
                    options = options,
                    streamsByEpisode = mapOf(eps.first().number to streams)
                )
            } catch (e: Exception) {
                toast = e.message ?: "Résolution du flux impossible"
            } finally {
                resolvingBatch = false
            }
        }
    }

    /** L'utilisateur a tapé une résolution : met le lot en file. */
    fun confirm(option: QualityOption) {
        val state = dialog ?: return
        dialog = null
        selectionMode = false
        selected.clear()
        scope.launch {
            state.episodes.forEachIndexed { index, episode ->
                enqueueLabel = "${index + 1}/${state.episodes.size}"
                val streams = state.streamsByEpisode[episode.number]
                    ?: runCatching { source.streams(episode) }.getOrDefault(emptyList())
                val stream = streams.getOrNull(option.playerIndex) ?: streams.firstOrNull()
                if (stream == null) {
                    toast = "Épisode ${episode.number} : aucun flux disponible"
                } else {
                    Downloads.startEpisodeDownload(
                        context = context,
                        animeTitle = animeTitle,
                        seasonName = season.name,
                        seasonUrl = season.url,
                        episodeNumber = episode.number,
                        episodeLabel = episode.label,
                        stream = stream,
                        preferredHeight = option.height,
                        onError = { msg -> toast = msg }
                    )
                }
            }
            enqueueLabel = null
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
            Column {
                Text(
                    text = season.name,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Progression de la saison (groupe).
                val done = seasonInfos.count { it.isCompleted }
                if (episodes != null && episodes!!.isNotEmpty() && done > 0) {
                    Text(
                        text = "$done/${episodes!!.size} épisodes téléchargés",
                        style = MaterialTheme.typography.bodySmall,
                        color = MintGreen
                    )
                }
            }
        }
        toast?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        if (resolvingBatch) {
            Text(
                text = "Analyse des lecteurs…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        enqueueLabel?.let {
            Text(
                text = "Mise en file $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(Modifier.height(12.dp))

        when {
            error != null -> ErrorView(message = error!!, onRetry = { retryKey++ })
            episodes == null -> LoadingView(message = "Chargement des épisodes…")
            episodes!!.isEmpty() -> Text(
                text = "Aucun épisode listé pour cette saison.",
                style = MaterialTheme.typography.bodyMedium
            )
            else -> Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(episodes!!, key = { _, e -> e.number }) { index, episode ->
                        val info = infoFor(episode)
                        val state = when {
                            resolving == episode.number -> EpDl.RESOLVING
                            info == null -> EpDl.NONE
                            info.isCompleted -> EpDl.DONE
                            info.isFailed -> EpDl.FAILED
                            info.isActive -> EpDl.DOWNLOADING
                            else -> EpDl.QUEUED // en pause / arrêté
                        }
                        AnimateEntrance(index = index) {
                            EpisodeRow(
                                episode = episode,
                                dlState = state,
                                dlPercent = info?.percent ?: 0f,
                                selectionMode = selectionMode,
                                isSelected = selected.contains(episode.number),
                                onDownload = { download(episode) },
                                onClick = {
                                    if (selectionMode) {
                                        if (selected.contains(episode.number)) {
                                            selected.remove(episode.number)
                                        } else {
                                            selected.add(episode.number)
                                        }
                                    } else {
                                        // Téléchargé : lecture hors-ligne depuis les
                                        // métadonnées persistées (aucune résolution
                                        // réseau). Sinon : résolution en ligne.
                                        val offline = info?.takeIf { it.isCompleted }?.meta?.toStream()
                                        onOpenPlayer(episode, offline)
                                    }
                                },
                                onLongClick = {
                                    if (!selectionMode) {
                                        selectionMode = true
                                        selected.clear()
                                    }
                                    if (!selected.contains(episode.number)) {
                                        selected.add(episode.number)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Barre d'action de la multi-sélection.
        if (selectionMode) {
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = DarkSurfaceCard,
                border = BorderStroke(1.dp, DarkCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        selected.clear()
                        episodes?.forEach { selected.add(it.number) }
                    }) { Text("Tout") }
                    Spacer(Modifier.width(4.dp))
                    Button(
                        enabled = selected.isNotEmpty(),
                        onClick = { downloadSelected() }
                    ) {
                        Text("Télécharger (${selected.size})")
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        selectionMode = false
                        selected.clear()
                    }) { Text("Fermer") }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    // Dialog de résolution : chaque pastille EST l'action — aucune
    // présélection, l'appli ne choisit pas à la place de l'utilisateur.
    dialog?.let { state ->
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(text = "Résolution du téléchargement") },
            text = {
                Column {
                    Text(
                        text = if (state.episodes.size == 1) {
                            "Quelle résolution veux-tu ?"
                        } else {
                            "${state.episodes.size} épisodes — quelle résolution pour tous ?"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    val options = state.options.ifEmpty {
                        listOf(QualityOption(height = 0, label = "Meilleure disponible", playerIndex = 0))
                    }
                    options.forEach { option ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = DarkSurfaceVariant,
                            border = BorderStroke(1.dp, DarkCardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .bounceClick(onClick = { confirm(option) })
                        ) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        text = "Tape une résolution pour lancer le téléchargement.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = { },
            dismissButton = {
                TextButton(onClick = { dialog = null }) { Text("Annuler") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeRow(
    episode: AnimeEpisode,
    dlState: EpDl,
    dlPercent: Float,
    selectionMode: Boolean,
    isSelected: Boolean,
    onDownload: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = DarkSurfaceCard,
        border = BorderStroke(1.dp, if (isSelected) Amber500 else DarkCardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                // Indicateur de sélection (gauche).
                Icon(
                    imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = if (isSelected) "Sélectionné" else "Non sélectionné",
                    tint = if (isSelected) Amber500 else DarkCardBorder,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = episode.number.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = episode.label,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            if (!selectionMode) {
                // État de téléchargement de l'épisode (droite).
                when (dlState) {
                    EpDl.RESOLVING -> SwiftSlateSpinner(size = 16.dp)
                    EpDl.QUEUED, EpDl.DOWNLOADING -> {
                        Text(
                            text = if (dlPercent > 0f) "${dlPercent.toInt()} %" else "…",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    EpDl.DONE -> Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Téléchargé — lecture hors-ligne",
                        tint = MintGreen
                    )
                    EpDl.FAILED -> Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = "Échec — réessayer",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.bounceClick(onClick = onDownload)
                    )
                    EpDl.NONE -> Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = "Télécharger",
                        tint = Amber500,
                        modifier = Modifier.bounceClick(onClick = onDownload)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Lire",
                    tint = Amber500
                )
            }
        }
    }
}
