package dot.jcversa.novay.downloads

import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File

/**
 * Sonde codec d'un épisode publié (V2.9.3).
 *
 * V2.9.3 : plus AUCUNE transformation des fichiers téléchargés. Le flux
 * assemblé (init éventuel + segments dans l'ordre du serveur) est publié
 * TEL QUEL : « .ts » pour le HLS TS — la forme validée par l'owner
 * (fluide partout : VLC téléphone et PC, lecteur intégré) — et « .mp4 »
 * pour le HLS fMP4 (conteneur déjà MP4). ffmpeg a été retiré (binaire
 * arm32 qui ne chargeait pas sur son appareil, +26 Mo d'APK pour rien)
 * ainsi que le repli maison MediaMuxer (MP4 défectueux : lag et
 * coupures de son constatés sur téléphone ET PC).
 *
 * Ne reste que la sonde : le MIME vidéo de la piste, affiché en badge
 * (« H.264 » / « HEVC ») sur l'écran téléchargements — HEVC sur un
 * appareil ancien 32 bits signifie décodage logiciel. [videoCodecLabel]
 * est PUR (testable JVM sans Android) ; [probeVideoMime] touche
 * MediaExtractor et ne s'exécute QUE dans le processus isolé :probe
 * (NovayProbeService).
 */
object NovayProbe {

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
     * est un diagnostic, pas une garantie. TOUJOURS appeler depuis le
     * processus isolé :probe (crash natif possible, cf. V2.9.1).
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
