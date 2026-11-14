package dot.jcversa.novay.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dot.jcversa.novay.ui.components.LocalSlateMotion
import dot.jcversa.novay.ui.components.NovayIcons
import dot.jcversa.novay.ui.components.NovayPlayPauseMorphIcon
import dot.jcversa.novay.ui.components.SwiftSlateSpinner
import dot.jcversa.novay.ui.theme.NovayTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Mini-lecteur flottant intelligent (in-app) :
 *  - apparaît automatiquement au défilement ou en quittant l'écran de lecture ;
 *  - reste déplaçable au doigt avec distinction stricte tap vs glissé ;
 *  - s'aimante intelligemment au coin sûr le plus proche en respectant barres système,
 *    dock inférieur et rail tablette ;
 *  - réutilise le MÊME `PlayerView` et le MÊME `ExoPlayer` sans coupure.
 */
@Composable
fun FloatingMiniPlayer(
    playerManager: PlayerStateManager,
    videoSurfaceSlot: @Composable () -> Unit,
    bottomDockClearance: Dp,
    leftRailClearance: Dp,
    onExpandToWatch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val motion = LocalSlateMotion.current
    val overlays = NovayTheme.overlays
    val scope = rememberCoroutineScope()
    val safeInsets = WindowInsets.safeDrawing.asPaddingValues()

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val containerHeightPx = with(density) { maxHeight.toPx() }

        val miniSizeDp = MiniPlayerGeometry.computeMiniPlayerSizeDp(
            availableWidthDp = maxWidth.value,
            videoAspectRatio = playerManager.videoAspectRatio,
        )
        val miniWidthDp = miniSizeDp.width.dp
        val miniHeightDp = miniSizeDp.height.dp
        val miniSizePx = MiniPlayerSizePx(
            width = with(density) { miniWidthDp.toPx() },
            height = with(density) { miniHeightDp.toPx() },
        )

        val marginPx = with(density) { 12.dp.toPx() }
        val leftInsetPx = with(density) { safeInsets.calculateLeftPadding(layoutDirection).toPx() + leftRailClearance.toPx() } + marginPx
        val topInsetPx = with(density) { safeInsets.calculateTopPadding().toPx() } + marginPx
        val rightInsetPx = with(density) { safeInsets.calculateRightPadding(layoutDirection).toPx() } + marginPx
        val bottomInsetPx = with(density) {
            maxOf(
                safeInsets.calculateBottomPadding().toPx(),
                bottomDockClearance.toPx(),
            )
        } + marginPx

        val bounds = MiniPlayerGeometry.computeSafeBounds(
            containerWidthPx = containerWidthPx,
            containerHeightPx = containerHeightPx,
            miniSize = miniSizePx,
            leftInsetPx = leftInsetPx,
            topInsetPx = topInsetPx,
            rightInsetPx = rightInsetPx,
            bottomInsetPx = bottomInsetPx,
        )

        var snappedCorner by remember { mutableStateOf(MiniPlayerCorner.BOTTOM_END) }
        var isDragging by remember { mutableStateOf(false) }

        val offsetAnim: Animatable<Offset, AnimationVector2D> = remember {
            val (initX, initY) = MiniPlayerGeometry.cornerOffset(MiniPlayerCorner.BOTTOM_END, bounds)
            Animatable(Offset(initX, initY), Offset.VectorConverter)
        }

        // Réaligne proprement vers le coin actif si les marges sûres changent (dock, rotation, tablette).
        LaunchedEffect(bounds, snappedCorner) {
            if (!isDragging) {
                val (tx, ty) = MiniPlayerGeometry.cornerOffset(snappedCorner, bounds)
                val target = Offset(tx, ty)
                if ((offsetAnim.value - target).getDistance() > 1f) {
                    if (motion.reduceMotion) {
                        offsetAnim.snapTo(target)
                    } else {
                        offsetAnim.animateTo(
                            targetValue = target,
                            animationSpec = spring(
                                dampingRatio = 0.84f,
                                stiffness = Spring.StiffnessMedium,
                            ),
                        )
                    }
                }
            }
        }

        val shape = RoundedCornerShape(14.dp)
        val dur = playerManager.durationMs
        val progress = if (dur > 0L) (playerManager.positionMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f

        Surface(
            shape = shape,
            shadowElevation = if (isDragging) 18.dp else 12.dp,
            tonalElevation = 4.dp,
            color = Color.Black,
            modifier = Modifier
                .offset { IntOffset(offsetAnim.value.x.roundToInt(), offsetAnim.value.y.roundToInt()) }
                .size(width = miniWidthDp, height = miniHeightDp)
                .border(0.75.dp, overlays.glassBorder, shape)
                .pointerInput(bounds, miniSizePx) {
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val dragStartBoxOffset = offsetAnim.value
                        val downInParent = down.position + dragStartBoxOffset
                        val velocityTracker = VelocityTracker()
                        velocityTracker.addPosition(down.uptimeMillis, downInParent)
                        var dragged = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val posInParent = change.position + offsetAnim.value

                            if (!change.pressed) {
                                if (dragged) {
                                    isDragging = false
                                    val velocity = velocityTracker.calculateVelocity()
                                    val targetCorner = MiniPlayerGeometry.nearestCorner(
                                        x = offsetAnim.value.x,
                                        y = offsetAnim.value.y,
                                        velocityX = velocity.x,
                                        velocityY = velocity.y,
                                        bounds = bounds,
                                    )
                                    snappedCorner = targetCorner
                                    val (sx, sy) = MiniPlayerGeometry.cornerOffset(targetCorner, bounds)
                                    scope.launch {
                                        if (motion.reduceMotion) {
                                            offsetAnim.snapTo(Offset(sx, sy))
                                        } else {
                                            offsetAnim.animateTo(
                                                targetValue = Offset(sx, sy),
                                                animationSpec = spring(
                                                    dampingRatio = 0.80f,
                                                    stiffness = 430f,
                                                ),
                                            )
                                        }
                                    }
                                }
                                break
                            }

                            val totalDeltaFromDown = posInParent - downInParent
                            if (!dragged && totalDeltaFromDown.getDistance() > touchSlop) {
                                dragged = true
                                isDragging = true
                            }
                            if (dragged) {
                                change.consume()
                                velocityTracker.addPosition(change.uptimeMillis, posInParent)
                                val (clampedX, clampedY) = MiniPlayerGeometry.clampOffset(
                                    x = dragStartBoxOffset.x + totalDeltaFromDown.x,
                                    y = dragStartBoxOffset.y + totalDeltaFromDown.y,
                                    bounds = bounds,
                                )
                                scope.launch {
                                    offsetAnim.snapTo(Offset(clampedX, clampedY))
                                }
                            }
                        }
                    }
                }
                .testTag("floating_mini_player"),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape),
            ) {
                // Surface vidéo unique + tap pour restaurer le lecteur complet
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(onClick = onExpandToWatch),
                ) {
                    videoSurfaceSlot()
                }

                // Voiles discrets haut/bas pour garder les contrôles lisibles sur toute scène
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.56f),
                                0.35f to Color.Transparent,
                                0.65f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.62f),
                            ),
                        ),
                )

                if (playerManager.isBuffering || playerManager.isResolvingStream) {
                    Box(modifier = Modifier.align(Alignment.Center)) {
                        SwiftSlateSpinner(size = 24.dp)
                    }
                }

                // Haut : Agrandir (gauche) & Fermer (droite)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MiniControlButton(
                        contentDescription = "Agrandir le lecteur",
                        onClick = onExpandToWatch,
                        testTag = "mini_player_expand_button",
                    ) {
                        Icon(
                            imageVector = NovayIcons.FullscreenEnter,
                            contentDescription = null,
                            tint = overlays.videoControlsInk,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    MiniControlButton(
                        contentDescription = "Fermer le mini-lecteur",
                        onClick = { playerManager.closeAndStop() },
                        testTag = "mini_player_close_button",
                    ) {
                        Icon(
                            imageVector = NovayIcons.Close,
                            contentDescription = null,
                            tint = overlays.videoControlsInk,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                // Bas gauche : Lecture / Pause
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 2.dp, bottom = 2.dp),
                ) {
                    MiniControlButton(
                        contentDescription = if (playerManager.isPlaying) "Pause" else "Lecture",
                        onClick = { playerManager.togglePlayPause() },
                        testTag = "mini_player_play_pause_button",
                    ) {
                        NovayPlayPauseMorphIcon(
                            isPlaying = playerManager.isPlaying,
                            tint = overlays.videoControlsInk,
                            size = 18.dp,
                        )
                    }
                }

                // Barre de progression crimson au bord inférieur
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .align(Alignment.BottomCenter)
                        .background(overlays.videoTrackInactive),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniControlButton(
    contentDescription: String,
    onClick: () -> Unit,
    testTag: String,
    icon: @Composable () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .semantics {
                this.contentDescription = contentDescription
                this.role = Role.Button
            }
            .clickable(onClick = onClick)
            .testTag(testTag),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.54f)),
        ) {
            icon()
        }
    }
}
