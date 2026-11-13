package dot.jcversa.novay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import dot.jcversa.novay.data.AnimeEpisode
import dot.jcversa.novay.data.AnimeSource
import dot.jcversa.novay.data.FranchiseEntry
import dot.jcversa.novay.data.ResolvedStream
import dot.jcversa.novay.data.voiranime.VoiranimeParsers
import dot.jcversa.novay.ui.components.AnimateEntrance
import dot.jcversa.novay.ui.components.ErrorView
import dot.jcversa.novay.ui.components.LoadingView
import dot.jcversa.novay.ui.components.bounceClick

/**
 * Écran Lecteur : résout le flux de l'épisode (page → iframe voembed →
 * payload chiffré → HLS) puis le joue avec media3/ExoPlayer, en passant
 * les en-têtes Referer/Origin/UA exigés par le CDN. Les résolutions sont
 * les VRAIES variantes de la master playlist.
 */
@Composable
fun PlayerScreen(
    source: AnimeSource,
    entry: FranchiseEntry,
    episode: AnimeEpisode,
    onBack: () -> Unit
) {
    var stream by remember { mutableStateOf<ResolvedStream?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var retryKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(episode.url, retryKey) {
        loading = true
        error = null
        stream = null
        try {
            stream = source.stream(episode)
        } catch (e: Exception) {
            error = e.message ?: "Erreur inconnue"
        } finally {
            loading = false
        }
    }

    // Écran allumé pendant la lecture.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Retour",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.bounceClick(onClick = onBack)
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = episode.label,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        when {
            loading -> LoadingView(message = "Résolution du flux depuis le lecteur…")
            error != null -> ErrorView(message = error!!, onRetry = { retryKey++ })
            stream != null -> PlayerContent(stream = stream!!)
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun PlayerContent(stream: ResolvedStream) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    // null = master playlist (qualité adaptative « Auto »).
    var selectedQualityUrl by remember(stream) { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    LaunchedEffect(stream, selectedQualityUrl) {
        val url = selectedQualityUrl ?: stream.url
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(stream.headers["User-Agent"] ?: VoiranimeParsers.UA)
            .setDefaultRequestProperties(stream.headers - "User-Agent")
            .setAllowCrossProtocolRedirects(true)
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
        val mediaSource = if (stream.isHls) {
            HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(url))
        } else {
            ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(url))
        }
        player.setMediaSource(mediaSource)
        player.prepare()
        player.playWhenReady = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { it.player = player },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        )

        if (stream.qualities.isNotEmpty()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Qualité (résolutions du lecteur)",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QualityPill(
                        label = "Auto",
                        selected = selectedQualityUrl == null,
                        onClick = { selectedQualityUrl = null }
                    )
                    stream.qualities.forEachIndexed { index, quality ->
                        AnimateEntrance(index = index) {
                            QualityPill(
                                label = quality.label,
                                selected = selectedQualityUrl == quality.url,
                                onClick = { selectedQualityUrl = quality.url }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Changer de qualité relance la lecture au début (V1).",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Flux direct (résolution unique du lecteur).",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}

@Composable
private fun QualityPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.bounceClick(onClick = onClick)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
