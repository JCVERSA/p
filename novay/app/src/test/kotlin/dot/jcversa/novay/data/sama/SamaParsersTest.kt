package dot.jcversa.novay.data.sama

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests des parseurs anime-sama — fixtures RÉELLES capturées par les
 * sondes CI du 13/11/2026 (runner GitHub Actions, chaîne validée E2E :
 * recherche → saisons → épisodes → api/lien.php → page lecteur → m3u8).
 */
class SamaParsersTest {

    // ── Origine dynamique (launcher) ─────────────────────────────────────

    @Test
    fun `bouton btn-primary du lanceur - origine`() {
        val html = """
            <html><body>
              <a class="btn btn-primary btn-lg" href="https://anime-sama.si">Entrer</a>
            </body></html>
        """.trimIndent()
        assertEquals("https://anime-sama.si", SamaParsers.parseLauncherOrigin(html, SamaParsers.LAUNCHER_URL))
    }

    @Test
    fun `href relatif resolu contre la base, pas de bouton - null`() {
        val html = """<a class="btn btn-primary" href="/catalogue/">Entrer</a>"""
        assertEquals(
            "https://anime-sama.pw",
            SamaParsers.parseLauncherOrigin(html, SamaParsers.LAUNCHER_URL)
        )
        assertNull(SamaParsers.parseLauncherOrigin("<html><body>rien</body></html>", "https://anime-sama.pw"))
    }

    @Test
    fun `normalisation d origine`() {
        assertEquals("https://anime-sama.to", SamaParsers.normalizeOrigin("https://anime-sama.to/catalogue/naruto/"))
        assertEquals("http://anime-sama.com", SamaParsers.normalizeOrigin("http://anime-sama.com"))
        assertNull(SamaParsers.normalizeOrigin("ftp://anime-sama.to"))
        assertNull(SamaParsers.normalizeOrigin("n'importe quoi"))
    }

    // ── Recherche (fixtures du POST fetch.php, sonde v13) ────────────────

    @Test
    fun `resultats asn-search-result avec titres et sous-titres`() {
        // Structure REELLE capturee par la sonde v22 (13/11) : le titre est
        // un <h3> (avec <mark> de surlignage), le sous-titre un <p>, le
        // contenu un <div> — pas seulement des div.
        val html = """
<div class="col-6 col-md-4">
  <a href="https://anime-sama.to/catalogue/naruto/" class="asn-search-result"><img class="asn-search-result-img" loading="lazy" src="https://cdn.jsdelivr.net/gh/Anime-Sama/IMG@img/contenu/thumb/naruto.webp" alt="" />
    <div class="asn-search-result-content"><h3 class="asn-search-result-title"><mark>Naruto</mark>  </h3><p class="asn-search-meta">Anime · Scans · 2002</p><p class="asn-search-result-subtitle">Shonen - Action</p></div>
  </a>
  <a class="asn-search-result" href="/catalogue/naruto-shippuden">
    <div class="asn-search-result-content"><h3 class="asn-search-result-title">Naruto <mark>Shippuden</mark></h3></div>
  </a>
</div>
        """.trimIndent()
        val out = SamaParsers.parseSamaSearch(html, "https://anime-sama.to")
        assertEquals(2, out.size)
        assertEquals("Naruto", out[0].title)
        assertEquals("Shonen - Action", out[0].subtitle)
        assertEquals("https://anime-sama.to/catalogue/naruto/", out[0].url)
        // href relatif resolu contre l'origine
        assertEquals("https://anime-sama.to/catalogue/naruto-shippuden", out[1].url)
        assertEquals("Naruto Shippuden", out[1].title)
    }

    @Test
    fun `sous-titre de repli sur la meta quand le sous-titre manque`() {
        val html = """
<a href="https://anime-sama.to/catalogue/naruto/" class="asn-search-result">
  <div class="asn-search-result-content"><h3 class="asn-search-result-title">Naruto</h3><p class="asn-search-meta">Anime · Scans · 2002</p></div>
</a>
        """.trimIndent()
        val out = SamaParsers.parseSamaSearch(html, "https://anime-sama.to")
        assertEquals(1, out.size)
        assertEquals("Naruto", out[0].title)
        assertEquals("Anime · Scans · 2002", out[0].subtitle)
    }

    @Test
    fun `html vide ou sans resultat - liste vide`() {
        assertTrue(SamaParsers.parseSamaSearch("", "https://anime-sama.to").isEmpty())
        assertTrue(SamaParsers.parseSamaSearch("<html><body>Accueil</body></html>", "https://anime-sama.to").isEmpty())
    }

    // ── Variantes de requête (portage 9.18kkk) ───────────────────────────

    @Test
    fun `variantes - article retire, plurielflechi, re scinde`() {
        // « the ramparts of ice » : originale, sans article, singulier
        // (« of » mot vide, « ice » trop court pour flechir)
        assertEquals(
            listOf("the ramparts of ice", "ramparts of ice", "rampart of ice"),
            SamaParsers.searchQueryVariants("the ramparts of ice")
        )
        // « rezero » ne matche pas « Re:Zero » : la variante scindee matche
        // (la variante pluriel « rezeros » complete la liste)
        assertEquals(
            listOf("rezero", "re zero", "rezeros"),
            SamaParsers.searchQueryVariants("rezero")
        )
    }

    @Test
    fun `variantes - bornees a 4 et dedupliquees`() {
        val v = SamaParsers.searchQueryVariants("le chat des forets noires")
        assertTrue(v.size <= 4)
        assertEquals(v.size, v.toSet().size)
        assertEquals("le chat des forets noires", v[0])
    }

    // ── Saisons (panneauAnime — fixtures réelles de la page naruto) ──────

    private val narutoPage = """
<script>
  panneauAnime("nom", "saison1/vostfr");
  panneauAnime(nom, url);
  panneauAnime("nom", "url");
  panneauAnime("Avec Fillers", "saison1/vostfr");
  panneauAnime("Sans Fillers", "saison1hs/vostfr");
  panneauAnime("Film", "film/vostfr");
</script>
    """.trimIndent()

    @Test
    fun `panneaux reels extraits, placeholders ignores, langue structurelle`() {
        val seasons = SamaParsers.parsePanneauSeasons(
            narutoPage,
            "https://anime-sama.to/catalogue/naruto/"
        )
        assertEquals(3, seasons.size)
        assertEquals("Avec Fillers", seasons[0].name)
        assertEquals("https://anime-sama.to/catalogue/naruto/saison1/vostfr/", seasons[0].url)
        assertEquals("VOSTFR", seasons[0].language)
        assertEquals("Sans Fillers", seasons[1].name)
        assertEquals("https://anime-sama.to/catalogue/naruto/saison1hs/vostfr/", seasons[1].url)
        assertEquals("Film", seasons[2].name)
        assertEquals("https://anime-sama.to/catalogue/naruto/film/vostfr/", seasons[2].url)
    }

    @Test
    fun `langue des sous-chemins (portage samaSubPathLanguage)`() {
        assertEquals("VOSTFR", SamaParsers.samaSubPathLanguage("saison1/vostfr"))
        assertEquals("VOSTFR", SamaParsers.samaSubPathLanguage("saison2/vost"))
        assertEquals("VF", SamaParsers.samaSubPathLanguage("saison1/vf"))
        assertEquals("VF", SamaParsers.samaSubPathLanguage("film/vf"))
        assertEquals("VF", SamaParsers.samaSubPathLanguage("scan/vf")) // vf isole par des separateurs
        assertNull(SamaParsers.samaSubPathLanguage("oav"))
        assertNull(SamaParsers.samaSubPathLanguage("saison1"))
    }

    @Test
    fun `sous-chemins doublons dedupliques`() {
        val html = """
            panneauAnime("Saison 1", "saison1/vf");
            panneauAnime("Saison 1 (bis)", "saison1/vf");
            panneauAnime("Saison 2", "saison2/vf");
        """.trimIndent()
        val seasons = SamaParsers.parsePanneauSeasons(html, "https://anime-sama.to/catalogue/x/")
        assertEquals(2, seasons.size)
    }

    // ── Variante miroir de langue (VF ⇄ VOSTFR, terrain 13/11) ───────────

    @Test
    fun `url de la variante miroir de langue`() {
        assertEquals(
            "https://anime-sama.to/catalogue/naruto/saison1/vf/",
            SamaParsers.mirrorLanguageUrl("https://anime-sama.to/catalogue/naruto/saison1/vostfr/")
        )
        assertEquals(
            "https://anime-sama.to/catalogue/naruto/saison1/vostfr/",
            SamaParsers.mirrorLanguageUrl("https://anime-sama.to/catalogue/naruto/saison1/vf/")
        )
        assertEquals(
            "https://anime-sama.to/catalogue/naruto/film/vf/",
            SamaParsers.mirrorLanguageUrl("https://anime-sama.to/catalogue/naruto/film/vostfr/")
        )
        assertNull(SamaParsers.mirrorLanguageUrl("https://anime-sama.to/catalogue/naruto/saison1/"))
    }

    @Test
    fun `chaine complete miroir - la saison a l url vf est bien VF`() {
        // Bug 13/11 (retour owner : « le badge VOSTFR est partout, meme sur
        // la VF ») : la langue de la saison miroir se lit sur SON PROPRE
        // sous-chemin, pas sur celui du panneau d'origine. C'est la CHAINE
        // EXACTE que SamaSource utilise pour étiqueter la saison sondée.
        val panelUrl = "https://anime-sama.to/catalogue/naruto/saison1/vostfr/"
        val mirrorUrl = SamaParsers.mirrorLanguageUrl(panelUrl)!!
        assertEquals("https://anime-sama.to/catalogue/naruto/saison1/vf/", mirrorUrl)
        assertEquals("VF", SamaParsers.samaSubPathLanguage(mirrorUrl))

        // Et dans l'autre sens : un panneau vf (rare, site qui liste les
        // deux) sonde un miroir vostfr correctement étiqueté VOSTFR.
        val panelVf = "https://anime-sama.to/catalogue/naruto/saison1/vf/"
        val mirrorVostfr = SamaParsers.mirrorLanguageUrl(panelVf)!!
        assertEquals("https://anime-sama.to/catalogue/naruto/saison1/vostfr/", mirrorVostfr)
        assertEquals("VOSTFR", SamaParsers.samaSubPathLanguage(mirrorVostfr))
    }

    // ── Classement par catégorie (retour owner : ne pas mélanger) ────────

    private fun season(name: String, url: String, language: String?) =
        dot.jcversa.novay.data.AnimeSeason(name = name, url = url, language = language)

    @Test
    fun `saisons classees par categorie - vf d'abord, vostfr ensuite, jamais meleangees`() {
        val seasons = listOf(
            season("Saison 1", "https://x/catalogue/a/saison1/vostfr/", "VOSTFR"),
            season("Saison 1", "https://x/catalogue/a/saison1/vf/", "VF"),
            season("Saison 2", "https://x/catalogue/a/saison2/vostfr/", "VOSTFR"),
            season("Film", "https://x/catalogue/a/film/vf/", "VF"),
            season("OAV", "https://x/catalogue/a/oav/", null)
        )
        val out = SamaParsers.orderByLanguage(seasons)
        // Toutes les VF, puis toutes les VOSTFR, puis les sans-langue.
        assertEquals(listOf("VF", "VF", "VOSTFR", "VOSTFR", null), out.map { it.language })
        // Ordre stable au sein de chaque groupe (ordre d'apparition initial).
        assertEquals("Saison 1", out[0].name)
        assertEquals("Film", out[1].name)
        assertEquals("Saison 1", out[2].name)
        assertEquals("Saison 2", out[3].name)
        assertEquals("OAV", out[4].name)
    }

    @Test
    fun `classement - liste vide ou mono-langue intacte`() {
        assertTrue(SamaParsers.orderByLanguage(emptyList()).isEmpty())
        val vostfrOnly = listOf(
            season("Saison 1", "u1", "VOSTFR"),
            season("Saison 2", "u2", "VOSTFR")
        )
        assertEquals(vostfrOnly, SamaParsers.orderByLanguage(vostfrOnly))
    }

    // ── Épisodes (fixtures réelles de la page saison naruto, sonde v15) ──

    private val seasonPage = """
<script>
  var epsJeton = "5279.1794690000.c225497abfd6d6f3164b5117";
  var eps1 = ["lien:186672","lien:186675","lien:186678","lien:186681"];
  var eps2 = ["lien:186671","lien:186674","lien:186677","lien:186680"];
  var epsMeta = {"186671":["uqload.vc"],"186672":["ansembed.net"],"186673":["shanelocaldog.com"],"186674":["uqload.vc"],"186675":["ansembed.net"]};
</script>
    """.trimIndent()

    @Test
    fun `tableaux eps positionnels avec identifiants lien`() {
        val lists = SamaParsers.parseEpsArrays(seasonPage)
        assertEquals(2, lists.size)
        assertEquals(listOf("lien:186672", "lien:186675", "lien:186678", "lien:186681"), lists[1])
        assertEquals(listOf("lien:186671", "lien:186674", "lien:186677", "lien:186680"), lists[2])
    }

    @Test
    fun `ancien format - urls directes dans les tableaux`() {
        val html = """var eps1 = ['https://sibnet.ru/embed.php?id=1','https://vidmoly.tv/embed-abc.html'];"""
        val lists = SamaParsers.parseEpsArrays(html)
        assertEquals(
            listOf("https://sibnet.ru/embed.php?id=1", "https://vidmoly.tv/embed-abc.html"),
            lists[1]
        )
    }

    @Test
    fun `epsMeta mappe les identifiants vers leurs hotes`() {
        val meta = SamaParsers.parseEpsMeta(seasonPage)
        assertEquals(listOf("ansembed.net"), meta["186672"])
        assertEquals(listOf("uqload.vc"), meta["186671"])
        assertEquals(5, meta.size)
    }

    @Test
    fun `jeton de session extrait`() {
        assertEquals("5279.1794690000.c225497abfd6d6f3164b5117", SamaParsers.parseEpsJeton(seasonPage))
        assertNull(SamaParsers.parseEpsJeton("<html>pas de jeton</html>"))
    }

    // ── API lecteur (fixtures réelles de /api/lien.php, sondes v18-v20) ──

    @Test
    fun `reponse api lien - url du lecteur extraite`() {
        assertEquals(
            "https://ansembed.net/embed-w9jnsq6ufugi.html",
            SamaParsers.parseLienApiResponse("""{"u":"https://ansembed.net/embed-w9jnsq6ufugi.html"}""")
        )
    }

    @Test
    fun `reponse api lien - erreur de jeton - null`() {
        assertNull(SamaParsers.parseLienApiResponse("""{"erreur":"jeton"}"""))
        assertNull(SamaParsers.parseLienApiResponse(""))
    }

    // ── Scan de page lecteur (fixture réelle ansembed, sonde v20) ────────

    @Test
    fun `m3u8 absolu en clair dans la page lecteur, master prefere`() {
        val html = """
<script>
  var file_id = 15196668;
  var file_code = 'w9jnsq6ufugi';
  jwplayer("player").setup({
    sources: [{file: "https://gate-2-an-p.vmnow.online/hls2/01/03021/1b8gwch0weq4_n/master.m3u8?t=TjZz9THnfMFfYKXoJrOL5MRgYwywHK17Hvs8x8xtqxQ=&s=1794600878&e=43200"}]
  });
</script>
        """.trimIndent()
        val found = SamaParsers.scanPlayerForStream(html, "https://ansembed.net")
        assertEquals(true, found?.second)
        assertTrue(found?.first!!.startsWith("https://gate-2-an-p.vmnow.online/hls2/01/03021/1b8gwch0weq4_n/master.m3u8?t="))
    }

    @Test
    fun `m3u8 relatif resolu contre l origine du lecteur`() {
        val html = """<script>var h = "/stream/123/master.m3u8.txt";</script>"""
        val found = SamaParsers.scanPlayerForStream(html, "https://ansembed.net/")
        assertEquals("https://ansembed.net/stream/123/master.m3u8.txt", found?.first)
        assertEquals(true, found?.second)
    }

    @Test
    fun `leurres bigbuckbunny et samples ignores, mp4 direct accepte`() {
        val leurre = """var x = "https://cdn.example/bigbuckbunny.mp4"; var y = "https://cdn.example/sample.m3u8";"""
        assertNull(SamaParsers.scanPlayerForStream(leurre, "https://ansembed.net"))
        val mp4 = """var z = "https://cdn.example/video_hd.mp4?token=1";"""
        val found = SamaParsers.scanPlayerForStream(mp4, "https://ansembed.net")
        assertEquals("https://cdn.example/video_hd.mp4?token=1", found?.first)
        assertEquals(false, found?.second)
    }
}
