package dot.jcversa.novay.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HlsPlaylist (V2.5 « novabox ») : parse des playlists média + nommage
 * LISIBLE des fichiers téléchargés (« Solo Leveling - S01E03 VOSTFR
 * 1080p.mp4 ») — validé par le owner.
 */
class HlsPlaylistTest {

    // ── parseMediaPlaylist / isMasterPlaylist ───────────────────────────

    @Test
    fun `master playlist reconnue et refusee par le parseur media`() {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=1500000,RESOLUTION=1280x720
            https://cdn.example/v720.m3u8
        """.trimIndent()
        assertTrue(HlsPlaylist.isMasterPlaylist(master))
        assertNull(HlsPlaylist.parseMediaPlaylist(master, "https://cdn.example/master.m3u8"))
    }

    @Test
    fun `playlist media ts segments relatifs resolus contre la base`() {
        val media = """
            #EXTM3U
            #EXT-X-TARGETDURATION:6
            #EXTINF:5.76,
            seg0.ts
            #EXTINF:5.76,
            seg1.ts
            #EXTINF:2.88,
            https://autre.example/seg2.ts
        """.trimIndent()
        assertFalse(HlsPlaylist.isMasterPlaylist(media))
        val parsed = HlsPlaylist.parseMediaPlaylist(media, "https://cdn.example/vostfr/index.m3u8")!!
        assertEquals(
            listOf(
                "https://cdn.example/vostfr/seg0.ts",
                "https://cdn.example/vostfr/seg1.ts",
                "https://autre.example/seg2.ts"
            ),
            parsed.segmentUrls
        )
        assertNull(parsed.initSegmentUrl)
        assertFalse(parsed.isFragmentedMp4)
    }

    @Test
    fun `playlist fmp4 segment d init ext-x-map resolu`() {
        val media = """
            #EXTM3U
            #EXT-X-TARGETDURATION:6
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:5.76,
            chunk1.m4s
        """.trimIndent()
        val parsed = HlsPlaylist.parseMediaPlaylist(media, "https://cdn.example/1080/index.m3u8")!!
        assertEquals("https://cdn.example/1080/init.mp4", parsed.initSegmentUrl)
        assertTrue(parsed.isFragmentedMp4)
        assertEquals(listOf("https://cdn.example/1080/chunk1.m4s"), parsed.segmentUrls)
    }

    @Test
    fun `playlist vide ou cassée rend null`() {
        assertNull(HlsPlaylist.parseMediaPlaylist("", "https://cdn.example/x.m3u8"))
        assertNull(HlsPlaylist.parseMediaPlaylist("#EXTM3U\n#EXT-X-ENDLIST", "https://cdn.example/x.m3u8"))
    }

    // ── Nomming lisible ──────────────────────────────────────────────────

    @Test
    fun `nommage complet anime, saison, episode, langue et qualite`() {
        assertEquals(
            "Solo Leveling - S01E03 VOSTFR 1080p.mp4",
            HlsPlaylist.buildEpisodeFileName(
                animeTitle = "Solo Leveling",
                seasonUrl = "https://anime-sama.to/catalogue/solo-leveling/saison1/vostfr/",
                episodeNumber = 3,
                language = "VOSTFR",
                qualityLabel = "1080P"
            )
        )
    }

    @Test
    fun `saison 10 et episode 12 sur deux chiffres`() {
        assertEquals(
            "My Hero Academia - S10E12 VF 720p.mp4",
            HlsPlaylist.buildEpisodeFileName(
                animeTitle = "My Hero Academia",
                seasonUrl = "https://anime-sama.to/catalogue/my-hero-academia/saison10/vf/",
                episodeNumber = 12,
                language = "VF",
                qualityLabel = "720p"
            )
        )
    }

    @Test
    fun `caracteres interdits du titre remplaces par des espaces`() {
        assertEquals(
            "Re Zero Starting Life in Another World - S01E01 VOSTFR 480p.mp4",
            HlsPlaylist.buildEpisodeFileName(
                animeTitle = "Re:Zero \"Starting Life\"/in Another*World?",
                seasonUrl = "https://anime-sama.to/catalogue/re-zero/saison1/vostfr/",
                episodeNumber = 1,
                language = "VOSTFR",
                qualityLabel = "480p"
            )
        )
    }

    @Test
    fun `film et oav nommes sans numero d episode`() {
        assertEquals(
            "Suzume - Film VOSTFR 1080p.mp4",
            HlsPlaylist.buildEpisodeFileName(
                animeTitle = "Suzume",
                seasonUrl = "https://anime-sama.to/catalogue/suzume/film/vostfr/",
                episodeNumber = 0,
                language = "VOSTFR",
                qualityLabel = "1080P"
            )
        )
        assertEquals(
            "Blue Lock - OAV VOSTFR.mp4",
            HlsPlaylist.buildEpisodeFileName(
                animeTitle = "Blue Lock",
                seasonUrl = "https://anime-sama.to/catalogue/blue-lock/oav/vostfr/",
                episodeNumber = 0,
                language = "VOSTFR",
                qualityLabel = ""
            )
        )
    }

    @Test
    fun `auto et direct omis du nom, langue absente omise`() {
        assertEquals(
            "Frieren - S02E01.mp4",
            HlsPlaylist.buildEpisodeFileName(
                animeTitle = "Frieren",
                seasonUrl = "https://anime-sama.to/catalogue/frieren/saison2/vostfr/",
                episodeNumber = 1,
                language = null,
                qualityLabel = "Auto"
            )
        )
        assertEquals(
            "One Piece - S01E100.mp4",
            HlsPlaylist.buildEpisodeFileName(
                animeTitle = "One Piece",
                seasonUrl = "https://anime-sama.to/catalogue/one-piece/saison1/vf/",
                episodeNumber = 100,
                language = null,
                qualityLabel = "DIRECT"
            )
        )
    }

    @Test
    fun `extension ts pour le repli honnete sans remux`() {
        assertEquals(
            "Bleach - S01E05 VOSTFR 720p.ts",
            HlsPlaylist.buildEpisodeFileName(
                animeTitle = "Bleach",
                seasonUrl = "https://anime-sama.to/catalogue/bleach/saison1/vostfr/",
                episodeNumber = 5,
                language = "VOSTFR",
                qualityLabel = "720P",
                extension = ".ts"
            )
        )
    }

    @Test
    fun `episode sans saison connue garde son numero`() {
        assertEquals(
            "Naruto - E17 VOSTFR.mp4",
            HlsPlaylist.buildEpisodeFileName(
                animeTitle = "Naruto",
                seasonUrl = "https://anime-sama.to/catalogue/naruto/vostfr/",
                episodeNumber = 17,
                language = "VOSTFR",
                qualityLabel = ""
            )
        )
    }

    @Test
    fun `sanitize compacte les espaces multiples`() {
        assertEquals("L'Attaque des Titans", HlsPlaylist.sanitizeFileNamePart(" L'Attaque  des\tTitans ? "))
    }

    // ── Collisions ───────────────────────────────────────────────────────

    @Test
    fun `sans collision le nom de base est garde`() {
        assertEquals(
            "A.mp4",
            HlsPlaylist.availableFileName("A.mp4") { false }
        )
    }

    @Test
    fun `collision incremente le suffixe entre parentheses`() {
        val existants = setOf("Solo Leveling - S01E03 VOSTFR 1080p.mp4", "Solo Leveling - S01E03 VOSTFR 1080p (2).mp4")
        assertEquals(
            "Solo Leveling - S01E03 VOSTFR 1080p (3).mp4",
            HlsPlaylist.availableFileName("Solo Leveling - S01E03 VOSTFR 1080p.mp4") { existants.contains(it) }
        )
    }

    @Test
    fun `collision sur un nom sans extension reste lisible`() {
        assertEquals("Film (2)", HlsPlaylist.availableFileName("Film") { it == "Film" })
    }
}
