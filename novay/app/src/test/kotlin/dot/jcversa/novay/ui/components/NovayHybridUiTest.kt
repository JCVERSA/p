package dot.jcversa.novay.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import dot.jcversa.novay.downloads.Downloads
import dot.jcversa.novay.downloads.EpisodeDownloadMeta
import dot.jcversa.novay.downloads.SpeedMeter
import dot.jcversa.novay.ui.screens.DownloadsDashboardCard
import dot.jcversa.novay.ui.screens.MoreScreen
import dot.jcversa.novay.ui.theme.NoVaYTheme
import dot.jcversa.novay.ui.theme.NovayThemePreset
import dot.jcversa.novay.ui.theme.NovayThemeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
class NovayHybridUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun moreScreen_displaysAllFiveThemePresetsAndThreeMotionOptions() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        NovayThemeState.setPreset(context, NovayThemePreset.NOIR_CRIMSON)
        NovayMotionState.setPreference(context, MotionPreference.SYSTEM)

        composeRule.setContent {
            val toast = rememberNovayToastController()
            CompositionLocalProvider(LocalNovayToast provides toast) {
                NoVaYTheme {
                    SlateMotionProvider {
                        MoreScreen(onOpenDownloads = {})
                    }
                }
            }
        }

        val presetsRow = composeRule.onNodeWithTag("more_theme_presets_row")
        NovayThemePreset.entries.forEach { preset ->
            presetsRow.performScrollToNode(hasTestTag("more_theme_preset_${preset.id}"))
            composeRule.onNodeWithTag("more_theme_preset_${preset.id}")
                .assertIsDisplayed()
        }

        presetsRow.performScrollToNode(hasTestTag("more_theme_preset_${NovayThemePreset.WARM_AMBER_GOLD.id}"))
        composeRule.onNodeWithTag("more_theme_preset_${NovayThemePreset.WARM_AMBER_GOLD.id}")
            .performClick()
        assertEquals(NovayThemePreset.WARM_AMBER_GOLD, NovayThemeState.preset)

        composeRule.onNodeWithTag("more_motion_${MotionPreference.REDUCED.id}")
            .performScrollTo()
            .performClick()
        assertEquals(MotionPreference.REDUCED, NovayMotionState.preference)

        // Restore defaults
        NovayThemeState.setPreset(context, NovayThemePreset.NOIR_CRIMSON)
        NovayMotionState.setPreference(context, MotionPreference.SYSTEM)
    }

    @Test
    fun offlineStateView_and_noResultsStateView_triggerFrenchActions() {
        var retried = false
        var openedDownloads = false
        var pickedSuggestion: String? = null
        var resetClicked = false

        composeRule.setContent {
            NoVaYTheme {
                SlateMotionProvider {
                    androidx.compose.foundation.layout.Column {
                        androidx.compose.foundation.layout.Box(
                            modifier = androidx.compose.ui.Modifier.weight(1f),
                        ) {
                            OfflineStateView(
                                onRetry = { retried = true },
                                onOpenDownloads = { openedDownloads = true },
                            )
                        }
                        androidx.compose.foundation.layout.Box(
                            modifier = androidx.compose.ui.Modifier.weight(1f),
                        ) {
                            NoResultsStateView(
                                title = "Aucun résultat",
                                subtitle = "Rien trouvé pour « xyz »",
                                suggestions = listOf("Frieren", "Action"),
                                onSelectSuggestion = { pickedSuggestion = it },
                                resetLabel = "Réinitialiser",
                                onReset = { resetClicked = true },
                            )
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithTag("offline_state_view").assertIsDisplayed()
        composeRule.onNodeWithTag("offline_retry_button").performClick()
        composeRule.onNodeWithTag("offline_downloads_button").performClick()
        assertTrue(retried)
        assertTrue(openedDownloads)

        composeRule.onNodeWithTag("no_results_state_view").assertIsDisplayed()
        composeRule.onNodeWithTag("no_results_suggestion_frieren").performClick()
        composeRule.onNodeWithTag("no_results_reset_button").performClick()
        assertEquals("Frieren", pickedSuggestion)
        assertTrue(resetClicked)
    }

    @Test
    fun partialLoadingBanner_and_downloadsDashboardCard_renderExpectedMetrics() {
        val sampleDownloads = listOf(
            Downloads.DownloadInfo(
                id = "1",
                state = 4, // STATE_COMPLETED
                percent = 100f,
                meta = EpisodeDownloadMeta(
                    animeTitle = "Frieren",
                    seasonName = "Saison 1",
                    seasonUrl = "https://a.to/s1",
                    episodeNumber = 1,
                    episodeLabel = "Épisode 1",
                    streamUrl = "https://cdn/ep1.mp4",
                    isHls = false,
                    headers = emptyMap(),
                ),
                bytesDownloaded = 250L * 1024L * 1024L,
                totalBytes = 250L * 1024L * 1024L,
            ),
            Downloads.DownloadInfo(
                id = "2",
                state = 2, // STATE_RUNNING
                percent = 45f,
                meta = EpisodeDownloadMeta(
                    animeTitle = "Frieren",
                    seasonName = "Saison 1",
                    seasonUrl = "https://a.to/s1",
                    episodeNumber = 2,
                    episodeLabel = "Épisode 2",
                    streamUrl = "https://cdn/ep2.mp4",
                    isHls = false,
                    headers = emptyMap(),
                ),
                bytesDownloaded = 90L * 1024L * 1024L,
                totalBytes = 200L * 1024L * 1024L,
            ),
        )
        val speeds = mapOf(
            "2" to SpeedMeter.Reading(bytesPerSecond = 2_400_000L, etaMillis = 12_000L),
        )

        composeRule.setContent {
            NoVaYTheme {
                SlateMotionProvider {
                    androidx.compose.foundation.layout.Column {
                        PartialLoadingBanner(
                            visible = true,
                            message = "Vérification des nouveautés…",
                        )
                        DownloadsDashboardCard(
                            infos = sampleDownloads,
                            speeds = speeds,
                            paused = false,
                            wifiOnly = false,
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("partial_loading_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("downloads_dashboard_card").assertIsDisplayed()
        composeRule.onNodeWithText("1 / 2").assertIsDisplayed()
    }
}
