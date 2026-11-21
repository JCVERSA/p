package dot.jcversa.novay.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import dot.jcversa.novay.data.AnimeEpisode
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.AnimeSeason
import dot.jcversa.novay.data.AnimeSource
import dot.jcversa.novay.data.CatalogCard
import dot.jcversa.novay.data.CatalogHero
import dot.jcversa.novay.data.CatalogHome
import dot.jcversa.novay.data.CatalogPage
import dot.jcversa.novay.data.ResolvedStream
import dot.jcversa.novay.ui.components.LocalSlateMotion
import dot.jcversa.novay.ui.components.SlateMotion
import dot.jcversa.novay.ui.components.SlateMotionProvider
import dot.jcversa.novay.ui.theme.NoVaYTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
class BrowseHybridCatalogTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun testSource(
        failPageTwoFirstAttempt: Boolean = false,
        withOptionalYearAndStatus: Boolean = true,
    ): AnimeSource {
        var pageTwoAttempts = 0
        val year1 = if (withOptionalYearAndStatus) "2024" else null
        val status1 = if (withOptionalYearAndStatus) "En cours" else null
        val page1Items = listOf(
            AnimeSearchResult(
                title = "Solo Leveling",
                url = "https://anime-sama.to/catalogue/solo-leveling/",
                slug = "solo-leveling",
                language = "VF",
                isVf = true,
                genres = listOf("Action", "Fantastique"),
                format = "Anime",
                releaseYear = year1,
                status = status1,
            ),
            AnimeSearchResult(
                title = "Horimiya",
                url = "https://anime-sama.to/catalogue/horimiya/",
                slug = "horimiya",
                language = "VOSTFR",
                genres = listOf("Romance", "Comédie"),
                format = "Anime",
                releaseYear = if (withOptionalYearAndStatus) "2021" else null,
                status = if (withOptionalYearAndStatus) "Terminé" else null,
            ),
            AnimeSearchResult(
                title = "Suzume",
                url = "https://anime-sama.to/catalogue/suzume/",
                slug = "suzume",
                language = "VF",
                isVf = true,
                genres = listOf("Aventure", "Drame"),
                format = "Film",
                releaseYear = if (withOptionalYearAndStatus) "2022" else null,
                status = if (withOptionalYearAndStatus) "Terminé" else null,
            ),
            AnimeSearchResult(
                title = "Chainsaw Man",
                url = "https://anime-sama.to/catalogue/chainsaw-man/",
                slug = "chainsaw-man",
                language = "VOSTFR",
                genres = listOf("Action", "Horreur"),
                format = "Anime",
            ),
        )
        val page2Items = listOf(
            AnimeSearchResult(
                title = "Dandadan",
                url = "https://anime-sama.to/catalogue/dandadan/",
                slug = "dandadan",
                language = "VOSTFR",
                genres = listOf("Action", "Comédie"),
                format = "Anime",
            ),
        )

        return object : AnimeSource {
            override val name = "test-hybrid"

            override suspend fun home() = CatalogHome(
                heroes = listOf(
                    CatalogHero(
                        title = "Frieren",
                        seasonLabel = "Saison 1",
                        genres = listOf("Aventure", "Fantastique"),
                        synopsis = "L'histoire après le voyage.",
                        seasonUrl = "https://anime-sama.to/catalogue/frieren/saison1/vostfr/",
                        language = "VOSTFR",
                    ),
                ),
                cards = listOf(
                    CatalogCard(
                        title = "Solo Leveling",
                        animeUrl = "https://anime-sama.to/catalogue/solo-leveling/",
                        seasonUrl = "https://anime-sama.to/catalogue/solo-leveling/saison1/vf/",
                        timeLabel = "19h15",
                        language = "VF",
                    ),
                ),
            )

            override suspend fun catalogPage(
                page: Int,
                genres: Set<String>,
                language: String?,
                format: String?,
            ): CatalogPage {
                val all = page1Items + page2Items
                if (genres.isNotEmpty() || language != null || format != null) {
                    val filtered = all.filter { item ->
                        val gOk = genres.all { g -> item.genres.any { it.equals(g, ignoreCase = true) } }
                        val lOk = language == null || item.language.equals(language, ignoreCase = true)
                        val fOk = format == null || item.format.equals(format, ignoreCase = true)
                        gOk && lOk && fOk
                    }
                    return CatalogPage(items = filtered, page = 1, hasNextPage = false)
                }
                return when (page) {
                    1 -> CatalogPage(items = page1Items, page = 1, hasNextPage = true)
                    2 -> {
                        pageTwoAttempts++
                        if (failPageTwoFirstAttempt && pageTwoAttempts == 1) {
                            throw IOException("Timeout page 2")
                        }
                        CatalogPage(items = page2Items, page = 2, hasNextPage = false)
                    }
                    else -> CatalogPage(items = emptyList(), page = page, hasNextPage = false)
                }
            }

            override suspend fun search(query: String) = emptyList<AnimeSearchResult>()
            override suspend fun seasons(anime: AnimeSearchResult) = emptyList<AnimeSeason>()
            override suspend fun episodes(season: AnimeSeason) = emptyList<AnimeEpisode>()
            override suspend fun stream(episode: AnimeEpisode): ResolvedStream = error("unused")
        }
    }

    @Test
    fun hybridCatalog_displaysDiscoveryRailsAndCompletePaginatedGridWithRetry() {
        composeRule.setContent {
            NoVaYTheme {
                SlateMotionProvider {
                    BrowseScreen(
                        source = testSource(failPageTwoFirstAttempt = true),
                        onOpenShow = {},
                    )
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("browse_spotlight_hero").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("browse_spotlight_hero").assertIsDisplayed()

        // Défiler vers la section Catalogue complet
        composeRule.onNodeWithTag("browse_hybrid_grid")
            .performScrollToNode(hasTestTag("browse_complete_catalog_header"))
        composeRule.onNodeWithTag("browse_complete_catalog_header").assertIsDisplayed()

        // Défiler jusqu'au bas pour déclencher la page 2 (qui échoue puis propose Réessayer)
        composeRule.onNodeWithTag("browse_hybrid_grid")
            .performScrollToIndex(9)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("browse_pagination_retry_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("browse_pagination_retry_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Dandadan").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Dandadan").assertIsDisplayed()
    }

    @Test
    fun filterSheet_appliesMultiCriteriaUpdatesBadgeShowsRemovableChipsAndResets() {
        composeRule.setContent {
            NoVaYTheme {
                SlateMotionProvider {
                    BrowseScreen(
                        source = testSource(withOptionalYearAndStatus = true),
                        onOpenShow = {},
                    )
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("browse_filter_button").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithTag("browse_spotlight_hero").fetchSemanticsNodes().isNotEmpty()
        }

        // Ouvre la feuille de filtres avancés
        composeRule.onNodeWithTag("browse_filter_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("catalog_filter_sheet").fetchSemanticsNodes().isNotEmpty()
        }

        // Sélectionne VF + Action
        composeRule.onNodeWithTag("filter_lang_vf").performClick()
        composeRule.onNodeWithTag("filter_genre_action").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("filter_sheet_preview_count").assertIsDisplayed()

        // Applique les filtres
        composeRule.onNodeWithTag("filter_sheet_apply").performClick()

        // Compteur sur le bouton filtre + puces amovibles + résultat filtré
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("browse_filter_badge").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithTag("browse_active_chip_lang_vf").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText("Solo Leveling").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("browse_filter_badge").assertIsDisplayed()
        composeRule.onNodeWithTag("browse_active_chip_lang_vf").assertIsDisplayed()
        composeRule.onNodeWithTag("browse_active_chip_genre_action").assertIsDisplayed()
        composeRule.onNodeWithText("Solo Leveling").assertIsDisplayed()

        // Réinitialisation complète via le chip Réinitialiser
        composeRule.onNodeWithTag("browse_filters_reset_chip").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("browse_spotlight_hero").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(composeRule.onAllNodesWithTag("browse_filter_badge").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun filterSheet_honestlyOmitsYearsStatusesAndYearSortWhenDataAbsent_andSupportsReduceMotion() {
        composeRule.setContent {
            NoVaYTheme {
                CompositionLocalProvider(LocalSlateMotion provides SlateMotion(reduceMotion = true)) {
                    BrowseScreen(
                        source = testSource(withOptionalYearAndStatus = false),
                        onOpenShow = {},
                    )
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("browse_spotlight_hero").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("browse_filter_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("catalog_filter_sheet").fetchSemanticsNodes().isNotEmpty()
        }

        // Aucune année ni statut dans le HTML : ces filtres et le tri par année sont honnêtement omis
        assertTrue(composeRule.onAllNodesWithTag("filter_year_all").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag("filter_status_all").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag("filter_sort_year_desc").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag("filter_sort_title_asc").assertIsDisplayed()
    }
}
