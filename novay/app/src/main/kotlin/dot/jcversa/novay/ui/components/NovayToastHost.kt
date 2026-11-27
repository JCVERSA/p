package dot.jcversa.novay.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dot.jcversa.novay.R
import dot.jcversa.novay.ui.theme.NovayTheme
import kotlinx.coroutines.delay

enum class NovayToastTone {
    SUCCESS,
    INFO,
    ERROR,
}

@Immutable
data class NovayToastMessage(
    val id: Long,
    val message: String,
    val tone: NovayToastTone = NovayToastTone.SUCCESS,
)

@Stable
class NovayToastController {
    var current by mutableStateOf<NovayToastMessage?>(null)
        private set

    private var nextId by mutableLongStateOf(1L)

    fun show(
        message: String,
        tone: NovayToastTone = NovayToastTone.SUCCESS,
    ) {
        if (message.isBlank()) return
        current = NovayToastMessage(
            id = nextId++,
            message = message,
            tone = tone,
        )
    }

    fun dismiss() {
        current = null
    }
}

val LocalNovayToast = staticCompositionLocalOf { NovayToastController() }

@Composable
fun rememberNovayToastController(): NovayToastController = remember { NovayToastController() }

/**
 * Notification flottante verre dépoli discrète : joue l'icône Lottie de
 * confirmation (lottie_checkcircle / lottie_alertcircle) au-dessus du dock.
 */
@Composable
fun NovayToastHost(
    controller: NovayToastController,
    modifier: Modifier = Modifier,
) {
    val active = controller.current
    val motion = LocalSlateMotion.current
    val overlays = NovayTheme.overlays

    LaunchedEffect(active?.id) {
        if (active != null) {
            delay(2600L)
            if (controller.current?.id == active.id) {
                controller.dismiss()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = active != null,
            enter = if (motion.reduceMotion) {
                EnterTransition.None
            } else {
                fadeIn(motion.enterSpec(200)) +
                    slideInVertically(motion.offsetEnterSpec(220)) { it / 2 }
            },
            exit = if (motion.reduceMotion) {
                ExitTransition.None
            } else {
                fadeOut(motion.exitSpec(140)) +
                    slideOutVertically { it / 3 }
            },
        ) {
            val toast = active ?: return@AnimatedVisibility
            val accentColor = when (toast.tone) {
                NovayToastTone.SUCCESS -> MaterialTheme.colorScheme.tertiary
                NovayToastTone.INFO -> MaterialTheme.colorScheme.primary
                NovayToastTone.ERROR -> MaterialTheme.colorScheme.error
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .novayCinemaGlass(
                        shape = RoundedCornerShape(999.dp),
                        tint = overlays.glassSurfaceElevated,
                        borderColor = accentColor.copy(alpha = 0.48f),
                        showBorder = true,
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .testTag("novay_toast_banner"),
            ) {
                when (toast.tone) {
                    NovayToastTone.SUCCESS -> NovayLottieIcon(
                        res = R.raw.lottie_checkcircle,
                        contentDescription = null,
                        tint = accentColor,
                        size = 20.dp,
                        restartKey = toast.id,
                    )
                    NovayToastTone.ERROR -> NovayLottieIcon(
                        res = R.raw.lottie_alertcircle,
                        contentDescription = null,
                        tint = accentColor,
                        size = 20.dp,
                        restartKey = toast.id,
                    )
                    NovayToastTone.INFO -> Icon(
                        imageVector = NovayIcons.Info,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = toast.message,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
