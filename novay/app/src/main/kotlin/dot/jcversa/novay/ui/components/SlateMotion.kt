package dot.jcversa.novay.ui.components

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize

/**
 * Système de mouvement NoVaY (calibré selon les règles d'Emil Kowalski) :
 *  - courbes EaseOut fortes à l'entrée (< 300ms, jamais EaseIn sur l'UI) ;
 *  - sorties asymétriques plus courtes (~140ms) pour un feeling vif ;
 *  - ressorts interruptibles pour les morphs d'icônes et capsules ;
 *  - zéro animation si le réglage système « réduire les animations » est actif.
 */
@Immutable
data class SlateMotion(
    val reduceMotion: Boolean = false
) {
    val easeOutExpo: CubicBezierEasing
        get() = EaseOutExpo

    fun spatialFloatSpec(): AnimationSpec<Float> = if (reduceMotion) {
        snap()
    } else {
        spring(
            dampingRatio = 0.84f,
            stiffness = 440f
        )
    }

    fun dockIndicatorSpec(): AnimationSpec<Float> = if (reduceMotion) {
        snap()
    } else {
        spring(
            dampingRatio = 0.82f,
            stiffness = 460f
        )
    }

    fun dockDpSpec(): AnimationSpec<Dp> = if (reduceMotion) {
        snap()
    } else {
        spring(
            dampingRatio = 0.82f,
            stiffness = 460f
        )
    }

    fun morphFloatSpec(): FiniteAnimationSpec<Float> = if (reduceMotion) {
        snap()
    } else {
        tween(durationMillis = 230, easing = EaseOutExpo)
    }

    fun expressiveFloatSpec(): AnimationSpec<Float> = if (reduceMotion) {
        snap()
    } else {
        spring(
            dampingRatio = 0.78f,
            stiffness = Spring.StiffnessMediumLow
        )
    }

    fun effectFloatSpec(): AnimationSpec<Float> = if (reduceMotion) {
        snap()
    } else {
        spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 520f
        )
    }

    fun enterSpec(durationMillis: Int = 220): FiniteAnimationSpec<Float> = if (reduceMotion) {
        snap()
    } else {
        tween(durationMillis = durationMillis.coerceAtMost(280), easing = EaseOutExpo)
    }

    fun colorSpec(durationMillis: Int = 180): FiniteAnimationSpec<Color> = if (reduceMotion) {
        snap()
    } else {
        tween(durationMillis = durationMillis.coerceAtMost(280), easing = EaseOutExpo)
    }

    fun exitSpec(durationMillis: Int = 140): FiniteAnimationSpec<Float> = if (reduceMotion) {
        snap()
    } else {
        tween(durationMillis = durationMillis.coerceAtMost(180), easing = FastOutExit)
    }

    fun transitionSpec(durationMillis: Int): FiniteAnimationSpec<Float> = if (reduceMotion) {
        snap()
    } else {
        tween(durationMillis = durationMillis.coerceAtMost(280), easing = EaseOutExpo)
    }

    fun sizeTransitionSpec(durationMillis: Int): FiniteAnimationSpec<IntSize> = if (reduceMotion) {
        snap()
    } else {
        tween(durationMillis = durationMillis.coerceAtMost(280), easing = EaseOutExpo)
    }

    fun stateCrossfade(): ContentTransform = if (reduceMotion) {
        EnterTransition.None togetherWith ExitTransition.None
    } else {
        (fadeIn(tween(210, easing = EaseOutExpo)) +
            scaleIn(initialScale = 0.985f, animationSpec = tween(210, easing = EaseOutExpo)))
            .togetherWith(fadeOut(tween(130, easing = FastOutExit)))
    }

    companion object {
        val EaseOutExpo = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
        val FastOutExit = CubicBezierEasing(0.4f, 0f, 1f, 1f)
    }
}

val LocalSlateMotion = staticCompositionLocalOf { SlateMotion(reduceMotion = false) }

@Composable
fun SlateMotionProvider(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var reduceMotion by remember(context) { mutableStateOf(animationsDisabled(context)) }

    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduceMotion = animationsDisabled(context)
            }
        }
        val animatorUri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        val transitionUri = Settings.Global.getUriFor(Settings.Global.TRANSITION_ANIMATION_SCALE)
        resolver.registerContentObserver(animatorUri, false, observer)
        resolver.registerContentObserver(transitionUri, false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }

    CompositionLocalProvider(
        LocalSlateMotion provides SlateMotion(reduceMotion = reduceMotion),
        content = content
    )
}

private fun animationsDisabled(context: Context): Boolean {
    return try {
        val resolver = context.contentResolver
        val animatorScale = Settings.Global.getFloat(
            resolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        val transitionScale = Settings.Global.getFloat(
            resolver,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
            1f
        )
        animatorScale == 0f || transitionScale == 0f
    } catch (_: Exception) {
        false
    }
}
