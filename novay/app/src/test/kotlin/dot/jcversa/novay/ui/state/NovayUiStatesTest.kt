package dot.jcversa.novay.ui.state

import dot.jcversa.novay.data.LibraryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NovayUiStatesTest {

    private fun sampleItem(
        title: String,
        addedAt: Long,
        languages: List<String>,
        newEpisodes: Int = 0,
        genres: List<String> = emptyList(),
    ): LibraryItemUi {
        val url = "https://anime-sama.fr/catalogue/${title.lowercase().replace(' ', '-')}"
        val baseline = if (newEpisodes > 0) mapOf("$url/s1" to 10) else emptyMap()
        val lastCounts = if (newEpisodes > 0) mapOf("$url/s1" to 10 + newEpisodes) else emptyMap()
        return LibraryItemUi(
            entry = LibraryEntry(
                title = title,
                url = url,
                languages = languages,
                addedAt = addedAt,
                baseline = baseline,
                lastCounts = lastCounts,
                genres = genres,
            ),
            downloadedCount = 0,
            resume = null,
        )
    }

    private val dataset = listOf(
        sampleItem("Solo Leveling", addedAt = 100L, languages = listOf("VOSTFR"), newEpisodes = 2, genres = listOf("Action", "Fantastique")),
        sampleItem("Dandadan", addedAt = 300L, languages = listOf("VF", "VOSTFR"), newEpisodes = 0, genres = listOf("Action", "Comédie", "Sci-Fi")),
        sampleItem("Attack on Titan", addedAt = 200L, languages = listOf("vf"), newEpisodes = 5, genres = listOf("Action", "Drame")),
        sampleItem("Bleach", addedAt = 400L, languages = listOf("VOSTFR"), newEpisodes = 2, genres = listOf("Action", "Shonen")),
    )

    @Test
    fun computeLibraryItems_filtersByLanguageCaseInsensitively() {
        val all = computeLibraryItems(dataset, LibraryFilter.ALL, LibrarySort.RECENT)
        val vf = computeLibraryItems(dataset, LibraryFilter.VF, LibrarySort.RECENT)
        val vostfr = computeLibraryItems(dataset, LibraryFilter.VOSTFR, LibrarySort.RECENT)

        assertEquals(4, all.size)
        assertEquals(listOf("Dandadan", "Attack on Titan"), vf.map { it.entry.title })
        assertEquals(listOf("Bleach", "Dandadan", "Solo Leveling"), vostfr.map { it.entry.title })
    }

    @Test
    fun computeLibraryItems_sortsByRecentTitleAndNewEpisodes() {
        val byRecent = computeLibraryItems(dataset, LibraryFilter.ALL, LibrarySort.RECENT)
        assertEquals(
            listOf("Bleach", "Dandadan", "Attack on Titan", "Solo Leveling"),
            byRecent.map { it.entry.title },
        )

        val byTitle = computeLibraryItems(dataset, LibraryFilter.ALL, LibrarySort.TITLE)
        assertEquals(
            listOf("Attack on Titan", "Bleach", "Dandadan", "Solo Leveling"),
            byTitle.map { it.entry.title },
        )

        val byNew = computeLibraryItems(dataset, LibraryFilter.ALL, LibrarySort.NEW_EPISODES)
        assertEquals(
            listOf("Attack on Titan", "Bleach", "Solo Leveling", "Dandadan"),
            byNew.map { it.entry.title },
        )
    }

    @Test
    fun computeLibraryItems_filtersByGenreAndLanguageCombined() {
        val comedyAll = computeLibraryItems(dataset, LibraryFilter.ALL, LibrarySort.RECENT, selectedGenre = "Comedy")
        assertEquals(listOf("Dandadan"), comedyAll.map { it.entry.title })

        val actionVf = computeLibraryItems(dataset, LibraryFilter.VF, LibrarySort.TITLE, selectedGenre = "Action")
        assertEquals(listOf("Attack on Titan", "Dandadan"), actionVf.map { it.entry.title })

        val romance = computeLibraryItems(dataset, LibraryFilter.ALL, LibrarySort.RECENT, selectedGenre = "Romance")
        assertTrue(romance.isEmpty())
    }

    @Test
    fun topLevelDestinations_haveFiveUniqueAccessibleEntries() {
        val entries = TopLevelDestination.entries
        assertEquals(5, entries.size)
        assertEquals(5, entries.map { it.testTagKey }.toSet().size)
        assertEquals(5, entries.map { it.morphKind }.toSet().size)
        assertTrue(entries.all { it.contentDescription.isNotBlank() })
    }
}
