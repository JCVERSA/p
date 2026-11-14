package dot.jcversa.novay.downloads

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * SpeedMeter (V2.3) : vitesse lissée sur fenêtre glissante + ETA —
 * horloge injectée, aucune dépendance Android.
 */
class SpeedMeterTest {

    @Test
    fun `vitesse = pente des octets dans la fenetre`() {
        var t = 0L
        val meter = SpeedMeter(clock = { t })
        meter.sample(0L, 1_000_000L)
        t = 5_000L
        val reading = meter.sample(1_000_000L, 1_000_000L)
        // 1 000 000 octets en 5 s = 200 000 o/s
        assertEquals(200_000L, reading.bytesPerSecond)
        // termine : plus rien a telecharger
        assertEquals(0L, reading.etaMillis)
    }

    @Test
    fun `eta = reste divise par la vitesse`() {
        var t = 0L
        val meter = SpeedMeter(clock = { t })
        meter.sample(0L, 600_000L)
        t = 2_000L
        // 200 000 octets en 2 s = 100 000 o/s ; reste 400 000 -> 4 s
        val reading = meter.sample(200_000L, 600_000L)
        assertEquals(100_000L, reading.bytesPerSecond)
        assertEquals(4_000L, reading.etaMillis)
    }

    @Test
    fun `un seul echantillon, vitesse inconnue`() {
        val meter = SpeedMeter(clock = { 0L })
        val reading = meter.sample(500L, 1_000L)
        assertEquals(0L, reading.bytesPerSecond)
        assertEquals(SpeedMeter.UNKNOWN, reading.etaMillis)
    }

    @Test
    fun `les echantillons hors fenetre sont oublies`() {
        var t = 0L
        val meter = SpeedMeter(windowMillis = 5_000L, clock = { t })
        meter.sample(0L, 10_000_000L)
        t = 20_000L // hors fenetre : cet echantillon ne compte plus
        meter.sample(5_000_000L, 10_000_000L)
        t = 22_000L
        val reading = meter.sample(6_000_000L, 10_000_000L)
        // pente sur la fenetre recente : 1 Mo en 2 s
        assertEquals(500_000L, reading.bytesPerSecond)
    }

    @Test
    fun `total inconnu, eta inconnu mais vitesse correcte`() {
        var t = 0L
        val meter = SpeedMeter(clock = { t })
        meter.sample(0L, 0L)
        t = 1_000L
        val reading = meter.sample(50_000L, 0L)
        assertEquals(50_000L, reading.bytesPerSecond)
        assertEquals(SpeedMeter.UNKNOWN, reading.etaMillis)
    }

    // ── Formatage français ──────────────────────────────────────────────

    @Test
    fun `formatage des vitesses`() {
        assertEquals("0 Ko/s", formatSpeed(0L))
        assertEquals("97 Ko/s", formatSpeed(99_328L)) // 97 * 1024 - arrondi
        assertEquals("2,4 Mo/s", formatSpeed(2_500_000L))
    }

    @Test
    fun `formatage des temps restants`() {
        assertEquals("—", formatEta(-1L))
        assertEquals("45s", formatEta(45_000L))
        assertEquals("3min 05s", formatEta(185_000L))
        assertEquals("1h 05min", formatEta(3_900_000L))
    }
}
