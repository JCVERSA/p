package dot.jcversa.novay.ui.components

import android.view.SoundEffectConstants
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

/**
 * Feedback tactile GPU pur (transform + alpha uniquement) :
 * compression contenue au toucher (0.965f), léger survol au pointeur,
 * ressort interruptible sans rebond et ripple Material conservé.
 */
@Composable
fun Modifier.bounceClick(
    enabled: Boolean = true,
    onClick: () -> Unit = {}
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val motion = LocalSlateMotion.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val targetScale = when {
        motion.reduceMotion -> 1f
        isPressed && enabled -> 0.965f
        isHovered && enabled -> 1.015f
        else -> 1f
    }
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = motion.effectFloatSpec(),
        label = "press_scale"
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = ripple(),
            onClick = {
                try {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    view.playSoundEffect(SoundEffectConstants.CLICK)
                } catch (_: Exception) {}
                onClick()
            }
        )
}

/**
 * Entrée sobre GPU (opacity + léger glissement vertical) sans ré-animation
 * lente lors du recyclage dans les listes.
 */
@Composable
fun AnimateEntrance(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val motion = LocalSlateMotion.current
    if (motion.reduceMotion || index > 6) {
        content()
        return
    }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = motion.enterSpec(190)) +
            slideInVertically(animationSpec = motion.sizeTransitionSpec(190).let {
                androidx.compose.animation.core.tween(
                    durationMillis = 190,
                    easing = SlateMotion.EaseOutExpo
                )
            }) { 10 },
        exit = fadeOut(animationSpec = motion.exitSpec(120))
    ) {
        content()
    }
}

/**
 * Conteneur Prédictif Retour (Android 14+ / Compose PredictiveBackHandler) :
 * réduit l'échelle (1f -> 0.91f), décale horizontalement selon le bord du
 * geste et arrondit les coins en GPU temps réel avant de revenir en arrière.
 */
@Composable
fun PredictiveBackContainer(
    enabled: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val motion = LocalSlateMotion.current
    var gestureProgress by remember { mutableFloatStateOf(0f) }
    var swipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(gestureProgress) {
        animatedProgress.snapTo(gestureProgress)
    }

    PredictiveBackHandler(enabled = enabled) { progressFlow: Flow<BackEventCompat> ->
        try {
            progressFlow.collect { event ->
                swipeEdge = event.swipeEdge
                gestureProgress = event.progress.coerceIn(0f, 1f)
            }
            gestureProgress = 0f
            onBack()
        } catch (e: CancellationException) {
            animatedProgress.animateTo(0f, animationSpec = motion.dockIndicatorSpec())
            gestureProgress = 0f
            throw e
        }
    }

    val activeProgress = if (motion.reduceMotion) 0f else animatedProgress.value
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                if (activeProgress > 0f) {
                    val scale = 1f - (0.085f * activeProgress)
                    val direction = if (swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
                    scaleX = scale
                    scaleY = scale
                    translationX = direction * 20.dp.toPx() * activeProgress
                    shape = RoundedCornerShape((24f * activeProgress).dp)
                    clip = true
                }
            }
    ) {
        content()
    }
}
