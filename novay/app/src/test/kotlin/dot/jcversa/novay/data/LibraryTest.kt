package dot.jcversa.novay.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bibliothèque (V2.4) : pure — upsert/remove, compteurs de nouveaux
 * épisodes, consommation à l'ouverture d'une saison, encode/decode.
 */
class LibraryTest {

    private fun entry(
        url: String = "https://anime-sama.lu/catalogue/solo-leveling",
        title: String = "Solo Leveling",
        slug: String = ""
    ) = LibraryEntry(
        title = title,
        url = url,
        slug = slug,
        posterUrl = "https://img/solo.jpg",
        languages = listOf("VOSTFR"),
        addedAt = 1_000L
    )

    @Test
    fun `upsert deduplique par url et conserve les compteurs`() {
        val original = entry().copy(
            baseline = mapOf("s1" to 10),
            lastCounts = mapOf("s1" to 12)
        )
        val updated = entry() // re-ajout sans compteurs
        val result = Library.upsert(listOf(original), updated)
        assertEquals(1, result.size)
        assertEquals(mapOf("s1" to 10), result.first().baseline)
        assertEquals(mapOf("s1" to 12), result.first().lastCounts)
    }

    @Test
    fun `remove retire l anime par url`() {
        val list = listOf(entry(), entry(url = "https://x/naruto", title = "Naruto"))
        val result = Library.remove(list, "https://x/naruto/")
        assertEquals(1, result.size)
        assertEquals("Solo Leveling", result.first().title)
    }

    @Test
    fun `nouveaux episodes = somme des depassements de baseline`() {
        val e = entry().copy(
            baseline = mapOf("s1" to 10, "s2" to 5),
            lastCounts = mapOf("s1" to 12, "s2" to 5)
        )
        assertEquals(2, e.newEpisodes)
    }

    @Test
    fun `comptage inferieur a la baseline = zero nouveau`() {
        val e = entry().copy(
            baseline = mapOf("s1" to 10),
            lastCounts = mapOf("s1" to 8) // épisodes retirés du site
        )
        assertEquals(0, e.newEpisodes)
    }

    @Test
    fun `sans comptage connue, aucun nouveau`() {
        assertEquals(0, entry().newEpisodes)
    }

    @Test
    fun `marquer une saison vue consomme ses nouveaux episodes`() {
        var list = listOf(
            entry().copy(baseline = mapOf("s1" to 10), lastCounts = mapOf("s1" to 14))
        )
        assertEquals(4, list.first().newEpisodes)
        list = Library.markSeasonSeen(list, "s1/", 14)
        assertEquals(0, list.first().newEpisodes)
        // La baseline est montee au comptage courant.
        assertEquals(14, list.first().baseline["s1"])
    }

    @Test
    fun `encode decode aller-retour complet`() {
        val list = listOf(
            entry().copy(
                languages = listOf("VF", "VOSTFR"),
                baseline = mapOf("s1" to 3),
                lastCounts = mapOf("s1" to 7, "s2" to 1)
            )
        )
        val decoded = Library.decode(Library.encode(list))
        assertEquals(1, decoded.size)
        val d = decoded.first()
        assertEquals("Solo Leveling", d.title)
        assertEquals(listOf("VF", "VOSTFR"), d.languages)
        assertEquals(mapOf("s1" to 3), d.baseline)
        assertEquals(mapOf("s1" to 7, "s2" to 1), d.lastCounts)
        assertEquals(4, d.newEpisodes)
    }

    @Test
    fun `decode avale le vide et le corrige`() {
        assertTrue(Library.decode(null).isEmpty())
        assertTrue(Library.decode("").isEmpty())
        assertTrue(Library.decode("pas du tout du json").isEmpty())
    }

    @Test
    fun `toSearchResult reconstruit l entree catalogue`() {
        val r = entry(slug = "solo-leveling").toSearchResult()
        assertEquals("Solo Leveling", r.title)
        assertEquals("solo-leveling", r.slug)
        assertEquals("https://img/solo.jpg", r.posterUrl)
        assertFalse(r.isVf)
    }
}
