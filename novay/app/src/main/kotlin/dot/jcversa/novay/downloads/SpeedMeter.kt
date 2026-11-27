package dot.jcversa.novay.downloads

import java.util.ArrayDeque

/**
 * Vitesse lissée et temps restant d'un téléchargement (V2.3) — PUR.
 *
 * Fenêtre glissante d'échantillons (5 s par défaut) : la vitesse est la
 * pente des octets reçus dans la fenêtre, l'ETA en découle. Horloge
 * injectée = testable JVM.
 *
 * Adapté de SpeedMeter du projet Downpour
 * (github.com/AlirezaJavan/Downpour, Apache-2.0) — réécrit pour NoVaY.
 */
class SpeedMeter(
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis
) {

    private val timestamps = ArrayDeque<Long>()
    private val byteCounts = ArrayDeque<Long>()

    /**
     * Enregistre un échantillon (octets reçus au total à cet instant) et
     * retourne la lecture courante : vitesse lissée + temps restant.
     */
    fun sample(totalDownloaded: Long, totalBytes: Long): Reading {
        val now = clock()
        timestamps.addLast(now)
        byteCounts.addLast(totalDownloaded)
        evictOlderThan(now - windowMillis)

        val speed = computeSpeed()
        val eta = computeEta(speed, totalDownloaded, totalBytes)
        return Reading(bytesPerSecond = speed, etaMillis = eta)
    }

    private fun evictOlderThan(threshold: Long) {
        while (timestamps.size > MIN_SAMPLES && timestamps.first() < threshold) {
            timestamps.removeFirst()
            byteCounts.removeFirst()
        }
    }

    private fun computeSpeed(): Long {
        if (timestamps.size < MIN_SAMPLES) return 0L
        val elapsedMillis = timestamps.last() - timestamps.first()
        if (elapsedMillis <= 0L) return 0L
        val movedBytes = byteCounts.last() - byteCounts.first()
        if (movedBytes <= 0L) return 0L
        return movedBytes * MILLIS_PER_SECOND / elapsedMillis
    }

    private fun computeEta(speed: Long, downloaded: Long, total: Long): Long {
        if (speed <= 0L || total <= 0L || downloaded < 0L) return UNKNOWN
        val remaining = (total - downloaded).coerceAtLeast(0L)
        return remaining * MILLIS_PER_SECOND / speed
    }

    /** Une lecture de vitesse : octets/s et temps restant en ms. */
    data class Reading(
        val bytesPerSecond: Long,
        val etaMillis: Long
    )

    companion object {
        const val UNKNOWN = -1L
        private const val DEFAULT_WINDOW_MILLIS = 5_000L
        private const val MILLIS_PER_SECOND = 1_000L
        private const val MIN_SAMPLES = 2
    }
}

/** « 2,4 Mo/s » (unités françaises, décimales). */
fun formatSpeed(bytesPerSecond: Long): String {
    if (bytesPerSecond <= 0L) return "0 Ko/s"
    val kb = bytesPerSecond / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> String.format(java.util.Locale.FRENCH, "%.1f Mo/s", mb)
        kb >= 1.0 -> String.format(java.util.Locale.FRENCH, "%.0f Ko/s", kb)
        else -> "$bytesPerSecond o/s"
    }
}

/** « 3 min 12 s » / « 45 s » / « 1 h 05 min ». */
fun formatEta(etaMillis: Long): String {
    if (etaMillis < 0L) return "—"
    val totalSeconds = etaMillis / 1000
    if (totalSeconds < 60) return "${totalSeconds}s"
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    if (minutes < 60) return "${minutes}min ${seconds.toString().padStart(2, '0')}s"
    val hours = minutes / 60
    val min = minutes % 60
    return "${hours}h ${min.toString().padStart(2, '0')}min"
}
