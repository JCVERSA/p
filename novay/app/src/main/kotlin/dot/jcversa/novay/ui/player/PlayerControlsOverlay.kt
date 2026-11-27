package dot.jcversa.novay.ui.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.annotation.OptIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import dev.chrisbanes.haze.rememberHazeState
import dot.jcversa.novay.ui.components.AnimateEntrance
import dot.jcversa.novay.ui.components.LocalSlateMotion
import dot.jcversa.novay.ui.components.NovayLottieIcon
import dot.jcversa.novay.ui.components.NovayPlayPauseMorphIcon
import dot.jcversa.novay.R
import dot.jcversa.novay.ui.components.NovayIcons
import dot.jcversa.novay.ui.components.SwiftSlateSpinner
import dot.jcversa.novay.ui.components.bounceClick
import dot.jcversa.novay.ui.components.novayCinemaGlass
import dot.jcversa.novay.ui.components.novayHazeSource
import dot.jcversa.novay.ui.theme.NovayTheme
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

private enum class SelectorSheet { QUALITY, SPEED, TRACKS }

private data class SeekFeedback(
    val deltaMs: Long,
    val targetMs: Long,
)

private data class VerticalFeedback(
    val isBrightness: Boolean,
    val percentage: Int,
)

/**
 * Hôte UNIQUE de la vue vidéo, déplacé entre le mode portrait intégré, le
 * mini-lecteur flottant et le plein écran via `movableContentOf` afin de
 * ne jamais détruire ni recréer le décodeur vidéo. Depuis la V2.9.6, la
 * vue est un conteneur neutre : il héberge le `PlayerView` ExoPlayer
 * (moteur unique — streaming comme fichiers téléchargés) ; le
 * `PlayerStateManager` gère son cadrage.
 */
@OptIn(UnstableApi::class)
@Composable
fun SinglePlayerViewHost(
    playerManager: PlayerStateManager,
    modifier: Modifier = Modifier,
) {
    val presentationMode = playerManager.presentationMode
    val videoAspectRatio = playerManager.videoAspectRatio
    AndroidView(
        factory = { ctx -> playerManager.createSinglePlayerView(ctx) },
        update = { view ->
            // Déclenche une passe de mesure propre lors du changement de
            // mode ou de ratio (le moteur actif cadre la vidéo lui-même).
            view.tag = presentationMode to videoAspectRatio
            view.requestLayout()
        },
        onRelease = { playerManager.onSingleViewHostReleased() },
        modifier = modifier.fillMaxSize(),
    )
}

/**
 * Lecteur vidéo intégré en portrait :
 *  - respecte le ratio d'aspect vidéo dans le flux vertical de la page ;
 *  - n'intercepte pas les glissés verticaux pour préserver le défilement fluide ;
 *  - propose double-tap ±10s, appui long 2×, qualité/vitesse/pistes, réduire et plein écran.
 */
@Composable
fun EmbeddedVideoPlayer(
    playerManager: PlayerStateManager,
    showSurface: Boolean,
    videoSurfaceSlot: @Composable () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val overlays = NovayTheme.overlays
    val motion = LocalSlateMotion.current
    val hazeState = rememberHazeState()
    var controlsVisible by remember { mutableStateOf(true) }
    var activeSheet by remember { mutableStateOf<SelectorSheet?>(null) }
    var seekFeedback by remember { mutableStateOf<SeekFeedback?>(null) }
    val doubleTapSeek = remember { DoubleTapSeekAccumulator() }

    LaunchedEffect(controlsVisible, playerManager.isPlaying, activeSheet) {
        if (controlsVisible && playerManager.isPlaying && activeSheet == null) {
            delay(3500L)
            controlsVisible = false
        }
    }

    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(800L)
            seekFeedback = null
        }
    }

    val rawAspect = MiniPlayerGeometry.normalizeVideoAspectRatio(playerManager.videoAspectRatio)
    val envelopeAspect = MiniPlayerGeometry.computeEmbeddedEnvelopeAspectRatio(rawAspect)
    val session = playerManager.session
    val stream = playerManager.stream
    val hasTracks = playerManager.audioTracks.size > 1 || playerManager.subtitleTracks.isNotEmpty()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(envelopeAspect)
            .background(Color.Black)
            .testTag("embedded_video_player"),
    ) {
        if (showSurface) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .novayHazeSource(hazeState),
                contentAlignment = Alignment.Center,
            ) {
                val fitted = MiniPlayerGeometry.computeFittedVideoSize(
                    containerWidth = maxWidth.value,
                    containerHeight = maxHeight.value,
                    videoAspectRatio = rawAspect,
                )
                Box(
                    modifier = Modifier
                        .size(width = fitted.width.dp, height = fitted.height.dp)
                        .testTag("embedded_video_fitted_box"),
                ) {
                    videoSurfaceSlot()
                }
            }

            // Gestes portrait non bloquants pour le défilement vertical :
            // tap simple, double-tap ±10s, appui long 2×.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(playerManager, playerManager.durationMs) {
                        val slop = 10.dp.toPx()
                        val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                        val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val startX = down.position.x
                            val startY = down.position.y
                            var isLongPress = false
                            var moved = false

                            while (true) {
                                val event = if (!isLongPress && !moved) {
                                    withTimeoutOrNull(longPressTimeout) { awaitPointerEvent() }
                                } else {
                                    awaitPointerEvent()
                                }
                                if (event == null) {
                                    if (playerManager.isPlaying) {
                                        playerManager.setTemporaryFastSpeed(true)
                                        isLongPress = true
                                    }
                                    continue
                                }
                                val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                                if (!change.pressed) {
                                    if (isLongPress) {
                                        playerManager.setTemporaryFastSpeed(false)
                                    } else if (!moved) {
                                        val second = withTimeoutOrNull(doubleTapTimeout) {
                                            awaitFirstDown(requireUnconsumed = false)
                                        }
                                        if (second == null) {
                                            controlsVisible = !controlsVisible
                                            if (!controlsVisible) activeSheet = null
                                        } else {
                                            val forward = second.position.x > size.width / 2f
                                            val inc = if (forward) {
                                                PlayerGestureMath.DOUBLE_TAP_INCREMENT_MS
                                            } else {
                                                -PlayerGestureMath.DOUBLE_TAP_INCREMENT_MS
                                            }
                                            val dur = playerManager.durationMs
                                            val target = (playerManager.currentPositionMs() + inc)
                                                .coerceIn(0L, if (dur > 0L) dur else Long.MAX_VALUE)
                                            playerManager.seekTo(target)
                                            seekFeedback = SeekFeedback(
                                                deltaMs = doubleTapSeek.tap(forward),
                                                targetMs = playerManager.currentPositionMs(),
                                            )
                                            while (true) {
                                                val upEvt = awaitPointerEvent()
                                                if (upEvt.changes.all { !it.pressed }) break
                                            }
                                        }
                                    }
                                    break
                                }
                                val dx = change.position.x - startX
                                val dy = change.position.y - startY
                                if (abs(dx) > slop || abs(dy) > slop) {
                                    moved = true
                                    // Laisse le LazyColumn parent gérer le glissé vertical librement.
                                    break
                                }
                            }
                        }
                    },
            )
        } else {
            // Emplacement de réserve quand la vidéo tourne dans le mini-lecteur flottant
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "En lecture dans le mini-lecteur",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { playerManager.requestRestoreToEmbedded() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Text("Ramener ici")
                    }
                }
            }
        }

        // États de résolution / mise en mémoire tampon / erreur
        when {
            playerManager.isResolvingStream -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SwiftSlateSpinner(size = 34.dp)
                    Text(
                        text = "Chargement du flux…",
                        style = MaterialTheme.typography.bodySmall,
                        color = overlays.videoControlsInk,
                    )
                }
            }
            playerManager.streamError != null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.78f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = NovayIcons.ErrorSeal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = stringResource(R.string.error_stream_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = overlays.videoControlsInk,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = playerManager.streamError ?: "Erreur vidéo",
                        style = MaterialTheme.typography.bodySmall,
                        color = overlays.videoControlsMutedInk,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = overlays.ctaContainer,
                            contentColor = overlays.onCtaContainer,
                        ),
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
            }
            playerManager.isBuffering && showSurface -> Box(
                modifier = Modifier.align(Alignment.Center),
            ) {
                SwiftSlateSpinner(size = 38.dp)
            }
        }

        if (playerManager.isFastForwardHold) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .novayCinemaGlass(
                        shape = RoundedCornerShape(999.dp),
                        hazeState = hazeState,
                        tint = overlays.glassSurfaceElevated,
                        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                        showBorder = true,
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .testTag("embedded_speed_hud"),
            ) {
                Icon(
                    imageVector = NovayIcons.SpeedGauge,
                    contentDescription = null,
                    tint = overlays.crimsonOnContainer,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "2× · Avance rapide",
                    style = MaterialTheme.typography.labelLarge,
                    color = overlays.videoControlsInk,
                )
            }
        }

        seekFeedback?.let { fb ->
            Box(
                modifier = Modifier
                    .align(if (fb.deltaMs >= 0) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 18.dp)
                    .novayCinemaGlass(
                        shape = RoundedCornerShape(16.dp),
                        hazeState = hazeState,
                        tint = overlays.glassSurfaceElevated,
                        borderColor = overlays.glassBorder,
                        showBorder = true,
                    )
                    .testTag("embedded_seek_hud"),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = (if (fb.deltaMs >= 0) "+" else "−") + "${abs(fb.deltaMs) / 1000} s",
                        style = MaterialTheme.typography.titleMedium,
                        color = overlays.videoControlsInk,
                    )
                    Text(
                        text = formatVideoTime(fb.targetMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = overlays.videoControlsMutedInk,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showSurface && controlsVisible && !playerManager.isResolvingStream && playerManager.streamError == null,
            enter = if (motion.reduceMotion) EnterTransition.None else fadeIn(motion.enterSpec(180)),
            exit = if (motion.reduceMotion) ExitTransition.None else fadeOut(motion.exitSpec(140)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                overlays.scrimTop,
                                Color.Transparent,
                                Color.Transparent,
                                overlays.scrimBottom,
                            ),
                        ),
                    ),
            ) {
                // Barre supérieure intégrée
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = session?.episode?.label ?: "",
                        style = MaterialTheme.typography.labelMedium,
                        color = overlays.videoControlsInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                    )
                    if (hasTracks) {
                        IconButton(
                            onClick = {
                                activeSheet = if (activeSheet == SelectorSheet.TRACKS) null else SelectorSheet.TRACKS
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("embedded_tracks_button"),
                        ) {
                            Icon(
                                imageVector = NovayIcons.Subtitles,
                                contentDescription = "Audio et sous-titres",
                                tint = if (activeSheet == SelectorSheet.TRACKS) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    overlays.videoControlsInk
                                },
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    if (session?.offline == false && stream != null && stream.qualities.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                activeSheet = if (activeSheet == SelectorSheet.QUALITY) null else SelectorSheet.QUALITY
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("embedded_quality_button"),
                        ) {
                            NovayLottieIcon(
                                res = R.raw.lottie_settings2,
                                contentDescription = "Qualité vidéo",
                                tint = if (activeSheet == SelectorSheet.QUALITY) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    overlays.videoControlsInk
                                },
                                size = 20.dp,
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            activeSheet = if (activeSheet == SelectorSheet.SPEED) null else SelectorSheet.SPEED
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("embedded_speed_button"),
                    ) {
                        Icon(
                            imageVector = NovayIcons.SpeedGauge,
                            contentDescription = "Vitesse de lecture",
                            tint = if (activeSheet == SelectorSheet.SPEED || playerManager.playbackSpeed != 1f) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                overlays.videoControlsInk
                            },
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(
                        onClick = { playerManager.minimizeToFloating() },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("embedded_minimize_button"),
                    ) {
                        NovayLottieIcon(
                            res = R.raw.lottie_minimize2,
                            contentDescription = "Réduire en mini-lecteur",
                            tint = overlays.videoControlsInk,
                            size = 20.dp,
                        )
                    }
                }

                // Bouton Play/Pause central
                if (!playerManager.isBuffering) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(58.dp)
                            .semantics {
                                this.contentDescription = if (playerManager.isPlaying) "Pause" else "Lecture"
                                this.role = Role.Button
                            }
                            .novayCinemaGlass(
                                shape = CircleShape,
                                hazeState = hazeState,
                                tint = overlays.glassSurface,
                                borderColor = overlays.glassBorder,
                                showBorder = true,
                            )
                            .bounceClick(onClick = { playerManager.togglePlayPause() })
                            .testTag("embedded_play_pause_button"),
                    ) {
                        // V2.9.2 : morph VECTORIEL pilote par l ETAT —
                        // toujours correct (instantane si animations
                        // coupees), contrairement au morph Lottie qui
                        // rejouait la meme animation dans les deux sens.
                        NovayPlayPauseMorphIcon(
                            isPlaying = playerManager.isPlaying,
                            tint = overlays.videoControlsInk,
                            size = 30.dp,
                        )
                    }
                }

                // Sélecteurs contextuels (Qualité / Vitesse / Pistes)
                PlayerSelectorPanel(
                    activeSheet = activeSheet,
                    playerManager = playerManager,
                    hazeState = hazeState,
                    onDismiss = { activeSheet = null },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 16.dp),
                )

                // Barre inférieure + bouton Plein écran
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(start = 12.dp, end = 4.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatVideoTime(playerManager.positionMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = overlays.videoControlsInk,
                    )
                    Slider(
                        value = playerManager.positionMs.toFloat(),
                        onValueChange = {
                            if (!playerManager.userSeeking) {
                                playerManager.setScrubbing(true)
                            }
                            playerManager.previewSeekPosition(it.toLong())
                        },
                        onValueChangeFinished = {
                            playerManager.commitSeekPosition(playerManager.positionMs)
                            playerManager.setScrubbing(false)
                        },
                        valueRange = 0f..(playerManager.durationMs.coerceAtLeast(1000L)).toFloat(),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .testTag("embedded_seek_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = overlays.videoTrackInactive,
                        ),
                    )
                    Text(
                        text = formatVideoTime(playerManager.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = overlays.videoControlsInk,
                    )
                    IconButton(
                        onClick = {
                            activeSheet = null
                            playerManager.enterFullscreen()
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("player_fullscreen_button"),
                    ) {
                        NovayLottieIcon(
                            res = R.raw.lottie_maximizeminimize,
                            contentDescription = "Plein écran paysage",
                            tint = overlays.videoControlsInk,
                            size = 20.dp,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Superposition plein écran paysage : reprend l'expérience cinéma complète
 * (gestes volume/luminosité, glissé seek, double-tap ±10s, appui long 2×,
 * verrouillage, pistes/qualité/vitesse) avec le MÊME moteur `ExoPlayer`.
 */
@Composable
fun FullscreenPlayerOverlay(
    playerManager: PlayerStateManager,
    videoSurfaceSlot: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val overlays = NovayTheme.overlays
    val motion = LocalSlateMotion.current
    val hazeState = rememberHazeState()
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val window = (context as? Activity)?.window
    val doubleTapSeek = remember { DoubleTapSeekAccumulator() }

    var controlsVisible by remember { mutableStateOf(true) }
    var controlsLocked by remember { mutableStateOf(false) }
    var activeSheet by remember { mutableStateOf<SelectorSheet?>(null) }
    var seekFeedback by remember { mutableStateOf<SeekFeedback?>(null) }
    var verticalFeedback by remember { mutableStateOf<VerticalFeedback?>(null) }

    BackHandler(enabled = true) {
        playerManager.exitFullscreen()
    }

    LaunchedEffect(controlsVisible, playerManager.isPlaying, activeSheet) {
        if (controlsVisible && playerManager.isPlaying && activeSheet == null) {
            delay(3500L)
            controlsVisible = false
        }
    }

    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(800L)
            seekFeedback = null
        }
    }

    LaunchedEffect(verticalFeedback) {
        if (verticalFeedback != null) {
            delay(800L)
            verticalFeedback = null
        }
    }

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

    val session = playerManager.session
    val stream = playerManager.stream
    val hasTracks = playerManager.audioTracks.size > 1 || playerManager.subtitleTracks.isNotEmpty()

    val rawAspect = MiniPlayerGeometry.normalizeVideoAspectRatio(playerManager.videoAspectRatio)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("fullscreen_video_player"),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .novayHazeSource(hazeState)
                .testTag("fullscreen_video_viewport"),
            contentAlignment = Alignment.Center,
        ) {
            val fitted = MiniPlayerGeometry.computeFittedVideoSize(
                containerWidth = maxWidth.value,
                containerHeight = maxHeight.value,
                videoAspectRatio = rawAspect,
            )
            Box(
                modifier = Modifier
                    .size(width = fitted.width.dp, height = fitted.height.dp)
                    .testTag("fullscreen_video_fitted_box"),
            ) {
                videoSurfaceSlot()
            }
        }

        // Couche de gestes plein écran (taps, double-taps, seek horizontal, volume/luminosité, 2×)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(playerManager, playerManager.durationMs, controlsLocked) {
                    if (controlsLocked) return@pointerInput
                    val slop = 8.dp.toPx()
                    val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                    val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startX = down.position.x
                        val startY = down.position.y
                        val startPosition = playerManager.currentPositionMs()
                        var mode = 0
                        var verticalIsBrightness = false
                        var verticalStartPct = 0

                        while (true) {
                            val event = if (mode == 0) {
                                withTimeoutOrNull(longPressTimeout) { awaitPointerEvent() }
                            } else {
                                awaitPointerEvent()
                            }
                            if (event == null) {
                                if (playerManager.isPlaying) {
                                    playerManager.setTemporaryFastSpeed(true)
                                }
                                mode = 3
                                continue
                            }
                            val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                            if (!change.pressed) {
                                when (mode) {
                                    1 -> playerManager.setScrubbing(false)
                                    3 -> playerManager.setTemporaryFastSpeed(false)
                                    0 -> {
                                        val second = withTimeoutOrNull(doubleTapTimeout) {
                                            awaitFirstDown(requireUnconsumed = false)
                                        }
                                        if (second == null) {
                                            controlsVisible = !controlsVisible
                                            if (!controlsVisible) activeSheet = null
                                        } else {
                                            val forward = second.position.x > size.width / 2f
                                            val inc = if (forward) {
                                                PlayerGestureMath.DOUBLE_TAP_INCREMENT_MS
                                            } else {
                                                -PlayerGestureMath.DOUBLE_TAP_INCREMENT_MS
                                            }
                                            val dur = playerManager.durationMs
                                            val target = (playerManager.currentPositionMs() + inc)
                                                .coerceIn(0L, if (dur > 0L) dur else Long.MAX_VALUE)
                                            playerManager.seekTo(target)
                                            seekFeedback = SeekFeedback(
                                                deltaMs = doubleTapSeek.tap(forward),
                                                targetMs = playerManager.currentPositionMs(),
                                            )
                                            while (true) {
                                                val e = awaitPointerEvent()
                                                if (e.changes.all { !it.pressed }) break
                                            }
                                        }
                                    }
                                }
                                break
                            }

                            val dx = change.position.x - startX
                            val dy = change.position.y - startY
                            if (mode == 0) {
                                when {
                                    abs(dx) > slop && abs(dx) > abs(dy) -> {
                                        mode = 1
                                        playerManager.setScrubbing(true)
                                    }
                                    abs(dy) > slop && abs(dy) > abs(dx) -> {
                                        mode = 2
                                        verticalIsBrightness = down.position.x < size.width / 2f
                                        verticalStartPct = if (verticalIsBrightness) {
                                            val cur = window?.attributes?.screenBrightness ?: -1f
                                            if (cur >= 0f) (cur * 100).toInt() else 50
                                        } else {
                                            val max = audioManager
                                                .getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                                .coerceAtLeast(1)
                                            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max
                                        }
                                    }
                                }
                            }
                            when (mode) {
                                1 -> {
                                    change.consume()
                                    val target = PlayerGestureMath.dragSeekTarget(
                                        startPositionMs = startPosition,
                                        startX = startX,
                                        currentX = change.position.x,
                                        durationMs = playerManager.durationMs,
                                    )
                                    playerManager.seekTo(target)
                                    seekFeedback = SeekFeedback(
                                        deltaMs = target - startPosition,
                                        targetMs = target,
                                    )
                                }
                                2 -> {
                                    change.consume()
                                    val pct = PlayerGestureMath.verticalTarget(
                                        startPercentage = verticalStartPct,
                                        startY = startY,
                                        currentY = change.position.y,
                                    )
                                    if (verticalIsBrightness) applyBrightness(pct) else applyVolume(pct)
                                    verticalFeedback = VerticalFeedback(verticalIsBrightness, pct)
                                }
                            }
                        }
                    }
                },
        )

        if (controlsLocked) {
            IconButton(
                onClick = {
                    controlsLocked = false
                    controlsVisible = true
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 8.dp, end = 8.dp)
                    .minimumInteractiveComponentSize()
                    .novayCinemaGlass(
                        shape = CircleShape,
                        hazeState = hazeState,
                        tint = overlays.glassSurface,
                        borderColor = overlays.glassBorder,
                        showBorder = true,
                    )
                    .testTag("player_unlock_button"),
            ) {
                NovayLottieIcon(
                    res = R.raw.lottie_lock,
                    contentDescription = "Déverrouiller les contrôles",
                    tint = overlays.videoControlsInk,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        if (playerManager.isFastForwardHold) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 64.dp)
                    .novayCinemaGlass(
                        shape = RoundedCornerShape(999.dp),
                        hazeState = hazeState,
                        tint = overlays.glassSurfaceElevated,
                        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                        showBorder = true,
                    )
                    .padding(horizontal = 18.dp, vertical = 8.dp)
                    .testTag("player_speed_hud"),
            ) {
                Icon(
                    imageVector = NovayIcons.SpeedGauge,
                    contentDescription = null,
                    tint = overlays.crimsonOnContainer,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "2× · Avance rapide",
                    style = MaterialTheme.typography.titleMedium,
                    color = overlays.videoControlsInk,
                )
            }
        }

        verticalFeedback?.let { vf ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .novayCinemaGlass(
                        shape = RoundedCornerShape(18.dp),
                        hazeState = hazeState,
                        tint = overlays.glassSurfaceElevated,
                        borderColor = overlays.glassBorder,
                        showBorder = true,
                    )
                    .testTag("player_vertical_hud"),
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(min = 144.dp)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        NovayLottieIcon(
                            res = if (vf.isBrightness) R.raw.lottie_sun else R.raw.lottie_volume,
                            contentDescription = null,
                            tint = overlays.videoControlsInk,
                            size = 24.dp,
                            loop = false,
                        )
                        Text(
                            text = if (vf.isBrightness) "Luminosité" else "Volume",
                            style = MaterialTheme.typography.labelMedium,
                            color = overlays.videoControlsMutedInk,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (vf.percentage / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .width(108.dp)
                            .height(4.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = overlays.videoTrackInactive,
                    )
                    Text(
                        text = "${vf.percentage} %",
                        style = MaterialTheme.typography.titleMedium,
                        color = overlays.videoControlsInk,
                    )
                }
            }
        }

        seekFeedback?.let { fb ->
            Box(
                modifier = Modifier
                    .align(if (fb.deltaMs >= 0) Alignment.CenterEnd else Alignment.CenterStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 28.dp)
                    .novayCinemaGlass(
                        shape = RoundedCornerShape(18.dp),
                        hazeState = hazeState,
                        tint = overlays.glassSurfaceElevated,
                        borderColor = overlays.glassBorder,
                        showBorder = true,
                    )
                    .testTag("player_seek_hud"),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = (if (fb.deltaMs >= 0) "+" else "−") + "${abs(fb.deltaMs) / 1000} s",
                        style = MaterialTheme.typography.titleLarge,
                        color = overlays.videoControlsInk,
                    )
                    Text(
                        text = formatVideoTime(fb.targetMs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = overlays.videoControlsMutedInk,
                    )
                }
            }
        }

        if (playerManager.isBuffering) {
            Box(modifier = Modifier.align(Alignment.Center)) {
                SwiftSlateSpinner(size = 44.dp)
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = if (motion.reduceMotion) EnterTransition.None else fadeIn(motion.enterSpec(180)),
            exit = if (motion.reduceMotion) ExitTransition.None else fadeOut(motion.exitSpec(140)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                overlays.scrimTop,
                                Color.Transparent,
                                Color.Transparent,
                                overlays.scrimBottom,
                            ),
                        ),
                    ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { playerManager.exitFullscreen() },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("fullscreen_back_button"),
                    ) {
                        Icon(
                            imageVector = NovayIcons.Back,
                            contentDescription = "Quitter le plein écran",
                            tint = overlays.videoControlsInk,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = session?.episode?.label ?: "",
                            style = MaterialTheme.typography.titleMedium,
                            color = overlays.videoControlsInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val sub = buildString {
                            if (!session?.animeTitle.isNullOrBlank()) {
                                append(session?.animeTitle)
                                append(" · ")
                            }
                            append(session?.season?.name.orEmpty())
                            if (session?.offline == true) append(" · hors-ligne")
                        }
                        Text(
                            text = sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = overlays.videoControlsMutedInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(
                        onClick = {
                            controlsLocked = true
                            controlsVisible = false
                            activeSheet = null
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("player_lock_button"),
                    ) {
                        NovayLottieIcon(
                            res = R.raw.lottie_lock,
                            contentDescription = "Verrouiller les contrôles",
                            tint = overlays.videoControlsInk,
                        )
                    }
                    if (hasTracks) {
                        IconButton(
                            onClick = {
                                activeSheet = if (activeSheet == SelectorSheet.TRACKS) null else SelectorSheet.TRACKS
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("player_tracks_button"),
                        ) {
                            Icon(
                                imageVector = NovayIcons.Subtitles,
                                contentDescription = "Audio et sous-titres",
                                tint = if (activeSheet == SelectorSheet.TRACKS) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    overlays.videoControlsInk
                                },
                            )
                        }
                    }
                    if (session?.offline == false && stream != null && stream.qualities.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                activeSheet = if (activeSheet == SelectorSheet.QUALITY) null else SelectorSheet.QUALITY
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("player_quality_button"),
                        ) {
                            NovayLottieIcon(
                                res = R.raw.lottie_settings2,
                                contentDescription = "Qualité",
                                tint = if (activeSheet == SelectorSheet.QUALITY) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    overlays.videoControlsInk
                                },
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            activeSheet = if (activeSheet == SelectorSheet.SPEED) null else SelectorSheet.SPEED
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("player_speed_button"),
                    ) {
                        Icon(
                            imageVector = NovayIcons.SpeedGauge,
                            contentDescription = "Vitesse de lecture",
                            tint = if (activeSheet == SelectorSheet.SPEED || playerManager.playbackSpeed != 1f) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                overlays.videoControlsInk
                            },
                        )
                    }
                }

                if (!playerManager.isBuffering) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(72.dp)
                            .semantics {
                                this.contentDescription = if (playerManager.isPlaying) "Pause" else "Lecture"
                                this.role = Role.Button
                            }
                            .novayCinemaGlass(
                                shape = CircleShape,
                                hazeState = hazeState,
                                tint = overlays.glassSurface,
                                borderColor = overlays.glassBorder,
                                showBorder = true,
                            )
                            .bounceClick(onClick = { playerManager.togglePlayPause() })
                            .testTag("player_play_pause_button"),
                    ) {
                        NovayPlayPauseMorphIcon(
                            isPlaying = playerManager.isPlaying,
                            tint = overlays.videoControlsInk,
                            size = 38.dp,
                        )
                    }
                }

                PlayerSelectorPanel(
                    activeSheet = activeSheet,
                    playerManager = playerManager,
                    hazeState = hazeState,
                    onDismiss = { activeSheet = null },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 40.dp),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatVideoTime(playerManager.positionMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = overlays.videoControlsInk,
                    )
                    Slider(
                        value = playerManager.positionMs.toFloat(),
                        onValueChange = {
                            if (!playerManager.userSeeking) {
                                playerManager.setScrubbing(true)
                            }
                            playerManager.previewSeekPosition(it.toLong())
                        },
                        onValueChangeFinished = {
                            playerManager.commitSeekPosition(playerManager.positionMs)
                            playerManager.setScrubbing(false)
                        },
                        valueRange = 0f..(playerManager.durationMs.coerceAtLeast(1000L)).toFloat(),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp)
                            .testTag("player_seek_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = overlays.videoTrackInactive,
                        ),
                    )
                    Text(
                        text = formatVideoTime(playerManager.durationMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = overlays.videoControlsInk,
                    )
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = { playerManager.exitFullscreen() },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("fullscreen_exit_button"),
                    ) {
                        NovayLottieIcon(
                            res = R.raw.lottie_maximizeminimize,
                            contentDescription = "Sortir du plein écran",
                            tint = overlays.videoControlsInk,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerSelectorPanel(
    activeSheet: SelectorSheet?,
    playerManager: PlayerStateManager,
    hazeState: dev.chrisbanes.haze.HazeState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val overlays = NovayTheme.overlays
    val motion = LocalSlateMotion.current
    val stream = playerManager.stream
    val isOffline = playerManager.session?.offline == true

    AnimatedVisibility(
        visible = activeSheet != null,
        enter = if (motion.reduceMotion) EnterTransition.None else fadeIn(motion.enterSpec(160)),
        exit = if (motion.reduceMotion) ExitTransition.None else fadeOut(motion.exitSpec(120)),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .novayCinemaGlass(
                    shape = RoundedCornerShape(18.dp),
                    hazeState = hazeState,
                    tint = overlays.glassSurfaceElevated,
                    borderColor = overlays.glassBorder,
                    showBorder = true,
                ),
        ) {
            when (activeSheet) {
                SelectorSheet.QUALITY -> if (!isOffline && stream != null && stream.qualities.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PlayerSelectorPill(
                            label = "Auto",
                            selected = playerManager.selectedQualityUrl == null,
                            onClick = {
                                playerManager.selectQuality(null)
                                onDismiss()
                            },
                        )
                        stream.qualities.forEachIndexed { index, quality ->
                            AnimateEntrance(index = index) {
                                PlayerSelectorPill(
                                    label = quality.label,
                                    selected = playerManager.selectedQualityUrl == quality.url,
                                    onClick = {
                                        playerManager.selectQuality(quality.url)
                                        onDismiss()
                                    },
                                )
                            }
                        }
                    }
                }
                SelectorSheet.SPEED -> {
                    val speeds = listOf(0.5f, 1f, 1.25f, 1.5f, 2f)
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        speeds.forEachIndexed { index, spd ->
                            val label = if (spd == 1f) "1×" else "${spd.toString().removeSuffix(".0")}×"
                            AnimateEntrance(index = index) {
                                PlayerSelectorPill(
                                    label = label,
                                    selected = playerManager.playbackSpeed == spd,
                                    onClick = {
                                        playerManager.selectPlaybackSpeed(spd)
                                        onDismiss()
                                    },
                                )
                            }
                        }
                    }
                }
                SelectorSheet.TRACKS -> {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (playerManager.audioTracks.isNotEmpty()) {
                            Text(
                                text = "Piste audio",
                                style = MaterialTheme.typography.labelMedium,
                                color = overlays.videoControlsMutedInk,
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                playerManager.audioTracks.forEach { option ->
                                    PlayerSelectorPill(
                                        label = option.label,
                                        selected = option.selected,
                                        onClick = {
                                            playerManager.selectAudioTrack(option)
                                            onDismiss()
                                        },
                                    )
                                }
                            }
                        }
                        if (playerManager.subtitleTracks.isNotEmpty()) {
                            Text(
                                text = "Sous-titres",
                                style = MaterialTheme.typography.labelMedium,
                                color = overlays.videoControlsMutedInk,
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                PlayerSelectorPill(
                                    label = "Désactivés",
                                    selected = playerManager.subtitlesDisabled,
                                    onClick = {
                                        playerManager.selectSubtitleTrack(null)
                                        onDismiss()
                                    },
                                )
                                playerManager.subtitleTracks.forEach { option ->
                                    PlayerSelectorPill(
                                        label = option.label,
                                        selected = option.selected && !playerManager.subtitlesDisabled,
                                        onClick = {
                                            playerManager.selectSubtitleTrack(option)
                                            onDismiss()
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                null -> {}
            }
        }
    }
}

@Composable
private fun PlayerSelectorPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val overlays = NovayTheme.overlays
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .background(
                color = if (selected) overlays.ctaContainer else overlays.glassSurface,
                shape = RoundedCornerShape(999.dp),
            )
            .bounceClick(onClick = onClick)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) overlays.onCtaContainer else overlays.videoControlsInk,
        )
    }
}

internal fun formatVideoTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0L)) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
