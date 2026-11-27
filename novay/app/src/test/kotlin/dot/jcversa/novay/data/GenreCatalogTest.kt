package dot.jcversa.novay.data

import dot.jcversa.novay.data.sama.SamaParsers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenreCatalogTest {

    @Test
    fun `normalize unifies aliases, accents, and casing`() {
        assertEquals("Action", GenreCatalog.normalize("  action "))
        assertEquals("Comédie", GenreCatalog.normalize("Comedy"))
        assertEquals("Comédie", GenreCatalog.normalize("comedie"))
        assertEquals("Slice of Life", GenreCatalog.normalize("slice-of-life"))
        assertEquals("Slice of Life", GenreCatalog.normalize("Tranche de vie"))
        assertEquals("Fantastique", GenreCatalog.normalize("fantasy"))
        assertEquals("Mystère", GenreCatalog.normalize("mystery"))
        assertEquals("Sci-Fi", GenreCatalog.normalize("Science Fiction"))
    }

    @Test
    fun `matches handles aliases, accents, and null filter`() {
        val genres = listOf("Action", "Comédie", "Tranche de vie")
        assertTrue(GenreCatalog.matches(genres, null))
        assertTrue(GenreCatalog.matches(genres, "action"))
        assertTrue(GenreCatalog.matches(genres, "Comedy"))
        assertTrue(GenreCatalog.matches(genres, "Slice of Life"))
        assertFalse(GenreCatalog.matches(genres, "Romance"))
        assertFalse(GenreCatalog.matches(emptyList(), "Action"))
    }

    @Test
    fun `mergeGenresForBar keeps core genres first and appends sorted extras without duplicates`() {
        val merged = GenreCatalog.mergeGenresForBar(
            listOf("Comedy", "Cyberpunk", "Action", "Mecha", "slice-of-life"),
        )
        assertEquals(GenreCatalog.CORE_GENRES, merged.take(GenreCatalog.CORE_GENRES.size))
        val extras = merged.drop(GenreCatalog.CORE_GENRES.size)
        assertEquals(listOf("Cyberpunk", "Mecha"), extras)
    }

    @Test
    fun `slug creates stable ascii test tag identifiers`() {
        assertEquals("action", GenreCatalog.slug("Action"))
        assertEquals("comedie", GenreCatalog.slug("Comédie"))
        assertEquals("slice_of_life", GenreCatalog.slug("Slice of Life"))
        assertEquals("sci_fi", GenreCatalog.slug("Sci-Fi"))
    }

    @Test
    fun `SamaParsers parseAnimeGenres extracts normalized genres from detail html`() {
        val html = """
            <section>
              <h2>Genres</h2>
              <p>Action, Comedy, Tranche de vie, Fantastique</p>
            </section>
        """.trimIndent()
        val parsed = SamaParsers.parseAnimeGenres(html)
        assertEquals(listOf("Action", "Comédie", "Slice of Life", "Fantastique"), parsed)
    }
}
