package dot.jcversa.novay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dot.jcversa.novay.performance.LocalPerformanceProfile
import dot.jcversa.novay.ui.theme.NovayTheme

/**
 * État Haze partagé pour les barres flottantes (AppBar, NavigationBar)
 * et le HUD verre dépoli du lecteur vidéo.
 */
val LocalNovayHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * Calcule le voile translucide de repli lorsque le flou temps réel est
 * désactivé sur appareils modestes ou anciens Android (< Android 12).
 */
internal fun fallbackScrimColor(tint: Color): Color {
    val boostedAlpha = (tint.alpha + 0.14f).coerceIn(0.84f, 0.96f)
    return tint.copy(alpha = boostedAlpha)
}

@Composable
fun Modifier.novayHazeSource(
    hazeState: HazeState? = LocalNovayHazeState.current,
): Modifier {
    val allowBlur = LocalPerformanceProfile.current.allowLiveHazeBlur
    return if (hazeState != null && allowBlur) {
        this.hazeSource(state = hazeState)
    } else {
        this
    }
}

@Composable
fun Modifier.novayCinemaGlass(
    shape: Shape = RectangleShape,
    hazeState: HazeState? = LocalNovayHazeState.current,
    tint: Color = NovayTheme.overlays.glassSurface,
    borderColor: Color = NovayTheme.overlays.glassBorder,
    showBorder: Boolean = false,
    blurRadius: Dp = 18.dp,
): Modifier {
    val allowBlur = LocalPerformanceProfile.current.allowLiveHazeBlur
    val clipped = this.clip(shape)
    val base = if (hazeState != null && allowBlur) {
        clipped.hazeEffect(
            state = hazeState,
            style = HazeStyle(
                backgroundColor = tint.copy(alpha = 1f),
                tint = HazeTint(tint),
                blurRadius = blurRadius,
                noiseFactor = 0.06f,
            ),
        )
    } else {
        clipped.background(fallbackScrimColor(tint))
    }
    return if (showBorder) {
        base.border(width = 0.5.dp, color = borderColor, shape = shape)
    } else {
        base
    }
}
