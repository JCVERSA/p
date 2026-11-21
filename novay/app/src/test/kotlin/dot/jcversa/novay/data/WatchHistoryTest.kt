package dot.jcversa.novay.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WatchHistory (V2.1) : « Reprendre la lecture » — fusion sans doublon,
 * ordre récent d'abord, aller/retour JSON avec les identifiants de
 * lecteur (lienIds + jeton) nécessaires à la reprise.
 */
class WatchHistoryTest {

    private fun entry(
        seasonUrl: String,
        episodeNumber: Int,
        watchedAt: Long = 1000L,
        animeTitle: String = "Naruto"
    ) = WatchHistoryEntry(
        animeTitle = animeTitle,
        animeUrl = "https://anime-sama.to/catalogue/naruto/",
        posterUrl = "https://cdn.example/naruto.webp",
        seasonName = "Saison 1",
        seasonUrl = seasonUrl,
        episodeNumber = episodeNumber,
        episodeLabel = "Épisode $episodeNumber",
        lienIds = listOf("lien:186672", "lien:186673"),
        jeton = "jeton-abc",
        watchedAt = watchedAt
    )

    @Test
    fun `aller-retour json complet avec lien ids et jeton`() {
        val entries = listOf(entry("https://anime-sama.to/catalogue/naruto/saison1/vostfr/", 42, 777L))
        val back = WatchHistory.decode(WatchHistory.encode(entries))
        assertEquals(entries, back)
    }

    @Test
    fun `json nul ou invalide, liste vide sans crash`() {
        assertEquals(emptyList<WatchHistoryEntry>(), WatchHistory.decode(null))
        assertEquals(emptyList<WatchHistoryEntry>(), WatchHistory.decode(""))
        assertEquals(emptyList<WatchHistoryEntry>(), WatchHistory.decode("{pas du json"))
    }

    @Test
    fun `revoir un episode le remonte en tete sans doublon`() {
        val a = entry("https://x/s1/", 1, watchedAt = 100L)
        val b = entry("https://x/s1/", 2, watchedAt = 200L)
        val again = entry("https://x/s1/", 1, watchedAt = 300L)
        val out = WatchHistory.upsert(WatchHistory.upsert(listOf(a, b), a), again)
        assertEquals(2, out.size)
        assertEquals(300L, out[0].watchedAt) // le plus recent en tete
        assertEquals(1, out[0].episodeNumber)
        assertEquals(2, out[1].episodeNumber)
    }

    @Test
    fun `meme numero d episode dans deux saisons differentes, deux entrees`() {
        val s1 = entry("https://x/saison1/vostfr/", 1)
        val vf = entry("https://x/saison1/vf/", 1)
        val out = WatchHistory.upsert(listOf(s1), vf)
        assertEquals(2, out.size)
    }

    @Test
    fun `liste bornee a 20 entrees, les plus anciennes tombent`() {
        var list = (1..25).map { entry("https://x/s$it/", it, watchedAt = it.toLong()) }
        // le plus ancien est en tete : on simule l'ordre decroissant
        list = list.sortedByDescending { it.watchedAt }
        val out = WatchHistory.upsert(list.drop(1), list.first())
        assertTrue(out.size <= 20)
        // l'entree la plus recente (watchedAt = 25) survit
        assertTrue(out.any { it.watchedAt == 25L })
        // la plus ancienne (watchedAt = 1) est tombee
        assertTrue(out.none { it.watchedAt == 1L })
    }

    @Test
    fun `aller-retour json avec position et duree`() {
        val e = entry("https://x/s1/", 7, watchedAt = 500L)
            .copy(positionMs = 654_321L, durationMs = 1_440_000L)
        val back = WatchHistory.decode(WatchHistory.encode(listOf(e)))
        assertEquals(654_321L, back[0].positionMs)
        assertEquals(1_440_000L, back[0].durationMs)
    }

    @Test
    fun `json ancien sans position, retourne zero sans crash`() {
        // Compatibilite ascendante : entrees ecrites avant la V2.2.
        val raw = """
[{"animeTitle":"Naruto","animeUrl":"u","posterUrl":"","seasonName":"S1","seasonUrl":"https://x/s1/","episodeNumber":3,"episodeLabel":"Épisode 3","lienIds":["lien:1"],"jeton":"j","watchedAt":123}]
        """.trimIndent()
        val back = WatchHistory.decode(raw)
        assertEquals(1, back.size)
        assertEquals(0L, back[0].positionMs)
        assertEquals(0L, back[0].durationMs)
    }

    @Test
    fun `withposition met a jour sans remonter l entree en tete`() {
        val a = entry("https://x/s1/", 1, watchedAt = 100L)
        val b = entry("https://x/s1/", 2, watchedAt = 200L)
        val out = WatchHistory.withPosition(listOf(b, a), "https://x/s1/", 1, 42_000L, 1_400_000L)
        // ordre inchange : b reste en tete
        assertEquals(2, out[0].episodeNumber)
        // seule l entree cible est modifiee
        assertEquals(42_000L, out[1].positionMs)
        assertEquals(1_400_000L, out[1].durationMs)
        assertEquals(100L, out[1].watchedAt)
        assertEquals(0L, out[0].positionMs)
    }

    @Test
    fun `findposition retrouve la position et zero si absent`() {
        val a = entry("https://x/s1/", 1).copy(positionMs = 99_000L)
        val entries = listOf(a)
        assertEquals(99_000L, WatchHistory.findPosition(entries, "https://x/s1/", 1))
        // slash final insensible
        assertEquals(99_000L, WatchHistory.findPosition(entries, "https://x/s1", 1))
        assertEquals(0L, WatchHistory.findPosition(entries, "https://x/s1/", 2))
        assertEquals(0L, WatchHistory.findPosition(emptyList(), "https://x/s1/", 1))
    }

    @Test
    fun `cle d unicite insensible au slash final`() {
        assertEquals(
            WatchHistory.entryKey("https://x/saison1/vf", 3),
            WatchHistory.entryKey("https://x/saison1/vf/", 3)
        )
    }
}
