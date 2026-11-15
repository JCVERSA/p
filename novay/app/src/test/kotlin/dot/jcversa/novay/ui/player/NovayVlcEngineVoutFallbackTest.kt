package dot.jcversa.novay.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V2.9.4 — décision du repli de rendu du moteur VLC (PUR, testé JVM).
 *
 * Contexte : l'écran noir « son sans image » constaté sur l'appareil du
 * owner depuis la V2.8 venait de l'attachement de la surface vidéo depuis
 * un thread de fond (contrat @MainThread de l'AWindow libVLC violé).
 * V2.9.4 attache sur le thread principal AVANT play() ; ce test verrouille
 * la décision du chien de garde qui, 3 s après Playing sans AUCUNE sortie
 * vidéo (Event.Vout), bascule automatiquement le rendu en SurfaceView.
 */
class NovayVlcEngineVoutFallbackTest {

    @Test
    fun `repli declenche quand lecture active sans sortie video`() {
        assertTrue(NovayVlcEngine.voutFallbackNeeded(active = true, hadVout = false, viewsAttached = true))
    }

    @Test
    fun `pas de repli si la sortie video existe deja`() {
        assertFalse(NovayVlcEngine.voutFallbackNeeded(active = true, hadVout = true, viewsAttached = true))
    }

    @Test
    fun `pas de repli si la lecture est arretee ou la surface detachee`() {
        assertFalse(NovayVlcEngine.voutFallbackNeeded(active = false, hadVout = false, viewsAttached = true))
        assertFalse(NovayVlcEngine.voutFallbackNeeded(active = true, hadVout = false, viewsAttached = false))
    }

    @Test
    fun `pas de repli apres un arret propre complet`() {
        assertFalse(NovayVlcEngine.voutFallbackNeeded(active = false, hadVout = true, viewsAttached = false))
    }
}
