package dot.jcversa.novay.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Benchmarks Macrobenchmark pour NoVaY :
 * compare le démarrage à froid sans AOT vs avec Baseline Profile, et mesure
 * la régularité des frames (`FrameTimingMetric`) lors du défilement et des
 * changements d'onglets sur appareil réel.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class StartupAndScrollMacrobenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupColdCompilationNone() = startup(CompilationMode.None())

    @Test
    fun startupColdBaselineProfile() = startup(
        CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require),
    )

    @Test
    fun scrollAndTabNavigationFrameTiming() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.DEFAULT,
            startupMode = StartupMode.WARM,
            iterations = 5,
            setupBlock = {
                pressHome()
                startActivityAndWait()
            },
        ) {
            device.findObject(By.desc("Catalogue"))?.click()
            device.waitForIdle()
            val scrollable = device.findObject(By.scrollable(true))
            scrollable?.fling(Direction.DOWN)
            device.waitForIdle()
            scrollable?.fling(Direction.UP)
            device.waitForIdle()

            device.findObject(By.desc("Historique"))?.click()
            device.waitForIdle()
            device.findObject(By.desc("Plus"))?.click()
            device.waitForIdle()
        }
    }

    private fun startup(compilationMode: CompilationMode) {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = 5,
        ) {
            pressHome()
            startActivityAndWait()
        }
    }

    companion object {
        private const val TARGET_PACKAGE = "com.aistudio.novay.kxvqrz"
    }
}
