package dot.jcversa.novay.ui.screens

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.pm.ActivityInfo
import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
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
import dot.jcversa.novay.data.AnimeSeason
import dot.jcversa.novay.data.ResolvedStream
import dot.jcversa.novay.data.WatchHistory
import dot.jcversa.novay.data.WatchHistoryEntry
import dot.jcversa.novay.data.voiranime.VoiranimeParsers
import dot.jcversa.novay.downloads.Downloads
import dot.jcversa.novay.ui.components.AnimateEntrance
import dot.jcversa.novay.ui.components.ErrorView
import dot.jcversa.novay.ui.components.LoadingView
import dot.jcversa.novay.ui.components.bounceClick
import dot.jcversa.novay.ui.player.DoubleTapSeekAccumulator
import dot.jcversa.novay.ui.player.PlayerGestureMath
import dot.jcversa.novay.ui.theme.Amber500
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Écran Lecteur (V2.1 : plein écran) : résout le flux de l'épisode puis le
 * joue en PLEIN ÉCRAN — passage paysage automatique, barres système
 * masquées (immersion), contrôles Compose maison (lecture/pause, barre de
 * progression, temps) et double-tap ±10 s. Si l'épisode est téléchargé,
 * la lecture passe par le cache offline — aucune requête réseau.
 */
@Composable
fun PlayerScreen(
    source: AnimeSource,
    season: AnimeSeason,
    episode: AnimeEpisode,
    animeTitle: String = "",
    posterUrl: String? = null,
    initialStream: ResolvedStream? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    // Lecture hors-ligne : flux fourni par l'écran Téléchargements, ou
    // épisode téléchargé (vérifié au chargement — index SQLite hors du
    // thread principal).
    var offline by remember(episode.url, episode.number) { mutableStateOf(initialStream != null) }
    var stream by remember { mutableStateOf<ResolvedStream?>(initialStream) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(initialStream == null) }
    var retryKey by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    // V2.2 : reprise a la position exacte ou l episode s etait arrete
    // (index SharedPreferences, hors du thread principal).
    var resumePositionMs by remember(episode.url, episode.number) { mutableStateOf(0L) }
    LaunchedEffect(episode.url, episode.number) {
        resumePositionMs = withContext(Dispatchers.IO) {
            WatchHistory.positionOf(context, season.url, episode.number)
        }
    }

    // Enregistrement de la position (periodique + fermeture du lecteur).
    val recordProgress: (Long, Long) -> Unit = { pos, dur ->
        scope.launch(Dispatchers.IO) {
            WatchHistory.recordPosition(context, season.url, episode.number, pos, dur)
        }
    }

    LaunchedEffect(episode.url, retryKey) {
        if (initialStream == null) {
            offline = withContext(Dispatchers.IO) {
                runCatching {
                    Downloads.isDownloaded(context, season.url, episode.number)
                }.getOrDefault(false)
            }
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
    }

    // Historique « Reprendre la lecture » : enregistré dès qu'un flux est
    // prêt (résolu en ligne OU lu hors-ligne), pour reprendre depuis
    // l'accueil sans repasser par la recherche.
    LaunchedEffect(stream, offline) {
        if (stream != null) {
            WatchHistory.record(
                context,
                WatchHistoryEntry(
                    animeTitle = animeTitle.ifEmpty { season.name },
                    animeUrl = episode.url,
                    posterUrl = posterUrl,
                    seasonName = season.name,
                    seasonUrl = season.url,
                    episodeNumber = episode.number,
                    episodeLabel = episode.label,
                    lienIds = episode.lienIds,
                    jeton = episode.jeton,
                    watchedAt = System.currentTimeMillis()
                )
            )
        }
    }

    // Écran allumé pendant la lecture.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    when {
        loading -> Column(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            PlayerHeader(
                title = episode.label,
                subtitle = season.name + if (offline) " · hors-ligne" else "",
                onBack = onBack
            )
            LoadingView(message = "Résolution du flux depuis le lecteur…")
        }
        error != null -> Column(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            PlayerHeader(
                title = episode.label,
                subtitle = season.name,
                onBack = onBack
            )
            ErrorView(message = error!!, onRetry = { retryKey++ })
        }
        stream != null -> PlayerContent(
            stream = stream!!,
            offline = offline,
            title = episode.label,
            subtitle = season.name + if (offline) " · hors-ligne" else "",
            resumePositionMs = resumePositionMs,
            onProgress = recordProgress,
            onBack = onBack
        )
    }
}

/** En-tête simple des états de chargement/erreur (mode portrait). */
@Composable
private fun PlayerHeader(title: String, subtitle: String, onBack: () -> Unit) {
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
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** mm:ss (ou h:mm:ss au-delà d'une heure). */
private fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0L)) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun PlayerContent(
    stream: ResolvedStream,
    offline: Boolean,
    title: String,
    subtitle: String,
    resumePositionMs: Long = 0L,
    onProgress: (positionMs: Long, durationMs: Long) -> Unit = { _, _ -> },
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val player = remember {
        ExoPlayer.Builder(context)
            // V2.2 : citoyen audio correct - le focus est demandé (une
            // autre appli qui parle nous met en pause, on rend la main à
            // la sortie) et débrancher le casque MET EN PAUSE au lieu de
            // basculer à fond sur le haut-parleur.
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
    }
    // null = master playlist (qualité adaptative « Auto »).
    var selectedQualityUrl by remember(stream) { mutableStateOf<String?>(null) }

    // Contrôles maison : visibilité, lecture, progression, feedback de seek.
    var controlsVisible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var userSeeking by remember { mutableStateOf(false) }
    var seekFeedback by remember { mutableStateOf<SeekFeedback?>(null) }
    var showQualities by remember { mutableStateOf(false) }
    // V2.2 (étape 7) : verrouillage des contrôles — les gestes et les
    // contrôles se figent, seul un bouton déverrouille.
    var controlsLocked by remember { mutableStateOf(false) }
    // V2.2 étape 5 : vitesse de lecture (bouton) + appui long = 2x
    // tant que le doigt reste posé.
    var playbackSpeed by remember { mutableStateOf(1f) }
    var showSpeed by remember { mutableStateOf(false) }
    var longPressFast by remember { mutableStateOf(false) }
    // V2.2 étape 2 : glissé vertical — luminosité (gauche) / volume
    // (droite), avec indicateur %.
    var verticalFeedback by remember { mutableStateOf<VerticalFeedback?>(null) }
    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    // Position : callback toujours frais dans les effets (polling/dispose).
    val currentOnProgress by rememberUpdatedState(onProgress)
    val window = (context as? Activity)?.window

    fun applyVolume(percent: Int) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val target = (percent * max / 100).coerceIn(0, max)
        runCatching { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0) }
    }

    fun applyBrightness(percent: Int) {
        val lp = window?.attributes ?: return
        lp.screenBrightness = percent / 100f
        window.attributes = lp
    }
    // V2.2 : double-tap cumulatif (inspire nextplayer - comportement).
    val doubleTapSeek = remember { DoubleTapSeekAccumulator() }

    // Plein écran : paysage automatique + barres système masquées.
    // Tout est restauré à la sortie (retour, ou fermeture de l'écran).
    DisposableEffect(Unit) {
        val activity = view.context as? Activity
        val window = activity?.window
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val controller = window?.let { WindowInsetsControllerCompat(it, view) }
        window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller?.show(WindowInsetsCompat.Type.systemBars())
            window?.let { WindowCompat.setDecorFitsSystemWindows(it, true) }
            player.release()
        }
    }

    LaunchedEffect(stream, selectedQualityUrl, offline) {
        // Changement de qualite en cours de lecture : on conserve la
        // position (0 au premier chargement, rien a restaurer).
        val keepMs = player.currentPosition.coerceAtLeast(0L)
        val url = selectedQualityUrl ?: stream.url
        val mediaSource = if (offline) {
            // V2 : cache offline d'abord, réseau en repli (jamais écrit).
            Downloads.offlineMediaSource(context, stream)
        } else {
            val httpFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(stream.headers["User-Agent"] ?: VoiranimeParsers.UA)
                .setDefaultRequestProperties(stream.headers - "User-Agent")
                .setAllowCrossProtocolRedirects(true)
            val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
            if (stream.isHls) {
                HlsMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(url))
            } else {
                ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(url))
            }
        }
        player.setMediaSource(mediaSource)
        player.prepare()
        player.playWhenReady = true
        if (keepMs > 0L) player.seekTo(keepMs)
    }

    // V2.2 : reprise a la position exacte - des que la duree est connue
    // (média préparé), on saute a la position enregistree. Une seule fois.
    var resumed by remember { mutableStateOf(false) }
    LaunchedEffect(resumePositionMs, player) {
        if (resumePositionMs <= 0L || resumed) return@LaunchedEffect
        while (player.duration <= 0L) delay(200)
        player.seekTo(resumePositionMs)
        resumed = true
    }

    // Enregistrement de la position : toutes les 5 s pendant la lecture
    // (une coupure brutale ne perd que 5 s max). Plus de 95 % parcouru =
    // episode fini : on reprendra du debut la prochaine fois.
    LaunchedEffect(player) {
        while (true) {
            delay(5000)
            if (!player.isPlaying) continue
            val pos = player.currentPosition
            val dur = player.duration
            if (dur > 0L && pos > 3000L) {
                val effective = if (pos > dur * 95L / 100L) 0L else pos
                currentOnProgress(effective, dur)
            }
        }
    }

    // ... et la position finale a la fermeture du lecteur. Déclaré APRÈS
    // le DisposableEffect du plein ecran (ordre LIFO : ce dispose-la
    // s execute AVANT le release du player).
    DisposableEffect(player) {
        onDispose {
            val pos = player.currentPosition
            val dur = player.duration
            if (dur > 0L && pos > 3000L) {
                val effective = if (pos > dur * 95L / 100L) 0L else pos
                currentOnProgress(effective, dur)
            }
        }
    }

    // Progression + état de lecture (polling léger, 2 fois par seconde).
    LaunchedEffect(player) {
        while (true) {
            isPlaying = player.isPlaying
            durationMs = player.duration.coerceAtLeast(0L)
            if (!userSeeking) positionMs = player.currentPosition.coerceAtLeast(0L)
            delay(500)
        }
    }

    // Les contrôles se masquent seuls après 3,5 s (uniquement en lecture).
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(3500)
            controlsVisible = false
        }
    }

    // Les feedbacks (seek, volume, luminosite) s'effacent 800 ms apres
    // la derniere mise a jour (un glisse continu les garde affiches).
    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(800)
            seekFeedback = null
        }
    }

    LaunchedEffect(verticalFeedback) {
        if (verticalFeedback != null) {
            delay(800)
            verticalFeedback = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false // contrôles Compose maison
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize()
        )

        // Couche de gestes 1 : tap = afficher/masquer les contrôles,
        // double-tap = ±10 s CUMULATIF (moitié gauche/droite de l'écran,
        // tap-tap-tap à droite = +10 s, +20 s, +30 s — cf. nextplayer).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(player, durationMs, controlsLocked) {
                    if (controlsLocked) return@pointerInput
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        // Appui long : vitesse 2x jusqu'au relâchement
                        // (passer un générique), puis retour à la vitesse
                        // choisie.
                        onLongPress = {
                            if (player.isPlaying) {
                                longPressFast = true
                                player.setPlaybackSpeed(2f)
                            }
                        },
                        onPress = {
                            tryAwaitRelease()
                            if (longPressFast) {
                                longPressFast = false
                                player.setPlaybackSpeed(playbackSpeed)
                            }
                        },
                        onDoubleTap = { offset ->
                            val forward = offset.x > size.width / 2
                            val inc = if (forward) {
                                PlayerGestureMath.DOUBLE_TAP_INCREMENT_MS
                            } else {
                                -PlayerGestureMath.DOUBLE_TAP_INCREMENT_MS
                            }
                            val target = (player.currentPosition + inc)
                                .coerceIn(0L, if (durationMs > 0) durationMs else Long.MAX_VALUE)
                            player.seekTo(target)
                            // L'indicateur montre le CUMUL de la rafale.
                            seekFeedback = SeekFeedback(
                                deltaMs = doubleTapSeek.tap(forward),
                                targetMs = player.currentPosition
                            )
                        }
                    )
                }
        )

        // Couche de gestes 2 : glissé horizontal = seek progressif
        // (50 ms de vidéo par pixel, borné à la durée). La vidéo suit le
        // doigt (scrubbing mode) au lieu de saccader.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(player, durationMs, controlsLocked) {
                    if (controlsLocked) return@pointerInput
                    // Seuil de direction (~8 dp, slop tactile standard) :
                    // en dessous, le doigt est considéré immobile.
                    val slop = 8.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var seeking = false
                        var verticalIsBrightness = false
                        var verticalStartPct = 0
                        var verticalActive = false
                        val startX = down.position.x
                        val startPosition = player.currentPosition
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val dx = change.position.x - startX
                            val dy = change.position.y - down.position.y
                            if (!seeking && !verticalActive) {
                                when {
                                    // Horizontal : seek progressif.
                                    abs(dx) > slop && abs(dx) > abs(dy) -> {
                                        seeking = true
                                        player.isScrubbingModeEnabled = true
                                    }
                                    // Vertical : luminosité (moitié gauche)
                                    // ou volume (moitié droite).
                                    abs(dy) > slop && abs(dy) > abs(dx) -> {
                                        verticalActive = true
                                        verticalIsBrightness = down.position.x < size.width / 2
                                        verticalStartPct = if (verticalIsBrightness) {
                                            val current = window?.attributes?.screenBrightness ?: -1f
                                            if (current >= 0f) (current * 100).toInt() else 50
                                        } else {
                                            val max = audioManager
                                                .getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                                .coerceAtLeast(1)
                                            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max
                                        }
                                    }
                                }
                            }
                            if (seeking) {
                                change.consume()
                                val target = PlayerGestureMath.dragSeekTarget(
                                    startPositionMs = startPosition,
                                    startX = startX,
                                    currentX = change.position.x,
                                    durationMs = durationMs
                                )
                                player.seekTo(target)
                                seekFeedback = SeekFeedback(
                                    deltaMs = target - startPosition,
                                    targetMs = target
                                )
                            } else if (verticalActive) {
                                change.consume()
                                val pct = PlayerGestureMath.verticalTarget(
                                    startPercentage = verticalStartPct,
                                    startY = down.position.y,
                                    currentY = change.position.y
                                )
                                if (verticalIsBrightness) applyBrightness(pct) else applyVolume(pct)
                                verticalFeedback = VerticalFeedback(
                                    isBrightness = verticalIsBrightness,
                                    percentage = pct
                                )
                            }
                        }
                        if (seeking) player.isScrubbingModeEnabled = false
                    }
                }
        )

        // Vitesse 2× pendant l'appui long.
        if (longPressFast) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xB3000000),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp)
            ) {
                Text(
                    text = "2×",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                )
            }
        }

        // Feedback volume / luminosité : icône + pourcentage, au centre.
        verticalFeedback?.let { vf ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xB3000000),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (vf.isBrightness) {
                            Icons.Outlined.BrightnessMedium
                        } else {
                            Icons.AutoMirrored.Filled.VolumeUp
                        },
                        contentDescription = null,
                        tint = Color.White
                    )
                    Text(
                        text = "${vf.percentage} %",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
            }
        }

        // Feedback de seek : côté tapé, delta cumulé + temps cible.
        seekFeedback?.let { fb ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xB3000000),
                modifier = Modifier
                    .align(if (fb.deltaMs >= 0) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 28.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = (if (fb.deltaMs >= 0) "+" else "−") +
                            "${abs(fb.deltaMs) / 1000} s",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Text(
                        text = formatTime(fb.targetMs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xCCFFFFFF)
                    )
                }
            }
        }

        // Contrôles maison (fondu).
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x99000000),
                                Color.Transparent,
                                Color.Transparent,
                                Color(0xB3000000)
                            )
                        )
                    )
            ) {
                // Barre du haut : retour, titre, qualité.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Retour",
                        tint = Color.White,
                        modifier = Modifier
                            .size(28.dp)
                            .bounceClick(onClick = onBack)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xCCFFFFFF),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // Rotation : bascule paysage/portrait, chacun en
                    // mode capteur (suit le sens où le téléphone est tenu).
                    Icon(
                        imageVector = Icons.Outlined.ScreenRotation,
                        contentDescription = "Rotation",
                        tint = Color.White,
                        modifier = Modifier
                            .size(28.dp)
                            .bounceClick(onClick = {
                                val activity = view.context as? Activity
                                activity?.requestedOrientation =
                                    if (activity?.resources?.configuration?.orientation ==
                                        Configuration.ORIENTATION_LANDSCAPE
                                    ) {
                                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                    } else {
                                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    }
                            })
                    )
                    if (!offline && stream.qualities.isNotEmpty()) {
                        Spacer(Modifier.width(12.dp))
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = "Qualité",
                            tint = if (showQualities) Amber500 else Color.White,
                            modifier = Modifier
                                .size(28.dp)
                                .bounceClick(onClick = { showQualities = !showQualities })
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Icon(
                        imageVector = Icons.Outlined.Speed,
                        contentDescription = "Vitesse de lecture",
                        tint = if (showSpeed || playbackSpeed != 1f) Amber500 else Color.White,
                        modifier = Modifier
                            .size(28.dp)
                            .bounceClick(onClick = { showSpeed = !showSpeed })
                    )
                }

                // Lecture / pause au centre.
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Lecture",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(64.dp)
                        .bounceClick(onClick = { if (player.isPlaying) player.pause() else player.play() })
                )

                // Choix de qualité (par-dessus la vidéo).
                AnimatedVisibility(
                    visible = showQualities && !offline && stream.qualities.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 40.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xE6141416)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SelectorPill(
                                label = "Auto",
                                selected = selectedQualityUrl == null,
                                onClick = {
                                    selectedQualityUrl = null
                                    showQualities = false
                                }
                            )
                            stream.qualities.forEachIndexed { index, quality ->
                                AnimateEntrance(index = index) {
                                    SelectorPill(
                                        label = quality.label,
                                        selected = selectedQualityUrl == quality.url,
                                        onClick = {
                                            selectedQualityUrl = quality.url
                                            showQualities = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Choix de la vitesse (par-dessus la vidéo).
                AnimatedVisibility(
                    visible = showSpeed,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 40.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xE6141416)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "0,5×" to 0.5f,
                                "0,75×" to 0.75f,
                                "1×" to 1f,
                                "1,25×" to 1.25f,
                                "1,5×" to 1.5f,
                                "2×" to 2f
                            ).forEach { (label, value) ->
                                SelectorPill(
                                    label = label,
                                    selected = playbackSpeed == value,
                                    onClick = {
                                        playbackSpeed = value
                                        player.setPlaybackSpeed(value)
                                        showSpeed = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Barre du bas : position, progression, durée.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(positionMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                    Slider(
                        value = positionMs.toFloat(),
                        onValueChange = {
                            userSeeking = true
                            positionMs = it.toLong()
                        },
                        onValueChangeFinished = {
                            player.seekTo(positionMs)
                            userSeeking = false
                        },
                        valueRange = 0f..(durationMs.coerceAtLeast(1000L)).toFloat(),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Amber500,
                            activeTrackColor = Amber500,
                            inactiveTrackColor = Color(0x66FFFFFF)
                        )
                    )
                    Text(
                        text = formatTime(durationMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectorPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color(0x33FFFFFF),
        modifier = Modifier.bounceClick(onClick = onClick)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White
        )
    }
}

/** Feedback de seek à l'écran : delta cumulé + position cible. */
private data class SeekFeedback(
    val deltaMs: Long,
    val targetMs: Long
)

/** Feedback d'un geste vertical : luminosité ou volume, en pourcentage. */
private data class VerticalFeedback(
    val isBrightness: Boolean,
    val percentage: Int
)
