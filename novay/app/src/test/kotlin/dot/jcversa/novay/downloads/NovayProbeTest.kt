package dot.jcversa.novay.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * V2.9.3 — étiquetage codec (badge de l'écran téléchargements). PUR :
 * aucune bibliothèque native requise. Le remux a été retiré en V2.9.3
 * (les épisodes sortent en .ts brut, sans transformation) ; seule la
 * sonde du badge codec reste.
 */
class NovayProbeTest {

    @Test
    fun `etiquettes codec connues et inconnues`() {
        assertEquals("H.264", NovayProbe.videoCodecLabel("video/avc"))
        assertEquals("H.264", NovayProbe.videoCodecLabel("video/avc1.640028"))
        assertEquals("HEVC", NovayProbe.videoCodecLabel("video/hevc"))
        assertEquals("HEVC", NovayProbe.videoCodecLabel("video/hev1.2.4.L153"))
        assertEquals("HEVC", NovayProbe.videoCodecLabel("video/hvc1.1.6.L93"))
        assertNull(NovayProbe.videoCodecLabel(null))
        // MIME inconnu : tronqué lisible, jamais une chaîne vide.
        assertEquals("video/xyz", NovayProbe.videoCodecLabel("video/xyz-more-info"))
    }
}
