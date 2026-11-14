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

    /** Sensibilité des gestes verticaux : % de plage par pixel glissé. */
    const val VERTICAL_PCT_PER_PX = 0.1f

    /**
     * Pourcentage cible d'un glissé vertical : monter le doigt AUGMENTE
     * (volume ou luminosité), le descendre diminue. Toujours borné
     * 0..[maxPercentage].
     */
    fun verticalTarget(
        startPercentage: Int,
        startY: Float,
        currentY: Float,
        maxPercentage: Int = 100
    ): Int {
        val delta = ((startY - currentY) * VERTICAL_PCT_PER_PX).toInt()
        return (startPercentage + delta).coerceIn(0, maxPercentage)
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
    // Sentinelle séparée : lastTapAt == 0 est une horloge VALABLE (les
    // tests injectent une horloge qui démarre à 0) — sans ce booléen, le
    // 2e tap croyait qu'on n'avait jamais tapé et remettait le cumul à 0.
    private var hasTapped = false

    /**
     * Un double-tap (moitié droite = avant, moitié gauche = arrière) :
     * retourne le cumul TOTAL depuis le début de la rafale.
     */
    fun tap(forward: Boolean): Long {
        val t = now()
        if (!hasTapped || t - lastTapAt > resetAfterMs) totalMs = 0L
        hasTapped = true
        lastTapAt = t
        totalMs += if (forward) incrementMs else -incrementMs
        return totalMs
    }

    fun reset() {
        totalMs = 0L
        lastTapAt = 0L
        hasTapped = false
    }
}
