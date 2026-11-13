package dot.jcversa.novay.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * EpisodeDownloadMeta (V2) : aller/retour JSON via request.data — la
 * relecture après redémarrage doit reconstruire le flux complet
 * (URL + en-têtes) sans résolution réseau.
 */
class EpisodeDownloadMetaTest {

    private val meta = EpisodeDownloadMeta(
        animeTitle = "Naruto Shippuden",
        seasonName = "Saison 1",
        seasonUrl = "https://anime-sama.to/catalogue/naruto-shippuden/saison1/vostfr/",
        episodeNumber = 42,
        episodeLabel = "Épisode 42",
        streamUrl = "https://gate.example/v1080.m3u8",
        isHls = true,
        headers = mapOf(
            "User-Agent" to "Mozilla/5.0 NoVaY",
            "Referer" to "https://ansembed.net/",
            "Origin" to "https://ansembed.net"
        )
    )

    @Test
    fun `aller-retour JSON via bytes`() {
        val bytes = meta.toJson().toByteArray(Charsets.UTF_8)
        val back = EpisodeDownloadMeta.fromJson(bytes)
        assertEquals(meta, back)
    }

    @Test
    fun `titre avec guillemets et antislash survit au JSON`() {
        val exotic = meta.copy(
            animeTitle = "L\"Attaque \\ des Titans",
            episodeLabel = "Épisode 1 « pilote »"
        )
        val back = EpisodeDownloadMeta.fromJson(exotic.toJson().toByteArray(Charsets.UTF_8))
        assertEquals(exotic, back)
    }

    @Test
    fun `toStream reconstruit le flux avec en-tetes et url de la variante`() {
        val stream = meta.toStream()
        assertEquals(meta.streamUrl, stream.url)
        assertTrue(stream.isHls)
        assertEquals(meta.headers, stream.headers)
        assertTrue(stream.qualities.isEmpty())
    }

    @Test
    fun `en-tetes absents, UA par defaut et referer vide`() {
        val bare = meta.copy(headers = emptyMap())
        val stream = EpisodeDownloadMeta.fromJson(
            bare.toJson().toByteArray(Charsets.UTF_8)
        )!!.toStream()
        assertEquals(dot.jcversa.novay.data.sama.SamaParsers.UA, stream.headers["User-Agent"])
        assertEquals("", stream.headers["Referer"])
    }

    @Test
    fun `data nulle ou JSON invalide, jamais de crash`() {
        assertNull(EpisodeDownloadMeta.fromJson(null))
        assertNull(EpisodeDownloadMeta.fromJson("{pas du json".toByteArray(Charsets.UTF_8)))
    }
}
