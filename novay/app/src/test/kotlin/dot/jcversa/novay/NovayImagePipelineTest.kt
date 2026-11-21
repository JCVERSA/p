package dot.jcversa.novay

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import coil.request.ImageRequest
import coil.request.SuccessResult
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.Library
import dot.jcversa.novay.data.LibraryEntry
import dot.jcversa.novay.data.WatchHistory
import dot.jcversa.novay.data.WatchHistoryEntry
import dot.jcversa.novay.data.sama.SamaParsers
import dot.jcversa.novay.data.sama.SamaSource
import dot.jcversa.novay.ui.components.PosterRecoveryCoordinator
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NovayImagePipelineTest {

    private lateinit var context: Context

    // Minimal valid 1x1 crimson PNG (68 bytes)
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
        NovayImagePipeline.clearHostStrategies()
        PosterRecoveryCoordinator.clearForTest()
        Library.save(context, emptyList())
        WatchHistory.clear(context)
    }

    @After
    fun tearDown() {
        NovayImagePipeline.clearHostStrategies()
        PosterRecoveryCoordinator.clearForTest()
    }

    private fun imageResponse(request: Request, code: Int, body: ByteArray, contentType: String = "image/png"): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 200) "OK" else "ERR")
            .body(body.toResponseBody(contentType.toMediaType()))
            .build()

    @Test
    fun `interceptor negotiates Referer on 403 and caches winning strategy per CDN host`() {
        val attempts = AtomicInteger(0)
        val client = NovayImagePipeline.createHttpClient(
            siteOriginProvider = { "https://anime-sama.to" },
            sleepMs = {},
            extraInterceptor = { chain ->
                val req = chain.request()
                attempts.incrementAndGet()
                val referer = req.header("Referer")
                if (referer == "https://anime-sama.to/") {
                    imageResponse(req, 200, validPngBytes)
                } else {
                    imageResponse(req, 403, ByteArray(0))
                }
            },
        )

        client.newCall(Request.Builder().url("https://strict-cdn.example/overgeared.png").build()).execute().use { resp ->
            assertEquals(200, resp.code)
        }
        assertEquals(2, attempts.get())
        assertEquals(RefererStrategy.SITE_ORIGIN, NovayImagePipeline.cachedStrategyFor("strict-cdn.example"))

        // Subsequent request to same host uses cached SITE_ORIGIN immediately on attempt #1.
        attempts.set(0)
        client.newCall(Request.Builder().url("https://strict-cdn.example/frieren.png").build()).execute().use { resp ->
            assertEquals(200, resp.code)
        }
        assertEquals(1, attempts.get())
    }

    @Test
    fun `interceptor retries 429 and 503 with backoff, retries SocketTimeout, and short-circuits 404`() {
        val counts = mutableMapOf<String, AtomicInteger>()
        val sleeps = mutableListOf<Long>()
        fun nextAttempt(path: String): Int = counts.getOrPut(path) { AtomicInteger(0) }.incrementAndGet()

        val client = NovayImagePipeline.createHttpClient(
            siteOriginProvider = { "https://anime-sama.to" },
            sleepMs = { sleeps.add(it) },
            maxTransientRetries = 2,
            extraInterceptor = { chain ->
                val req = chain.request()
                when (val path = req.url.encodedPath) {
                    "/rate-limited.png" -> {
                        if (nextAttempt(path) == 1) {
                            Response.Builder()
                                .request(req)
                                .protocol(Protocol.HTTP_1_1)
                                .code(429)
                                .message("Too Many Requests")
                                .header("Retry-After", "1")
                                .body(ByteArray(0).toResponseBody(null))
                                .build()
                        } else {
                            imageResponse(req, 200, validPngBytes)
                        }
                    }
                    "/flaky-503.png" -> {
                        if (nextAttempt(path) <= 2) imageResponse(req, 503, ByteArray(0))
                        else imageResponse(req, 200, validPngBytes)
                    }
                    "/slow-timeout.png" -> {
                        if (nextAttempt(path) == 1) throw SocketTimeoutException("read timed out")
                        imageResponse(req, 200, validPngBytes)
                    }
                    "/missing-404.png" -> {
                        nextAttempt(path)
                        imageResponse(req, 404, ByteArray(0))
                    }
                    "/soft-404-html.png" -> {
                        nextAttempt(path)
                        imageResponse(req, 200, "<html>404</html>".toByteArray(), "text/html; charset=utf-8")
                    }
                    else -> imageResponse(req, 404, ByteArray(0))
                }
            },
        )

        client.newCall(Request.Builder().url("https://cdn.example/rate-limited.png").build()).execute().use {
            assertEquals(200, it.code)
        }
        client.newCall(Request.Builder().url("https://cdn.example/flaky-503.png").build()).execute().use {
            assertEquals(200, it.code)
        }
        client.newCall(Request.Builder().url("https://cdn.example/slow-timeout.png").build()).execute().use {
            assertEquals(200, it.code)
        }
        client.newCall(Request.Builder().url("https://cdn.example/missing-404.png").build()).execute().use {
            assertEquals(404, it.code)
        }
        client.newCall(Request.Builder().url("https://cdn.example/soft-404-html.png").build()).execute().use {
            assertEquals(422, it.code)
            assertEquals("no-store", it.header("Cache-Control"))
        }

        assertEquals(2, counts["/rate-limited.png"]?.get())
        assertEquals(3, counts["/flaky-503.png"]?.get())
        assertEquals(2, counts["/slow-timeout.png"]?.get())
        assertEquals(1, counts["/missing-404.png"]?.get())
        assertEquals(1, counts["/soft-404-html.png"]?.get())
        assertTrue("Expected Retry-After sleep of 1000ms", sleeps.contains(1000L))
    }

    @Test
    fun `Coil ImageLoader caches valid posters and never caches 404 or soft-404 responses`() = runBlocking {
        val networkHits = AtomicInteger(0)
        val okUrl = "https://cache-cdn.example/overgeared.png"
        val errUrl = "https://cache-cdn.example/broken-404.png"

        val client = NovayImagePipeline.createHttpClient(
            sleepMs = {},
            extraInterceptor = { chain ->
                val req = chain.request()
                networkHits.incrementAndGet()
                if (req.url.toString() == okUrl) {
                    imageResponse(req, 200, validPngBytes)
                } else {
                    imageResponse(req, 404, ByteArray(0))
                }
            },
        )
        val loader = NovayImagePipeline.buildImageLoader(context, client)
        loader.memoryCache?.clear()
        loader.diskCache?.clear()

        val firstOk = loader.execute(
            ImageRequest.Builder(context)
                .data(okUrl)
                .memoryCacheKey(okUrl)
                .diskCacheKey(okUrl)
                .build(),
        )
        assertTrue(firstOk is SuccessResult)
        assertEquals(1, networkHits.get())

        // Second request must be served from Coil memory/disk cache with 0 extra network hits.
        val cachedOk = loader.execute(
            ImageRequest.Builder(context)
                .data(okUrl)
                .memoryCacheKey(okUrl)
                .diskCacheKey(okUrl)
                .build(),
        )
        assertTrue(cachedOk is SuccessResult)
        assertEquals(1, networkHits.get())

        // Error responses must NOT be cached: hitting errUrl twice hits network twice.
        val firstErr = loader.execute(
            ImageRequest.Builder(context).data(errUrl).memoryCacheKey(errUrl).diskCacheKey(errUrl).build(),
        )
        assertTrue(firstErr !is SuccessResult)
        val secondErr = loader.execute(
            ImageRequest.Builder(context).data(errUrl).memoryCacheKey(errUrl).diskCacheKey(errUrl).build(),
        )
        assertTrue(secondErr !is SuccessResult)
        assertEquals(3, networkHits.get())
    }

    @Test
    fun `SamaSource resolvePosterUrl and PosterRecoveryCoordinator backfill Library and WatchHistory in single flight`() = runBlocking {
        val showPageFetches = AtomicInteger(0)
        val origin = "https://anime-sama.to"
        val brokenOldPoster = "https://dead-cdn.example/overgeared-old.jpg"
        val healedPoster = "https://cdn.example/Overgeared%20HD.jpg"

        Library.save(
            context,
            listOf(
                LibraryEntry(
                    title = "Overgeared",
                    url = "https://anime-sama.com/catalogue/overgeared/",
                    slug = "overgeared",
                    posterUrl = null,
                    addedAt = 1L,
                ),
            ),
        )
        WatchHistory.record(
            context,
            WatchHistoryEntry(
                animeTitle = "Overgeared",
                animeUrl = "https://anime-sama.com/catalogue/overgeared/",
                posterUrl = brokenOldPoster,
                seasonName = "Saison 1",
                seasonUrl = "https://anime-sama.com/catalogue/overgeared/saison1/vostfr/",
                episodeNumber = 3,
                episodeLabel = "Épisode 3",
                lienIds = emptyList(),
                jeton = "",
                watchedAt = 2L,
            ),
        )

        val samaClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val url = req.url.toString()
                when {
                    url.startsWith(SamaParsers.LAUNCHER_URL) -> Response.Builder()
                        .request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body("""<a class="btn-primary" href="$origin/">Go</a>""".toResponseBody("text/html".toMediaType()))
                        .build()
                    url == "$origin/" -> Response.Builder()
                        .request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body("<html></html>".toResponseBody("text/html".toMediaType()))
                        .build()
                    url == "$origin/catalogue/overgeared/" -> {
                        showPageFetches.incrementAndGet()
                        Thread.sleep(30)
                        Response.Builder()
                            .request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                            .body("""<img id="coverOeuvre" src="//cdn.example/Overgeared HD.jpg">""".toResponseBody("text/html".toMediaType()))
                            .build()
                    }
                    else -> Response.Builder()
                        .request(req).protocol(Protocol.HTTP_1_1).code(404).message("NF")
                        .body(ByteArray(0).toResponseBody(null))
                        .build()
                }
            }
            .build()

        val source = SamaSource(http = samaClient)
        val brokenAnime = AnimeSearchResult(
            title = "Overgeared",
            url = "https://anime-sama.com/catalogue/overgeared/",
            slug = "overgeared",
            posterUrl = brokenOldPoster,
        )

        // Fire concurrent recovery calls (e.g. ResumeBanner + LibraryCard + ShowScreen simultaneously)
        val results = (0 until 4).map {
            async { PosterRecoveryCoordinator.resolveAndPersist(context, source, brokenAnime) }
        }.awaitAll()

        assertTrue(results.all { it == healedPoster })
        assertEquals("Concurrent recovery calls should single-flight to 1 show page fetch", 1, showPageFetches.get())

        val updatedLib = Library.all(context).first()
        val updatedHist = WatchHistory.recent(context).first()
        assertNotNull(updatedLib.posterUrl)
        assertEquals(healedPoster, updatedLib.posterUrl)
        assertEquals(healedPoster, updatedHist.posterUrl)

        // Ensure another broken-free title stays distinct and isn't overwritten
        assertNull(
            Library.all(context).firstOrNull { it.slug == "nonexistent" }
        )
    }
}
