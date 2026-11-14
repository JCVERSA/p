package dot.jcversa.novay.ui.player

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import dot.jcversa.novay.ui.theme.NoVaYTheme
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Vérifie sous Robolectric que le mode plein écran paysage applique réellement un cadrage Fit/Contain
 * centré sans jamais rogner ni étirer la vidéo pour les ratios 16:9, 4:3, portrait 9:16 et cinémascope,
 * et que les modes portrait intégré et mini-lecteur flottant restent conformes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FullscreenVideoFitUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `fullscreen_video_fitted_box centre et encadre sans rognage 16_9, 4_3 et portrait 9_16 en paysage`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        lateinit var manager: PlayerStateManager
        var fittedPixelSize = IntSize.Zero
        var viewportPixelSize = IntSize.Zero

        composeRule.setContent {
            val scope = rememberCoroutineScope()
            manager = rememberManager(context, scope)
            NoVaYTheme {
                Box(modifier = Modifier.size(width = 800.dp, height = 360.dp)) {
                    FullscreenPlayerOverlay(
                        playerManager = manager,
                        videoSurfaceSlot = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Red)
                                    .testTag("test_video_surface")
                                    .onGloballyPositioned { coords ->
                                        fittedPixelSize = coords.size
                                        viewportPixelSize = coords.parentLayoutCoordinates?.parentLayoutCoordinates?.size ?: IntSize.Zero
                                    },
                            )
                        },
                    )
                }
            }
        }

        val testRatios = listOf(16f / 9f, 4f / 3f, 9f / 16f, 2.39f)
        for (expectedRatio in testRatios) {
            composeRule.runOnIdle {
                manager.setVideoAspectRatioForTest(expectedRatio)
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("fullscreen_video_viewport").assertIsDisplayed()
            composeRule.onNodeWithTag("fullscreen_video_fitted_box").assertIsDisplayed()

            val viewportBounds = composeRule.onNodeWithTag("fullscreen_video_viewport")
                .fetchSemanticsNode().boundsInRoot
            val fittedBounds = composeRule.onNodeWithTag("fullscreen_video_fitted_box")
                .fetchSemanticsNode().boundsInRoot

            assertTrue("Le flux video ne doit jamais depasser la largeur du plein ecran", fittedBounds.width <= viewportBounds.width + 1f)
            assertTrue("Le flux video ne doit jamais depasser la hauteur du plein ecran", fittedBounds.height <= viewportBounds.height + 1f)
            assertTrue("Largeur/hauteur attendues positives", fittedBounds.width > 0f && fittedBounds.height > 0f)

            val actualRatio = fittedBounds.width / fittedBounds.height
            assertEquals("Le ratio original $expectedRatio doit etre conserve", expectedRatio, actualRatio, 0.02f)

            // Doit etre centre horizontalement et verticalement dans le viewport plein ecran
            val viewportCenterX = (viewportBounds.left + viewportBounds.right) / 2f
            val fittedCenterX = (fittedBounds.left + fittedBounds.right) / 2f
            val viewportCenterY = (viewportBounds.top + viewportBounds.bottom) / 2f
            val fittedCenterY = (fittedBounds.top + fittedBounds.bottom) / 2f
            assertTrue("Centrage horizontal requis", abs(viewportCenterX - fittedCenterX) <= 2f)
            assertTrue("Centrage vertical requis", abs(viewportCenterY - fittedCenterY) <= 2f)
        }
    }

    @Test
    fun `embedded et floating mini player conservent le ratio video sans recadrage`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        lateinit var manager: PlayerStateManager

        composeRule.setContent {
            val scope = rememberCoroutineScope()
            manager = rememberManager(context, scope)
            NoVaYTheme {
                Box(modifier = Modifier.size(width = 400.dp, height = 800.dp)) {
                    EmbeddedVideoPlayer(
                        playerManager = manager,
                        showSurface = true,
                        videoSurfaceSlot = { Box(Modifier.fillMaxSize().background(Color.Blue)) },
                        onRetry = {},
                    )
                    FloatingMiniPlayer(
                        playerManager = manager,
                        videoSurfaceSlot = { Box(Modifier.fillMaxSize().background(Color.Green)) },
                        bottomDockClearance = 60.dp,
                        leftRailClearance = 0.dp,
                        onExpandToWatch = {},
                    )
                }
            }
        }

        composeRule.runOnIdle { manager.setVideoAspectRatioForTest(4f / 3f) }
        composeRule.waitForIdle()

        val embeddedBounds = composeRule.onNodeWithTag("embedded_video_fitted_box")
            .fetchSemanticsNode().boundsInRoot
        assertEquals(4f / 3f, embeddedBounds.width / embeddedBounds.height, 0.03f)

        composeRule.onNodeWithTag("floating_mini_player").assertIsDisplayed()
        val miniBounds = composeRule.onNodeWithTag("floating_mini_player")
            .fetchSemanticsNode().boundsInRoot
        assertEquals(4f / 3f, miniBounds.width / miniBounds.height, 0.03f)
    }

    @androidx.compose.runtime.Composable
    private fun rememberManager(
        context: Context,
        scope: kotlinx.coroutines.CoroutineScope,
    ): PlayerStateManager = androidx.compose.runtime.remember {
        PlayerStateManager(context, scope)
    }
}
