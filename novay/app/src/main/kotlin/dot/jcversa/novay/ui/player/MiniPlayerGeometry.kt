package dot.jcversa.novay.ui.player

import kotlin.math.hypot

/**
 * Modes de présentation du lecteur adaptatif (strictement séparés de l'état de lecture).
 */
enum class PlayerPresentationMode {
    HIDDEN,
    EMBEDDED_PORTRAIT,
    FLOATING_MINI,
    FULLSCREEN_LANDSCAPE,
}

enum class MiniPlayerCorner {
    TOP_START,
    TOP_END,
    BOTTOM_START,
    BOTTOM_END,
}

data class MiniPlayerBounds(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
)

data class MiniPlayerSizePx(
    val width: Float,
    val height: Float,
)

data class FittedVideoSize(
    val width: Float,
    val height: Float,
)

/**
 * Maths pures de positionnement, dimensionnement Fit (CONTAIN) sans recadrage,
 * collision aux bords sûrs, aimantation aux coins et hystérésis au défilement.
 */
object MiniPlayerGeometry {

    const val DEFAULT_ASPECT_RATIO = 16f / 9f
    const val EXIT_TO_MINI_VISIBLE_THRESHOLD = 0.35f
    const val RETURN_TO_INLINE_VISIBLE_THRESHOLD = 0.65f
    const val SNAP_VELOCITY_PROJECTION_SEC = 0.085f

    /** Largeur responsive de référence du mini-lecteur (dp). */
    fun responsiveWidthDp(availableWidthDp: Float): Float = when {
        availableWidthDp <= 0f -> 172f
        availableWidthDp < 360f -> 156f
        availableWidthDp < 600f -> 178f
        availableWidthDp < 840f -> 208f
        else -> 236f
    }

    /**
     * Préserve le ratio d'aspect vidéo réel (paysage, 4:3, portrait 9:16, cinémascope)
     * en ne rejetant que les valeurs invalides ou corrompues.
     */
    fun normalizeVideoAspectRatio(rawRatio: Float): Float =
        if (rawRatio.isFinite() && rawRatio > 0f) {
            rawRatio.coerceIn(0.25f, 4.0f)
        } else {
            DEFAULT_ASPECT_RATIO
        }

    /** Ratio ergonomique pour les usages bornés (alias conservé pour rétro-compatibilité). */
    fun sanitizedAspectRatio(rawRatio: Float): Float =
        if (rawRatio.isFinite() && rawRatio > 0f) {
            rawRatio.coerceIn(1.25f, 2.25f)
        } else {
            DEFAULT_ASPECT_RATIO
        }

    /**
     * Calcule la plus grande boîte respectant exactement [videoAspectRatio] qui tient
     * entièrement dans [containerWidth] × [containerHeight] (équivalent `ContentScale.Fit` /
     * `RESIZE_MODE_FIT`), sans jamais rogner ni déformer l'image.
     */
    fun computeFittedVideoSize(
        containerWidth: Float,
        containerHeight: Float,
        videoAspectRatio: Float,
    ): FittedVideoSize {
        if (containerWidth <= 0f || containerHeight <= 0f) {
            return FittedVideoSize(width = 0f, height = 0f)
        }
        val safeVideoAspect = normalizeVideoAspectRatio(videoAspectRatio)
        val containerAspect = containerWidth / containerHeight
        return if (containerAspect > safeVideoAspect) {
            // Conteneur plus large que la vidéo -> barres noires gauche/droite (pillarboxing)
            FittedVideoSize(
                width = containerHeight * safeVideoAspect,
                height = containerHeight,
            )
        } else {
            // Conteneur plus haut que la vidéo -> barres noires haut/bas (letterboxing)
            FittedVideoSize(
                width = containerWidth,
                height = containerWidth / safeVideoAspect,
            )
        }
    }

    /** Ratio de l'enveloppe portrait intégrée (borne les vidéos verticales sans rogner). */
    fun computeEmbeddedEnvelopeAspectRatio(videoAspectRatio: Float): Float =
        normalizeVideoAspectRatio(videoAspectRatio).coerceIn(1.0f, 2.35f)

    /** Dimensionne le mini-lecteur en préservant le ratio réel de la vidéo (y compris portrait). */
    fun computeMiniPlayerSizeDp(
        availableWidthDp: Float,
        videoAspectRatio: Float,
    ): FittedVideoSize {
        val baseLandscapeWidth = responsiveWidthDp(availableWidthDp)
        val safeAspect = normalizeVideoAspectRatio(videoAspectRatio)
        return if (safeAspect >= 1f) {
            val maxHeight = baseLandscapeWidth * 0.9f
            val fitted = computeFittedVideoSize(
                containerWidth = baseLandscapeWidth,
                containerHeight = maxHeight,
                videoAspectRatio = safeAspect,
            )
            FittedVideoSize(
                width = fitted.width.coerceAtLeast(120f),
                height = fitted.height.coerceAtLeast(72f),
            )
        } else {
            // Vidéo portrait (ex. 9:16) : hauteur bornée et largeur ajustée au ratio exact
            val maxHeight = baseLandscapeWidth * 1.15f
            val fitted = computeFittedVideoSize(
                containerWidth = baseLandscapeWidth,
                containerHeight = maxHeight,
                videoAspectRatio = safeAspect,
            )
            if (fitted.width < 108f) {
                FittedVideoSize(width = 108f, height = 108f / safeAspect)
            } else {
                fitted
            }
        }
    }

    /** Calcule le rectangle sûr de déplacement, en évitant encoches, dock et rail. */
    fun computeSafeBounds(
        containerWidthPx: Float,
        containerHeightPx: Float,
        miniSize: MiniPlayerSizePx,
        leftInsetPx: Float,
        topInsetPx: Float,
        rightInsetPx: Float,
        bottomInsetPx: Float,
    ): MiniPlayerBounds {
        val rawMinX = leftInsetPx
        val rawMinY = topInsetPx
        val rawMaxX = (containerWidthPx - rightInsetPx - miniSize.width).coerceAtLeast(rawMinX)
        val rawMaxY = (containerHeightPx - bottomInsetPx - miniSize.height).coerceAtLeast(rawMinY)
        return MiniPlayerBounds(
            minX = rawMinX,
            minY = rawMinY,
            maxX = rawMaxX,
            maxY = rawMaxY,
        )
    }

    fun clampOffset(
        x: Float,
        y: Float,
        bounds: MiniPlayerBounds,
    ): Pair<Float, Float> = Pair(
        x.coerceIn(bounds.minX, bounds.maxX),
        y.coerceIn(bounds.minY, bounds.maxY),
    )

    fun cornerOffset(
        corner: MiniPlayerCorner,
        bounds: MiniPlayerBounds,
    ): Pair<Float, Float> = when (corner) {
        MiniPlayerCorner.TOP_START -> bounds.minX to bounds.minY
        MiniPlayerCorner.TOP_END -> bounds.maxX to bounds.minY
        MiniPlayerCorner.BOTTOM_START -> bounds.minX to bounds.maxY
        MiniPlayerCorner.BOTTOM_END -> bounds.maxX to bounds.maxY
    }

    /** Choisit le coin sûr le plus proche en tenant compte d'une légère inertie au lâcher. */
    fun nearestCorner(
        x: Float,
        y: Float,
        velocityX: Float,
        velocityY: Float,
        bounds: MiniPlayerBounds,
    ): MiniPlayerCorner {
        val projectedX = (x + velocityX * SNAP_VELOCITY_PROJECTION_SEC).coerceIn(bounds.minX, bounds.maxX)
        val projectedY = (y + velocityY * SNAP_VELOCITY_PROJECTION_SEC).coerceIn(bounds.minY, bounds.maxY)

        return MiniPlayerCorner.entries.minBy { corner ->
            val (cx, cy) = cornerOffset(corner, bounds)
            hypot((projectedX - cx).toDouble(), (projectedY - cy).toDouble())
        }
    }

    /** Calcule la fraction visible [0f..1f] d'un item de LazyColumn. */
    fun computeVisibleFraction(
        itemOffset: Int,
        itemSize: Int,
        viewportStart: Int,
        viewportEnd: Int,
    ): Float {
        if (itemSize <= 0 || viewportEnd <= viewportStart) return 0f
        val itemStart = itemOffset
        val itemEnd = itemOffset + itemSize
        val visiblePx = (minOf(itemEnd, viewportEnd) - maxOf(itemStart, viewportStart)).coerceAtLeast(0)
        return (visiblePx.toFloat() / itemSize.toFloat()).coerceIn(0f, 1f)
    }

    /** Applique une hystérésis pour éviter tout scintillement lors des micro-défilements. */
    fun resolveInlineModeWithHysteresis(
        currentMode: PlayerPresentationMode,
        visibleFraction: Float,
    ): PlayerPresentationMode = when (currentMode) {
        PlayerPresentationMode.FULLSCREEN_LANDSCAPE -> PlayerPresentationMode.FULLSCREEN_LANDSCAPE
        PlayerPresentationMode.HIDDEN -> PlayerPresentationMode.HIDDEN
        PlayerPresentationMode.EMBEDDED_PORTRAIT -> {
            if (visibleFraction < EXIT_TO_MINI_VISIBLE_THRESHOLD) {
                PlayerPresentationMode.FLOATING_MINI
            } else {
                PlayerPresentationMode.EMBEDDED_PORTRAIT
            }
        }
        PlayerPresentationMode.FLOATING_MINI -> {
            if (visibleFraction > RETURN_TO_INLINE_VISIBLE_THRESHOLD) {
                PlayerPresentationMode.EMBEDDED_PORTRAIT
            } else {
                PlayerPresentationMode.FLOATING_MINI
            }
        }
    }
}
