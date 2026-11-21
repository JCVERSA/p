package dot.jcversa.novay.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * V3.0 — téléchargement des chapitres de scans : nommage du fichier CBZ
 * publié, métadonnées persistées (round-trip JSON) et extraction des
 * pages hors-ligne (ordre, complétude, réutilisation du cache).
 */
class ScanDownloadsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // ── Nommage du fichier publié ────────────────────────────────────────

    @Test
    fun `buildScanChapterFileName produit un nom lisible et sans caracteres interdits`() {
        assertEquals(
            "Solo Leveling - Chapitre 12.cbz",
            Downloads.buildScanChapterFileName("Solo Leveling", "Chapitre 12"),
        )
        assertEquals(
            "Re Zero - Chapitre 5.5.cbz",
            Downloads.buildScanChapterFileName("Re: Zero", "Chapitre 5.5"),
        )
        assertEquals(
            "Anime - Chapitre.cbz",
            Downloads.buildScanChapterFileName("", ""),
        )
    }

    // ── Métadonnées (round-trip JSON) ────────────────────────────────────

    @Test
    fun `meta scan roundtrip conserve kind pageUrls et champs du chapitre`() {
        val meta = EpisodeDownloadMeta(
            animeTitle = "Solo Leveling",
            seasonName = "Scans VF (Japscan)",
            seasonUrl = "https://anime-sama.to/catalogue/solo-leveling/scan/vf/",
            episodeNumber = 12,
            episodeLabel = "Chapitre 12",
            streamUrl = "https://anime-sama.to/s2/scans/Solo%20Leveling/12/1.jpg",
            isHls = false,
            headers = mapOf("User-Agent" to "ua-test"),
            kind = EpisodeDownloadMeta.KIND_SCAN,
            pageUrls = listOf(
                "https://anime-sama.to/s2/scans/Solo%20Leveling/12/1.jpg",
                "https://anime-sama.to/s2/scans/Solo%20Leveling/12/2.jpg",
                "https://anime-sama.to/s2/scans/Solo%20Leveling/12/3.jpg",
            ),
        )
        val restored = EpisodeDownloadMeta.fromJson(meta.toJson().toByteArray(Charsets.UTF_8))
        assertEquals(meta, restored)
        assertTrue(restored!!.isScan)
        assertEquals(3, restored.pageUrls.size)
    }

    @Test
    fun `meta video sans champ kind reste une tache video`() {
        val meta = EpisodeDownloadMeta(
            animeTitle = "A", seasonName = "B", seasonUrl = "C",
            episodeNumber = 1, episodeLabel = "D",
            streamUrl = "E", isHls = true, headers = emptyMap(),
        )
        val restored = EpisodeDownloadMeta.fromJson(meta.toJson().toByteArray(Charsets.UTF_8))
        assertEquals(meta, restored)
        assertTrue(restored!!.isScan.not())
    }

    // ── Extraction CBZ ───────────────────────────────────────────────────

    private fun writeCbz(target: File, entries: List<Pair<String, ByteArray>>) {
        ZipOutputStream(FileOutputStream(target)).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    @Test
    fun `unzipScanPages extrait les pages dans l ordre et complete`() {
        val cbz = tmp.newFile("chapter.cbz")
        writeCbz(
            cbz,
            listOf(
                "00002.jpg" to byteArrayOf(2),
                "00001.jpg" to byteArrayOf(1),
                "00003.jpg" to byteArrayOf(3),
                "notes.txt" to byteArrayOf(0), // ignoré : pas une page
                "dossier/" to ByteArray(0),    // ignoré : dossier
            ),
        )
        val outDir = File(tmp.root, "pages")

        val pages = Downloads.unzipScanPages(cbz, outDir)

        assertEquals(3, pages.size)
        // Ordre trié par nom d'entrée, indépendant de l'ordre d'écriture.
        assertEquals(1, pages[0].readBytes()[0].toInt())
        assertEquals(2, pages[1].readBytes()[0].toInt())
        assertEquals(3, pages[2].readBytes()[0].toInt())

        // Cache : la seconde extraction réutilise les fichiers existants.
        val again = Downloads.unzipScanPages(cbz, outDir)
        assertEquals(pages, again)
    }

    @Test
    fun `unzipScanPages renvoie vide si le cbz est absent ou sans pages`() {
        assertTrue(
            Downloads.unzipScanPages(File(tmp.root, "inconnu.cbz"), File(tmp.root, "x")).isEmpty()
        )
        val vide = tmp.newFile("vide.cbz")
        writeCbz(vide, listOf("lisezmoi.txt" to byteArrayOf(1)))
        assertTrue(Downloads.unzipScanPages(vide, File(tmp.root, "y")).isEmpty())
    }

    // ── Identifiants de file ─────────────────────────────────────────────

    @Test
    fun `scanDownloadId se distingue des identifiants d episodes`() {
        val panelUrl = "https://anime-sama.to/catalogue/solo-leveling/scan/vf/"
        assertEquals(
            "https://anime-sama.to/catalogue/solo-leveling/scan/vf#chap12",
            Downloads.scanDownloadId(panelUrl, 12),
        )
        assertTrue(
            Downloads.scanDownloadId(panelUrl, 12) != Downloads.downloadId(panelUrl, 12)
        )
    }
}
