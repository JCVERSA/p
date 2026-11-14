package dot.jcversa.novay.ui.state

import androidx.compose.ui.text.input.TextFieldValue
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.LibraryEntry
import dot.jcversa.novay.data.SearchHistoryEntry
import dot.jcversa.novay.data.WatchHistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartSuggestionEngineTest {

    private val library = listOf(
        LibraryEntry(
            title = "Frieren: Beyond Journey's End",
            url = "https://anime-sama.fr/catalogue/frieren",
            posterUrl = "https://img/frieren.jpg",
        ),
        LibraryEntry(
            title = "Pokémon Horizons",
            url = "https://anime-sama.fr/catalogue/pokemon-horizons",
            posterUrl = "https://img/pokemon.jpg",
        ),
    )

    private val history = listOf(
        WatchHistoryEntry(
            animeTitle = "Solo Leveling",
            animeUrl = "https://anime-sama.fr/catalogue/solo-leveling",
            posterUrl = null,
            seasonName = "Saison 2",
            seasonUrl = "https://anime-sama.fr/catalogue/solo-leveling/saison2/vostfr",
            episodeNumber = 4,
            episodeLabel = "Épisode 4",
            lienIds = emptyList(),
            jeton = "",
            watchedAt = 1_000L,
        ),
    )

    private val featured = listOf(
        AnimeSearchResult(
            title = "Dandadan",
            url = "https://anime-sama.fr/catalogue/dandadan",
        ),
        AnimeSearchResult(
            title = "Frieren: Beyond Journey's End",
            url = "https://anime-sama.fr/catalogue/frieren/",
        ),
    )

    @Test
    fun `findHighlightSpans gere la casse, les accents et les requetes multi-mots`() {
        val accentSpans = SmartSuggestionEngine.findHighlightSpans("Pokémon Horizons", "poke")
        assertEquals(listOf(HighlightSpan(0, 4)), accentSpans)

        val multiWordSpans = SmartSuggestionEngine.findHighlightSpans("Solo Leveling", "solo lev")
        assertEquals(listOf(HighlightSpan(0, 8)), multiWordSpans)

        val splitTokenSpans = SmartSuggestionEngine.findHighlightSpans("Frieren: Beyond Journey's End", "frieren end")
        assertEquals(listOf(HighlightSpan(0, 7), HighlightSpan(26, 29)), splitTokenSpans)
    }

    @Test
    fun `champ vide focalise regroupe recents, bibliotheque-historique et themes`() {
        val recents = listOf(
            SearchHistoryEntry("Frieren", 200L),
            SearchHistoryEntry("Isekai", 100L),
        )
        val ui = SmartSuggestionEngine.buildSuggestions(
            query = "",
            recentSearches = recents,
            libraryEntries = library,
            watchHistory = history,
            featuredAnime = featured,
            liveCatalog = emptyList(),
            discoveryChips = BROWSE_DISCOVERY_CHIPS,
            isLiveLoading = false,
        )

        assertEquals(listOf("Frieren", "Isekai"), ui.querySuggestions.map { it.query })
        assertEquals(3, ui.localAnime.size)
        assertEquals(AnimeSuggestionOrigin.LIBRARY, ui.localAnime[0].origin)
        assertEquals(AnimeSuggestionOrigin.HISTORY, ui.localAnime[2].origin)
        assertEquals(BROWSE_DISCOVERY_CHIPS, ui.popularTags)
        assertTrue(ui.liveAnime.isEmpty())
    }

    @Test
    fun `saisie deduplique les animes locaux contre le catalogue en direct et conserve les alias`() {
        val live = listOf(
            AnimeSearchResult(title = "Frieren: Beyond Journey's End", url = "https://anime-sama.fr/catalogue/frieren/"),
            AnimeSearchResult(title = "Sousou no Frieren Film", url = "https://anime-sama.fr/catalogue/frieren-film"),
            AnimeSearchResult(title = "L'Attaque des Titans", url = "https://anime-sama.fr/catalogue/snk"),
        )
        val ui = SmartSuggestionEngine.buildSuggestions(
            query = "frier",
            recentSearches = listOf(SearchHistoryEntry("Frieren", 500L)),
            libraryEntries = library,
            watchHistory = history,
            featuredAnime = featured,
            liveCatalog = live,
            discoveryChips = BROWSE_DISCOVERY_CHIPS,
            isLiveLoading = false,
        )

        assertEquals(listOf("Frieren"), ui.querySuggestions.map { it.query })
        assertEquals(1, ui.localAnime.size)
        assertEquals(AnimeSuggestionOrigin.LIBRARY, ui.localAnime.first().origin)
        assertEquals(
            listOf("Sousou no Frieren Film", "L'Attaque des Titans"),
            ui.liveAnime.map { it.anime.title },
        )
        assertEquals(listOf(HighlightSpan(10, 15)), ui.liveAnime.first().spans)
        assertTrue(ui.liveAnime[1].spans.isEmpty())
    }

    @Test
    fun `BrowseSearchController pilote la machine a etats focus-saisie-soumission-retour`() {
        val controller = BrowseSearchController()
        assertEquals(BrowseSearchDisplay.SpotlightIdle, controller.searchDisplay)
        assertFalse(controller.canHandleBack)

        controller.onFocusChanged(true)
        assertEquals(BrowseSearchDisplay.FocusedEmptySuggestions, controller.searchDisplay)
        assertTrue(controller.canHandleBack)

        controller.onQueryChange(TextFieldValue("fri"))
        assertEquals(BrowseSearchDisplay.TypingSuggestions, controller.searchDisplay)

        assertNull(controller.prepareSubmit("f"))
        assertEquals("fri", controller.prepareSubmit())
        assertEquals(BrowseSearchDisplay.LoadingResults, controller.searchDisplay)

        controller.finishSubmitSuccess(emptyList())
        assertEquals(BrowseSearchDisplay.EmptyResults, controller.searchDisplay)

        controller.onFocusChanged(true)
        assertEquals(BrowseSearchDisplay.TypingSuggestions, controller.searchDisplay)

        controller.fillQuery("Frieren")
        assertEquals("Frieren", controller.queryField.text)
        assertEquals(7, controller.queryField.selection.start)

        controller.resetToSpotlight()
        assertEquals(BrowseSearchDisplay.SpotlightIdle, controller.searchDisplay)
        assertFalse(controller.canHandleBack)
    }

    @Test
    fun `BrowseSearchController gere la selection de genre, erreur, reessai et reinitialisation`() {
        val controller = BrowseSearchController()

        controller.selectGenre("Action")
        assertEquals("Action", controller.selectedGenre)
        assertEquals(BrowseSearchDisplay.GenreLoading, controller.searchDisplay)
        assertTrue(controller.canHandleBack)

        controller.finishGenreError("Reseau indisponible")
        assertEquals(BrowseSearchDisplay.GenreError, controller.searchDisplay)

        controller.retryGenre()
        assertEquals(BrowseSearchDisplay.GenreLoading, controller.searchDisplay)

        controller.finishGenreSuccess(
            listOf(AnimeSearchResult(title = "Jujutsu Kaisen", url = "https://anime-sama.fr/catalogue/jjk", genres = listOf("Action")))
        )
        assertEquals(BrowseSearchDisplay.GenreResults, controller.searchDisplay)

        // Re-tapping active genre clears filter back to SpotlightIdle
        controller.selectGenre("Action")
        assertNull(controller.selectedGenre)
        assertEquals(BrowseSearchDisplay.SpotlightIdle, controller.searchDisplay)
        assertFalse(controller.canHandleBack)
    }
}
