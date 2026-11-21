package dot.jcversa.novay.ui.theme

import android.app.Activity
import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import dot.jcversa.novay.ui.components.SlateMotion

/**
 * Jetons sémantiques de verre cinéma, superposition vidéo, squelettes
 * shimmer et jetons de contraste WCAG AA — zéro couleur codée en dur dans les écrans.
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
    /** Fond CTA plein accessible (>= 5.10:1 avec [onCtaContainer]). */
    val ctaContainer: Color,
    val onCtaContainer: Color,
    /** Accent éclairci accessible (>= 5.06:1 sur secondaryContainer et pastilles VF). */
    val crimsonOnContainer: Color,
)

/**
 * Thèmes visuels sélectionnables dans Plus -> Apparence.
 */
enum class NovayThemePreset(
    val id: String,
    val label: String,
    val subtitle: String,
) {
    NOIR_CRIMSON(
        id = "noir_crimson",
        label = "Noir Crimson",
        subtitle = "Noir cinéma profond & rouge signature",
    ),
    MIDNIGHT_SLATE(
        id = "midnight_slate",
        label = "Midnight Slate",
        subtitle = "Ardoise nocturne & bleu saphir",
    ),
    OLED_PURE_BLACK(
        id = "oled_pure_black",
        label = "OLED Pure Black",
        subtitle = "Noir absolu haute densité pour dalles OLED",
    ),
    WARM_AMBER_GOLD(
        id = "warm_amber_gold",
        label = "Warm Amber Gold",
        subtitle = "Classique projection ambre & or chaud",
    ),
    POSTER_ADAPTIVE(
        id = "poster_adaptive",
        label = "Accent adaptatif",
        subtitle = "S'adapte en douceur aux couleurs de l'affiche active",
    ),
    ;

    companion object {
        fun fromId(id: String?): NovayThemePreset =
            entries.firstOrNull { it.id == id } ?: NOIR_CRIMSON
    }
}

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
    ctaContainer = Color(0xFFD9143C),
    onCtaContainer = Color(0xFFFFFFFF),
    crimsonOnContainer = Color(0xFFFF6B85),
)

private val NovayAmoledOverlays = NovayNoirOverlays.copy(
    glassSurface = Color(0xCC0A0A0E),
    glassSurfaceElevated = Color(0xEA101016),
    glassBorder = Color(0x36FFFFFF),
    shimmerBase = Color(0xFF101016),
    shimmerHighlight = Color(0xFF242432),
)

private val NovayMidnightSlateOverlays = NovayNoirOverlays.copy(
    glassSurface = Color(0xBA101622),
    glassSurfaceElevated = Color(0xDE161E2E),
    glassBorder = Color(0x2C93C5FD),
    shimmerBase = Color(0xFF131A28),
    shimmerHighlight = Color(0xFF243048),
    ctaContainer = Color(0xFF0284C7),
    onCtaContainer = Color(0xFFFFFFFF),
    crimsonOnContainer = Color(0xFF7DD3FC),
)

private val NovayWarmAmberOverlays = NovayNoirOverlays.copy(
    glassSurface = Color(0xBA16120D),
    glassSurfaceElevated = Color(0xDE1D1811),
    glassBorder = Color(0x2CFCD34D),
    shimmerBase = Color(0xFF191510),
    shimmerHighlight = Color(0xFF2E261C),
    ctaContainer = Color(0xFFB45309),
    onCtaContainer = Color(0xFFFFFFFF),
    crimsonOnContainer = Color(0xFFFCD34D),
)

val LocalNovayOverlayColors = staticCompositionLocalOf { NovayNoirOverlays }
val LocalDynamicPosterAccent = staticCompositionLocalOf<Color?> { null }

object NovayTheme {
    val overlays: NovayOverlayColors
        @Composable get() = LocalNovayOverlayColors.current
}

/**
 * Thème NoVaY — Cinema Midnight Noir & Crimson :
 *  - primary            = Crimson cinéma (#FB3758) pour les accents sur fond sombre ;
 *  - ctaContainer       = Crimson plein (#D9143C, 5.10:1 avec #FFFFFF) pour les CTA ;
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
    outline = Color(0xFF66667C),
    outlineVariant = Color(0xFF262634),
    surfaceContainerLowest = Color(0xFF0E0E14),
    surfaceContainerLow = Color(0xFF12121A),
    surfaceContainer = Color(0xFF161620),
    surfaceContainerHigh = Color(0xFF1F1F2D),
    surfaceContainerHighest = Color(0xFF282838),
)

/** Variante Midnight Slate : ardoise profonde et bleu saphir. */
private val NovayMidnightSlateScheme = NovayNoirScheme.copy(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF001E2E),
    primaryContainer = Color(0xFF0C4A6E),
    onPrimaryContainer = Color(0xFFE0F2FE),
    inversePrimary = Color(0xFF0284C7),
    secondary = Color(0xFFF59E0B),
    onSecondary = Color(0xFF1F1400),
    secondaryContainer = Color(0xFF102A43),
    onSecondaryContainer = Color(0xFFBAE6FD),
    background = Color(0xFF0A0E16),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF0A0E16),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF182032),
    onSurfaceVariant = Color(0xFFABB8CE),
    surfaceTint = Color(0xFF38BDF8),
    outline = Color(0xFF60708A),
    outlineVariant = Color(0xFF243046),
    surfaceContainerLowest = Color(0xFF0D121D),
    surfaceContainerLow = Color(0xFF121927),
    surfaceContainer = Color(0xFF172031),
    surfaceContainerHigh = Color(0xFF1F2B40),
    surfaceContainerHighest = Color(0xFF283750),
)

/** Variante AMOLED High Contrast : noir pur pour dalles OLED. */
private val NovayAmoledScheme = NovayNoirScheme.copy(
    background = Color.Black,
    onBackground = Color(0xFFFFFFFF),
    surface = Color.Black,
    onSurface = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFFC4C6D4),
    surfaceContainerLowest = Color(0xFF060609),
    surfaceContainerLow = Color(0xFF0B0B10),
    surfaceContainer = Color(0xFF101016),
    surfaceContainerHigh = Color(0xFF181822),
    surfaceContainerHighest = Color(0xFF222230),
    outlineVariant = Color(0xFF242432),
)

/** Variante Warm Amber Gold : projection dorée et charbon chaud. */
private val NovayWarmAmberScheme = NovayNoirScheme.copy(
    primary = Color(0xFFF59E0B),
    onPrimary = Color(0xFF241400),
    primaryContainer = Color(0xFF78350F),
    onPrimaryContainer = Color(0xFFFEF3C7),
    inversePrimary = Color(0xFFD97706),
    secondary = Color(0xFFFB7185),
    onSecondary = Color(0xFF2B0712),
    secondaryContainer = Color(0xFF38230A),
    onSecondaryContainer = Color(0xFFFDE68A),
    background = Color(0xFF0C0A08),
    onBackground = Color(0xFFFAF6F0),
    surface = Color(0xFF0C0A08),
    onSurface = Color(0xFFFAF6F0),
    surfaceVariant = Color(0xFF1E1914),
    onSurfaceVariant = Color(0xFFC8BDB0),
    surfaceTint = Color(0xFFF59E0B),
    outline = Color(0xFF786B5D),
    outlineVariant = Color(0xFF2F2820),
    surfaceContainerLowest = Color(0xFF100D0A),
    surfaceContainerLow = Color(0xFF16120E),
    surfaceContainer = Color(0xFF1C1712),
    surfaceContainerHigh = Color(0xFF251E17),
    surfaceContainerHighest = Color(0xFF30271E),
)

val NovayShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * État de thème au niveau du processus : gère les 5 préréglages d'apparence,
 * conserve la rétrocompatibilité stricte de `amoled` / `setAmoled`, et
 * propage l'accent adaptatif des affiches en temps réel.
 */
object NovayThemeState {
    private const val PREFS = "novay_theme_prefs"
    private const val KEY_AMOLED = "amoled"
    private const val KEY_PRESET = "theme_preset"
    private const val KEY_LAST_NON_AMOLED_PRESET = "last_non_amoled_preset"

    var preset by mutableStateOf(NovayThemePreset.NOIR_CRIMSON)
        private set

    var amoled by mutableStateOf(false)
        private set

    var dynamicAccent by mutableStateOf<Color?>(null)
        private set

    private var lastNonAmoledPreset: NovayThemePreset = NovayThemePreset.NOIR_CRIMSON

    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val savedAmoled = prefs.getBoolean(KEY_AMOLED, false)
        val savedPresetId = prefs.getString(KEY_PRESET, null)
        val savedNonAmoledId = prefs.getString(KEY_LAST_NON_AMOLED_PRESET, null)
        lastNonAmoledPreset = NovayThemePreset.fromId(savedNonAmoledId).let {
            if (it == NovayThemePreset.OLED_PURE_BLACK) NovayThemePreset.NOIR_CRIMSON else it
        }
        val resolvedPreset = when {
            savedPresetId != null -> NovayThemePreset.fromId(savedPresetId)
            savedAmoled -> NovayThemePreset.OLED_PURE_BLACK
            else -> NovayThemePreset.NOIR_CRIMSON
        }
        preset = resolvedPreset
        amoled = resolvedPreset == NovayThemePreset.OLED_PURE_BLACK
        if (resolvedPreset != NovayThemePreset.OLED_PURE_BLACK) {
            lastNonAmoledPreset = resolvedPreset
        }
    }

    fun setPreset(context: Context, value: NovayThemePreset) {
        if (value != NovayThemePreset.OLED_PURE_BLACK) {
            lastNonAmoledPreset = value
        }
        preset = value
        amoled = value == NovayThemePreset.OLED_PURE_BLACK
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PRESET, value.id)
            .putBoolean(KEY_AMOLED, amoled)
            .putString(KEY_LAST_NON_AMOLED_PRESET, lastNonAmoledPreset.id)
            .apply()
    }

    fun setAmoled(context: Context, value: Boolean) {
        val nextPreset = if (value) {
            NovayThemePreset.OLED_PURE_BLACK
        } else {
            if (preset == NovayThemePreset.OLED_PURE_BLACK) lastNonAmoledPreset else preset
        }
        setPreset(context, nextPreset)
    }

    fun updateDynamicAccent(color: Color?) {
        dynamicAccent = color
    }
}

/**
 * Calcule la palette et les jetons d'overlay correspondant à un [NovayThemePreset]
 * (utilisé à la fois pour le thème actif et pour les aperçus en direct dans Plus).
 */
fun previewThemeFor(
    preset: NovayThemePreset,
    dynamicAccent: Color? = null,
): Pair<ColorScheme, NovayOverlayColors> = when (preset) {
    NovayThemePreset.NOIR_CRIMSON -> NovayNoirScheme to NovayNoirOverlays
    NovayThemePreset.MIDNIGHT_SLATE -> NovayMidnightSlateScheme to NovayMidnightSlateOverlays
    NovayThemePreset.OLED_PURE_BLACK -> NovayAmoledScheme to NovayAmoledOverlays
    NovayThemePreset.WARM_AMBER_GOLD -> NovayWarmAmberScheme to NovayWarmAmberOverlays
    NovayThemePreset.POSTER_ADAPTIVE -> adaptiveThemePair(dynamicAccent)
}

private fun adaptiveThemePair(rawAccent: Color?): Pair<ColorScheme, NovayOverlayColors> {
    val baseAccent = rawAccent ?: Color(0xFFFB3758)
    // Assure une lisibilité lumineuse sur fond sombre tout en gardant un bouton CTA contrasté en texte blanc.
    val brightAccent = if (baseAccent.luminance() < 0.24f) {
        lerp(baseAccent, Color.White, 0.34f)
    } else {
        baseAccent
    }
    val ctaFill = if (baseAccent.luminance() > 0.42f) {
        lerp(baseAccent, Color(0xFF09090D), 0.35f)
    } else {
        lerp(baseAccent, Color(0xFF09090D), 0.14f)
    }
    val softContainer = lerp(Color(0xFF14141D), baseAccent, 0.22f)
    val readableOnContainer = lerp(brightAccent, Color.White, 0.30f)

    val scheme = NovayNoirScheme.copy(
        primary = brightAccent,
        onPrimary = if (brightAccent.luminance() > 0.56f) Color(0xFF0A0A0E) else Color.White,
        primaryContainer = lerp(Color(0xFF1A1A26), baseAccent, 0.38f),
        onPrimaryContainer = Color(0xFFFDFDFE),
        secondaryContainer = softContainer,
        onSecondaryContainer = readableOnContainer,
        surfaceTint = brightAccent,
    )
    val overlays = NovayNoirOverlays.copy(
        ctaContainer = ctaFill,
        onCtaContainer = Color.White,
        crimsonOnContainer = readableOnContainer,
        glassBorder = brightAccent.copy(alpha = 0.26f),
    )
    return scheme to overlays
}

/**
 * Thème NoVaY Cinema Hybrid : sombre permanent optimisé pour le visionnage nocturne,
 * avec 5 ambiances sélectionnables et accent adaptatif doux.
 */
@Composable
fun NoVaYTheme(
    content: @Composable () -> Unit
) {
    val preset = NovayThemeState.preset
    val dynamicAccent = NovayThemeState.dynamicAccent
    val (targetScheme, targetOverlays) = previewThemeFor(preset, dynamicAccent)

    val primaryAnim by animateColorAsState(
        targetValue = targetScheme.primary,
        animationSpec = tween(240, easing = SlateMotion.EaseOutExpo),
        label = "theme_primary",
    )
    val ctaAnim by animateColorAsState(
        targetValue = targetOverlays.ctaContainer,
        animationSpec = tween(240, easing = SlateMotion.EaseOutExpo),
        label = "theme_cta",
    )
    val crimsonContainerAnim by animateColorAsState(
        targetValue = targetOverlays.crimsonOnContainer,
        animationSpec = tween(240, easing = SlateMotion.EaseOutExpo),
        label = "theme_accent_text",
    )
    val secondaryContainerAnim by animateColorAsState(
        targetValue = targetScheme.secondaryContainer,
        animationSpec = tween(240, easing = SlateMotion.EaseOutExpo),
        label = "theme_secondary_container",
    )

    val colorScheme = targetScheme.copy(
        primary = primaryAnim,
        surfaceTint = primaryAnim,
        secondaryContainer = secondaryContainerAnim,
    )
    val overlayColors = targetOverlays.copy(
        ctaContainer = ctaAnim,
        crimsonOnContainer = crimsonContainerAnim,
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = false
                insetsController.isAppearanceLightNavigationBars = false
            }
        }
    }

    CompositionLocalProvider(
        LocalNovayOverlayColors provides overlayColors,
        LocalDynamicPosterAccent provides dynamicAccent,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = NovayShapes,
            content = content
        )
    }
}
