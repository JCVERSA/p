package dot.jcversa.novay.data.sama

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parseurs de la page d'accueil anime-sama (V3) — fixtures portées des
 * sondes CI 14/11 (structure ak-slide / as-carte réelle, aplatie).
 */
class SamaParsersHomeTest {

    private val origin = "https://anime-sama.to"

    // ── decodeHtmlEntities ────────────────────────────────────────────────

    @Test
    fun `entites html nommees et numeriques decodees`() {
        assertEquals("'Tis Time for \"Torture,\" Princess",
            SamaParsers.decodeHtmlEntities("&#039;Tis Time for &quot;Torture,&quot; Princess"))
        assertEquals("Ao & Bi", SamaParsers.decodeHtmlEntities("Ao &amp; Bi"))
        assertEquals("É", SamaParsers.decodeHtmlEntities("&#201;"))
        assertEquals("É", SamaParsers.decodeHtmlEntities("&#xC9;"))
        // Entité inconnue : laissée telle quelle.
        assertEquals("a &bizarroide; b", SamaParsers.decodeHtmlEntities("a &bizarroide; b"))
        assertEquals("rien", SamaParsers.decodeHtmlEntities("rien"))
    }

    // ── seasonLabelFromUrl ────────────────────────────────────────────────

    @Test
    fun `libelle de saison deduit de l url`() {
        assertEquals("Saison 4", SamaParsers.seasonLabelFromUrl("/catalogue/tokyo-revengers/saison4/vostfr/"))
        assertEquals("Saison 1", SamaParsers.seasonLabelFromUrl("https://anime-sama.to/catalogue/naruto/saison1/vf/"))
        assertEquals("Film", SamaParsers.seasonLabelFromUrl("/catalogue/naruto/film/vostfr/"))
        assertEquals("OAV", SamaParsers.seasonLabelFromUrl("/catalogue/x/oav/vostfr/"))
        assertNull(SamaParsers.seasonLabelFromUrl("/catalogue/naruto/"))
    }

    // ── parseHeroSlides ───────────────────────────────────────────────────

    private val heroHtml = """
        <div class="ak-carousel"><div class="ak-slide" aria-hidden="true"><div class="ak-slide-bg">
        <img decoding="async" src="https://raw.githubusercontent.com/Anime-Sama/IMG/img/contenu/reincarnated-as-a-sword.jpg"></div>
        <div class="ak-slide-content"><h2 class="ak-slide-title">Clone ignoré</h2></div></div>
        <div class="ak-slide"><div class="ak-slide-bg"><img decoding="async" loading="lazy"
        src="https://raw.githubusercontent.com/Anime-Sama/IMG/img/contenu/reincarnated-as-a-sword.jpg" alt=""></div>
        <div class="ak-slide-overlay"></div><div class="ak-slide-content"><div class="ak-slide-info">
        <span class="ak-badge ak-badge--saison"><svg viewBox="0 0 24 24"></svg>Saison 2 en cours</span>
        <h2 class="ak-slide-title">Reincarnated as a Sword</h2>
        <div class="ak-slide-genres"><span class="ak-genre-tag">Action</span><span class="ak-genre-tag">Aventure</span>
        <span class="ak-genre-tag">Comédie</span><span class="ak-genre-tag">Fantasy</span></div></div>
        <p class="ak-slide-synopsis">Fran est une jeune fille fougueuse &amp; aux oreilles de chat.</p>
        <div class="ak-slide-ctas"><a href="/catalogue/reincarnated-as-a-sword/saison2/vostfr/" class="ak-slide-cta">
        <svg viewBox="0 0 24 24"></svg>Visionner en VOSTFR <img src="https://cdn.jsdelivr.net/gh/Anime-Sama/IMG@img/autres/flagfr.svg"></a></div></div></div>
        <div class="ak-slide"><div class="ak-slide-bg"><img src="https://raw.githubusercontent.com/Anime-Sama/IMG/img/contenu/overgeared.jpg"></div>
        <div class="ak-slide-content"><div class="ak-slide-info"><span class="ak-badge ak-badge--saison">Saison 1</span>
        <h2 class="ak-slide-title">Overgeared</h2></div>
        <p class="ak-slide-synopsis">Grid, équipé d&#039;objets de niveau divin ?!</p>
        <div class="ak-slide-ctas"><a class="ak-slide-cta" href="/catalogue/overgeared/saison1/vostfr/">Visionner</a></div></div></div>
        </div>
    """.trimIndent()

    @Test
    fun `le carousel heros parse titres, badge, genres, synopsis et lien`() {
        val heroes = SamaParsers.parseHeroSlides(heroHtml, origin)
        // Le clone aria-hidden est ignoré : 2 slides réels.
        assertEquals(2, heroes.size)
        val first = heroes[0]
        assertEquals("Reincarnated as a Sword", first.title)
        assertEquals("Saison 2 en cours", first.seasonLabel)
        assertEquals(listOf("Action", "Aventure", "Comédie", "Fantasy"), first.genres)
        assertEquals("Fran est une jeune fille fougueuse & aux oreilles de chat.", first.synopsis)
        assertEquals("$origin/catalogue/reincarnated-as-a-sword/saison2/vostfr/", first.seasonUrl)
        assertEquals("VOSTFR", first.language)
        assertEquals(
            "https://raw.githubusercontent.com/Anime-Sama/IMG/img/contenu/reincarnated-as-a-sword.jpg",
            first.posterUrl
        )
        // Second slide : entité &#039; décodée dans le synopsis, href APRÈS la classe.
        assertEquals("Overgeared", heroes[1].title)
        assertEquals("Grid, équipé d'objets de niveau divin ?!", heroes[1].synopsis)
        assertEquals("Saison 1", heroes[1].seasonLabel)
    }

    @Test
    fun `carousel vide ou casse rend une liste vide`() {
        assertTrue(SamaParsers.parseHeroSlides("", origin).isEmpty())
        assertTrue(SamaParsers.parseHeroSlides("<html><body>Rien ici</body></html>", origin).isEmpty())
    }

    // ── parseHomeCards ────────────────────────────────────────────────────

    private val cardsHtml = """
        <div class="as-carte as-carte-oeuvre" data-url="/catalogue/07-ghost/" data-cat="oeuvre" data-nom="07 Ghost"
        data-img="https://raw.githubusercontent.com/Anime-Sama/IMG/img/contenu/07-ghost.jpg"><a class="as-carte-lien"
        href="/catalogue/07-ghost/"><div class="as-carte-image"><img src="https://cdn.jsdelivr.net/gh/Anime-Sama/IMG@img/contenu/thumb/07-ghost.webp"></div></a></div>
        <div class="as-carte as-carte-anime" data-url="/catalogue/tokyo-revengers/" data-cat="oeuvre" data-nom="Tokyo Revengers"
        data-img="https://raw.githubusercontent.com/Anime-Sama/IMG/img/contenu/tokyo-revengers.jpg"><a class="as-carte-lien"
        href="/catalogue/tokyo-revengers/saison4/vostfr/" draggable="false"><div class="as-carte-image">
        <img src="https://cdn.jsdelivr.net/gh/Anime-Sama/IMG@img/contenu/thumb/tokyo-revengers.webp" alt="" loading="lazy">
        </div><div class="as-carte-texte"><div class="as-carte-titre" title="Tokyo Revengers">Tokyo Revengers</div>
        <span class="as-carte-temps">11h30</span></div></a></div>
        <div class="as-carte as-carte-anime" data-url="/catalogue/blue-box/" data-cat="oeuvre" data-nom="Blue Box"
        data-img="https://raw.githubusercontent.com/Anime-Sama/IMG/img/contenu/blue-box.jpg"><a class="as-carte-lien"
        href="/catalogue/blue-box/saison2/vf/" draggable="false"><div class="as-carte-image">
        <img src="https://cdn.jsdelivr.net/gh/Anime-Sama/IMG@img/contenu/thumb/blue-box.webp" alt="" loading="lazy">
        </div><span class="as-carte-temps">11h20</span></a></div>
        <div class="as-carte as-carte-anime" data-url="/catalogue/tokyo-revengers/" data-cat="oeuvre" data-nom="Tokyo Revengers (doublon)"
        data-img="x"><a class="as-carte-lien" href="/catalogue/tokyo-revengers/saison3/vf/"></a></div>
        <div class="as-carte as-carte-anime" data-url="/catalogue/tis-time/" data-cat="oeuvre"
        data-nom="&#039;Tis Time for &quot;Torture,&quot; Princess" data-img="x"><a class="as-carte-lien"
        href="/catalogue/tis-time/saison1/vostfr/"></a></div>
    """.trimIndent()

    @Test
    fun `les cartes anime de la home parsees et dedupliquees`() {
        val cards = SamaParsers.parseHomeCards(cardsHtml, origin)
        // 1 carte oeuvre ignorée + 1 doublon tokyo-revengers ignoré = 3.
        assertEquals(3, cards.size)
        val tokyo = cards[0]
        assertEquals("Tokyo Revengers", tokyo.title)
        assertEquals("$origin/catalogue/tokyo-revengers/", tokyo.animeUrl)
        assertEquals("$origin/catalogue/tokyo-revengers/saison4/vostfr/", tokyo.seasonUrl)
        assertEquals("11h30", tokyo.timeLabel)
        assertEquals("VOSTFR", tokyo.language)
        assertEquals(
            "https://cdn.jsdelivr.net/gh/Anime-Sama/IMG@img/contenu/thumb/tokyo-revengers.webp",
            tokyo.posterUrl
        )
        // VF détectée depuis /vf/ et titre avec entités décodé.
        val blueBox = cards[1]
        assertEquals("VF", blueBox.language)
        assertEquals("Blue Box", blueBox.title)
        assertEquals(
            "'Tis Time for \"Torture,\" Princess",
            cards[2].title
        )
        // Carte sans heure : timeLabel null.
        assertNull(cards[2].timeLabel)
    }

    @Test
    fun `home vide ou cassee rend une liste vide`() {
        assertTrue(SamaParsers.parseHomeCards("", origin).isEmpty())
        assertTrue(SamaParsers.parseHomeCards("<html>Pas de cartes</html>", origin).isEmpty())
    }

    @Test
    fun `les deux parseurs cohabitent sur une vraie home`() {
        val home = heroHtml + cardsHtml
        val heroes = SamaParsers.parseHeroSlides(home, origin)
        val cards = SamaParsers.parseHomeCards(home, origin)
        assertEquals(2, heroes.size)
        assertEquals(3, cards.size)
    }
}
