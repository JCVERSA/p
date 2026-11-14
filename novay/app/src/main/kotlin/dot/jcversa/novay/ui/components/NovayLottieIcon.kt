package dot.jcversa.novay.ui.components

import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty

/**
 * Icône animée Lottie (V2.7) — les animations useAnimations (MIT), lues
 * par lottie-compose, teintées à la couleur du thème SwiftSlate.
 *
 * Le dessin des icônes est monochrome (traits et remplissages) : les
 * propriétés dynamiques STROKE_COLOR/FILL_COLOR sur le sélecteur "**"
 *appliquent la teinte à toutes les couches.
 *
 * @param restartKey change la valeur pour rejouer l'animation (morphs
 *   play/pause, plein écran, sélection…).
 * @param loop boucle infinie (loaders, empty states).
 * @param size [Dp.Unspecified] pour laisser le modifier dimensionner.
 */
@Composable
fun NovayLottieIcon(
    @RawRes res: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    size: Dp = Dp.Unspecified,
    loop: Boolean = false,
    playing: Boolean = true,
    speed: Float = 1f,
    restartKey: Any? = null,
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(res))
    val effectiveTint = if (tint == Color.Unspecified) {
        MaterialTheme.colorScheme.onSurface
    } else {
        tint
    }
    val argb = effectiveTint.toArgb()
    val dynamicProperties = rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(LottieProperty.STROKE_COLOR, argb, "**"),
        rememberLottieDynamicProperty(LottieProperty.FILL_COLOR, argb, "**"),
    )
    val sized = if (size == Dp.Unspecified) modifier else modifier.size(size)
    Box(
        modifier = sized.semantics {
            if (contentDescription != null) this.contentDescription = contentDescription
        },
        contentAlignment = Alignment.Center,
    ) {
        key(restartKey) {
            val progress by animateLottieCompositionAsState(
                composition = composition,
                iterations = if (loop) LottieConstants.IterateForever else 1,
                isPlaying = playing,
                speed = speed,
            )
            LottieAnimation(
                composition = composition,
                progress = { progress },
                dynamicProperties = dynamicProperties,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
