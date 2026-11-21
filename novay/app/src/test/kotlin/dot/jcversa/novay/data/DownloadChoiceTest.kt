package dot.jcversa.novay.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DownloadChoice (V2) : la MEILLEURE qualité est choisie pour le
 * téléchargement — les qualités d'un ResolvedStream sont triées par
 * hauteur décroissante (cf. parseHlsMasterPlaylist).
 */
class DownloadChoiceTest {

    private fun stream(
        qualities: List<StreamQuality>,
        url: String = "https://cdn.example/master.m3u8",
        isHls: Boolean = true
    ) = ResolvedStream(
        url = url,
        isHls = isHls,
        headers = mapOf(
            "User-Agent" to "UA-test",
            "Referer" to "https://embed.example/",
            "Origin" to "https://embed.example"
        ),
        qualities = qualities
    )

    @Test
    fun `meilleure qualite = premiere de la liste triee`() {
        val s = stream(
            listOf(
                StreamQuality(label = "1080P", url = "https://cdn.example/v1080.m3u8", height = 1080),
                StreamQuality(label = "720P", url = "https://cdn.example/v720.m3u8", height = 720),
                StreamQuality(label = "480P", url = "https://cdn.example/v480.m3u8", height = 480)
            )
        )
        val choice = DownloadChoice.best(s)
        assertEquals("https://cdn.example/v1080.m3u8", choice.url)
        assertTrue(choice.isHls)
        assertEquals("1080P", choice.qualityLabel)
    }

    @Test
    fun `sans variantes lues, master HLS directe`() {
        val s = stream(qualities = emptyList())
        val choice = DownloadChoice.best(s)
        assertEquals("https://cdn.example/master.m3u8", choice.url)
        assertTrue(choice.isHls)
        assertEquals("Auto", choice.qualityLabel)
    }

    @Test
    fun `sans variantes et mp4, fichier direct`() {
        val s = stream(qualities = emptyList(), url = "https://cdn.example/ep1.mp4", isHls = false)
        val choice = DownloadChoice.best(s)
        assertEquals("https://cdn.example/ep1.mp4", choice.url)
        assertFalse(choice.isHls)
        assertEquals("Direct", choice.qualityLabel)
    }

    @Test
    fun `une seule qualite, elle est choisie`() {
        val s = stream(
            listOf(StreamQuality(label = "720P", url = "https://cdn.example/v720.m3u8", height = 720))
        )
        val choice = DownloadChoice.best(s)
        assertEquals("https://cdn.example/v720.m3u8", choice.url)
        assertEquals("720P", choice.qualityLabel)
    }

    @Test
    fun `choix par hauteur exacte`() {
        val s = stream(
            listOf(
                StreamQuality(label = "1080P", url = "https://cdn.example/v1080.m3u8", height = 1080),
                StreamQuality(label = "720P", url = "https://cdn.example/v720.m3u8", height = 720)
            )
        )
        val choice = DownloadChoice.pick(s, 720)
        assertEquals("https://cdn.example/v720.m3u8", choice.url)
        assertEquals("720P", choice.qualityLabel)
    }

    @Test
    fun `hauteur absente, repli sur la meilleure de ce lecteur`() {
        val s = stream(
            listOf(
                StreamQuality(label = "1080P", url = "https://cdn.example/v1080.m3u8", height = 1080)
            )
        )
        val choice = DownloadChoice.pick(s, 360)
        assertEquals("https://cdn.example/v1080.m3u8", choice.url)
        assertEquals("1080P", choice.qualityLabel)
    }

    @Test
    fun `hauteur nulle, meilleure qualite`() {
        val s = stream(
            listOf(
                StreamQuality(label = "1080P", url = "https://cdn.example/v1080.m3u8", height = 1080),
                StreamQuality(label = "480P", url = "https://cdn.example/v480.m3u8", height = 480)
            )
        )
        assertEquals("1080P", DownloadChoice.pick(s, null).qualityLabel)
    }
}
