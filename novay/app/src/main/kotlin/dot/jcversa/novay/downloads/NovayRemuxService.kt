package dot.jcversa.novay.downloads

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.ResultReceiver
import java.io.File

/**
 * Service de remux ISOLÉ (V2.9.1) — tourne dans son PROPRE PROCESSUS
 * Android (`:remux`, voir le manifeste).
 *
 * Pourquoi : le crash constaté sur l'appareil du owner (fermeture sauvage
 * à 99 %, puis à chaque ouverture) vient de la finalisation — le remux
 * ffmpeg et/ou la sonde MediaExtractor plantent NATIVEMENT (SIGSEGV) ou
 * lèvent un Error non rattrapable (`UnsatisfiedLinkError`). Dans le
 * processus principal, aucun try/catch ne peut sauver l'app d'un crash
 * natif. Dans ce processus dédié, le pire qui arrive est la mort du
 * processus :remux — le moteur principal la détecte
 * (onServiceDisconnected / délai) et publie le repli honnête .ts.
 *
 * Le résultat revient par ResultReceiver (Parcelable, inter-processus) :
 * palier gagnant (0 = repli .ts) + codec sondé.
 */
class NovayRemuxService : Service() {

    override fun onBind(intent: Intent?): IBinder = Binder()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val inputPath = intent?.getStringExtra(EXTRA_INPUT)
        val outputPath = intent?.getStringExtra(EXTRA_OUTPUT)
        val receiver = intent?.getParcelableExtra<ResultReceiver>(EXTRA_RECEIVER)
        if (inputPath == null || outputPath == null || receiver == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val input = File(inputPath)
        val output = File(outputPath)

        // Paliers ffmpeg (recette du bot). Throwable : même une Error de
        // chargement de la bibliothèque doit ressortir en « repli », pas
        // en crash muet.
        var outcome = runCatching { NovayRemux.remuxWithFfmpeg(input, output) }
            .getOrElse { NovayRemux.Outcome(0, null, NovayRemux.truncateDiagnostic(it.message)) }

        // Palier 3 — repli natif MediaMuxer (éprouvé V2.5→V2.8) si ffmpeg
        // a résisté : les flux TS ressortent quand même en MP4 même sans
        // ffmpeg sur l'appareil.
        if (outcome.tier == 0 &&
            runCatching { NovayRemux.remuxWithMediaMuxer(input, output) }.getOrDefault(false)
        ) {
            outcome = NovayRemux.Outcome(3, null, null)
        }

        // Sonde codec sur le fichier FINAL (remuxé) si possible, sinon sur
        // le flux brut — dans CE processus, un plantage MediaExtractor ne
        // touche pas l'application.
        val codec = runCatching {
            val probed = if (outcome.tier > 0) NovayRemux.probeVideoMime(output) else NovayRemux.probeVideoMime(input)
            NovayRemux.videoCodecLabel(probed)
        }.getOrNull()

        val bundle = Bundle().apply {
            putInt(EXTRA_TIER, outcome.tier)
            putString(EXTRA_CODEC, codec)
            putString(EXTRA_DIAGNOSTIC, outcome.diagnostic)
        }
        runCatching { receiver.send(if (outcome.tier > 0) RESULT_OK else RESULT_FALLBACK, bundle) }
        stopSelf()
        return START_NOT_STICKY
    }

    companion object {
        const val EXTRA_INPUT = "novay.remux.input"
        const val EXTRA_OUTPUT = "novay.remux.output"
        const val EXTRA_RECEIVER = "novay.remux.receiver"
        const val EXTRA_TIER = "novay.remux.tier"
        const val EXTRA_CODEC = "novay.remux.codec"
        const val EXTRA_DIAGNOSTIC = "novay.remux.diagnostic"
        const val RESULT_OK = 1
        const val RESULT_FALLBACK = 0
    }
}
