package dot.jcversa.novay.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NovayIconsTest {

    @Test
    fun allCustomIcons_initializeWith24dpViewportAndPaths() {
        val icons = listOf(
            NovayIcons.Back,
            NovayIcons.ChevronRight,
            NovayIcons.Close,
            NovayIcons.Refresh,
            NovayIcons.Overflow,
            NovayIcons.Play,
            NovayIcons.Pause,
            NovayIcons.PlayCircle,
            NovayIcons.ClapperFilm,
            NovayIcons.Spotlight,
            NovayIcons.SeasonReel,
            NovayIcons.Lock,
            NovayIcons.ScreenRotate,
            NovayIcons.QualitySliders,
            NovayIcons.SpeedGauge,
            NovayIcons.Brightness,
            NovayIcons.Volume,
            NovayIcons.Search,
            NovayIcons.FillQuery,
            NovayIcons.GridCompact,
            NovayIcons.GridComfortable,
            NovayIcons.ViewList,
            NovayIcons.Sort,
            NovayIcons.LibraryShelf,
            NovayIcons.HistoryClock,
            NovayIcons.UpdatesSeal,
            NovayIcons.Download,
            NovayIcons.DownloadDone,
            NovayIcons.Check,
            NovayIcons.CheckCircle,
            NovayIcons.UncheckedCircle,
            NovayIcons.MarkAllSeen,
            NovayIcons.QueuedClock,
            NovayIcons.ErrorSeal,
            NovayIcons.OfflineCloud,
            NovayIcons.DeleteTrash,
            NovayIcons.AmoledMoon,
            NovayIcons.WifiSignal,
            NovayIcons.FullscreenEnter,
            NovayIcons.FullscreenExit,
            NovayIcons.MinimizePlayer,
            NovayIcons.Subtitles,
        )

        icons.forEach { vector ->
            assertEquals(24.dp, vector.defaultWidth)
            assertEquals(24.dp, vector.defaultHeight)
            assertEquals(24f, vector.viewportWidth)
            assertEquals(24f, vector.viewportHeight)
            assertTrue("Expected vector paths in ${vector.name}", vector.root.size > 0)
        }
    }

    @Test
    fun directionalIcons_enableAutoMirror_andSymmetricDoNot() {
        assertTrue(NovayIcons.Back.autoMirror)
        assertTrue(NovayIcons.ChevronRight.autoMirror)
        assertTrue(NovayIcons.FillQuery.autoMirror)
        assertTrue(NovayIcons.ViewList.autoMirror)
        assertTrue(NovayIcons.Sort.autoMirror)
        assertTrue(NovayIcons.Volume.autoMirror)
        assertTrue(NovayIcons.MinimizePlayer.autoMirror)

        assertFalse(NovayIcons.Play.autoMirror)
        assertFalse(NovayIcons.Lock.autoMirror)
        assertFalse(NovayIcons.Search.autoMirror)
        assertFalse(NovayIcons.FullscreenEnter.autoMirror)
        assertFalse(NovayIcons.FullscreenExit.autoMirror)
        assertFalse(NovayIcons.Subtitles.autoMirror)
    }
}
