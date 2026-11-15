package dot.jcversa.novay.downloads

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.nio.ByteBuffer

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

    /**
     * Résultat du remux isolé : palier gagnant (0 = repli .ts), codec sondé
     * et diagnostic éventuel (raison de l'échec ffmpeg, affichée à l'owner).
     */
    data class Outcome(
        val tier: Int,
        val codec: String?,
        val diagnostic: String? = null,
    )

    /**
     * Première ligne utile d'une sortie d'erreur, tronquée proprement
     * (PUR, testable JVM) : l'écran téléchargements affiche la RAISON d'un
     * repli .ts au lieu d'un échec muet.
     */
    fun truncateDiagnostic(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val line = raw.lines().firstOrNull { it.isNotBlank() } ?: return null
        val cleaned = line.trim().replace(Regex("\\s+"), " ")
        return cleaned.take(80).takeIf { it.isNotBlank() }
    }

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
     * (1 ou 2), ou 0 avec la RAISON du dernier échec (l'appelant publie
     * le .ts honnête et affiche le diagnostic).
     */
    fun remuxWithFfmpeg(input: File, output: File): Outcome {
        var lastDiagnostic: String? = null
        for (tier in 1..2) {
            val args = when (tier) {
                1 -> tier1Args(input.absolutePath, output.absolutePath)
                else -> tier2Args(input.absolutePath, output.absolutePath)
            }
            val session = FFmpegKit.executeWithArguments(args)
            if (ReturnCode.isSuccess(session.returnCode) &&
                output.exists() && output.length() > 0L
            ) {
                return Outcome(tier, null)
            }
            lastDiagnostic = truncateDiagnostic(
                session.failStackTrace ?: session.allLogsAsString,
            )
            output.delete()
        }
        return Outcome(0, null, lastDiagnostic)
    }

    /**
     * Palier 3 — repli natif SANS ffmpeg (MediaExtractor + MediaMuxer,
     * le chemin éprouvé V2.5→V2.8, pistes entrelacées comme la sortie
     * ffmpeg du bot). Si ffmpeg ne charge pas sur l'appareil, les flux TS
     * ressortent quand même en MP4.
     */
    fun remuxWithMediaMuxer(input: File, output: File): Boolean {
        val extractor = MediaExtractor()
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        return try {
            extractor.setDataSource(input.absolutePath)
            val trackMap = mutableMapOf<Int, Int>()
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    trackMap[i] = muxer.addTrack(format)
                }
            }
            if (trackMap.isEmpty()) return false
            muxer.start()
            // Entrelacement (la lecon novabox/ffmpeg -c copy) : toutes les
            // pistes selectionnees ENSEMBLE -> echantillons dans l ordre du
            // fichier -> MP4 alterne image/son.
            trackMap.keys.forEach { extractor.selectTrack(it) }
            val maxSample = trackMap.keys.maxOf { i ->
                runCatching {
                    extractor.getTrackFormat(i).getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                }.getOrDefault(0).coerceAtLeast(0)
            }.coerceAtLeast(1 shl 20)
            val buffer = ByteBuffer.allocateDirect(maxSample)
            val info = MediaCodec.BufferInfo()
            while (true) {
                info.offset = 0
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.size = size
                val dstTrack = trackMap[extractor.sampleTrackIndex]
                val timeUs = extractor.sampleTime
                val flags = extractor.sampleFlags and MediaCodec.BUFFER_FLAG_KEY_FRAME
                if (dstTrack != null && timeUs >= 0) {
                    info.presentationTimeUs = timeUs
                    info.flags = flags
                    muxer.writeSampleData(dstTrack, buffer, info)
                }
                extractor.advance()
            }
            muxer.stop()
            true
        } catch (_: Exception) {
            false
        } finally {
            runCatching { muxer.release() }
            runCatching { extractor.release() }
            if (!output.exists() || output.length() == 0L) {
                output.delete()
                return false
            }
        }
    }

    /**
     * Étiquette lisible du codec vidéo : « H.264 », « HEVC », ou le MIME
     * tronqué au premier séparateur. Null si inconnu — l'UI n'affiche rien.
     * HEVC se décline en plusieurs étiquettes MIME (hevc, hev1, hvc1).
     */
    fun videoCodecLabel(mime: String?): String? = when {
        mime == null -> null
        mime.startsWith("video/avc", ignoreCase = true) -> "H.264"
        mime.startsWith("video/hevc", ignoreCase = true) ||
            mime.startsWith("video/hev1", ignoreCase = true) ||
            mime.startsWith("video/hvc1", ignoreCase = true) -> "HEVC"
        else -> mime.takeWhile { it.isLetterOrDigit() || it == '/' }
            .takeIf { it.length > "video/".length }
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
