package dot.jcversa.novay.data.voiranime

import dot.jcversa.novay.data.AnimeSearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * Tests des parseurs voiranime — fixtures portées de tests/voiranime.test.ts
 * et tests/playerExtractors.test.ts (bot). Portage 1:1 : mêmes entrées,
 * mêmes attendus.
 */
class VoiranimeParsersTest {

    // ── Fixtures (voiranime.test.ts) ──────────────────────────────────────

    private val searchHtml = """
<div class="page-item-detail">
  <a href="https://voir-anime.to/anime/sparks-of-tomorrow-vf/" title="Sparks of Tomorrow (VF)"><img></a>
  <h3><a href="https://voir-anime.to/anime/sparks-of-tomorrow-vf/">Sparks of Tomorrow (VF)</a></h3>
</div>
<div class="page-item-detail">
  <h3><a href="https://voir-anime.to/anime/sparks-of-tomorrow/">Sparks of Tomorrow</a></h3>
</div>
<div class="page-item-detail">
  <h3><a href="https://voir-anime.to/anime/mushoku-tensei-3-vf/">Mushoku Tensei 3 (VF)</a></h3>
</div>
<nav><a href="https://voir-anime.to/anime-genre/action/" title="Action">Action</a></nav>
""".trimIndent()

    private val animeUrl = "https://voir-anime.to/anime/sparks-of-tomorrow-vf/"

    private val episodesHtml = """
<div class="listing">
  <a href="https://voir-anime.to/anime/sparks-of-tomorrow-vf/sparks-of-tomorrow-09-vf/">EP 09</a>
  <a href="https://voir-anime.to/anime/sparks-of-tomorrow-vf/sparks-of-tomorrow-01-vf/">EP 01</a>
  <a href="https://voir-anime.to/anime/sparks-of-tomorrow-vf/sparks-of-tomorrow-05-vf/">EP 05</a>
  <a href="https://voir-anime.to/anime/sparks-of-tomorrow-vf/film-vf-sparks/">FILM</a>
  <a href="https://voir-anime.to/anime/other-anime/other-01-vf/">OTHER</a>
</div>
""".trimIndent()

    private fun entry(title: String, url: String, slug: String) =
        AnimeSearchResult(title = title, url = url, slug = slug, language = "VF", isVf = true)

    // ── parseVoiranimeSearch ──────────────────────────────────────────────

    @Test
    fun `extrait les entrees, deduplique, saute les liens de genre et marque VF structurellement`() {
        val out = VoiranimeParsers.parseVoiranimeSearch(searchHtml)
        assertEquals(3, out.size)
        val slugs = out.map { it.slug }
        assertTrue(slugs.contains("sparks-of-tomorrow-vf"))
        assertTrue(slugs.contains("sparks-of-tomorrow"))
        assertEquals(true, out.first { it.slug == "sparks-of-tomorrow-vf" }.isVf)
        assertEquals(false, out.first { it.slug == "sparks-of-tomorrow" }.isVf)
        assertEquals("Sparks of Tomorrow", out.first { it.slug == "sparks-of-tomorrow-vf" }.title)
        assertTrue(slugs.none { it.contains("anime-genre") })
    }

    @Test
    fun `html vide = liste vide`() {
        assertTrue(VoiranimeParsers.parseVoiranimeSearch("").isEmpty())
    }

    @Test
    fun `liens relatifs resolus (grilles madara)`() {
        val html = """<h3><a href="/anime/solo-leveling-vf/">Solo Leveling (VF)</a></h3>"""
        val out = VoiranimeParsers.parseVoiranimeSearch(html)
        assertEquals(1, out.size)
        assertEquals("solo-leveling-vf", out[0].slug)
        assertEquals("https://voir-anime.to/anime/solo-leveling-vf/", out[0].url)
    }

    // ── isVoiranimeVfSlug ─────────────────────────────────────────────────

    @Test
    fun `slugs et urls vf reconnus`() {
        assertEquals(true, VoiranimeParsers.isVoiranimeVfSlug("one-piece-vf"))
        assertEquals(true, VoiranimeParsers.isVoiranimeVfSlug("https://voir-anime.to/anime/one-piece-vf/"))
        assertEquals(false, VoiranimeParsers.isVoiranimeVfSlug("one-piece"))
    }

    // ── parseVoiranimeEpisodes ────────────────────────────────────────────

    @Test
    fun `episodes numerotes en ordre croissant, autres entrees ignorees`() {
        val out = VoiranimeParsers.parseVoiranimeEpisodes(episodesHtml, animeUrl)
        assertEquals(listOf(0, 1, 5, 9), out.map { it.number })
        assertEquals(listOf(1, 5, 9), out.filter { it.number > 0 }.map { it.number })
        assertTrue(out.all { it.url.startsWith(animeUrl) })
        assertEquals("Film", out.first { it.number == 0 }.label)
    }

    // ── parseVoiranimePlayer ──────────────────────────────────────────────

    @Test
    fun `trouve l'iframe voembed`() {
        val html = """<html><body><iframe src="https://voembed.net/embed-miu8i0n5yyms.html" allowfullscreen></iframe></body></html>"""
        assertEquals("https://voembed.net/embed-miu8i0n5yyms.html", VoiranimeParsers.parseVoiranimePlayer(html))
    }

    @Test
    fun `fallback regex puis null sinon`() {
        val html = """<script>var u="https://voembed.net/embed-abc123.html";</script>"""
        assertEquals("https://voembed.net/embed-abc123.html", VoiranimeParsers.parseVoiranimePlayer(html))
        assertNull(VoiranimeParsers.parseVoiranimePlayer("<html><body>hi</body></html>"))
        assertNull(VoiranimeParsers.parseVoiranimePlayer(""))
    }

    // ── voiranimeSlugFamily ───────────────────────────────────────────────

    @Test
    fun `famille de slug - langue, saison, base`() {
        val f1 = VoiranimeParsers.voiranimeSlugFamily("rezero-kara-hajimeru-isekai-seikatsu-s4-vf")
        assertEquals("rezero-kara-hajimeru-isekai-seikatsu", f1.base)
        assertEquals(4, f1.season)
        assertEquals("VF", f1.language)

        val f2 = VoiranimeParsers.voiranimeSlugFamily("mushoku-tensei-3-vf")
        assertEquals("mushoku-tensei", f2.base)
        assertEquals(3, f2.season)

        val f3 = VoiranimeParsers.voiranimeSlugFamily("sparks-of-tomorrow-vf")
        assertEquals("sparks-of-tomorrow", f3.base)
        assertNull(f3.season) // convention S1

        val f4 = VoiranimeParsers.voiranimeSlugFamily("solo-leveling-vostfr")
        assertEquals("solo-leveling", f4.base)
        assertEquals("VOSTFR", f4.language)
    }

    // ── organizeVfSelection ───────────────────────────────────────────────

    private val kimetsu = listOf(
        entry("Kimetsu no Yaiba: Mugenjou-hen Movie 1 – Akaza Sairai", "u1", "kimetsu-no-yaiba-mugenjou-hen-movie-1-akaza-sairai-vf"),
        entry("Kimetsu no Yaiba 4", "u2", "kimetsu-no-yaiba-4-vf"),
        entry("Kimetsu no Yaiba 3", "u3", "kimetsu-no-yaiba-3-vf"),
        entry("Kimetsu no Yaiba 2", "u4", "kimetsu-no-yaiba-2-vf"),
        entry("Kimetsu no Yaiba: Mugen Ressha-hen", "u5", "kimetsu-no-yaiba-mugen-ressha-hen-vf")
    )

    @Test
    fun `regroupe par anime, saisons croissantes puis films nommes (transcript reel)`() {
        val out = VoiranimeParsers.organizeVfSelection(kimetsu)
        assertEquals(
            listOf(
                // film SANS marqueur structurel : titre complet, on n'invente pas « Saison 1 »
                "Kimetsu no Yaiba: Mugen Ressha-hen",
                "Kimetsu no Yaiba — Saison 2",
                "Kimetsu no Yaiba — Saison 3",
                "Kimetsu no Yaiba — Saison 4",
                "Kimetsu no Yaiba — Film : Mugenjou-hen Movie 1 – Akaza Sairai"
            ),
            out.map { it.label }
        )
    }

    @Test
    fun `R2 = saison 2, slug -N reconnu, sans marqueur NI sous-titre = Saison 1`() {
        val out = VoiranimeParsers.organizeVfSelection(
            listOf(
                entry("Code Geass: Lelouch of the Rebellion R2", "u2", "code-geass-r2-vf"),
                entry("Code Geass: Lelouch of the Rebellion", "u1", "code-geass-hangyaku-no-lelouch-vf"),
                entry("Mushoku Tensei", "u3", "mushoku-tensei-3-vf"),
                entry("Sparks of Tomorrow", "u4", "sparks-of-tomorrow-vf")
            )
        )
        assertEquals(
            listOf(
                "Code Geass: Lelouch of the Rebellion|1",
                "Code Geass: Lelouch of the Rebellion — Saison 2|2",
                "Mushoku Tensei — Saison 3|3",
                "Sparks of Tomorrow — Saison 1|1"
            ),
            out.map { "${it.label}|${it.season}" }
        )
    }

    @Test
    fun `le resultat porte toujours l'entree d'origine`() {
        val out = VoiranimeParsers.organizeVfSelection(kimetsu)
        assertEquals(kimetsu.size, out.size)
        assertTrue(out.all { o -> kimetsu.any { it.slug == o.result.slug } })
    }

    @Test
    fun `groupFranchises plie en franchises contigues`() {
        val franchises = VoiranimeParsers.groupFranchises(kimetsu)
        assertEquals(1, franchises.size)
        assertEquals("Kimetsu no Yaiba", franchises[0].baseTitle)
        assertEquals(5, franchises[0].entries.size)
    }

    // ── decodeVoePayload (playerExtractors.test.ts) ───────────────────────

    private fun b64(s: String): String = Base64.getEncoder().encodeToString(s.toByteArray(Charsets.UTF_8))

    private fun rot13(s: String): String {
        val out = StringBuilder(s.length)
        for (c in s) {
            val o = c.code
            out.append(
                when {
                    o in 65..90 -> (((o - 65 + 13) % 26) + 65).toChar()
                    o in 97..122 -> (((o - 97 + 13) % 26) + 97).toChar()
                    else -> c
                }
            )
        }
        return out.toString()
    }

    /** Inverse de decodeVoePayload, étape par étape (comme le test bot). */
    private fun encodeVoePayload(json: String): String {
        val s5 = b64(json) // couche base64 finale
        val s4 = s5.reversed() // avant le reverse
        val s3 = String(s4.map { (it.code + 3).toChar() }.toCharArray()) // avant le ord-3
        val s2 = b64(s3) // avant le 2e décodage base64
        return rot13(s2) // avant le rot13
    }

    @Test
    fun `round-trip rot13 + double base64 + ord-shift + reverse`() {
        val json = """{"source":"https://cdn.example.voe/video480.mp4"}"""
        val decoded = VoiranimeParsers.decodeVoePayload(encodeVoePayload(json))
        assertEquals(json, decoded)
        assertEquals("https://cdn.example.voe/video480.mp4", VoiranimeParsers.voeSourceFromJson(decoded!!))
    }

    @Test
    fun `payload invalide = null`() {
        assertNull(VoiranimeParsers.decodeVoePayload("not-a-valid-payload!!!"))
        assertNull(VoiranimeParsers.decodeVoePayload(""))
    }

    @Test
    fun `payload voe depuis une page html avec script application json`() {
        val json = """{"source":"https://cdn.example.voe/hls/master.m3u8"}"""
        val html = "<html><body><script type=\"application/json\">[\"${encodeVoePayload(json)}\"]</script></body></html>"
        assertEquals("https://cdn.example.voe/hls/master.m3u8", VoiranimeParsers.parseVoePayloadSource(html))
    }

    @Test
    fun `flux leurre bigbuckbunny ignores`() {
        val json = """{"source":"https://cdn.example.voe/bigbuckbunny.mp4"}"""
        val html = "<script type=\"application/json\">[\"${encodeVoePayload(json)}\"]</script>"
        assertNull(VoiranimeParsers.parseVoePayloadSource(html))
    }

    @Test
    fun `redirection in-page et m3u8 en clair`() {
        assertEquals(
            "https://voe.example/next",
            VoiranimeParsers.parseVoeRedirect("""<script>window.location.href="https://voe.example/next"</script>""")
        )
        assertEquals(
            "https://cdn.example.voe/plain/master.m3u8",
            VoiranimeParsers.findPlainM3u8("""var x = "https://cdn.example.voe/plain/master.m3u8";""")
        )
        assertNull(VoiranimeParsers.findPlainM3u8("""var x = "https://cdn.example.voe/sample.m3u8";"""))
    }

    @Test
    fun `domaines voe reconnus, hotes connus exclus`() {
        assertEquals(true, VoiranimeParsers.isVoeStyleUrl("https://voe.sx/e/abc123"))
        assertEquals(true, VoiranimeParsers.isVoeStyleUrl("https://voembed.net/embed-abc.html"))
        assertEquals(false, VoiranimeParsers.isVoeStyleUrl("https://video.sibnet.ru/e/abc123"))
        assertEquals(false, VoiranimeParsers.isVoeStyleUrl(""))
    }

    // ── parseHlsMasterPlaylist ────────────────────────────────────────────

    @Test
    fun `master playlist - variantes resolues et triees par hauteur`() {
        val master = """
#EXTM3U
#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
360p.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=2800000,RESOLUTION=1280x720
https://cdn.example.voe/720p.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=1400000,RESOLUTION=854x480
480p.m3u8?token=abc
""".trimIndent()
        val out = VoiranimeParsers.parseHlsMasterPlaylist(master, "https://cdn.example.voe/hls/master.m3u8")
        assertEquals(3, out.size)
        assertEquals("720P", out[0].label)
        assertEquals("https://cdn.example.voe/720p.m3u8", out[0].url)
        assertEquals("480P", out[1].label)
        assertEquals("https://cdn.example.voe/hls/480p.m3u8?token=abc", out[1].url)
        assertEquals("360P", out[2].label)
        assertEquals("https://cdn.example.voe/hls/360p.m3u8", out[2].url)
    }

    @Test
    fun `master sans variantes = liste vide`() {
        assertTrue(VoiranimeParsers.parseHlsMasterPlaylist("#EXTM3U\n#EXTINF:6,\nseg0.ts\n", "https://x/y.m3u8").isEmpty())
        assertTrue(VoiranimeParsers.parseHlsMasterPlaylist("", "https://x/y.m3u8").isEmpty())
    }
}
