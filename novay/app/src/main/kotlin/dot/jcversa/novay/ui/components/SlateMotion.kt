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
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Préférence utilisateur de mouvement (synchronisée avec le réglage système par défaut).
 */
enum class MotionPreference(
    val id: String,
    val label: String,
    val description: String,
) {
    SYSTEM(
        id = "system",
        label = "Système",
        description = "Suit automatiquement le réglage d'accessibilité Android.",
    ),
    FULL(
        id = "full",
        label = "Complet",
        description = "Active toutes les transitions fluides, ressorts et effets cinématiques.",
    ),
    REDUCED(
        id = "reduced",
        label = "Réduit",
        description = "Supprime les déplacements amples et accélère les changements d'état.",
    ),
    ;

    companion object {
        fun fromId(id: String?): MotionPreference =
            entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/**
 * État persistant de la préférence de mouvement NoVaY.
 */
object NovayMotionState {
    private const val PREFS = "novay_motion_prefs"
    private const val KEY_PREFERENCE = "motion_preference"

    var preference by mutableStateOf(MotionPreference.SYSTEM)
        private set

    fun init(context: Context) {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PREFERENCE, MotionPreference.SYSTEM.id)
        preference = MotionPreference.fromId(raw)
    }

    fun setPreference(context: Context, value: MotionPreference) {
        preference = value
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PREFERENCE, value.id)
            .apply()
    }
}

/**
 * Système de mouvement NoVaY (calibré selon les règles d'Emil Kowalski) :
 *  - courbes EaseOut fortes à l'entrée (< 300ms, jamais EaseIn sur l'UI) ;
 *  - sorties asymétriques plus courtes (~140ms) pour un feeling vif ;
 *  - ressorts interruptibles pour les morphs d'icônes, modales et capsules ;
 *  - zéro animation si le réglage système « réduire les animations » ou la préférence Réduit est actif.
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

    fun tabIndicatorSpec(): AnimationSpec<Dp> = dockDpSpec()

    fun sheetSpringSpec(): AnimationSpec<Float> = if (reduceMotion) {
        snap()
    } else {
        spring(
            dampingRatio = 0.86f,
            stiffness = 420f
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

    fun enterSpecWithDelay(index: Int, durationMillis: Int = 220): FiniteAnimationSpec<Float> =
        if (reduceMotion) {
            snap()
        } else {
            tween(
                durationMillis = durationMillis.coerceAtMost(280),
                delayMillis = staggeredDelayMs(index),
                easing = EaseOutExpo,
            )
        }

    fun offsetEnterSpec(durationMillis: Int = 220): FiniteAnimationSpec<IntOffset> = if (reduceMotion) {
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

    fun modalEnterTransition(): EnterTransition = if (reduceMotion) {
        EnterTransition.None
    } else {
        fadeIn(tween(220, easing = EaseOutExpo)) +
            scaleIn(initialScale = 0.96f, animationSpec = tween(220, easing = EaseOutExpo)) +
            slideInVertically(animationSpec = offsetEnterSpec(220)) { it / 10 }
    }

    fun modalExitTransition(): ExitTransition = if (reduceMotion) {
        ExitTransition.None
    } else {
        fadeOut(tween(140, easing = FastOutExit)) +
            scaleOut(targetScale = 0.97f, animationSpec = tween(140, easing = FastOutExit)) +
            slideOutVertically(animationSpec = tween(140, easing = FastOutExit)) { it / 14 }
    }

    fun staggeredDelayMs(index: Int, maxItems: Int = 6, stepMs: Int = 26): Int =
        if (reduceMotion || index <= 0) 0 else (index.coerceAtMost(maxItems) * stepMs)

    companion object {
        val EaseOutExpo = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
        val FastOutExit = CubicBezierEasing(0.4f, 0f, 1f, 1f)
    }
}

val LocalSlateMotion = staticCompositionLocalOf { SlateMotion(reduceMotion = false) }

@Composable
fun SlateMotionProvider(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var systemReduceMotion by remember(context) { mutableStateOf(animationsDisabled(context)) }
    val userPreference = NovayMotionState.preference

    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                systemReduceMotion = animationsDisabled(context)
            }
        }
        val animatorUri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        val transitionUri = Settings.Global.getUriFor(Settings.Global.TRANSITION_ANIMATION_SCALE)
        resolver.registerContentObserver(animatorUri, false, observer)
        resolver.registerContentObserver(transitionUri, false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }

    val effectiveReduceMotion = when (userPreference) {
        MotionPreference.SYSTEM -> systemReduceMotion
        MotionPreference.FULL -> false
        MotionPreference.REDUCED -> true
    }

    CompositionLocalProvider(
        LocalSlateMotion provides SlateMotion(reduceMotion = effectiveReduceMotion),
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
