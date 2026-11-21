package dot.jcversa.novay.downloads

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * V2.9.1 — garde anti-boucle de crash à l'ouverture.
 *
 * Scénario réel constaté sur l'appareil du owner : la finalisation
 * (remux) plantait à 99 %, la tâche restait RUNNING dans l'index, et
 * chaque ouverture de l'app relançait la finalisation → crash en
 * boucle. Une tâche morte pendant la FINALISATION ne doit JAMAIS être
 * relancée automatiquement.
 */
class FileDownloadEngineInterruptedStateTest {

    @Test
    fun `tache morte en finalisation devient PAUSED (tous segments recus)`() {
        assertEquals(
            FileDownloadEngine.State.PAUSED,
            FileDownloadEngine.interruptedStateOnLoad(
                state = FileDownloadEngine.State.RUNNING,
                segmentsDone = 250,
                segmentCount = 250,
            ),
        )
    }

    @Test
    fun `tache morte en plein telechargement redevient QUEUED (reprise normale)`() {
        assertEquals(
            FileDownloadEngine.State.QUEUED,
            FileDownloadEngine.interruptedStateOnLoad(
                state = FileDownloadEngine.State.RUNNING,
                segmentsDone = 42,
                segmentCount = 250,
            ),
        )
    }

    @Test
    fun `tache sans compte de segments reprend comme avant`() {
        assertEquals(
            FileDownloadEngine.State.QUEUED,
            FileDownloadEngine.interruptedStateOnLoad(
                state = FileDownloadEngine.State.RUNNING,
                segmentsDone = 0,
                segmentCount = 0,
            ),
        )
    }

    @Test
    fun `waiting wifi redevient QUEUED`() {
        assertEquals(
            FileDownloadEngine.State.QUEUED,
            FileDownloadEngine.interruptedStateOnLoad(
                state = FileDownloadEngine.State.WAITING_WIFI,
                segmentsDone = 10,
                segmentCount = 250,
            ),
        )
    }

    @Test
    fun `etats stables intouches meme avec segments complets`() {
        for (state in listOf(
            FileDownloadEngine.State.DONE,
            FileDownloadEngine.State.PAUSED,
            FileDownloadEngine.State.FAILED,
            FileDownloadEngine.State.QUEUED,
        )) {
            assertEquals(
                state,
                FileDownloadEngine.interruptedStateOnLoad(state, segmentsDone = 250, segmentCount = 250),
            )
        }
    }
}
