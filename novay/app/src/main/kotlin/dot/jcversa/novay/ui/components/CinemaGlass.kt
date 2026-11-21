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
import dot.jcversa.novay.ui.theme.NovayTheme

/**
 * État Haze partagé pour les barres flottantes (AppBar, NavigationBar)
 * et le HUD verre dépoli du lecteur vidéo.
 */
val LocalNovayHazeState = staticCompositionLocalOf<HazeState?> { null }

@Composable
fun Modifier.novayHazeSource(
    hazeState: HazeState? = LocalNovayHazeState.current,
): Modifier = if (hazeState != null) {
    this.hazeSource(state = hazeState)
} else {
    this
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
    val clipped = this.clip(shape)
    val base = if (hazeState != null) {
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
        clipped.background(tint)
    }
    return if (showBorder) {
        base.border(width = 0.5.dp, color = borderColor, shape = shape)
    } else {
        base
    }
}
