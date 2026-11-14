package dot.jcversa.novay.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dot.jcversa.novay.R

/**
 * Spinner de chargement NoVaY (V2.7) : animation Lottie useAnimations
 * (« loading ») teintée à la couleur du thème, en boucle.
 *
 * La signature historique (modifier, size, tint, speedMs) est conservée :
 * les écrans qui l'utilisent ne changent pas. speedMs dérive la vitesse
 * de lecture de l'animation (1100 ms = vitesse 1).
 */
@Composable
fun SwiftSlateSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = Color.Unspecified,
    speedMs: Int = 1100,
) {
    val effectiveTint = if (tint == Color.Unspecified) {
        MaterialTheme.colorScheme.primary
    } else {
        tint
    }
    Box(modifier = modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
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
