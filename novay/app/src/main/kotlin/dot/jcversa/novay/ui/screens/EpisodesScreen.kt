package dot.jcversa.novay.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dot.jcversa.novay.data.AnimeEpisode
import dot.jcversa.novay.data.AnimeSeason
import dot.jcversa.novay.data.AnimeSource
import dot.jcversa.novay.data.ResolvedStream
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

/**
 * Écran Épisodes : liste réelle de la saison + téléchargement par épisode
 * (V2). Un épisode téléchargé se lit hors-ligne (cache de l'appli) ; la
 * progression se suit ici ou dans l'écran Téléchargements.
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
    val scope = rememberCoroutineScope()

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    // Le message d'erreur s'efface tout seul apres 4 s.
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(4000)
            toast = null
        }
    }

    LaunchedEffect(season.url, retryKey) {
        error = null
        episodes = null
        try {
            episodes = source.episodes(season)
        } catch (e: Exception) {
            error = e.message ?: "Erreur inconnue"
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

    fun download(episode: AnimeEpisode) {
        if (resolving != null) return
        // Notification de progression (Android 13+) : demandée une fois,
        // poliment — un refus n'interrompt pas les téléchargements.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        scope.launch {
            resolving = episode.number
            try {
                val stream = source.stream(episode)
                Downloads.startEpisodeDownload(
                    context = context,
                    animeTitle = animeTitle,
                    seasonName = season.name,
                    seasonUrl = season.url,
                    episodeNumber = episode.number,
                    episodeLabel = episode.label,
                    stream = stream,
                    onStart = { },
                    onError = { msg ->
                        toast = msg
                    }
                )
            } catch (e: Exception) {
                toast = e.message ?: "Résolution du flux impossible"
            } finally {
                resolving = null
            }
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
                text = season.name,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        toast?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp)
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
            else -> LazyColumn(
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
                            onDownload = { download(episode) },
                            onClick = {
                                // Téléchargé : lecture hors-ligne depuis les
                                // métadonnées persistées (aucune résolution
                                // réseau). Sinon : résolution en ligne.
                                val offline = info?.takeIf { it.isCompleted }?.meta?.toStream()
                                onOpenPlayer(episode, offline)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: AnimeEpisode,
    dlState: EpDl,
    dlPercent: Float,
    onDownload: () -> Unit,
    onClick: () -> Unit
) {
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
            // V2 : état de téléchargement de l'épisode.
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
