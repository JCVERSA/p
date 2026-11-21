package dot.jcversa.novay.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsToggleable
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.core.app.ApplicationProvider
import dot.jcversa.novay.R
import dot.jcversa.novay.data.AnimeEpisode
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.AnimeSeason
import dot.jcversa.novay.data.AnimeSource
import dot.jcversa.novay.data.CatalogCard
import dot.jcversa.novay.data.CatalogHero
import dot.jcversa.novay.data.CatalogHome
import dot.jcversa.novay.data.Library
import dot.jcversa.novay.data.LibraryEntry
import dot.jcversa.novay.data.ResolvedStream
import dot.jcversa.novay.ui.screens.BrowseScreen
import dot.jcversa.novay.ui.screens.DownloadsWifiOnlyCard
import dot.jcversa.novay.ui.screens.LibraryScreen
import dot.jcversa.novay.ui.screens.MoreScreen
import dot.jcversa.novay.ui.screens.UpdatesScreen
import dot.jcversa.novay.ui.theme.NoVaYTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
class NovayDesignAuditUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun animeComfortableGridItem_preservesIndependentContinueButtonForTalkBack() {
        var cardClicks = 0
        var continueClicks = 0

        composeRule.setContent {
            NoVaYTheme {
                SlateMotionProvider {
                    AnimeComfortableGridItem(
                        title = "Frieren",
                        posterUrl = null,
                        animeUrl = "https://anime-sama.to/catalogue/frieren/",
                        sharedTransitionEnabled = false,
                        onClick = { cardClicks++ },
                        onClickContinue = { continueClicks++ },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("continue_reading_button")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        assertTrue("Continue button click should fire", continueClicks == 1)
        assertTrue("Parent card click should remain separate", cardClicks == 0)
    }

    @Test
    fun errorView_rendersContextualTitleAndRetryButton() {
        var retries = 0

        composeRule.setContent {
            NoVaYTheme {
                SlateMotionProvider {
                    ErrorView(
                        message = "Délai dépassé",
                        onRetry = { retries++ },
                        title = "Catalogue inaccessible",
                    )
                }
            }
        }

        composeRule.onNodeWithText("Catalogue inaccessible").assertIsDisplayed()
        composeRule.onNodeWithText("Délai dépassé").assertIsDisplayed()
        composeRule.onNodeWithTag("error_retry_button").performClick()

        assertTrue(retries == 1)
    }

    @Test
    fun moreScreen_switchesExposeUnifiedRoleSwitchSemantics() {
        composeRule.setContent {
            NoVaYTheme {
                SlateMotionProvider {
                    MoreScreen(onOpenDownloads = {})
                }
            }
        }

        composeRule.waitForIdle()
        val downloadsRow = composeRule.onNodeWithTag("more_open_downloads")
            .assertIsDisplayed()
        assertTrue(
            "more_open_downloads row should stay compact",
            downloadsRow.getUnclippedBoundsInRoot().height <= 120.dp,
        )

        val amoledRow = composeRule.onNodeWithTag("more_amoled_switch")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsToggleable()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        assertTrue(
            "more_amoled_switch row should stay compact",
            amoledRow.getUnclippedBoundsInRoot().height <= 120.dp,
        )

        val wifiRow = composeRule.onNodeWithTag("more_wifi_switch")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsToggleable()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        assertTrue(
            "more_wifi_switch row should stay compact",
            wifiRow.getUnclippedBoundsInRoot().height <= 120.dp,
        )
    }

    @Test
    fun downloadsScreen_wifiSwitchExposesRoleSwitchSemantics() {
        composeRule.setContent {
            NoVaYTheme {
                SlateMotionProvider {
                    var wifiOnly by remember { mutableStateOf(false) }
                    DownloadsWifiOnlyCard(
                        wifiOnly = wifiOnly,
                        onToggle = { wifiOnly = it },
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("downloads_wifi_switch")
            .assertIsDisplayed()
            .assertIsToggleable()
            .assertIsOff()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .performClick()
            .assertIsOn()
    }

    @Test
    fun reduceMotion_rendersStaticSpinnerAndLottieWithoutCrash() {
        composeRule.setContent {
            NoVaYTheme {
                CompositionLocalProvider(LocalSlateMotion provides SlateMotion(reduceMotion = true)) {
                    SwiftSlateSpinner()
                    NovayLottieIcon(
                        res = R.raw.lottie_playpause,
                        contentDescription = "Lecture",
                        loop = true,
                    )
                }
            }
        }

        composeRule.waitForIdle()
    }

    @Test
    fun libraryEmptyState_displaysExploreCatalogCtaAndCompactBrandSeal() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        Library.save(ctx, emptyList())
        var exploreClicks = 0

        composeRule.setContent {
            NoVaYTheme {
                SlateMotionProvider {
                    LibraryScreen(
                        onOpenShow = {},
                        onResume = {},
                        showBrandSeal = true,
                        onExploreCatalog = { exploreClicks++ },
                    )
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("library_empty_browse_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("library_brand_seal").assertIsDisplayed()
        composeRule.onNodeWithTag("library_empty_browse_button")
            .assertIsDisplayed()
            .performClick()

        assertTrue(exploreClicks == 1)
    }

    @Test
    fun updatesScreen_emptyAndUpToDateStatesExposePrimaryActions() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val emptySource = object : AnimeSource {
            override val name = "test-source"
            override suspend fun search(query: String) = emptyList<AnimeSearchResult>()
            override suspend fun seasons(anime: AnimeSearchResult) = emptyList<AnimeSeason>()
            override suspend fun episodes(season: AnimeSeason) = emptyList<AnimeEpisode>()
            override suspend fun stream(episode: AnimeEpisode): ResolvedStream = error("unused")
        }

        Library.save(ctx, emptyList())
        var exploreClicks = 0

        composeRule.setContent {
            NoVaYTheme {
                SlateMotionProvider {
                    UpdatesScreen(
                        source = emptySource,
                        onOpenShow = {},
                        onResume = {},
                        onExploreCatalog = { exploreClicks++ },
                    )
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("updates_empty_browse_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("updates_empty_browse_button").performClick()
        assertTrue(exploreClicks == 1)
    }

    @Test
    fun updatesScreen_upToDateStateExposesRefreshCta() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        Library.save(
            ctx,
            listOf(
                LibraryEntry(
                    title = "Frieren",
                    url = "https://anime-sama.to/catalogue/frieren/",
                    addedAt = 100L,
                ),
            ),
        )
        var seasonCalls = 0
        val countingSource = object : AnimeSource {
            override val name = "test-source"
            override suspend fun search(query: String) = emptyList<AnimeSearchResult>()
            override suspend fun seasons(anime: AnimeSearchResult): List<AnimeSeason> {
                seasonCalls++
                return emptyList()
            }
            override suspend fun episodes(season: AnimeSeason) = emptyList<AnimeEpisode>()
            override suspend fun stream(episode: AnimeEpisode): ResolvedStream = error("unused")
        }

        composeRule.setContent {
            NoVaYTheme {
                SlateMotionProvider {
                    UpdatesScreen(
                        source = countingSource,
                        onOpenShow = {},
                        onResume = {},
                        onExploreCatalog = {},
                    )
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("updates_empty_refresh_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("updates_empty_refresh_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { seasonCalls > 0 }
        assertTrue(seasonCalls == 1)

        Library.save(ctx, emptyList())
    }

    @Test
    fun browseScreen_unavailableHomeRetriesAndRendersCatalogCardMetadata() {
        var homeAttempts = 0
        val recoveringSource = object : AnimeSource {
            override val name = "test-source"
            override suspend fun home(): CatalogHome {
                homeAttempts++
                return if (homeAttempts == 1) {
                    CatalogHome(heroes = emptyList(), cards = emptyList())
                } else {
                    CatalogHome(
                        heroes = listOf(
                            CatalogHero(
                                title = "Frieren",
                                seasonUrl = "https://anime-sama.to/catalogue/frieren/saison1/vostfr/",
                                language = "VOSTFR",
                            ),
                        ),
                        cards = listOf(
                            CatalogCard(
                                title = "Solo Leveling",
                                animeUrl = "https://anime-sama.to/catalogue/solo-leveling/",
                                seasonUrl = "https://anime-sama.to/catalogue/solo-leveling/saison1/vf/",
                                timeLabel = "18h40",
                                language = "VF",
                            ),
                        ),
                    )
                }
            }
            override suspend fun search(query: String) = emptyList<AnimeSearchResult>()
            override suspend fun seasons(anime: AnimeSearchResult) = emptyList<AnimeSeason>()
            override suspend fun episodes(season: AnimeSeason) = emptyList<AnimeEpisode>()
            override suspend fun stream(episode: AnimeEpisode): ResolvedStream = error("unused")
        }

        composeRule.setContent {
            NoVaYTheme {
                SlateMotionProvider {
                    BrowseScreen(
                        source = recoveringSource,
                        onOpenShow = {},
                        showBrandSeal = true,
                    )
                }
            }
        }

        composeRule.onNodeWithTag("browse_brand_seal").assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("browse_home_retry_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("browse_home_retry_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("18h40").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("VF")[0].assertIsDisplayed()
        composeRule.onAllNodesWithText("18h40")[0].assertIsDisplayed()
    }

    @Test
    fun novayBrandSealAndSectionHeader_exposeAccessibilityAndLayout() {
        composeRule.setContent {
            NoVaYTheme {
                SlateMotionProvider {
                    NovayBrandSeal(accessibleLabel = "Logo NoVaY")
                    NovaySectionHeader(
                        title = "Section test",
                        trailing = { Text("3/5") },
                    )
                }
            }
        }

        composeRule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Logo NoVaY")))
            .assertIsDisplayed()
            .assertContentDescriptionEquals("Logo NoVaY")
        composeRule.onNodeWithText("Section test").assertIsDisplayed()
        composeRule.onNodeWithText("3/5").assertIsDisplayed()
    }
}
