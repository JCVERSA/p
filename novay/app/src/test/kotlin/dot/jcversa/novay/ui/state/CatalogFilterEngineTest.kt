package dot.jcversa.novay.ui.state

import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.CatalogPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogFilterEngineTest {

    private val sampleItems = listOf(
        AnimeSearchResult(
            title = "Solo Leveling",
            url = "https://anime-sama.to/catalogue/solo-leveling/",
            language = "VF",
            isVf = true,
            genres = listOf("Action", "Fantastique"),
            format = "Anime",
            releaseYear = "2024",
            status = "En cours",
        ),
        AnimeSearchResult(
            title = "Frieren",
            url = "https://anime-sama.to/catalogue/frieren/",
            language = "VOSTFR",
            genres = listOf("Aventure", "Fantastique"),
            format = "Anime",
            releaseYear = "2023",
            status = "Terminé",
        ),
        AnimeSearchResult(
            title = "Suzume",
            url = "https://anime-sama.to/catalogue/suzume/",
            language = "VF",
            isVf = true,
            genres = listOf("Aventure", "Drame"),
            format = "Film",
            releaseYear = "2022",
            status = "Terminé",
        ),
    )

    @Test
    fun `extractAvailableFacets omet les annees et statuts quand aucun item ne les expose`() {
        val bareItems = listOf(
            AnimeSearchResult(title = "Naruto", url = "https://anime-sama.to/catalogue/naruto/"),
        )
        val facets = CatalogFilterEngine.extractAvailableFacets(bareItems)
        assertTrue(facets.years.isEmpty())
        assertTrue(facets.statuses.isEmpty())
        assertFalse(CatalogSortOption.YEAR_DESC in facets.sortOptions)
    }

    @Test
    fun `extractAvailableFacets expose les vraies annees et statuts presents`() {
        val facets = CatalogFilterEngine.extractAvailableFacets(sampleItems)
        assertEquals(listOf("2024", "2023", "2022"), facets.years)
        assertEquals(listOf("En cours", "Terminé"), facets.statuses)
        assertTrue(CatalogSortOption.YEAR_DESC in facets.sortOptions)
    }

    @Test
    fun `filterAndSort combine multi-genres, langue, format, annee, statut et tri`() {
        val filtered = CatalogFilterEngine.filterAndSort(
            items = sampleItems,
            filter = CatalogFilterState(
                genres = setOf("Action", "Fantastique"),
                language = "VF",
                format = "Anime",
                releaseYear = "2024",
                status = "En cours",
            ),
        )
        assertEquals(listOf("Solo Leveling"), filtered.map { it.title })

        val sortedAz = CatalogFilterEngine.filterAndSort(
            items = sampleItems,
            filter = CatalogFilterState(sort = CatalogSortOption.TITLE_ASC),
        )
        assertEquals(listOf("Frieren", "Solo Leveling", "Suzume"), sortedAz.map { it.title })

        val sortedRecent = CatalogFilterEngine.filterAndSort(
            items = sampleItems,
            filter = CatalogFilterState(sort = CatalogSortOption.RECENT),
            recentOrderUrls = mapOf("https://anime-sama.to/catalogue/suzume" to 0),
        )
        assertEquals("Suzume", sortedRecent.first().title)
    }

    @Test
    fun `toRemovableChips et removeChip mettent a jour les filtres et la persistance string`() {
        val initial = CatalogFilterState(
            genres = setOf("Action", "Romance"),
            language = "VF",
            format = "Film",
            sort = CatalogSortOption.TITLE_ASC,
        )
        assertEquals(5, initial.activeCount)
        val restored = CatalogFilterState.fromSavedString(initial.toSavedString())
        assertEquals(initial, restored)

        val chips = CatalogFilterEngine.toRemovableChips(initial)
        assertEquals(5, chips.size)
        val langChip = chips.first { it.kind == CatalogFilterChipItem.Kind.LANGUAGE }
        val afterRemove = CatalogFilterEngine.removeChip(initial, langChip)
        assertNull(afterRemove.language)
        assertEquals(4, afterRemove.activeCount)
    }

    @Test
    fun `BrowseSearchController passe en FilteredCatalog pour filtres multi-criteres et gere la pagination`() {
        val controller = BrowseSearchController()
        controller.updateFilter(
            CatalogFilterState(
                genres = setOf("Action", "Fantastique"),
                language = "VF",
            ),
        )
        assertEquals(BrowseSearchDisplay.FilteredCatalog, controller.searchDisplay)
        assertTrue(controller.canHandleBack)
        assertEquals(3, controller.activeFilterCount)

        controller.startCatalogPageLoad(1)
        assertTrue(controller.catalogPageState.isLoading)
        controller.finishCatalogPageSuccess(
            CatalogPage(items = sampleItems.take(2), page = 1, hasNextPage = true),
            resetExisting = true,
        )
        assertEquals(2, controller.catalogPageState.items.size)
        assertTrue(controller.catalogPageState.hasNextPage)

        controller.startCatalogPageLoad(2)
        assertTrue(controller.catalogPageState.isLoadingMore)
        controller.finishCatalogPageSuccess(
            CatalogPage(items = sampleItems.drop(2), page = 2, hasNextPage = false),
            resetExisting = false,
        )
        assertEquals(3, controller.catalogPageState.items.size)
        assertFalse(controller.catalogPageState.hasNextPage)

        controller.resetAllFilters()
        assertEquals(BrowseSearchDisplay.SpotlightIdle, controller.searchDisplay)
        assertFalse(controller.canHandleBack)
    }
}
