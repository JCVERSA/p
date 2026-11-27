package dot.jcversa.novay.ui.player

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests unitaires vérifiant la détection des erreurs de révocation / décodage
 * MediaCodec ("Released by resource manager") pour déclencher l'auto-reprise.
 */
class PlayerRecoveryTest {

    @Test
    fun `detecte les codes MediaCodec et decodeur comme recuperables`() {
        assertTrue(
            PlayerStateManager.isCodecReclaimOrDecoderError(
                errorCode = PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED,
                cause = null,
            ),
        )
        assertTrue(
            PlayerStateManager.isCodecReclaimOrDecoderError(
                errorCode = PlaybackException.ERROR_CODE_DECODING_FAILED,
                cause = null,
            ),
        )
        assertTrue(
            PlayerStateManager.isCodecReclaimOrDecoderError(
                errorCode = PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                cause = null,
            ),
        )
        assertTrue(
            PlayerStateManager.isCodecReclaimOrDecoderError(
                errorCode = PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
                cause = null,
            ),
        )
    }

    @Test
    fun `ne classe pas les erreurs reseau comme erreur codec`() {
        assertFalse(
            PlayerStateManager.isCodecReclaimOrDecoderError(
                errorCode = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                cause = IllegalStateException("Network timeout"),
            ),
        )
    }
}
