package dot.jcversa.novay.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import dot.jcversa.novay.data.AnimeDetails
import dot.jcversa.novay.data.AnimeEpisode
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.AnimeSeason
import dot.jcversa.novay.data.AnimeSource
import dot.jcversa.novay.data.Library
import dot.jcversa.novay.data.ResolvedStream
import dot.jcversa.novay.data.WatchHistory
import dot.jcversa.novay.data.WatchHistoryEntry
import dot.jcversa.novay.ui.components.LocalSlateMotion
import dot.jcversa.novay.ui.components.SlateMotion
import dot.jcversa.novay.ui.theme.NoVaYTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
class ShowScreenRedesignTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private val sampleAnime = AnimeSearchResult(
        title = "Frieren",
        url = "https://anime-sama.to/catalogue/frieren/",
        slug = "frieren",
        genres = listOf("Fantastique", "Aventure"),
    )

    private val vfSeason1 = AnimeSeason(
        name = "Saison 1",
        url = "https://anime-sama.to/catalogue/frieren/saison1/vf/",
        language = "VF",
    )
    private val vfSeason2 = AnimeSeason(
        name = "Saison 2",
        url = "https://anime-sama.to/catalogue/frieren/saison2/vf/",
        language = "VF",
    )
    private val vostSeason1 = AnimeSeason(
        name = "Saison 1 VO",
        url = "https://anime-sama.to/catalogue/frieren/saison1/vostfr/",
        language = "VOSTFR",
    )

    private val relatedAnime = AnimeSearchResult(
        title = "Violet Evergarden",
        url = "https://anime-sama.to/catalogue/violet-evergarden/",
        slug = "violet-evergarden",
    )

    private val longSynopsis =
        "Après une quête de dix ans aux côtés du héros Himmel pour vaincre le Roi Démon, l'elfe mage Frieren reprend la route afin de mieux comprendre la vie humaine et honorer les souvenirs partagés avec ses anciens compagnons."

    @Before
    fun cleanPrefs() {
        Library.save(context, emptyList())
        WatchHistory.clear(context)
    }

    private fun fakeSource(
        seasons: List<AnimeSeason> = listOf(vostSeason1, vfSeason1, vfSeason2),
        details: AnimeDetails = AnimeDetails(
            synopsis = longSynopsis,
            genres = listOf("Fantastique", "Aventure", "Drame"),
            status = "En cours",
            releaseYear = "2023",
            format = "Anime",
            alternativeTitle = "Sousou no Frieren",
            related = listOf(relatedAnime),
        ),
        episodes: List<AnimeEpisode> = listOf(
            AnimeEpisode(1, vfSeason1.url, "Épisode 1"),
            AnimeEpisode(2, vfSeason1.url, "Épisode 2"),
        ),
    ) = object : AnimeSource {
        override val name: String = "fake"
        override suspend fun search(query: String) = emptyList<AnimeSearchResult>()
        override suspend fun seasons(anime: AnimeSearchResult) = seasons
        override suspend fun episodes(season: AnimeSeason) = episodes
        override suspend fun stream(episode: AnimeEpisode) =
            ResolvedStream("https://example.com/1.m3u8", true, emptyMap(), emptyList())
        override suspend fun details(anime: AnimeSearchResult) = details
    }

    @Test
    fun hero_synopsis_languageSwitch_collapse_detailsSheet_and_relatedWork() {
        var openedRelated: AnimeSearchResult? = null
        var openedSeason: AnimeSeason? = null

        composeRule.setContent {
            NoVaYTheme {
                ShowScreen(
                    source = fakeSource(),
                    anime = sampleAnime,
                    onBack = {},
                    onOpenEpisodes = { openedSeason = it },
                    onResume = {},
                    onOpenShow = { openedRelated = it },
                )
            }
        }

        composeRule.waitUntil(5000) {
            composeRule.onAllNodesWithTag("show_primary_cta").fetchSemanticsNodes().isNotEmpty()
        }

        // Metadata line displays real parsed metadata + genres
        composeRule.onNodeWithText("Anime · 2023 · En cours · 3 saisons").assertIsDisplayed()
        composeRule.onNodeWithText("Sousou no Frieren").assertIsDisplayed()
        composeRule.onNodeWithText("Drame").assertIsDisplayed()

        // Synopsis expandable toggle
        composeRule.onNodeWithTag("show_synopsis_toggle").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Réduire").assertIsDisplayed()

        // VF is default when VF and VOSTFR both exist
        composeRule.onNodeWithText("Saison 1").assertIsDisplayed()
        composeRule.onAllNodesWithText("Saison 1 VO").assertCountEquals(0)

        // Switching to VOSTFR shows VOSTFR seasons
        composeRule.onNodeWithTag("show_lang_segment_VOSTFR").performClick()
        composeRule.onNodeWithText("Saison 1 VO").assertIsDisplayed()

        // Collapsing VOSTFR group hides its rows; expanding shows them again
        composeRule.onNodeWithTag("season_group_header_VOSTFR").performClick()
        composeRule.onAllNodesWithText("Saison 1 VO").assertCountEquals(0)
        composeRule.onNodeWithTag("season_group_header_VOSTFR").performClick()
        composeRule.onNodeWithText("Saison 1 VO").assertIsDisplayed()

        // Switching to All ("Tout") displays both VF and VOSTFR
        composeRule.onNodeWithTag("show_lang_segment_ALL").performClick()
        composeRule.onNodeWithText("Saison 1").assertIsDisplayed()

        // Follow button toggles state
        composeRule.onNodeWithTag("show_favorite_button").performClick()
        composeRule.onNodeWithText("Suivi").assertIsDisplayed()

        // Details sheet opens with real details and closes cleanly
        composeRule.onNodeWithTag("show_details_button").performClick()
        composeRule.onNodeWithTag("show_details_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("show_details_close_button").performClick()
        composeRule.waitForIdle()

        // Related rail scrolls into view and triggers onOpenShow
        composeRule.onNodeWithTag("show_seasons_list").performScrollToNode(hasTestTag("show_related_card_0"))
        composeRule.onNodeWithTag("show_related_card_0").performClick()
        assertEquals(relatedAnime.url, openedRelated?.url)
    }

    @Test
    fun missingMetadata_isOmittedCleanly_and_reduceMotionRendersSafely() {
        composeRule.setContent {
            NoVaYTheme {
                CompositionLocalProvider(LocalSlateMotion provides SlateMotion(reduceMotion = true)) {
                    ShowScreen(
                        source = fakeSource(
                            seasons = listOf(vostSeason1),
                            details = AnimeDetails(),
                        ),
                        anime = AnimeSearchResult(
                            title = "Minimal Anime",
                            url = "https://anime-sama.to/catalogue/minimal/",
                        ),
                        onBack = {},
                        onOpenEpisodes = {},
                        onResume = {},
                    )
                }
            }
        }

        composeRule.waitUntil(5000) {
            composeRule.onAllNodesWithTag("show_primary_cta").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Saison 1 VO").assertIsDisplayed()
        composeRule.onAllNodesWithTag("show_synopsis_toggle").assertCountEquals(0)
        composeRule.onAllNodesWithTag("show_related_card_0").assertCountEquals(0)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w900dp-h600dp")
    fun splitPane_and_episodesPane_highlightActiveResumeEpisode() {
        WatchHistory.record(
            context,
            WatchHistoryEntry(
                animeTitle = sampleAnime.title,
                animeUrl = sampleAnime.url,
                posterUrl = null,
                seasonName = vfSeason1.name,
                seasonUrl = vfSeason1.url,
                episodeNumber = 2,
                episodeLabel = "Épisode 2",
                lienIds = emptyList(),
                jeton = "",
                watchedAt = 1000L,
                positionMs = 125_000L,
                durationMs = 1_440_000L,
            ),
        )

        composeRule.setContent {
            NoVaYTheme {
                ShowScreen(
                    source = fakeSource(),
                    anime = sampleAnime,
                    onBack = {},
                    onOpenEpisodes = {},
                    onResume = {},
                )
            }
        }

        composeRule.waitUntil(5000) {
            composeRule.onAllNodesWithTag("episode_active_resume_badge", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeRule.onNodeWithTag("episode_active_resume_badge", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Reprise à 2:05").assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithText("Reprendre Ep. 2").fetchSemanticsNodes().isNotEmpty(),
        )
    }
}
