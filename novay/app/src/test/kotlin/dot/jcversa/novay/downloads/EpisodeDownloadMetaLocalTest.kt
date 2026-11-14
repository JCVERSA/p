package dot.jcversa.novay.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * EpisodeDownloadMeta (V2.5) : champs « novabox » — l'épisode téléchargé
 * est un VRAI fichier dans Download/NoVaY/ du téléphone ; la relecture
 * après redémarrage doit lire le fichier local (jamais le réseau) et le
 * JSON reste rétrocompatible avec les métadonnées V2.
 */
class EpisodeDownloadMetaLocalTest {

    private val base = EpisodeDownloadMeta(
        animeTitle = "Solo Leveling",
        seasonName = "Saison 1",
        seasonUrl = "https://anime-sama.to/catalogue/solo-leveling/saison1/vostfr/",
        episodeNumber = 3,
        episodeLabel = "Épisode 3",
        streamUrl = "https://gate.example/v1080.m3u8",
        isHls = true,
        headers = emptyMap()
    )

    @Test
    fun `aller-retour JSON des champs novabox`() {
        val local = base.copy(
            localUri = "content://media/external/downloads/1024",
            fileName = "Solo Leveling - S01E03 VOSTFR 1080p.mp4",
            qualityLabel = "1080p"
        )
        val back = EpisodeDownloadMeta.fromJson(
            local.toJson().toByteArray(Charsets.UTF_8)
        )
        assertEquals(local, back)
        assertEquals("Solo Leveling - S01E03 VOSTFR 1080p.mp4", back?.fileName)
    }

    @Test
    fun `json v2 sans champs novabox reste lisible`() {
        val legacy = """
            {"animeTitle":"Solo Leveling","seasonName":"Saison 1",
             "seasonUrl":"https://anime-sama.to/catalogue/solo-leveling/saison1/vostfr/",
             "episodeNumber":3,"episodeLabel":"Épisode 3",
             "streamUrl":"https://gate.example/v1080.m3u8","isHls":true}
        """.trimIndent().toByteArray(Charsets.UTF_8)
        val back = EpisodeDownloadMeta.fromJson(legacy)
        assertEquals(base.copy(), back)
        assertEquals(null, back?.localUri)
        assertEquals(null, back?.fileName)
        assertEquals("", back?.qualityLabel)
    }

    @Test
    fun `toStream lit le fichier local en priorite, sans reseau`() {
        val local = base.copy(
            localUri = "content://media/external/downloads/1024",
            fileName = "Solo Leveling - S01E03 VOSTFR 1080p.mp4"
        )
        val stream = local.toStream()
        assertEquals("content://media/external/downloads/1024", stream.url)
        assertTrue(!stream.isHls)
        assertTrue(stream.headers.isEmpty())
    }

    @Test
    fun `toStream retombe sur le flux reseau sans fichier local`() {
        val stream = base.toStream()
        assertEquals(base.streamUrl, stream.url)
        assertTrue(stream.isHls)
    }

    @Test
    fun `fichier file Uri sept a neuf lu lui aussi en local`() {
        val local = base.copy(
            localUri = "file:///storage/emulated/0/Download/NoVaY/Solo Leveling - S01E03 VOSTFR 1080p.mp4",
            fileName = "Solo Leveling - S01E03 VOSTFR 1080p.mp4"
        )
        assertEquals(local.localUri, local.toStream().url)
        assertTrue(!local.toStream().isHls)
    }
}
