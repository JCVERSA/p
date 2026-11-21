package dot.jcversa.novay.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests unitaires purs pour la géométrie du mini-lecteur flottant :
 * dimensionnement responsive, bornes sûres (dock/rail/encoches),
 * aimantation aux 4 coins et hystérésis de visibilité au défilement.
 */
class MiniPlayerGeometryTest {

    @Test
    fun `responsiveWidthDp s adapte des petits ecrans aux tablettes`() {
        assertEquals(156f, MiniPlayerGeometry.responsiveWidthDp(320f), 0.01f)
        assertEquals(178f, MiniPlayerGeometry.responsiveWidthDp(412f), 0.01f)
        assertEquals(208f, MiniPlayerGeometry.responsiveWidthDp(720f), 0.01f)
        assertEquals(236f, MiniPlayerGeometry.responsiveWidthDp(1024f), 0.01f)
        assertEquals(172f, MiniPlayerGeometry.responsiveWidthDp(0f), 0.01f)
    }

    @Test
    fun `sanitizedAspectRatio normalise les valeurs invalides ou extremes`() {
        assertEquals(MiniPlayerGeometry.DEFAULT_ASPECT_RATIO, MiniPlayerGeometry.sanitizedAspectRatio(0f), 0.001f)
        assertEquals(MiniPlayerGeometry.DEFAULT_ASPECT_RATIO, MiniPlayerGeometry.sanitizedAspectRatio(Float.NaN), 0.001f)
        assertEquals(MiniPlayerGeometry.DEFAULT_ASPECT_RATIO, MiniPlayerGeometry.sanitizedAspectRatio(-1.5f), 0.001f)
        assertEquals(1.25f, MiniPlayerGeometry.sanitizedAspectRatio(0.75f), 0.001f)
        assertEquals(2.25f, MiniPlayerGeometry.sanitizedAspectRatio(3.0f), 0.001f)
        assertEquals(16f / 9f, MiniPlayerGeometry.sanitizedAspectRatio(16f / 9f), 0.001f)
    }

    @Test
    fun `computeSafeBounds et clampOffset respectent dock rail et marges`() {
        val bounds = MiniPlayerGeometry.computeSafeBounds(
            containerWidthPx = 1080f,
            containerHeightPx = 2400f,
            miniSize = MiniPlayerSizePx(width = 450f, height = 253f),
            leftInsetPx = 120f,
            topInsetPx = 80f,
            rightInsetPx = 32f,
            bottomInsetPx = 220f,
        )
        assertEquals(120f, bounds.minX, 0.01f)
        assertEquals(80f, bounds.minY, 0.01f)
        assertEquals(598f, bounds.maxX, 0.01f)
        assertEquals(1927f, bounds.maxY, 0.01f)

        val clampedTopLeft = MiniPlayerGeometry.clampOffset(-100f, -50f, bounds)
        assertEquals(120f, clampedTopLeft.first, 0.01f)
        assertEquals(80f, clampedTopLeft.second, 0.01f)

        val clampedBottomRight = MiniPlayerGeometry.clampOffset(2000f, 3000f, bounds)
        assertEquals(598f, clampedBottomRight.first, 0.01f)
        assertEquals(1927f, clampedBottomRight.second, 0.01f)
    }

    @Test
    fun `nearestCorner choisit le coin le plus proche selon position et velocite`() {
        val bounds = MiniPlayerBounds(minX = 32f, minY = 64f, maxX = 600f, maxY = 1800f)

        assertEquals(
            MiniPlayerCorner.TOP_START,
            MiniPlayerGeometry.nearestCorner(x = 80f, y = 120f, velocityX = 0f, velocityY = 0f, bounds = bounds),
        )
        assertEquals(
            MiniPlayerCorner.TOP_END,
            MiniPlayerGeometry.nearestCorner(x = 520f, y = 120f, velocityX = 0f, velocityY = 0f, bounds = bounds),
        )
        assertEquals(
            MiniPlayerCorner.BOTTOM_START,
            MiniPlayerGeometry.nearestCorner(x = 80f, y = 1650f, velocityX = 0f, velocityY = 0f, bounds = bounds),
        )
        assertEquals(
            MiniPlayerCorner.BOTTOM_END,
            MiniPlayerGeometry.nearestCorner(x = 540f, y = 1700f, velocityX = 0f, velocityY = 0f, bounds = bounds),
        )

        // Geste lance vers le bas-droite depuis le milieu gauche -> passe au coin oppose
        assertEquals(
            MiniPlayerCorner.BOTTOM_END,
            MiniPlayerGeometry.nearestCorner(x = 280f, y = 860f, velocityX = 4500f, velocityY = 4500f, bounds = bounds),
        )
    }

    @Test
    fun `computeVisibleFraction calcule exactement la visibilite partielle`() {
        assertEquals(
            1f,
            MiniPlayerGeometry.computeVisibleFraction(itemOffset = 0, itemSize = 600, viewportStart = 0, viewportEnd = 1800),
            0.001f,
        )
        assertEquals(
            0.5f,
            MiniPlayerGeometry.computeVisibleFraction(itemOffset = -300, itemSize = 600, viewportStart = 0, viewportEnd = 1800),
            0.001f,
        )
        assertEquals(
            0f,
            MiniPlayerGeometry.computeVisibleFraction(itemOffset = -600, itemSize = 600, viewportStart = 0, viewportEnd = 1800),
            0.001f,
        )
        assertEquals(
            0f,
            MiniPlayerGeometry.computeVisibleFraction(itemOffset = 0, itemSize = 0, viewportStart = 0, viewportEnd = 1800),
            0.001f,
        )
    }

    @Test
    fun `resolveInlineModeWithHysteresis evite le scintillement aux micro-scrolls`() {
        // Tant que >= 35 % reste visible, on reste integre
        assertEquals(
            PlayerPresentationMode.EMBEDDED_PORTRAIT,
            MiniPlayerGeometry.resolveInlineModeWithHysteresis(
                currentMode = PlayerPresentationMode.EMBEDDED_PORTRAIT,
                visibleFraction = 0.40f,
            ),
        )
        // Sous 35 %, passage automatique au mini-lecteur flottant
        assertEquals(
            PlayerPresentationMode.FLOATING_MINI,
            MiniPlayerGeometry.resolveInlineModeWithHysteresis(
                currentMode = PlayerPresentationMode.EMBEDDED_PORTRAIT,
                visibleFraction = 0.25f,
            ),
        )
        // Une fois en mini-lecteur, un retour partiel a 50 % ne redocking pas encore (zone neutre)
        assertEquals(
            PlayerPresentationMode.FLOATING_MINI,
            MiniPlayerGeometry.resolveInlineModeWithHysteresis(
                currentMode = PlayerPresentationMode.FLOATING_MINI,
                visibleFraction = 0.50f,
            ),
        )
        // Au-dessus de 65 %, redocking fluide dans le lecteur integre
        assertEquals(
            PlayerPresentationMode.EMBEDDED_PORTRAIT,
            MiniPlayerGeometry.resolveInlineModeWithHysteresis(
                currentMode = PlayerPresentationMode.FLOATING_MINI,
                visibleFraction = 0.70f,
            ),
        )
        // Plein ecran et etat ferme restent verrouilles
        assertEquals(
            PlayerPresentationMode.FULLSCREEN_LANDSCAPE,
            MiniPlayerGeometry.resolveInlineModeWithHysteresis(
                currentMode = PlayerPresentationMode.FULLSCREEN_LANDSCAPE,
                visibleFraction = 0f,
            ),
        )
        assertTrue(
            MiniPlayerGeometry.resolveInlineModeWithHysteresis(
                currentMode = PlayerPresentationMode.HIDDEN,
                visibleFraction = 1f,
            ) == PlayerPresentationMode.HIDDEN,
        )
    }

    @Test
    fun `normalizeVideoAspectRatio preserve 4_3, 16_9, portrait 9_16 et scope sans forcer 16_9`() {
        assertEquals(4f / 3f, MiniPlayerGeometry.normalizeVideoAspectRatio(4f / 3f), 0.001f)
        assertEquals(9f / 16f, MiniPlayerGeometry.normalizeVideoAspectRatio(9f / 16f), 0.001f)
        assertEquals(2.39f, MiniPlayerGeometry.normalizeVideoAspectRatio(2.39f), 0.001f)
        assertEquals(MiniPlayerGeometry.DEFAULT_ASPECT_RATIO, MiniPlayerGeometry.normalizeVideoAspectRatio(0f), 0.001f)
        assertEquals(MiniPlayerGeometry.DEFAULT_ASPECT_RATIO, MiniPlayerGeometry.normalizeVideoAspectRatio(Float.NaN), 0.001f)
    }

    @Test
    fun `computeFittedVideoSize encadre en Fit sans rognage pour 16_9, 4_3, portrait et ultrawide`() {
        // 16:9 sur ecran paysage moderne 20:9 (2400 x 1080) -> barres noires gauche/droite
        val fit169On209 = MiniPlayerGeometry.computeFittedVideoSize(2400f, 1080f, 16f / 9f)
        assertEquals(1920f, fit169On209.width, 0.5f)
        assertEquals(1080f, fit169On209.height, 0.5f)

        // 4:3 sur ecran paysage 16:9 (1920 x 1080) -> barres noires gauche/droite
        val fit43On169 = MiniPlayerGeometry.computeFittedVideoSize(1920f, 1080f, 4f / 3f)
        assertEquals(1440f, fit43On169.width, 0.5f)
        assertEquals(1080f, fit43On169.height, 0.5f)

        // Video portrait 9:16 en plein ecran paysage (1920 x 1080) -> largeur 607.5, hauteur 1080 (zero rognage haut/bas)
        val fitPortraitOnLandscape = MiniPlayerGeometry.computeFittedVideoSize(1920f, 1080f, 9f / 16f)
        assertEquals(607.5f, fitPortraitOnLandscape.width, 0.5f)
        assertEquals(1080f, fitPortraitOnLandscape.height, 0.5f)

        // CinemaScope 2.39:1 sur ecran paysage 16:9 (1920 x 1080) -> barres noires haut/bas
        val fitScopeOn169 = MiniPlayerGeometry.computeFittedVideoSize(1920f, 1080f, 2.39f)
        assertEquals(1920f, fitScopeOn169.width, 0.5f)
        assertEquals(1920f / 2.39f, fitScopeOn169.height, 0.5f)

        // 16:9 sur ecran portrait (1080 x 2400) -> barres noires haut/bas
        val fit169OnPortrait = MiniPlayerGeometry.computeFittedVideoSize(1080f, 2400f, 16f / 9f)
        assertEquals(1080f, fit169OnPortrait.width, 0.5f)
        assertEquals(607.5f, fit169OnPortrait.height, 0.5f)
    }

    @Test
    fun `computeMiniPlayerSizeDp conserve le ratio reel sans tronquer les videos portrait ou 4_3`() {
        val landscape169 = MiniPlayerGeometry.computeMiniPlayerSizeDp(412f, 16f / 9f)
        assertEquals(16f / 9f, landscape169.width / landscape169.height, 0.02f)

        val classic43 = MiniPlayerGeometry.computeMiniPlayerSizeDp(412f, 4f / 3f)
        assertEquals(4f / 3f, classic43.width / classic43.height, 0.02f)

        val vertical916 = MiniPlayerGeometry.computeMiniPlayerSizeDp(412f, 9f / 16f)
        assertEquals(9f / 16f, vertical916.width / vertical916.height, 0.02f)
        assertTrue(vertical916.width >= 108f)
    }
}
