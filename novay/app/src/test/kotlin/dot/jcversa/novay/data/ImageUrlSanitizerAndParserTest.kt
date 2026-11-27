package dot.jcversa.novay.data

import dot.jcversa.novay.data.sama.SamaParsers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageUrlSanitizerAndParserTest {

    private val origin = "https://anime-sama.to"

    @Test
    fun `sanitizePosterUrl normalizes entities, protocol-relative, relative paths, and unsafe characters`() {
        assertEquals(
            "https://cdn.example.com/posters/Overgeared%20(2024).jpg?w=400&q=80",
            ImageUrlSanitizer.sanitizePosterUrl(
                " //cdn.example.com/posters/Overgeared (2024).jpg?w=400&amp;q=80 ",
                origin,
            ),
        )
        assertEquals(
            "$origin/img/contenu/Overgeared%20S1.webp",
            ImageUrlSanitizer.sanitizePosterUrl("/img/contenu/Overgeared S1.webp", origin),
        )
        assertEquals(
            "https://cdn.jsdelivr.net/gh/Anime-Sama/IMG@img/contenu/thumb/tokyo-revengers.webp",
            ImageUrlSanitizer.sanitizePosterUrl(
                "https://cdn.jsdelivr.net/gh/Anime-Sama/IMG@img/contenu/thumb/tokyo-revengers.webp",
                origin,
            ),
        )
        // Does not double-encode existing %20
        assertEquals(
            "https://cdn.statically.io/gh/Anime-Sama/Overgeared%20TV.jpg",
            ImageUrlSanitizer.sanitizePosterUrl(
                "https://cdn.statically.io/gh/Anime-Sama/Overgeared%20TV.jpg",
                origin,
            ),
        )
    }

    @Test
    fun `sanitizePosterUrl rejects blank, placeholder, data-uri, and non-http schemes`() {
        assertNull(ImageUrlSanitizer.sanitizePosterUrl(null, origin))
        assertNull(ImageUrlSanitizer.sanitizePosterUrl("", origin))
        assertNull(ImageUrlSanitizer.sanitizePosterUrl("   ", origin))
        assertNull(ImageUrlSanitizer.sanitizePosterUrl("x", origin))
        assertNull(ImageUrlSanitizer.sanitizePosterUrl("null", origin))
        assertNull(ImageUrlSanitizer.sanitizePosterUrl("data:image/gif;base64,R0lGOD", origin))
        assertNull(ImageUrlSanitizer.sanitizePosterUrl("javascript:alert(1)", origin))
        assertNull(ImageUrlSanitizer.sanitizePosterUrl("ftp://cdn.example.com/poster.jpg", origin))
        assertNull(ImageUrlSanitizer.sanitizePosterUrl("/img/spacer.gif", origin))
        assertNull(ImageUrlSanitizer.sanitizePosterUrl("https:///missing-host.jpg", origin))
    }

    @Test
    fun `extractAnimeUrlFromSeasonUrl extracts show root cleanly`() {
        assertEquals(
            "https://anime-sama.to/catalogue/overgeared/",
            ImageUrlSanitizer.extractAnimeUrlFromSeasonUrl(
                "https://anime-sama.to/catalogue/overgeared/saison1/vostfr/"
            ),
        )
        assertEquals(
            "/catalogue/frieren/",
            ImageUrlSanitizer.extractAnimeUrlFromSeasonUrl("/catalogue/frieren/saison1/vf/"),
        )
        assertNull(ImageUrlSanitizer.extractAnimeUrlFromSeasonUrl("https://cdn.example.com/video.mp4"))
    }

    @Test
    fun `parseHomeCards recovers Overgeared poster from data-img, data-src, srcset, and relative paths`() {
        val html = """
            <div class="as-carte as-carte-anime" data-url="/catalogue/overgeared/" data-cat="oeuvre" data-nom="Overgeared"
                 data-img="//cdn.example.com/overgeared cover.jpg">
              <a class="as-carte-lien" href="/catalogue/overgeared/saison1/vostfr/">
                <div class="as-carte-image">
                  <img src="data:image/gif;base64,R0lGOD" data-lazy-src="/img/spacer.gif" alt="">
                </div>
                <span class="as-carte-temps">18h00</span>
              </a>
            </div>
            <div class="as-carte as-carte-anime" data-url="/catalogue/solo-leveling/" data-cat="oeuvre" data-nom="Solo Leveling"
                 data-img="x">
              <a class="as-carte-lien" href="/catalogue/solo-leveling/saison2/vostfr/">
                <div class="as-carte-image">
                  <img srcset="/img/solo-300.webp 1x, /img/solo-600.webp 2x">
                </div>
              </a>
            </div>
        """.trimIndent()

        val cards = SamaParsers.parseHomeCards(html, origin)
        assertEquals(2, cards.size)
        assertEquals("Overgeared", cards[0].title)
        assertEquals("https://cdn.example.com/overgeared%20cover.jpg", cards[0].posterUrl)
        assertEquals("Solo Leveling", cards[1].title)
        assertEquals("$origin/img/solo-600.webp", cards[1].posterUrl)
    }

    @Test
    fun `parseHeroSlides resolves data-src and css background-image`() {
        val html = """
            <div class="ak-slide">
              <div class="ak-slide-bg" style="background-image: url('/img/overgeared hero.webp');"></div>
              <div class="ak-slide-content">
                <h2 class="ak-slide-title">Overgeared</h2>
                <a class="ak-slide-cta" href="/catalogue/overgeared/saison1/vostfr/">Visionner</a>
              </div>
            </div>
            <div class="ak-slide">
              <div class="ak-slide-bg"><img src="data:image/gif;base64,R0lGOD" data-src="//img.example.org/frieren.jpg"></div>
              <div class="ak-slide-content">
                <h2 class="ak-slide-title">Frieren</h2>
                <a class="ak-slide-cta" href="/catalogue/frieren/saison1/vf/">Visionner</a>
              </div>
            </div>
        """.trimIndent()

        val slides = SamaParsers.parseHeroSlides(html, origin)
        assertEquals(2, slides.size)
        assertEquals("$origin/img/overgeared%20hero.webp", slides[0].posterUrl)
        assertEquals("https://img.example.org/frieren.jpg", slides[1].posterUrl)
    }

    @Test
    fun `parseSamaCatalogueCards handles lazy data-original, srcset, and relative images`() {
        val html = """
            <a href="/catalogue/overgeared/">
              <img src="data:image/gif;base64,R0lGOD" data-original="//cdn.example.net/Overgeared poster.jpg?a=1&amp;b=2">
              <h3 class="card-title">Overgeared</h3>
            </a>
            <a href="/catalogue/dandadan/" data-img="/img/dandadan-fallback.webp">
              <h3 class="card-title">DanDaDan</h3>
            </a>
        """.trimIndent()

        val items = SamaParsers.parseSamaCatalogueCards(html, origin)
        assertEquals(2, items.size)
        assertEquals("https://cdn.example.net/Overgeared%20poster.jpg?a=1&b=2", items[0].posterUrl)
        assertEquals("$origin/img/dandadan-fallback.webp", items[1].posterUrl)
    }

    @Test
    fun `parseAnimePoster prioritizes coverOeuvre then og_image then first content image`() {
        val withCover = """
            <html>
              <head><meta property="og:image" content="https://cdn.example.com/og-overgeared.jpg"></head>
              <body>
                <img src="/assets/logo.svg">
                <img id="coverOeuvre" data-src="//cdn.example.com/cover-overgeared.webp" src="data:image/gif;base64,R0lGOD">
              </body>
            </html>
        """.trimIndent()
        assertEquals(
            "https://cdn.example.com/cover-overgeared.webp",
            SamaParsers.parseAnimePoster(withCover, origin),
        )

        val withOgOnly = """
            <html>
              <head><meta content="/img/og only overgeared.jpg" property="og:image"></head>
              <body><h1>Overgeared</h1></body>
            </html>
        """.trimIndent()
        assertEquals(
            "$origin/img/og%20only%20overgeared.jpg",
            SamaParsers.parseAnimePoster(withOgOnly, origin),
        )
        assertTrue(SamaParsers.parseAnimePoster("<html><body></body></html>", origin) == null)
    }
}
