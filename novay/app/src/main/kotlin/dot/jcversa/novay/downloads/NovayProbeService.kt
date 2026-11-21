package dot.jcversa.novay.downloads

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.ResultReceiver
import java.io.File

/**
 * Service de sonde codec ISOLÉ (V2.9.3) — tourne dans son PROPRE PROCESSUS
 * Android (`:probe`, voir le manifeste).
 *
 * Pourquoi : MediaExtractor peut planter NATIVEMENT (SIGSEGV) sur des flux
 * exotiques ; dans le processus principal, aucun try/catch ne peut sauver
 * l'app — le crash V2.9 à 99 % l'a prouvé sur l'appareil du owner. Ici, le
 * pire des cas est la mort du processus :probe ; le moteur principal la
 * détecte (onServiceDisconnected / délai) et publie simplement sans badge.
 *
 * V2.9.3 : le remux a été RETIRÉ — ffmpeg ne chargeait pas sur l'appareil
 * du owner et le repli MediaMuxer produisait des MP4 défectueux. Les
 * épisodes sortent désormais en .ts brut ; ce service ne fait plus que
 * la sonde du badge « H.264 / HEVC ».
 */
class NovayProbeService : Service() {

    override fun onBind(intent: Intent?): IBinder = Binder()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val inputPath = intent?.getStringExtra(EXTRA_INPUT)
        val receiver = intent?.getParcelableExtra<ResultReceiver>(EXTRA_RECEIVER)
        if (inputPath == null || receiver == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Dans CE processus, un plantage MediaExtractor ne touche pas
        // l'application — au pire, pas de badge codec.
        val codec = runCatching {
            NovayProbe.videoCodecLabel(NovayProbe.probeVideoMime(File(inputPath)))
        }.getOrNull()

        val bundle = Bundle().apply { putString(EXTRA_CODEC, codec) }
        runCatching { receiver.send(RESULT_OK, bundle) }
        stopSelf()
        return START_NOT_STICKY
    }

    companion object {
        const val EXTRA_INPUT = "novay.probe.input"
        const val EXTRA_RECEIVER = "novay.probe.receiver"
        const val EXTRA_CODEC = "novay.probe.codec"
        const val RESULT_OK = 1
    }
}
