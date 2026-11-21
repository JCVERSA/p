package dot.jcversa.novay.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.Library
import dot.jcversa.novay.data.LibraryCollection
import dot.jcversa.novay.data.LibraryEntry
import dot.jcversa.novay.ui.components.MotionPreference
import dot.jcversa.novay.ui.components.NovayMotionState
import dot.jcversa.novay.ui.state.BrowseMood
import dot.jcversa.novay.ui.state.LibraryFilter
import dot.jcversa.novay.ui.state.LibraryItemUi
import dot.jcversa.novay.ui.state.LibrarySort
import dot.jcversa.novay.ui.state.computeLibraryItems
import dot.jcversa.novay.ui.state.filterItemsByMood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThemePresetsAndMotionTest {

    @Test
    fun allFiveThemePresets_produceDistinctPalettesAndOledBlackUsesPureBlack() {
        val sampleAccent = Color(0xFF38BDF8)
        val pairs = NovayThemePreset.entries.associateWith { preset ->
            previewThemeFor(preset, sampleAccent)
        }

        assertEquals(5, pairs.size)
        val oledScheme = pairs.getValue(NovayThemePreset.OLED_PURE_BLACK).first
        assertEquals(Color.Black, oledScheme.background)
        assertEquals(Color.Black, oledScheme.surface)

        val noirPrimary = pairs.getValue(NovayThemePreset.NOIR_CRIMSON).first.primary
        val slatePrimary = pairs.getValue(NovayThemePreset.MIDNIGHT_SLATE).first.primary
        val amberPrimary = pairs.getValue(NovayThemePreset.WARM_AMBER_GOLD).first.primary
        assertNotEquals(noirPrimary, slatePrimary)
        assertNotEquals(slatePrimary, amberPrimary)
    }

    @Test
    fun themeState_preservesAmoledToggleBackToPreviousNonOledPreset() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        NovayThemeState.setPreset(context, NovayThemePreset.MIDNIGHT_SLATE)
        assertFalse(NovayThemeState.amoled)
        assertEquals(NovayThemePreset.MIDNIGHT_SLATE, NovayThemeState.preset)

        NovayThemeState.setAmoled(context, true)
        assertTrue(NovayThemeState.amoled)
        assertEquals(NovayThemePreset.OLED_PURE_BLACK, NovayThemeState.preset)

        NovayThemeState.setAmoled(context, false)
        assertFalse(NovayThemeState.amoled)
        assertEquals(NovayThemePreset.MIDNIGHT_SLATE, NovayThemeState.preset)

        NovayThemeState.init(context)
        assertEquals(NovayThemePreset.MIDNIGHT_SLATE, NovayThemeState.preset)

        // Reset to default for subsequent tests
        NovayThemeState.setPreset(context, NovayThemePreset.NOIR_CRIMSON)
    }

    @Test
    fun motionState_persistsAndRestoresUserPreference() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        NovayMotionState.setPreference(context, MotionPreference.REDUCED)
        assertEquals(MotionPreference.REDUCED, NovayMotionState.preference)

        NovayMotionState.init(context)
        assertEquals(MotionPreference.REDUCED, NovayMotionState.preference)

        NovayMotionState.setPreference(context, MotionPreference.FULL)
        assertEquals(MotionPreference.FULL, NovayMotionState.preference)

        NovayMotionState.setPreference(context, MotionPreference.SYSTEM)
    }

    @Test
    fun posterPaletteExtractor_extractsSaturatedColorFromBitmap() {
        val bmp = Bitmap.createBitmap(24, 36, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(AndroidColor.rgb(20, 140, 220))
        val palette = PosterPaletteExtractor.extractFromBitmap(bmp)

        assertTrue(palette.accent.blue > palette.accent.red)
        assertTrue(palette.softGlow.alpha < 0.3f)
        bmp.recycle()
    }

    @Test
    fun browseMood_filtersAnimeByMatchingGenresAndKeepsAllForAll() {
        val items = listOf(
            AnimeSearchResult("Solo Leveling", "https://example.org/solo", genres = listOf("Action", "Fantastique")),
            AnimeSearchResult("Horimiya", "https://example.org/horimiya", genres = listOf("Romance", "Tranche de vie")),
            AnimeSearchResult("Psycho-Pass", "https://example.org/psycho", genres = listOf("Sci-Fi", "Policier")),
        )

        assertEquals(3, filterItemsByMood(items, BrowseMood.ALL).size)
        assertEquals(listOf("Solo Leveling"), filterItemsByMood(items, BrowseMood.ACTION).map { it.title })
        assertEquals(listOf("Horimiya"), filterItemsByMood(items, BrowseMood.ROMANCE).map { it.title })
        assertEquals(listOf("Psycho-Pass"), filterItemsByMood(items, BrowseMood.SCIFI).map { it.title })
    }

    @Test
    fun libraryCollections_encodeDecodeToggleAndFilterCorrectly() {
        val cols = listOf(
            LibraryCollection(id = "c1", name = "Coups de cœur", animeUrls = listOf("https://a.to/cat/frieren/")),
        )
        val toggled = Library.toggleAnimeInCollection(cols, "c1", "https://a.to/cat/dandadan")
        val encoded = Library.encodeCollections(toggled)
        val decoded = Library.decodeCollections(encoded)

        assertEquals(1, decoded.size)
        assertEquals(
            listOf("https://a.to/cat/frieren", "https://a.to/cat/dandadan"),
            decoded.first().animeUrls,
        )

        val dataset = listOf(
            LibraryItemUi(LibraryEntry("Frieren", "https://a.to/cat/frieren"), 0, null),
            LibraryItemUi(LibraryEntry("Dandadan", "https://a.to/cat/dandadan"), 0, null),
            LibraryItemUi(LibraryEntry("Bleach", "https://a.to/cat/bleach"), 0, null),
        )
        val filtered = computeLibraryItems(
            allItems = dataset,
            filter = LibraryFilter.ALL,
            sort = LibrarySort.TITLE,
            selectedCollection = decoded.first(),
        )
        assertEquals(listOf("Dandadan", "Frieren"), filtered.map { it.entry.title })
    }
}
