package dot.jcversa.novay.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Gestes du lecteur (V2.2) : maths pures du seek par glissé et du cumul
 * de double-taps.
 */
class PlayerGestureMathTest {

    // ── Glissé horizontal ──────────────────────────────────────────────

    @Test
    fun `glisse vers la droite avance de 50 ms par pixel`() {
        // 300 px vers la droite depuis 1 min = +15 s
        val target = PlayerGestureMath.dragSeekTarget(
            startPositionMs = 60_000L,
            startX = 100f,
            currentX = 400f,
            durationMs = 1_400_000L
        )
        assertEquals(75_000L, target)
    }

    @Test
    fun `glisse vers la gauche recule`() {
        val target = PlayerGestureMath.dragSeekTarget(
            startPositionMs = 60_000L,
            startX = 400f,
            currentX = 100f,
            durationMs = 1_400_000L
        )
        assertEquals(45_000L, target)
    }

    @Test
    fun `cible bornee a la duree et a zero`() {
        assertEquals(
            1_400_000L,
            PlayerGestureMath.dragSeekTarget(1_399_000L, 0f, 10_000f, 1_400_000L)
        )
        assertEquals(
            0L,
            PlayerGestureMath.dragSeekTarget(5_000L, 10_000f, 0f, 1_400_000L)
        )
    }

    @Test
    fun `duree inconnue, la position ne bouge pas`() {
        assertEquals(
            60_000L,
            PlayerGestureMath.dragSeekTarget(60_000L, 0f, 5_000f, 0L)
        )
    }

    // ── Glissé vertical (volume / luminosité) ──────────────────────────

    @Test
    fun `monter le doigt augmente le pourcentage`() {
        // 200 px vers le haut depuis 40 % = +20 %
        assertEquals(
            60,
            PlayerGestureMath.verticalTarget(startPercentage = 40, startY = 800f, currentY = 600f)
        )
    }

    @Test
    fun `descendre le doigt diminue le pourcentage`() {
        assertEquals(
            20,
            PlayerGestureMath.verticalTarget(startPercentage = 40, startY = 600f, currentY = 800f)
        )
    }

    @Test
    fun `pourcentage borne a zero et cent`() {
        assertEquals(
            100,
            PlayerGestureMath.verticalTarget(startPercentage = 90, startY = 1000f, currentY = 0f)
        )
        assertEquals(
            0,
            PlayerGestureMath.verticalTarget(startPercentage = 5, startY = 0f, currentY = 1000f)
        )
    }

    @Test
    fun `glisse inferieur au seuil d un point, valeur inchangee`() {
        // 4 px de glisse = 0,4 % -> 0 apres arrondi
        assertEquals(
            40,
            PlayerGestureMath.verticalTarget(startPercentage = 40, startY = 500f, currentY = 496f)
        )
    }

    // ── Cumul de double-taps ───────────────────────────────────────────

    @Test
    fun `taps rapides cumulent`() {
        var t = 0L
        val acc = DoubleTapSeekAccumulator(now = { t })
        assertEquals(10_000L, acc.tap(forward = true))
        t = 200L
        assertEquals(20_000L, acc.tap(forward = true))
        t = 400L
        assertEquals(30_000L, acc.tap(forward = true))
    }

    @Test
    fun `au dela de 750 ms sans tap, le cumul repart de zero`() {
        var t = 0L
        val acc = DoubleTapSeekAccumulator(now = { t })
        acc.tap(forward = true)
        t = 200L
        acc.tap(forward = true)
        assertEquals(20_000L, acc.totalMs)
        t = 200L + 751L
        assertEquals(10_000L, acc.tap(forward = true))
    }

    @Test
    fun `melange avant et arriere dans la meme rafale`() {
        var t = 0L
        val acc = DoubleTapSeekAccumulator(now = { t })
        assertEquals(10_000L, acc.tap(forward = true))
        t = 100L
        assertEquals(20_000L, acc.tap(forward = true))
        t = 200L
        assertEquals(10_000L, acc.tap(forward = false))
        t = 300L
        assertEquals(0L, acc.tap(forward = false))
    }

    @Test
    fun `reset explicite`() {
        var t = 0L
        val acc = DoubleTapSeekAccumulator(now = { t })
        acc.tap(forward = true)
        acc.reset()
        assertEquals(0L, acc.totalMs)
        t = 100L
        // apres reset, un tap arriere part de zero et donne -10 s
        assertEquals(-10_000L, acc.tap(forward = false))
    }
}
