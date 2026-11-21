package dot.jcversa.novay.data.sama

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SamaShowDetailsParserTest {

    @Test
    fun `parseAnimeDetails extrait synopsis genres metadonnees et oeuvres associees reelles`() {
        val html = """
            <html>
              <head>
                <meta property="og:image" content="https://cdn.example.com/posters/frieren.jpg" />
              </head>
              <body>
                <img id="coverOeuvre" src="/img/frieren-cover.webp" />
                <h2 id="titreAlter">Sousou no Frieren</h2>
                <p class="anime-meta">Anime · 2023</p>
                <p>Statut : En cours</p>
                <h2>Genres</h2>
                <p>Fantastique, Aventure, Drame</p>
                <h2>Synopsis</h2>
                <p>Après la défaite du Roi Démon, l'elfe Frieren entreprend un long voyage pour comprendre les humains et honorer la mémoire de ses compagnons.</p>
                <h2>Œuvres associées</h2>
                <a href="/catalogue/frieren/">Frieren (doublon ignoré)</a>
                <a href="/catalogue/violet-evergarden/">
                  <img src="/img/violet.webp" />
                  <h3>Violet Evergarden</h3>
                </a>
                <a href="/catalogue/apotheosis/">
                  <img src="/img/mao.webp" />
                  <h3>Les Carnets de l'Apothicaire</h3>
                </a>
              </body>
            </html>
        """.trimIndent()

        val details = SamaParsers.parseAnimeDetails(
            html = html,
            animeUrl = "https://anime-sama.to/catalogue/frieren/",
            origin = "https://anime-sama.to",
        )

        assertEquals("https://anime-sama.to/img/frieren-cover.webp", details.posterUrl)
        assertEquals("Sousou no Frieren", details.alternativeTitle)
        assertEquals("Anime", details.format)
        assertEquals("2023", details.releaseYear)
        assertEquals("En cours", details.status)
        assertTrue(details.synopsis!!.contains("l'elfe Frieren"))
        assertEquals(listOf("Fantastique", "Aventure", "Drame"), details.genres)
        assertEquals(2, details.related.size)
        assertEquals("Violet Evergarden", details.related[0].title)
        assertEquals("https://anime-sama.to/catalogue/violet-evergarden/", details.related[0].url)
        assertEquals("https://anime-sama.to/img/violet.webp", details.related[0].posterUrl)
    }

    @Test
    fun `parseAnimeDetails ne fabrique jamais de champs absents et filtre les placeholders`() {
        val html = """
            <html><body>
              <h2 id="titreAlter">nom</h2>
              <h2>Synopsis</h2>
              <p>Aucun synopsis disponible.</p>
            </body></html>
        """.trimIndent()

        val details = SamaParsers.parseAnimeDetails(
            html = html,
            animeUrl = "https://anime-sama.to/catalogue/minimal/",
            origin = "https://anime-sama.to",
        )

        assertNull(details.alternativeTitle)
        assertNull(details.synopsis)
        assertNull(details.status)
        assertNull(details.releaseYear)
        assertNull(details.format)
        assertTrue(details.genres.isEmpty())
        assertTrue(details.related.isEmpty())
    }
}
