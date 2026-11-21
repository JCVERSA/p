package dot.jcversa.novay.data.sama

import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.ScanPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V3.0 — parseurs et source des scans (panneaux panneauScan, chapitres
 * creerListe/newSP, API get_nb_chap_et_img, URLs d'images s2/scans).
 */
class SamaScansTest {

    private val oeuvreUrl = "https://anime-sama.to/catalogue/solo-leveling/"

    // ── Panneaux (panneauScan) ─────────────────────────────────────────────

    @Test
    fun `parseScanPanels extrait les panneaux, ignore les liens va et deduplique`() {
        val html = """
            <script>
                panneauAnime("Saison 1", "saison1/vostfr");
                panneauScan("Scans VF (Japscan)", "scan/vf");
                panneauScan("Scans VA", "scan/va");
                panneauScan("Scans VF (Japscan)", "scan/vf");
                panneauScan("Scans VOSTFR (Miura)", "scan/vostfr");
            </script>
        """.trimIndent()

        val panels = SamaParsers.parseScanPanels(html, oeuvreUrl)

        assertEquals(2, panels.size)
        val vf = panels.first()
        assertEquals("Scans VF (Japscan)", vf.name)
        assertEquals("https://anime-sama.to/catalogue/solo-leveling/scan/vf/", vf.url)
        assertEquals("VF", vf.language)
        assertEquals("Japscan", vf.scanlator)
        assertEquals("VOSTFR", panels[1].language)
        assertEquals("Miura", panels[1].scanlator)
    }

    @Test
    fun `parseScanPanels ignore les placeholders d exemple`() {
        val html = """panneauScan("nom", "url");"""
        assertTrue(SamaParsers.parseScanPanels(html, oeuvreUrl).isEmpty())
    }

    // ── Titre d'œuvre ─────────────────────────────────────────────────────

    @Test
    fun `parseScanOeuvreTitle prend le premier noeud texte et decode les entites`() {
        val html = """
            <h1 id="titreOeuvre">Solo Leveling <span class="badge">VF</span></h1>
        """.trimIndent()
        assertEquals("Solo Leveling", SamaParsers.parseScanOeuvreTitle(html))

        val entities = """<h1 id="titreOeuvre">Re&amp;Zero <small>Kai</small></h1>"""
        assertEquals("Re&Zero", SamaParsers.parseScanOeuvreTitle(entities))

        assertNull(SamaParsers.parseScanOeuvreTitle("<h1 id=\"autre\">Rien</h1>"))
    }

    // ── API de comptes ────────────────────────────────────────────────────

    @Test
    fun `parseScanImageCounts lit id vers nombre de pages`() {
        val counts = SamaParsers.parseScanImageCounts("""{"1":18,"2":20,"3":0,"4":22}""")
        assertEquals(mapOf("1" to 18, "2" to 20, "4" to 22), counts)
        assertTrue(SamaParsers.parseScanImageCounts("pas du tout du json").isEmpty())
        assertTrue(SamaParsers.parseScanImageCounts("").isEmpty())
    }

    // ── Chapitres (creerListe / newSP / complément API) ───────────────────

    @Test
    fun `parseScanChapters liste, complete par l API et fournit les URLs d images`() {
        val html = """
            resetListe();
            creerListe(1, 3);
            newSP("Vacances");
        """.trimIndent()
        val counts = mapOf("1" to 10, "2" to 12, "3" to 12, "4" to 14, "5" to 8)
        val imageBase = "https://anime-sama.to/s2/scans/Solo%20Leveling"

        val chapters = SamaParsers.parseScanChapters(html, counts, imageBase)

        assertEquals(5, chapters.size)
        // Commandes listées : chapitres 1 à 3, puis le SP « Vacances » (id 4).
        assertEquals(listOf(1, 2, 3, 4, 5), chapters.map { it.id })
        assertEquals(listOf("1", "2", "3", "Vacances", "4"), chapters.map { it.number })
        assertEquals("Chapitre 1", chapters[0].label)
        assertEquals("Chapitre Vacances", chapters[3].label)
        // Le complément API reprend la numérotation après les SP (id 5 = « 4 »).
        assertEquals(listOf(10, 12, 12, 14, 8), chapters.map { it.pageCount })
        assertEquals(
            "https://anime-sama.to/s2/scans/Solo%20Leveling/2/1.jpg",
            chapters[1].imageUrlPrefix + "1.jpg",
        )
    }

    @Test
    fun `parseScanChapters sans resetListe se fie au compte API seul`() {
        val chapters = SamaParsers.parseScanChapters(
            scanHtml = "<html>rien de listé</html>",
            imageCounts = mapOf("1" to 5, "2" to 6),
            imageBase = "https://anime-sama.to/s2/scans/Test",
        )
        assertEquals(2, chapters.size)
        assertEquals("Chapitre 1", chapters[0].label)
        assertEquals(5, chapters[0].pageCount)
    }

    // ── SamaSource.scanChapters (HTTP simulé) ─────────────────────────────

    @Test
    fun `scanChapters resout l origine, le titre, l API et les pages`() = runBlocking {
        val origin = "https://anime-sama.to"
        val panelPage = """
            <html><body>
            <h1 id="titreOeuvre">Solo Leveling <span>badge</span></h1>
            <script>
                resetListe();
                creerListe(1, 2);
            </script>
            </body></html>
        """.trimIndent()

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val url = req.url.toString()
                when {
                    url.startsWith(SamaParsers.LAUNCHER_URL) -> Response.Builder()
                        .request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body("""<a class="btn-primary" href="$origin/">Go</a>""".toResponseBody("text/html".toMediaType()))
                        .build()
                    url == "$origin/" -> Response.Builder()
                        .request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body("<html></html>".toResponseBody("text/html".toMediaType()))
                        .build()
                    url == "$origin/catalogue/solo-leveling/" -> Response.Builder()
                        .request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body("""panneauScan("Scans VF (Japscan)", "scan/vf");""".toResponseBody("text/html".toMediaType()))
                        .build()
                    url == "$origin/catalogue/solo-leveling/scan/vf/" -> Response.Builder()
                        .request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body(panelPage.toResponseBody("text/html".toMediaType()))
                        .build()
                    url.startsWith("$origin/s2/scans/get_nb_chap_et_img.php") -> {
                        // Le titre doit être transmis encodé en paramètre oeuvre.
                        assertTrue(url.endsWith("oeuvre=Solo%20Leveling"))
                        Response.Builder()
                            .request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                            .body("""{"1":18,"2":20}""".toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                    else -> Response.Builder()
                        .request(req).protocol(Protocol.HTTP_1_1).code(404).message("NF")
                        .body(ByteArray(0).toResponseBody(null))
                        .build()
                }
            }
            .build()

        val source = SamaSource(http = client)
        val anime = AnimeSearchResult(
            title = "Solo Leveling",
            url = "$origin/catalogue/solo-leveling/",
            slug = "solo-leveling",
        )

        val panels = source.scanPanels(anime)
        assertEquals(listOf(ScanPanel(name = "Scans VF (Japscan)", url = "$origin/catalogue/solo-leveling/scan/vf/", language = "VF", scanlator = "Japscan")), panels)

        val chapters = source.scanChapters(anime, panels.first())
        assertEquals(2, chapters.size)
        assertEquals(18, chapters[0].pageCount)
        assertEquals(
            "$origin/s2/scans/Solo%20Leveling/1/1.jpg",
            chapters[0].imageUrlPrefix + "1.jpg",
        )
    }
}
