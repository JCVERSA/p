package dot.jcversa.novay.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchHistoryTest {

    @Test
    fun `upsert deduplique par casse, accents et espaces et promeut en tete`() {
        var list = emptyList<SearchHistoryEntry>()
        list = SearchHistory.upsert(list, "Frieren", searchedAt = 100L)
        list = SearchHistory.upsert(list, "Solo Leveling", searchedAt = 200L)
        list = SearchHistory.upsert(list, "  friéren  ", searchedAt = 300L)

        assertEquals(2, list.size)
        assertEquals("friéren", list.first().query)
        assertEquals(300L, list.first().searchedAt)
        assertEquals("Solo Leveling", list[1].query)
    }

    @Test
    fun `upsert ignore le vide et borne la liste a MAX`() {
        var list = SearchHistory.upsert(emptyList(), "   ", searchedAt = 1L)
        assertTrue(list.isEmpty())

        for (i in 1..18) {
            list = SearchHistory.upsert(list, "Anime $i", searchedAt = i.toLong())
        }
        assertEquals(SearchHistory.MAX, list.size)
        assertEquals("Anime 18", list.first().query)
        assertEquals("Anime 7", list.last().query)
    }

    @Test
    fun `remove supprime sans sensibilite a la casse ni aux accents`() {
        val list = listOf(
            SearchHistoryEntry("Café Terrace", 200L),
            SearchHistoryEntry("Naruto", 100L),
        )
        val after = SearchHistory.remove(list, "cafe terrace")
        assertEquals(listOf("Naruto"), after.map { it.query })
    }

    @Test
    fun `encode et decode font un aller-retour complet et tolerant`() {
        val original = listOf(
            SearchHistoryEntry("Dandadan", 500L),
            SearchHistoryEntry("Bleach", 400L),
        )
        val decoded = SearchHistory.decode(SearchHistory.encode(original))
        assertEquals(original, decoded)

        assertTrue(SearchHistory.decode(null).isEmpty())
        assertTrue(SearchHistory.decode("").isEmpty())
        assertTrue(SearchHistory.decode("not json").isEmpty())
    }
}
