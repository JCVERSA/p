package dot.jcversa.novay.ui.player

/**
 * Maths des gestes du lecteur (V2.2) — PURES (testables JVM).
 *
 * Comportements inspirés de l'analyse de NextPlayer (GPL-3.0 :
 * comportement seulement, aucun code copié) :
 *  - glissé horizontal = seek progressif (~50 ms de vidéo par pixel) ;
 *  - double-tap répété qui CUMULE (+10 s, +10 s = +20 s), reset après
 *    750 ms sans tap.
 */
object PlayerGestureMath {

    /** Sensibilité du glissé horizontal : ms de vidéo par pixel glissé. */
    const val DRAG_MS_PER_PX = 50L

    /** Pas d'un double-tap (ms). */
    const val DOUBLE_TAP_INCREMENT_MS = 10_000L

    /** Délai sans tap après lequel le cumul repart de zéro (ms). */
    const val DOUBLE_TAP_RESET_MS = 750L

    /**
     * Position cible d'un glissé horizontal : depuis la position de départ
     * [startPositionMs] et l'abscisse de départ [startX], chaque pixel vers
     * la droite avance de [DRAG_MS_PER_PX] (vers la gauche : recule).
     * Toujours bornée à la durée (ou à la position courante si durée
     * inconnue).
     */
    fun dragSeekTarget(
        startPositionMs: Long,
        startX: Float,
        currentX: Float,
        durationMs: Long
    ): Long {
        val start = startPositionMs.coerceAtLeast(0L)
        if (durationMs <= 0L) return start
        val delta = ((currentX - startX).toLong()) * DRAG_MS_PER_PX
        return (start + delta).coerceIn(0L, durationMs)
    }
}

/**
 * Cumul des double-taps : tap-tap-tap à droite = +10 s, +20 s, +30 s…
 * Le cumul retombe à zéro après [resetAfterMs] sans tap. L'horloge est
 * injectée pour tester le reset en JVM.
 */
class DoubleTapSeekAccumulator(
    private val incrementMs: Long = PlayerGestureMath.DOUBLE_TAP_INCREMENT_MS,
    private val resetAfterMs: Long = PlayerGestureMath.DOUBLE_TAP_RESET_MS,
    private val now: () -> Long = System::currentTimeMillis
) {

    var totalMs: Long = 0L
        private set

    private var lastTapAt = 0L

    /**
     * Un double-tap (moitié droite = avant, moitié gauche = arrière) :
     * retourne le cumul TOTAL depuis le début de la rafale.
     */
    fun tap(forward: Boolean): Long {
        val t = now()
        if (lastTapAt == 0L || t - lastTapAt > resetAfterMs) totalMs = 0L
        lastTapAt = t
        totalMs += if (forward) incrementMs else -incrementMs
        return totalMs
    }

    fun reset() {
        totalMs = 0L
        lastTapAt = 0L
    }
}
