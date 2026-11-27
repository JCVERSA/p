package dot.jcversa.novay.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Contrôle l'orientation et l'immersion système uniquement sur demande explicite
 * du mode plein écran paysage :
 *  - la lecture par défaut respecte la préférence naturelle tant qu'aucun plein écran n'a été forcé ;
 *  - le passage plein écran verrouille en paysage capteur et masque les barres système ;
 *  - la sortie du plein écran restaure l'orientation portrait sur téléphone si la séance reste active,
 *    et libère toujours vers SCREEN_ORIENTATION_UNSPECIFIED dès que la séance est fermée ou sur tablette.
 */
@Composable
fun PlayerOrientationController(
    hasActiveSession: Boolean,
    isFullscreen: Boolean,
    keepScreenOn: Boolean,
) {
    val view = LocalView.current
    var hadEnteredFullscreen by remember { mutableStateOf(false) }

    LaunchedEffect(hasActiveSession) {
        if (!hasActiveSession) {
            hadEnteredFullscreen = false
        }
    }

    DisposableEffect(view, keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(hasActiveSession, isFullscreen, view) {
        val activity = view.context as? Activity ?: return@LaunchedEffect
        val window = activity.window ?: return@LaunchedEffect
        val controller = WindowInsetsControllerCompat(window, view)
        val isCompactDevice = view.context.resources.configuration.smallestScreenWidthDp < 600
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (isFullscreen && hasActiveSession) {
            hadEnteredFullscreen = true
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = if (hasActiveSession && isCompactDevice && hadEnteredFullscreen) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val activity = view.context as? Activity ?: return@onDispose
            val window = activity.window ?: return@onDispose
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, view).show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}
