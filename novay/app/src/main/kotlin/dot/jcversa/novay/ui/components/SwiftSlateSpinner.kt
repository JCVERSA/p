package dot.jcversa.novay.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dot.jcversa.novay.R

/**
 * Spinner de chargement NoVaY (V2.7) : animation Lottie useAnimations
 * (« loading ») teintée à la couleur du thème, en boucle. En mode
 * réduction de mouvement, affiche un anneau stationnaire calme.
 */
@Composable
fun SwiftSlateSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = Color.Unspecified,
    speedMs: Int = 1100,
) {
    val reduceMotion = LocalSlateMotion.current.reduceMotion
    val effectiveTint = if (tint == Color.Unspecified) {
        MaterialTheme.colorScheme.primary
    } else {
        tint
    }
    Box(modifier = modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
        if (reduceMotion) {
            CircularProgressIndicator(
                progress = { 0.72f },
                modifier = Modifier.size(size),
                color = effectiveTint,
                trackColor = effectiveTint.copy(alpha = 0.22f),
                strokeWidth = 2.5.dp,
            )
        } else {
            NovayLottieIcon(
                res = R.raw.lottie_loading,
                contentDescription = null,
                tint = effectiveTint,
                size = size,
                loop = true,
                playing = true,
                speed = 1100f / speedMs.coerceAtLeast(1),
            )
        }
    }
}
