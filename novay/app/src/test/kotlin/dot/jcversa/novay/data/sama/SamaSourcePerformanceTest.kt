package dot.jcversa.novay.data.sama

import dot.jcversa.novay.data.AnimeEpisode
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.AnimeSeason
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class SamaSourcePerformanceTest {

    private fun htmlResponse(request: okhttp3.Request, code: Int, body: String, mime: String = "text/html"): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 200) "OK" else "ERR")
            .body(body.toResponseBody(mime.toMediaType()))
            .build()

    @Test
    fun `origin single-flight deduplicates concurrent launcher calls`() = runBlocking {
        val launcherHits = AtomicInteger(0)
        val catalogueHits = AtomicInteger(0)
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val url = req.url.toString()
                when {
                    url.startsWith(SamaParsers.LAUNCHER_URL) -> {
                        launcherHits.incrementAndGet()
                        Thread.sleep(40)
                        htmlResponse(
                            req,
                            200,
                            """<a class="btn-primary" href="https://anime-sama.to/">Go</a>"""
                        )
                    }
                    url == "https://anime-sama.to/" -> htmlResponse(req, 200, "<html></html>")
                    url.endsWith("/catalogue/") -> {
                        catalogueHits.incrementAndGet()
                        htmlResponse(
                            req,
                            200,
                            """<a href="https://anime-sama.to/catalogue/frieren/"><h3>Frieren</h3><img src="/img/frieren.jpg"></a>"""
                        )
                    }
                    else -> htmlResponse(req, 404, "")
                }
            }
            .build()

        val clock = AtomicLong(1_000L)
        val source = SamaSource(http = client, clockMs = clock::get)

        val batches = (0 until 6).map {
            async { source.featured() }
        }.awaitAll()

        assertTrue(batches.all { it.size == 1 && it.first().title == "Frieren" })
        assertEquals("Launcher should only be queried once across concurrent callers", 1, launcherHits.get())
    }

    @Test
    fun `ttl caches deduplicate featured search seasons and episodes until expiry`() = runBlocking {
        val hits = ConcurrentHashMap<String, AtomicInteger>()
        fun hit(key: String): Int = hits.computeIfAbsent(key) { AtomicInteger(0) }.incrementAndGet()

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val url = req.url.toString()
                when {
                    url.startsWith(SamaParsers.LAUNCHER_URL) -> {
                        hit("launcher")
                        htmlResponse(req, 200, """<a class="btn-primary" href="https://anime-sama.to/">Go</a>""")
                    }
                    url == "https://anime-sama.to/" -> htmlResponse(req, 200, "<html></html>")
                    url == "https://anime-sama.to/catalogue/" -> {
                        hit("featured")
                        htmlResponse(
                            req,
                            200,
                            """<a href="https://anime-sama.to/catalogue/naruto/"><h3>Naruto</h3><img src="/img/naruto.jpg"></a>"""
                        )
                    }
                    url.endsWith("fetch.php") -> {
                        hit("search")
                        htmlResponse(
                            req,
                            200,
                            """<a class="asn-search-result" href="https://anime-sama.to/catalogue/naruto/"><h3 class="asn-search-result-title">Naruto</h3><img class="asn-search-result-img" src="/img/naruto.jpg"></a>"""
                        )
                    }
                    url == "https://anime-sama.to/catalogue/naruto/" -> {
                        hit("seasons")
                        htmlResponse(
                            req,
                            200,
                            """panneauAnime("Saison 1", "saison1/vostfr");"""
                        )
                    }
                    url == "https://anime-sama.to/catalogue/naruto/saison1/vf/" -> {
                        hit("mirror_vf")
                        htmlResponse(req, 404, "")
                    }
                    url == "https://anime-sama.to/catalogue/naruto/saison1/vostfr/" -> {
                        hit("episodes")
                        htmlResponse(
                            req,
                            200,
                            """var epsJeton = "tok1"; var eps1 = ["lien:101", "lien:102"];"""
                        )
                    }
                    else -> htmlResponse(req, 404, "")
                }
            }
            .build()

        val clock = AtomicLong(10_000L)
        val source = SamaSource(http = client, clockMs = clock::get)
        val anime = AnimeSearchResult("Naruto", "https://anime-sama.to/catalogue/naruto/", "naruto")
        val season = AnimeSeason("Saison 1", "https://anime-sama.to/catalogue/naruto/saison1/vostfr/", "VOSTFR")

        // First call warms all caches
        assertEquals(1, source.featured().size)
        assertEquals(1, source.search("naruto").size)
        assertEquals(1, source.seasons(anime).size)
        assertEquals(2, source.episodes(season).size)

        // Second call within TTL uses memory caches (0 additional HTTP hits)
        clock.addAndGet(60_000L)
        assertEquals(1, source.featured().size)
        assertEquals(1, source.search("  Naruto  ").size)
        assertEquals(1, source.seasons(anime).size)
        assertEquals(2, source.episodes(season).size)

        assertEquals(1, hits["featured"]?.get())
        assertEquals(1, hits["search"]?.get())
        assertEquals(1, hits["seasons"]?.get())
        assertEquals(1, hits["episodes"]?.get())

        // Advance past 10m TTL -> caches expire cleanly
        clock.addAndGet(11L * 60 * 1000)
        source.featured()
        source.search("naruto")
        source.seasons(anime)
        source.episodes(season)

        assertEquals(2, hits["featured"]?.get())
        assertEquals(2, hits["search"]?.get())
        assertEquals(2, hits["seasons"]?.get())
        assertEquals(2, hits["episodes"]?.get())
    }

    @Test
    fun `streams refreshes expired jeton once and resolves players in parallel`() = runBlocking {
        val seasonFetches = AtomicInteger(0)
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val url = req.url.toString()
                when {
                    url.startsWith(SamaParsers.LAUNCHER_URL) ->
                        htmlResponse(req, 200, """<a class="btn-primary" href="https://anime-sama.to/">Go</a>""")
                    url == "https://anime-sama.to/" -> htmlResponse(req, 200, "<html></html>")
                    url == "https://anime-sama.to/catalogue/bleach/saison1/vostfr/" -> {
                        seasonFetches.incrementAndGet()
                        htmlResponse(req, 200, """var epsJeton = "fresh_tok";""")
                    }
                    url.contains("/api/lien.php?j=stale_tok") ->
                        htmlResponse(req, 200, """{"erreur":"jeton"}""", "application/json")
                    url == "https://anime-sama.to/api/lien.php?j=fresh_tok&l=10" ->
                        htmlResponse(req, 200, """{"u":"https://player.example/e1"}""", "application/json")
                    url == "https://anime-sama.to/api/lien.php?j=fresh_tok&l=11" ->
                        htmlResponse(req, 200, """{"u":"https://player.example/e2"}""", "application/json")
                    url == "https://player.example/e1" ->
                        htmlResponse(req, 200, "source=\"https://cdn.example/v1/stream.mp4\"")
                    url == "https://player.example/e2" ->
                        htmlResponse(req, 200, "source=\"https://cdn.example/v2/stream.mp4\"")
                    else -> htmlResponse(req, 404, "")
                }
            }
            .build()

        val source = SamaSource(http = client)
        val ep = AnimeEpisode(
            number = 1,
            url = "https://anime-sama.to/catalogue/bleach/saison1/vostfr/",
            label = "Épisode 1",
            lienIds = listOf("lien:10", "lien:11"),
            jeton = "stale_tok"
        )

        val resolved = source.streams(ep)
        assertEquals(1, seasonFetches.get())
        assertEquals(2, resolved.size)
        assertEquals("https://cdn.example/v1/stream.mp4", resolved[0].url)
        assertEquals("https://cdn.example/v2/stream.mp4", resolved[1].url)
    }
}
