package dot.jcversa.novay.downloads

import android.media.MediaExtractor
import android.media.MediaFormat
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File

/**
 * Remux final d'un épisode téléchargé (V2.9) — LA RECETTE DU BOT novabox,
 * validée par l'owner comme référence de fluidité, portée sur ffmpeg-kit :
 *
 *  - Palier 1 : copie directe (`-c copy`) + `aac_adtstoasc` (ADTS → ASC,
 *    le filtre qui rend l'audio TS lisible en MP4) + `+faststart` (l'index
 *    `moov` passe devant `mdat` : lecture quasi instantanée partout) ;
 *  - palier 2 : audio ré-encodé en AAC 128k si les horodatages résistent ;
 *  - repli honnête : le `.ts` brut (lisible dans VLC et notre lecteur VLC).
 *
 * La partie args/étiquettes est PURE (testable JVM sans binaire ffmpeg) ;
 * seul [remuxWithFfmpeg] touche la bibliothèque native.
 *
 * Diagnostic codec inclus : le MIME vidéo est sondé par MediaExtractor et
 * remonté dans les métadonnées — « HEVC » sur un appareil 32 bits ancien
 * explique un décodage logiciel (son sans image, lag), « H.264 » écarte
 * l'hypothèse.
 */
object NovayRemux {

    /** Palier 1 — copie sans réencodage (le chemin du bot). */
    fun tier1Args(input: String, output: String): Array<String> = arrayOf(
        "-y",
        "-i", input,
        "-c", "copy",
        "-bsf:a", "aac_adtstoasc",
        "-movflags", "+faststart",
        output,
    )

    /** Palier 2 — audio ré-encodé AAC (horodatages audio exotiques). */
    fun tier2Args(input: String, output: String): Array<String> = arrayOf(
        "-y",
        "-i", input,
        "-c:v", "copy",
        "-c:a", "aac",
        "-b:a", "128k",
        "-movflags", "+faststart",
        output,
    )

    /**
     * Exécute les paliers dans l'ordre ; retourne le palier gagnant
     * (1 ou 2), ou 0 si tout a résisté (l'appelant publie le .ts honnête).
     */
    fun remuxWithFfmpeg(input: File, output: File): Int {
        for (tier in 1..2) {
            val args = when (tier) {
                1 -> tier1Args(input.absolutePath, output.absolutePath)
                else -> tier2Args(input.absolutePath, output.absolutePath)
            }
            val session = FFmpegKit.executeWithArguments(args)
            if (ReturnCode.isSuccess(session.returnCode) &&
                output.exists() && output.length() > 0L
            ) {
                return tier
            }
            output.delete()
        }
        return 0
    }

    /**
     * Étiquette lisible du codec vidéo : « H.264 », « HEVC », ou le MIME
     * brut tronqué. Null si inconnu — l'UI n'affiche alors rien.
     */
    fun videoCodecLabel(mime: String?): String? = when {
        mime == null -> null
        mime.startsWith("video/avc", ignoreCase = true) -> "H.264"
        mime.startsWith("video/hevc", ignoreCase = true) -> "HEVC"
        else -> mime.take(12).takeIf { it.isNotBlank() }
    }

    /**
     * SONDE le MIME de la piste vidéo d'un fichier (TS ou fMP4 concaténé)
     * via MediaExtractor — aucune dépendance ffmpeg, aucun réencodage.
     * Peut échouer silencieusement sur un flux exotique : le badge codec
     * est un diagnostic, pas une garantie.
     */
    fun probeVideoMime(file: File): String? = runCatching {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i)
                    .getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/", ignoreCase = true)) return mime
            }
            null
        } finally {
            runCatching { extractor.release() }
        }
    }.getOrNull()
}
