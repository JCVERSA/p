package dot.jcversa.novay.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Générateur de Baseline Profile pour NoVaY :
 * capture le démarrage à froid, le passage vers Catalogue / Bibliothèque,
 * le défilement de grille et l'ouverture des réglages de performance.
 *
 * Exécutable sur appareil Android 13+ ou Gradle Managed Device via :
 *   gradle :app:generateBaselineProfile
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generateCriticalUserJourneys() {
        rule.collect(
            packageName = TARGET_PACKAGE,
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()

            device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)), 5_000L)
            device.waitForIdle()

            // Parcours Catalogue + scroll grille d'affiches
            device.findObject(By.desc("Catalogue"))?.click()
            device.waitForIdle()
            val scrollable = device.findObject(By.scrollable(true))
            scrollable?.scroll(Direction.DOWN, 0.85f)
            device.waitForIdle()
            scrollable?.scroll(Direction.UP, 0.85f)

            // Retour Bibliothèque & Réglages
            device.findObject(By.desc("Bibliothèque"))?.click()
            device.waitForIdle()
            device.findObject(By.desc("Plus"))?.click()
            device.waitForIdle()
        }
    }

    companion object {
        private const val TARGET_PACKAGE = "com.aistudio.novay.kxvqrz"
    }
}
