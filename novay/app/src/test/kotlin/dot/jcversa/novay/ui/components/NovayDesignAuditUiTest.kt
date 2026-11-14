package dot.jcversa.novay.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsToggleable
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import dot.jcversa.novay.R
import dot.jcversa.novay.ui.screens.DownloadsWifiOnlyCard
import dot.jcversa.novay.ui.screens.MoreScreen
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
}
