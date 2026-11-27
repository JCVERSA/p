package dot.jcversa.novay.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * V3.3 — lots de téléchargements : identifiants de lot, estimations de
 * taille honnêtes (jamais inventées) et formatage binaire lisible.
 */
class BatchDownloadsTest {

    // ── Identifiants de lot ───────────────────────────────────────────────

    @Test
    fun `batchIdFor normalise l URL de saison et distingue les lots`() {
        assertEquals(
            "season:https://anime-sama.to/catalogue/overgeared/saison1/vf",
            Downloads.batchIdFor("https://anime-sama.to/catalogue/overgeared/saison1/vf/"),
        )
        assertEquals(
            Downloads.batchIdFor("https://a.to/x/vf/"),
            Downloads.batchIdFor("https://a.to/x/vf"),
        )
    }

    // ── Estimations de taille ─────────────────────────────────────────────

    @Test
    fun `estimateBytesFromCompleted moyenne les tailles reelles de la meme hauteur`() {
        val completed = listOf(
            720 to 250_000_000L,
            720 to 350_000_000L,
            1080 to 900_000_000L,
        )
        // Résolution demandée = 720 : moyenne des 720p uniquement.
        assertEquals(300_000_000L, Downloads.estimateBytesFromCompleted(completed, 720))
        // Aucun 480p connu : repli sur la moyenne globale.
        assertEquals(500_000_000L, Downloads.estimateBytesFromCompleted(completed, 480))
    }

    @Test
    fun `estimateBytesFromCompleted ne devine rien sans repere`() {
        assertNull(Downloads.estimateBytesFromCompleted(emptyList(), 720))
        // Tailles nulles ignorées : pas de moyenne faussée.
        assertNull(Downloads.estimateBytesFromCompleted(listOf(720 to 0L), 720))
    }

    // ── Formatage ─────────────────────────────────────────────────────────

    @Test
    fun `formatBytes produit des unites binaires lisibles`() {
        assertEquals("512 o", Downloads.formatBytes(512))
        assertEquals("4 Ko", Downloads.formatBytes(4 * 1024))
        assertEquals("230 Mo", Downloads.formatBytes(230L * 1024 * 1024))
        assertEquals("1,4 Go", Downloads.formatBytes((1.4 * 1024 * 1024 * 1024).toLong()))
        assertEquals("—", Downloads.formatBytes(-1))
    }

    // ── Métadonnées de lot (round-trip) ───────────────────────────────────

    @Test
    fun `meta episode roundtrip conserve le lot et son libelle`() {
        val meta = EpisodeDownloadMeta(
            animeTitle = "Overgeared",
            seasonName = "Saison 1 VF",
            seasonUrl = "https://anime-sama.to/catalogue/overgeared/saison1/vf/",
            episodeNumber = 4,
            episodeLabel = "Épisode 4",
            streamUrl = "https://cdn.example/master.m3u8",
            isHls = true,
            headers = emptyMap(),
            batchId = Downloads.batchIdFor("https://anime-sama.to/catalogue/overgeared/saison1/vf/"),
            batchLabel = "Overgeared — Saison 1 VF",
        )
        val restored = EpisodeDownloadMeta.fromJson(meta.toJson().toByteArray(Charsets.UTF_8))
        assertEquals(meta, restored)
        assertEquals("Overgeared — Saison 1 VF", restored!!.batchLabel)
    }

    private fun info(
        id: String,
        state: Int,
        done: Long,
        total: Long,
    ): Downloads.DownloadInfo {
        val meta = EpisodeDownloadMeta(
            animeTitle = "Anime",
            seasonName = "Saison 1",
            seasonUrl = "https://anime-sama.example/catalogue/anime/saison-1",
            episodeNumber = 1,
            episodeLabel = "Épisode 1",
            streamUrl = "https://cdn.example/master.m3u8",
            isHls = true,
            headers = emptyMap(),
            batchId = "season:https://anime-sama.example/catalogue/anime/saison-1",
            batchLabel = "Anime — Saison 1",
        )
        return Downloads.DownloadInfo(
            id = id,
            percent = 0f,
            meta = meta,
            state = state,
            bytesDownloaded = done,
            totalBytes = total,
        )
    }
}
