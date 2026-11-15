package dot.jcversa.novay.downloads

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * V2.9 — arguments des paliers de remux ffmpeg (recette du bot novabox)
 * et étiquetage codec. PUR : aucune bibliothèque native requise.
 *
 * Les arguments sont figés par test : tout changement silencieux de la
 * recette (oubli de faststart, mauvais bitrate audio…) doit casser ici
 * avant de toucher un vrai épisode.
 */
class NovayRemuxTest {

    @Test
    fun `palier 1 = copie + aac_adtstoasc + faststart (recette du bot)`() {
        assertArrayEquals(
            arrayOf(
                "-y",
                "-i", "/tmp/combined.bin",
                "-c", "copy",
                "-bsf:a", "aac_adtstoasc",
                "-movflags", "+faststart",
                "/tmp/remuxed.mp4",
            ),
            NovayRemux.tier1Args("/tmp/combined.bin", "/tmp/remuxed.mp4"),
        )
    }

    @Test
    fun `palier 2 = video copiee + audio AAC 128k + faststart`() {
        assertArrayEquals(
            arrayOf(
                "-y",
                "-i", "/tmp/combined.bin",
                "-c:v", "copy",
                "-c:a", "aac",
                "-b:a", "128k",
                "-movflags", "+faststart",
                "/tmp/remuxed.mp4",
            ),
            NovayRemux.tier2Args("/tmp/combined.bin", "/tmp/remuxed.mp4"),
        )
    }

    @Test
    fun `diagnostic tronque premiere ligne utile, 80 caracteres max`() {
        // Null / vide -> null (pas de note).
        assertNull(NovayRemux.truncateDiagnostic(null))
        assertNull(NovayRemux.truncateDiagnostic(""))
        assertNull(NovayRemux.truncateDiagnostic("   \n  \n"))
        // Premiere ligne non vide, espaces normalises.
        assertEquals(
            "java.lang.UnsatisfiedLinkError: libffmpegkit.so",
            NovayRemux.truncateDiagnostic("  \njava.lang.UnsatisfiedLinkError: libffmpegkit.so\n  at ..."),
        )
        // Troncature a 80 caracteres.
        val longue = "x".repeat(200)
        assertEquals(80, NovayRemux.truncateDiagnostic(longue)!!.length)
    }

    @Test
    fun `etiquettes codec connues et inconnues`() {
        assertEquals("H.264", NovayRemux.videoCodecLabel("video/avc"))
        assertEquals("H.264", NovayRemux.videoCodecLabel("video/avc1.640028"))
        assertEquals("HEVC", NovayRemux.videoCodecLabel("video/hevc"))
        assertEquals("HEVC", NovayRemux.videoCodecLabel("video/hev1.2.4.L153"))
        assertNull(NovayRemux.videoCodecLabel(null))
        // MIME inconnu : tronque lisible, jamais une chaîne vide.
        assertEquals("video/xyz", NovayRemux.videoCodecLabel("video/xyz-more-info"))
    }
}
