package dot.jcversa.novay.performance

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.rememberHazeState
import dot.jcversa.novay.R
import dot.jcversa.novay.ui.components.AnimeCompactGridItem
import dot.jcversa.novay.ui.components.NovayLottieIcon
import dot.jcversa.novay.ui.components.SharedShimmerScope
import dot.jcversa.novay.ui.components.ShimmerBox
import dot.jcversa.novay.ui.components.fallbackScrimColor
import dot.jcversa.novay.ui.components.novayCinemaGlass
import dot.jcversa.novay.ui.components.novayHazeSource
import dot.jcversa.novay.ui.components.rememberShimmerProgress
import dot.jcversa.novay.ui.screens.MoreScreen
import dot.jcversa.novay.ui.theme.NoVaYTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class NovayPerformanceUiAndCompatTest {

    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun tearDown() {
        NovayPerformanceState.resetForTest()
    }

    // ── Scénario 2 : repli CinemaGlass sans flou GPU sur appareil modeste ─

    @Test
    fun `scenario 2 - CinemaGlass uses elevated translucent scrim fallback when allowLiveHazeBlur is disabled`() {
        val baseTint = Color(0xCC0D0E14)
        val fallback = fallbackScrimColor(baseTint)
        assertTrue(
            "Le voile de repli doit renforcer l'opacité (${fallback.alpha} > ${baseTint.alpha}) pour préserver la lisibilité sans flou",
            fallback.alpha > baseTint.alpha,
        )

        val lowProfile = PerformanceClassifier.resolve(
            DeviceCapabilities(totalRamMb = 2048, isLowRamDevice = true, cpuCores = 4, sdkInt = 24),
            PerformanceMode.SAVER,
        )
        composeRule.setContent {
            NoVaYTheme {
                CompositionLocalProvider(LocalPerformanceProfile provides lowProfile) {
                    val haze = rememberHazeState()
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .novayHazeSource(haze),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .novayCinemaGlass(hazeState = haze, showBorder = true)
                                .testTag("cinema_glass_fallback"),
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("cinema_glass_fallback").assertIsDisplayed()
    }

    // ── Scénario 5 : Lottie statique sur profil économique ────────────────

    @Test
    fun `scenario 5 - NovayLottieIcon renders cleanly in static low-power mode without ticker`() {
        val saverProfile = PerformanceClassifier.resolve(
            DeviceCapabilities(totalRamMb = 2048, isLowRamDevice = true, cpuCores = 4, sdkInt = 24),
            PerformanceMode.SAVER,
        )
        composeRule.setContent {
            NoVaYTheme {
                CompositionLocalProvider(LocalPerformanceProfile provides saverProfile) {
                    NovayLottieIcon(
                        res = R.raw.lottie_download,
                        contentDescription = "Téléchargement statique",
                        loop = true,
                        modifier = Modifier.testTag("lottie_saver_icon"),
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("lottie_saver_icon").assertIsDisplayed()
    }

    // ── Scénario 6 : horloge Shimmer partagée et mode statique bas niveau ─

    @Test
    fun `scenario 6 - SharedShimmerScope shares single ticker provider across loading tiles`() {
        val highProfile = PerformanceClassifier.resolve(
            DeviceCapabilities(totalRamMb = 8192, isLowRamDevice = false, cpuCores = 8, sdkInt = 35),
            PerformanceMode.HIGH,
        )
        val capturedProviders = mutableListOf<() -> Float>()

        composeRule.setContent {
            NoVaYTheme {
                CompositionLocalProvider(LocalPerformanceProfile provides highProfile) {
                    SharedShimmerScope {
                        val p1 = rememberShimmerProgress()
                        val p2 = rememberShimmerProgress()
                        SideEffect {
                            capturedProviders.clear()
                            capturedProviders.add(p1)
                            capturedProviders.add(p2)
                        }
                        Column {
                            ShimmerBox(modifier = Modifier.size(48.dp), progressProvider = p1)
                            ShimmerBox(modifier = Modifier.size(48.dp), progressProvider = p2)
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(2, capturedProviders.size)
        assertSame(
            "Les tuiles d'un même SharedShimmerScope doivent partager l'instance de ticker",
            capturedProviders[0],
            capturedProviders[1],
        )
        assertTrue(capturedProviders[0]() >= 0f)
    }

    @Test
    fun `scenario 6b - SharedShimmerScope switches to static -1f provider on LOW tier`() {
        val lowProfile = PerformanceClassifier.resolve(
            DeviceCapabilities(totalRamMb = 2048, isLowRamDevice = true, cpuCores = 4, sdkInt = 24),
            PerformanceMode.SAVER,
        )
        var lowProgressValue = 0f
        composeRule.setContent {
            NoVaYTheme {
                CompositionLocalProvider(LocalPerformanceProfile provides lowProfile) {
                    SharedShimmerScope {
                        val p = rememberShimmerProgress()
                        lowProgressValue = p()
                    }
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(-1f, lowProgressValue, 0.001f)
    }

    // ── Scénario 8 : stabilité Compose & comptage des recompositions ───────

    @Test
    fun `scenario 8 - AnimeCompactGridItem skips recomposition when unrelated parent state changes`() {
        val cardCompositions = AtomicInteger(0)
        val parentCompositions = AtomicInteger(0)
        composeRule.setContent {
            NoVaYTheme {
                var unrelatedCounter by remember { mutableIntStateOf(0) }
                val onOpen = remember { {} }
                val onComposed = remember {
                    {
                        cardCompositions.incrementAndGet()
                        Unit
                    }
                }
                parentCompositions.incrementAndGet()
                Column {
                    Text("Parent $unrelatedCounter")
                    Button(
                        onClick = { unrelatedCounter++ },
                        modifier = Modifier.testTag("bump_parent"),
                    ) {
                        Text("Incrémenter")
                    }
                    StableProbeWrapper(
                        onComposed = onComposed,
                        onOpen = onOpen,
                    )
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(1, parentCompositions.get())
        assertEquals(1, cardCompositions.get())

        repeat(3) {
            composeRule.onNodeWithTag("bump_parent").performClick()
            composeRule.waitForIdle()
        }
        assertTrue("Le parent doit avoir recomposé au moins 4 fois", parentCompositions.get() >= 4)
        assertEquals(
            "Une carte stable ne doit pas être recomposée quand l'état parent non lié change",
            1,
            cardCompositions.get(),
        )
    }

    @androidx.compose.runtime.Composable
    private fun StableProbeWrapper(
        onComposed: () -> Unit,
        onOpen: () -> Unit,
    ) {
        SideEffect { onComposed() }
        AnimeCompactGridItem(
            title = "Solo Leveling",
            posterUrl = null,
            animeUrl = "https://anime-sama.to/catalogue/solo-leveling/",
            sharedTransitionEnabled = false,
            onClick = onOpen,
            modifier = Modifier.testTag("probe_grid_card"),
        )
    }

    // ── Scénario 10 : parité Android 7.0 (API 24) & sélection dans Plus ───

    @Test
    @Config(sdk = [24])
    fun `scenario 10 - Android 7 API 24 compatibility renders MoreScreen performance selector and scrim safely`() {
        NovayPerformanceState.resetForTest(
            DeviceCapabilities(totalRamMb = 2048, isLowRamDevice = true, cpuCores = 4, sdkInt = 24),
            PerformanceMode.AUTO,
        )

        composeRule.setContent {
            NoVaYTheme {
                PerformanceProfileProvider {
                    MoreScreen(onOpenDownloads = {})
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("more_performance_saver").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(PerformanceMode.SAVER, NovayPerformanceState.mode)
        assertEquals(PerformanceTier.LOW, NovayPerformanceState.currentProfile.tier)
        assertNotNull(NovayPerformanceState.currentProfile.effectiveReason)
        composeRule.onNodeWithTag("more_performance_active_reason").assertIsDisplayed()

        composeRule.onNodeWithTag("more_performance_balanced").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(PerformanceMode.BALANCED, NovayPerformanceState.mode)
    }

    @Test
    @Config(sdk = [36])
    fun `scenario 10b - Android 16 API 36 flagship toggles performance selector cleanly`() {
        NovayPerformanceState.resetForTest(
            DeviceCapabilities(totalRamMb = 12288, isLowRamDevice = false, cpuCores = 8, sdkInt = 36),
            PerformanceMode.AUTO,
        )
        composeRule.setContent {
            NoVaYTheme {
                PerformanceProfileProvider {
                    MoreScreen(onOpenDownloads = {})
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("more_performance_high").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(PerformanceMode.HIGH, NovayPerformanceState.mode)
        assertEquals(PerformanceTier.HIGH, NovayPerformanceState.currentProfile.tier)
        assertTrue(NovayPerformanceState.currentProfile.allowLiveHazeBlur)
    }
}
