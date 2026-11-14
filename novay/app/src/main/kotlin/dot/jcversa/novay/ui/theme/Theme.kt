package dot.jcversa.novay.ui.theme

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * Jetons sémantiques de verre cinéma, superposition vidéo et squelettes
 * shimmer — zéro couleur codée en dur dans les écrans.
 */
@Immutable
data class NovayOverlayColors(
    val glassSurface: Color,
    val glassSurfaceElevated: Color,
    val glassBorder: Color,
    val scrimTop: Color,
    val scrimBottom: Color,
    val videoControlsInk: Color,
    val videoControlsMutedInk: Color,
    val videoTrackInactive: Color,
    val shimmerBase: Color,
    val shimmerHighlight: Color,
)

private val NovayNoirOverlays = NovayOverlayColors(
    glassSurface = Color(0xBA12121A),
    glassSurfaceElevated = Color(0xDE161620),
    glassBorder = Color(0x28FFFFFF),
    scrimTop = Color(0xA6000000),
    scrimBottom = Color(0xCC000000),
    videoControlsInk = Color(0xFFF9FAFB),
    videoControlsMutedInk = Color(0xD9F4F4F6),
    videoTrackInactive = Color(0x5CFFFFFF),
    shimmerBase = Color(0xFF161620),
    shimmerHighlight = Color(0xFF282838),
)

private val NovayAmoledOverlays = NovayNoirOverlays.copy(
    glassSurface = Color(0xC80D0D12),
    glassSurfaceElevated = Color(0xE6111118),
    shimmerBase = Color(0xFF101016),
    shimmerHighlight = Color(0xFF222230),
)

val LocalNovayOverlayColors = staticCompositionLocalOf { NovayNoirOverlays }

object NovayTheme {
    val overlays: NovayOverlayColors
        @Composable get() = LocalNovayOverlayColors.current
}

/**
 * Thème NoVaY — Cinema Midnight Noir & Crimson :
 *  - primary            = Crimson cinéma (#FB3758) pour les CTA et lectures ;
 *  - secondary          = Or projection (#F59E0B) réservé aux nouveaux épisodes ;
 *  - secondaryContainer = Pastille active crimson foncé (#3B111E) ;
 *  - tertiary           = Émeraude (#34D399) pour l'état téléchargé / hors-ligne.
 */
private val NovayNoirScheme = darkColorScheme(
    primary = Color(0xFFFB3758),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF881337),
    onPrimaryContainer = Color(0xFFFFE4E6),
    inversePrimary = Color(0xFFE11D48),
    secondary = Color(0xFFF59E0B),
    onSecondary = Color(0xFF1F1400),
    secondaryContainer = Color(0xFF3B111E),
    onSecondaryContainer = Color(0xFFFDA4AF),
    tertiary = Color(0xFF34D399),
    onTertiary = Color(0xFF00291B),
    tertiaryContainer = Color(0xFF064E3B),
    onTertiaryContainer = Color(0xFFA7F3D0),
    background = Color(0xFF09090D),
    onBackground = Color(0xFFF4F4F6),
    surface = Color(0xFF09090D),
    onSurface = Color(0xFFF4F4F6),
    surfaceVariant = Color(0xFF181822),
    onSurfaceVariant = Color(0xFFB4B4C4),
    surfaceTint = Color(0xFFFB3758),
    inverseSurface = Color(0xFFF4F4F6),
    inverseOnSurface = Color(0xFF09090D),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF3D0005),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
    outline = Color(0xFF5A5A6E),
    outlineVariant = Color(0xFF262634),
    surfaceContainerLowest = Color(0xFF0E0E14),
    surfaceContainerLow = Color(0xFF12121A),
    surfaceContainer = Color(0xFF161620),
    surfaceContainerHigh = Color(0xFF1F1F2D),
    surfaceContainerHighest = Color(0xFF282838),
)

/**
 * Variante AMOLED : noir pur pour le fond de scène, conteneurs légèrement
 * relevés (#0D0D12 -> #1E1E28) pour conserver la hiérarchie au défilement.
 */
private val NovayAmoledScheme = NovayNoirScheme.copy(
    background = Color.Black,
    onBackground = Color(0xFFF9FAFB),
    surface = Color.Black,
    onSurface = Color(0xFFF9FAFB),
    surfaceContainerLowest = Color(0xFF08080B),
    surfaceContainerLow = Color(0xFF0D0D12),
    surfaceContainer = Color(0xFF111118),
    surfaceContainerHigh = Color(0xFF181822),
    surfaceContainerHighest = Color(0xFF222230),
    outlineVariant = Color(0xFF1F1F2B),
)

val NovayShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(28.dp),
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
 * Thème NoVaY Cinema Midnight Noir : sombre permanent optimisé pour le
 * visionnage nocturne et l'immersion cinéma.
 */
@Composable
fun NoVaYTheme(
    content: @Composable () -> Unit
) {
    val amoled = NovayThemeState.amoled
    val colorScheme = if (amoled) NovayAmoledScheme else NovayNoirScheme
    val overlayColors = if (amoled) NovayAmoledOverlays else NovayNoirOverlays

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                @Suppress("DEPRECATION")
                window.statusBarColor = Color.Transparent.toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor = Color.Transparent.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = false
                insetsController.isAppearanceLightNavigationBars = false
            }
        }
    }

    CompositionLocalProvider(LocalNovayOverlayColors provides overlayColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = NovayShapes,
            content = content
        )
    }
}
