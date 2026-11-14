package dot.jcversa.novay.ui.theme

import android.app.Activity
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Thème NoVaY V2.4 : design system porté de Mihon (fork de Tachiyomi,
 * github.com/mihonapp/mihon, Apache-2.0). Schéma par défaut « Tachiyomi »
 * (bleu #2979FF) + variante AMOLED (noir pur), sombre permanent.
 *
 * Chaque couleur a le rôle sémantique Mihon :
 *  - secondary        = badge « nouveaux épisodes » ;
 *  - tertiary         = badge « téléchargé » ;
 *  - secondaryContainer = pastille de sélection de la barre de navigation.
 */
private val NovayDarkScheme = darkColorScheme(
    primary = Color(0xFFB0C6FF),
    onPrimary = Color(0xFF002D6E),
    primaryContainer = Color(0xFF00429B),
    onPrimaryContainer = Color(0xFFD9E2FF),
    inversePrimary = Color(0xFF0058CA),
    secondary = Color(0xFFB0C6FF),
    onSecondary = Color(0xFF002D6E),
    secondaryContainer = Color(0xFF00429B),
    onSecondaryContainer = Color(0xFFD9E2FF),
    tertiary = Color(0xFF7ADC77),
    onTertiary = Color(0xFF003909),
    tertiaryContainer = Color(0xFF005312),
    onTertiaryContainer = Color(0xFF95F990),
    background = Color(0xFF1B1B1F),
    onBackground = Color(0xFFE3E2E6),
    surface = Color(0xFF1B1B1F),
    onSurface = Color(0xFFE3E2E6),
    surfaceVariant = Color(0xFF211F26),
    onSurfaceVariant = Color(0xFFC5C6D0),
    surfaceTint = Color(0xFFB0C6FF),
    inverseSurface = Color(0xFFE3E2E6),
    inverseOnSurface = Color(0xFF1B1B1F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8F9099),
    outlineVariant = Color(0xFF44464F),
    surfaceContainerLowest = Color(0xFF1A181D),
    surfaceContainerLow = Color(0xFF1E1C22),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF292730),
    surfaceContainerHighest = Color(0xFF302E38),
)

/**
 * AMOLED : noir pur pour le fond, conteneurs quasi noirs (BaseColorScheme
 * de Mihon — « pas de noir absolu pour ce qui défile derrière »).
 */
private val NovayAmoledScheme = NovayDarkScheme.copy(
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceContainerLowest = Color(0xFF0C0C0C),
    surfaceContainerLow = Color(0xFF0C0C0C),
    surfaceContainer = Color(0xFF0C0C0C),
    surfaceContainerHigh = Color(0xFF131313),
    surfaceContainerHighest = Color(0xFF1B1B1B),
)

/**
 * État AMOLED au niveau du processus : la bascule dans Plus → Réglages
 * recompose toute l'appli instantanément, la préférence survit au
 * redémarrage (initialisée par MainActivity avant le premier rendu).
 */
object NovayThemeState {
    private const val PREFS = "novay_theme_prefs"
    private const val KEY_AMOLED = "amoled"

    var amoled by mutableStateOf(false)
        private set

    fun init(context: Context) {
        amoled = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AMOLED, false)
    }

    fun setAmoled(context: Context, value: Boolean) {
        amoled = value
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AMOLED, value).apply()
    }
}

/**
 * Thème NoVaY : sombre permanent (streaming de nuit oblige), look Mihon.
 */
@Composable
fun NoVaYTheme(
    content: @Composable () -> Unit
) {
    val amoled = NovayThemeState.amoled
    val colorScheme = if (amoled) NovayAmoledScheme else NovayDarkScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = false
                insetsController.isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
