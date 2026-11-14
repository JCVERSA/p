package dot.jcversa.novay.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import dot.jcversa.novay.data.AnimeEpisode
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.AnimeSeason
import dot.jcversa.novay.data.AnimeSource
import dot.jcversa.novay.data.CatalogHero
import dot.jcversa.novay.data.CatalogHome
import dot.jcversa.novay.data.GenreCatalog
import dot.jcversa.novay.data.Library
import dot.jcversa.novay.data.LibraryEntry
import dot.jcversa.novay.data.ResolvedStream
import dot.jcversa.novay.ui.screens.BrowseScreen
import dot.jcversa.novay.ui.screens.LibraryScreen
import dot.jcversa.novay.ui.theme.NoVaYTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
class GenreFilterChipBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun genreFilterChipBar_supportsScrollSingleSelectAndAllReset() {
        var selectedCallback: String? = "initial"

        composeRule.setContent {
            NoVaYTheme {
                SlateMotionProvider {
                    var selected by remember { mutableStateOf<String?>(null) }
                    GenreFilterChipBar(
                        genres = GenreCatalog.CORE_GENRES,
                        selectedGenre = selected,
                        onSelectGenre = {
                            selected = it
                            selectedCallback = it
                        },
                        testTagPrefix = "test_genre",
                    )
                }
            }
        }

        val allChip = composeRule.onNodeWithTag("test_genre_all")
            .assertIsDisplayed()
            .assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))

        val actionChip = composeRule.onNodeWithTag("test_genre_action")
            .assertIsDisplayed()
            .assertIsNotSelected()

        actionChip.performClick()
        composeRule.waitForIdle()
        assertEquals("Action", selectedCallback)
        actionChip.assertIsSelected()
        allChip.assertIsNotSelected()

        // Re-tapping active chip clears selection back to Tout
        actionChip.performClick()
        composeRule.waitForIdle()
        assertNull(selectedCallback)
        allChip.assertIsSelected()

        // Scroll lazily to reach Sport at the end of the chip bar
        composeRule.onNodeWithTag("test_genre_row")
            .performScrollToNode(hasTestTag("test_genre_sport"))
        val sportChip = composeRule.onNodeWithTag("test_genre_sport")
            .assertIsDisplayed()
        sportChip.performClick()
        composeRule.waitForIdle()
        assertEquals("Sport", selectedCallback)
        sportChip.assertIsSelected()

        // Scroll back to Tout and reset to All
        composeRule.onNodeWithTag("test_genre_row")
            .performScrollToNode(hasTestTag("test_genre_all"))
        allChip.performClick()
        composeRule.waitForIdle()
        assertNull(selectedCallback)
        allChip.assertIsSelected()
    }

    @Test
    fun libraryScreen_filtersSavedAnimeByGenreAndAllowsReset() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        Library.save(
            ctx,
            listOf(
                LibraryEntry(
                    title = "Solo Leveling",
                    url = "https://anime-sama.to/catalogue/solo-leveling/",
                    genres = listOf("Action", "Fantastique"),
                    languages = listOf("VOSTFR"),
                    addedAt = 200L,
                ),
                LibraryEntry(
                    title = "Horimiya",
                    url = "https://anime-sama.to/catalogue/horimiya/",
                    genres = listOf("Romance", "Slice of Life"),
                    languages = listOf("VOSTFR"),
                    addedAt = 100L,
                ),
            ),
        )

        composeRule.setContent {
            NoVaYTheme {
                SlateMotionProvider {
                    LibraryScreen(
                        onOpenShow = {},
                        onResume = {},
                    )
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Solo Leveling").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Solo Leveling").assertIsDisplayed()
        composeRule.onNodeWithText("Horimiya").assertIsDisplayed()

        // Filter to Romance -> only Horimiya remains
        composeRule.onNodeWithTag("library_genre_chip_romance").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Horimiya").assertIsDisplayed()
        composeRule.onNodeWithText("Solo Leveling").assertDoesNotExist()

        // Filter to Sport (no entries) -> empty filter state + reset CTA
        composeRule.onNodeWithTag("library_genre_chip_row")
            .performScrollToNode(hasTestTag("library_genre_chip_sport"))
        composeRule.onNodeWithTag("library_genre_chip_sport").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("library_reset_filters_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Solo Leveling").assertIsDisplayed()
        composeRule.onNodeWithText("Horimiya").assertIsDisplayed()

        Library.save(ctx, emptyList())
    }

    @Test
    fun browseScreen_filtersCatalogByLiveGenreAndResetsWithAllChip() {
        val fakeSource = object : AnimeSource {
            override val name = "test-source"
            override suspend fun home() = CatalogHome(
                heroes = listOf(
                    CatalogHero(
                        title = "Frieren",
                        seasonLabel = "Saison 1",
                        genres = listOf("Aventure", "Fantastique"),
                        synopsis = null,
                        seasonUrl = "https://anime-sama.to/catalogue/frieren/saison1/vostfr/",
                        posterUrl = null,
                        language = "VOSTFR",
                    ),
                ),
                cards = emptyList(),
            )
            override suspend fun search(query: String) = emptyList<AnimeSearchResult>()
            override suspend fun catalogByGenre(genre: String): List<AnimeSearchResult> =
                if (genre.equals("Romance", ignoreCase = true)) {
                    listOf(
                        AnimeSearchResult(
                            title = "Kaguya-sama",
                            url = "https://anime-sama.to/catalogue/kaguya-sama/",
                            genres = listOf("Romance", "Comédie"),
                        ),
                    )
                } else {
                    emptyList()
                }
            override suspend fun seasons(anime: AnimeSearchResult) = emptyList<AnimeSeason>()
            override suspend fun episodes(season: AnimeSeason) = emptyList<AnimeEpisode>()
            override suspend fun stream(episode: AnimeEpisode): ResolvedStream = error("unused")
        }

        composeRule.setContent {
            NoVaYTheme {
                SlateMotionProvider {
                    BrowseScreen(
                        source = fakeSource,
                        onOpenShow = {},
                    )
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag("browse_spotlight_hero").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag("browse_genre_chip_romance").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Kaguya-sama").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Kaguya-sama").assertIsDisplayed()

        composeRule.onNodeWithTag("browse_genre_chip_row")
            .performScrollToNode(hasTestTag("browse_genre_chip_all"))
        composeRule.onNodeWithTag("browse_genre_chip_all").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag("browse_spotlight_hero").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
    }
}
