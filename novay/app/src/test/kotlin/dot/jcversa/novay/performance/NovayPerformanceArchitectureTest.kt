package dot.jcversa.novay.performance

import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.media3.exoplayer.analytics.PlayerId
import dot.jcversa.novay.NovayImagePipeline
import dot.jcversa.novay.R
import dot.jcversa.novay.ui.player.PlayerStateManager
import dot.jcversa.novay.ui.theme.PosterPaletteExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NovayPerformanceArchitectureTest {

    private lateinit var context: Context

    // PNG minimal 1x1 pour mesurer les hits/evictions réels du MemoryCache Coil
    private val validPngBytes: ByteArray = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
        0x08, 0x02, 0x00, 0x00, 0x00, 0x90.toByte(), 0x77, 0x53,
        0xDE.toByte(), 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41,
        0x54, 0x08, 0xD7.toByte(), 0x63, 0xF8.toByte(), 0xCF.toByte(), 0xC0.toByte(), 0x00,
        0x00, 0x03, 0x01, 0x01, 0x00, 0x18, 0xDD.toByte(), 0x8D.toByte(),
        0xB0.toByte(), 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E,
        0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte(),
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        NovayPerformanceState.resetForTest()
        PosterPaletteExtractor.trimToSize(0)
    }

    @After
    fun tearDown() {
        NovayPerformanceState.resetForTest()
        PosterPaletteExtractor.trimToSize(0)
    }

    // ── Scénario 1 : classification matérielle & adaptative multi-niveaux ─

    @Test
    fun `scenario 1 - classifies 2GB API 24 low-end, 4GB mid-range, and 12GB flagship devices accurately`() {
        val lowEnd = DeviceCapabilities(
            totalRamMb = 2048,
            isLowRamDevice = true,
            cpuCores = 4,
            sdkInt = 24,
        )
        val lowProfile = PerformanceClassifier.resolve(lowEnd, PerformanceMode.AUTO)
        assertEquals(PerformanceTier.LOW, lowProfile.tier)
        assertFalse(lowProfile.allowLiveHazeBlur)
        assertFalse(lowProfile.allowAtmosphericBackdropBlur)
        assertFalse(lowProfile.allowCardTextShadowBlur)
        assertFalse(lowProfile.allowAnimatedShimmer)
        assertTrue(lowProfile.preferRgb565Posters)
        assertEquals(0.12, lowProfile.imageMemoryCachePercent, 0.001)
        assertEquals(0, lowProfile.coilCrossfadeMs)
        assertEquals(ExoPlayerBufferProfile.LOW_RAM, lowProfile.exoBufferProfile)

        val midRange = DeviceCapabilities(
            totalRamMb = 4096,
            isLowRamDevice = false,
            cpuCores = 8,
            sdkInt = 29,
        )
        val midProfile = PerformanceClassifier.resolve(midRange, PerformanceMode.AUTO)
        assertEquals(PerformanceTier.MID, midProfile.tier)
        // Android 10 (< API 31) n'a pas RenderEffect : Haze live blur coupé au profit du voile translucide
        assertFalse(midProfile.allowLiveHazeBlur)
        assertFalse(midProfile.allowAtmosphericBackdropBlur)
        assertTrue(midProfile.allowAnimatedShimmer)
        assertEquals(0.18, midProfile.imageMemoryCachePercent, 0.001)
        assertEquals(ExoPlayerBufferProfile.BALANCED, midProfile.exoBufferProfile)

        val flagship = DeviceCapabilities(
            totalRamMb = 12288,
            isLowRamDevice = false,
            cpuCores = 8,
            sdkInt = 35,
        )
        val highProfile = PerformanceClassifier.resolve(flagship, PerformanceMode.AUTO)
        assertEquals(PerformanceTier.HIGH, highProfile.tier)
        assertTrue(highProfile.allowLiveHazeBlur)
        assertTrue(highProfile.allowAtmosphericBackdropBlur)
        assertTrue(highProfile.allowCardTextShadowBlur)
        assertTrue(highProfile.allowAnimatedShimmer)
        assertFalse(highProfile.preferRgb565Posters)
        assertEquals(0.24, highProfile.imageMemoryCachePercent, 0.001)
        assertEquals(ExoPlayerBufferProfile.FLAGSHIP, highProfile.exoBufferProfile)
    }

    @Test
    fun `scenario 1b - thermal throttling, power save, memory pressure, and SAVER override step down blur cleanly`() {
        val flagship = DeviceCapabilities(
            totalRamMb = 8192,
            isLowRamDevice = false,
            cpuCores = 8,
            sdkInt = 34,
        )

        val powerSaveProfile = PerformanceClassifier.resolve(
            flagship.copy(isPowerSaveMode = true),
            PerformanceMode.AUTO,
        )
        assertEquals(PerformanceTier.LOW, powerSaveProfile.tier)
        assertFalse(powerSaveProfile.allowLiveHazeBlur)
        assertFalse(powerSaveProfile.allowAtmosphericBackdropBlur)

        val thermalProfile = PerformanceClassifier.resolve(
            flagship.copy(isThermalThrottled = true),
            PerformanceMode.AUTO,
        )
        assertEquals(PerformanceTier.LOW, thermalProfile.tier)
        assertFalse(thermalProfile.allowLiveHazeBlur)

        val memPressureProfile = PerformanceClassifier.resolve(
            flagship.copy(memoryPressureLevel = ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW),
            PerformanceMode.AUTO,
        )
        assertEquals(PerformanceTier.LOW, memPressureProfile.tier)

        val userSaverProfile = PerformanceClassifier.resolve(flagship, PerformanceMode.SAVER)
        assertEquals(PerformanceTier.LOW, userSaverProfile.tier)
        assertFalse(userSaverProfile.allowLiveHazeBlur)
        assertTrue(userSaverProfile.preferRgb565Posters)
    }

    // ── Scénario 3 : pipeline images, cache RAM & purge onTrimMemory ──────

    @Test
    fun `scenario 3 - image loader adapts memory cache size across tiers and responds to onTrimMemory eviction`() {
        val lowProfile = PerformanceClassifier.resolve(
            DeviceCapabilities(totalRamMb = 2048, isLowRamDevice = true, cpuCores = 4, sdkInt = 26),
            PerformanceMode.AUTO,
        )
        val highProfile = PerformanceClassifier.resolve(
            DeviceCapabilities(totalRamMb = 8192, isLowRamDevice = false, cpuCores = 8, sdkInt = 35),
            PerformanceMode.AUTO,
        )

        val lowLoader = NovayImagePipeline.buildImageLoader(context, profile = lowProfile)
        val highLoader = NovayImagePipeline.buildImageLoader(context, profile = highProfile)

        val lowMaxBytes = lowLoader.memoryCache!!.maxSize
        val highMaxBytes = highLoader.memoryCache!!.maxSize
        assertTrue(
            "Le cache RAM bas de gamme ($lowMaxBytes) doit être strictement inférieur au cache flagship ($highMaxBytes)",
            lowMaxBytes < highMaxBytes,
        )

        val hits = AtomicInteger(0)
        val client = NovayImagePipeline.createHttpClient(
            sleepMs = {},
            extraInterceptor = { chain ->
                hits.incrementAndGet()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(validPngBytes.toResponseBody("image/png".toMediaType()))
                    .build()
            },
        )
        val testLoader = NovayImagePipeline.buildImageLoader(context, httpClient = client, profile = lowProfile)
        val url = "https://cdn.example.invalid/poster-perf-check.png"
        val request = ImageRequest.Builder(context)
            .data(url)
            .memoryCacheKey(url)
            .size(160, 240)
            .allowRgb565(lowProfile.preferRgb565Posters)
            .build()

        runBlocking {
            val main = shadowOf(Looper.getMainLooper())
            val d1 = async(Dispatchers.IO) { testLoader.execute(request) }
            while (!d1.isCompleted) {
                main.idle()
                if (!d1.isCompleted) Thread.sleep(4L)
            }
            assertTrue(d1.await() is SuccessResult)
            assertTrue("Le cache mémoire doit retenir l'affiche après décodage", testLoader.memoryCache!!.size > 0)

            // Simulation d'une alerte mémoire système : éviction immédiate
            testLoader.memoryCache!!.trimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
            assertEquals(0, testLoader.memoryCache!!.size)
        }
        assertEquals(1, hits.get())
    }

    // ── Scénario 4 : PosterPaletteExtractor en lecture groupée getPixels ──

    @Test
    fun `scenario 4 - poster palette extractor samples bulk pixels fast and respects cache trimming`() {
        val bmp = Bitmap.createBitmap(48, 72, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(AndroidColor.rgb(220, 45, 72))

        val startNs = System.nanoTime()
        var palette = PosterPaletteExtractor.extractFromBitmap(bmp)
        repeat(49) {
            palette = PosterPaletteExtractor.extractFromBitmap(bmp)
        }
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0
        bmp.recycle()

        assertNotNull(palette)
        assertTrue("50 extractions 48x72 doivent rester très rapides (< 1000ms), mesuré=${elapsedMs}ms", elapsedMs < 1000.0)

        PosterPaletteExtractor.trimToSize(0)
        assertEquals(0, PosterPaletteExtractor.cacheSize())
    }

    // ── Scénario 7 : bornes buffer Media3 ExoPlayer par tier RAM ──────────

    @Test
    fun `scenario 7 - ExoPlayer LoadControl shrinks memory buffer footprint on low-RAM devices`() {
        val lowControl = PlayerStateManager.buildLoadControl(ExoPlayerBufferProfile.LOW_RAM)
        val highControl = PlayerStateManager.buildLoadControl(ExoPlayerBufferProfile.FLAGSHIP)

        val lowMaxUs = ExoPlayerBufferProfile.LOW_RAM.maxBufferMs * 1000L
        val highMaxUs = ExoPlayerBufferProfile.FLAGSHIP.maxBufferMs * 1000L
        assertTrue(lowMaxUs < highMaxUs)
        assertTrue(
            lowControl.getBackBufferDurationUs(PlayerId.UNSET) < highControl.getBackBufferDurationUs(PlayerId.UNSET),
        )
        assertEquals(
            ExoPlayerBufferProfile.LOW_RAM.backBufferDurationMs * 1000L,
            lowControl.getBackBufferDurationUs(PlayerId.UNSET),
        )
    }

    // ── Scénario 9 : suivi des frames lentes & hystérésis d'adaptation ────

    @Test
    fun `scenario 9 - FrameJankWindowTracker downgrades on sustained dropped frames and recovers when smooth`() {
        val flagship = DeviceCapabilities(totalRamMb = 8192, isLowRamDevice = false, cpuCores = 8, sdkInt = 35)
        NovayPerformanceState.resetForTest(flagship, PerformanceMode.AUTO)
        assertEquals(PerformanceTier.HIGH, NovayPerformanceState.currentProfile.tier)
        assertTrue(NovayPerformanceState.currentProfile.allowLiveHazeBlur)

        val tracker = FrameJankWindowTracker { jankFraction, total ->
            NovayPerformanceState.recordFrameWindowResult(jankFraction, total)
        }

        // 2 fenêtres consécutives de 60 frames avec 50% de frames > 24ms -> déclenche l'allègement
        repeat(2) {
            repeat(30) { tracker.recordFrameDurationNs(34_000_000L) }
            repeat(30) { tracker.recordFrameDurationNs(8_000_000L) }
        }
        assertEquals(PerformanceTier.LOW, NovayPerformanceState.currentProfile.tier)
        assertFalse(NovayPerformanceState.currentProfile.allowLiveHazeBlur)

        // 4 fenêtres fluides consécutives (< 8% de frames lentes) -> rétablit le niveau matériel
        repeat(4) {
            repeat(2) { tracker.recordFrameDurationNs(30_000_000L) }
            repeat(58) { tracker.recordFrameDurationNs(7_000_000L) }
        }
        assertEquals(PerformanceTier.HIGH, NovayPerformanceState.currentProfile.tier)
        assertTrue(NovayPerformanceState.currentProfile.allowLiveHazeBlur)
    }

    @Test
    fun `persists user performance mode across re-initialization`() {
        NovayPerformanceState.setMode(context, PerformanceMode.SAVER)
        assertEquals(PerformanceMode.SAVER, NovayPerformanceState.mode)
        assertEquals(PerformanceTier.LOW, NovayPerformanceState.currentProfile.tier)

        NovayPerformanceState.init(context)
        assertEquals(PerformanceMode.SAVER, NovayPerformanceState.mode)

        NovayPerformanceState.setMode(context, PerformanceMode.AUTO)
        assertEquals(PerformanceMode.AUTO, NovayPerformanceState.mode)
    }

    @Test
    fun `performance strings are localized cleanly in French`() {
        assertEquals("Performances & fluidité", context.getString(R.string.more_performance_title))
    }
}
