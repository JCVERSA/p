package dot.jcversa.novay.downloads

import dot.jcversa.novay.data.sama.SamaParsers

/**
 * Noyau PUR du téléchargement fichier (V2.5 « novabox ») — testable JVM.
 *
 * Le moteur télécharge la playlist de la VARIANTE choisie (pas le master :
 * embarquer toutes les résolutions pèserait 4× plus lourd), segment par
 * segment, puis publie un vrai fichier dans Download/NoVaY/ du téléphone.
 */
object HlsPlaylist {

    /** Une playlist média : URLs des segments + segment d'init fMP4. */
    data class MediaPlaylist(
        val segmentUrls: List<String>,
        /** EXT-X-MAP (fMP4) : segment d'initialisation, s'il existe. */
        val initSegmentUrl: String?
    ) {
        val isFragmentedMp4: Boolean get() = initSegmentUrl != null
    }

    /** True si la playlist est un MASTER (variantes #EXT-X-STREAM-INF). */
    fun isMasterPlaylist(raw: String): Boolean =
        raw.contains("#EXT-X-STREAM-INF")

    /**
     * Parse une playlist média : chaque ligne non-commentaire non-vide
     * est une URL de segment (résolue contre [baseUrl]) ; #EXT-X-MAP
     * porte le segment d'initialisation fMP4. Les URI par plage
     * (#EXT-X-BYTERANGE) ne sont pas gérées (pas utilisées par les
     * lecteurs du site).
     */
    fun parseMediaPlaylist(raw: String, baseUrl: String): MediaPlaylist? {
        if (raw.isBlank() || isMasterPlaylist(raw)) return null
        val segments = mutableListOf<String>()
        var init: String? = null
        for (line in raw.lines()) {
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> continue
                trimmed.startsWith("#EXT-X-MAP") -> {
                    init = Regex("URI=\"([^\"]+)\"").find(trimmed)
                        ?.groupValues?.get(1)
                        ?.let { SamaParsers.resolveUrl(it, baseUrl) }
                }
                trimmed.startsWith("#") -> continue
                else -> segments.add(SamaParsers.resolveUrl(trimmed, baseUrl))
            }
        }
        if (segments.isEmpty()) return null
        return MediaPlaylist(segmentUrls = segments, initSegmentUrl = init)
    }

    // ── Nom du fichier « façon novabox », lisible ─────────────────────────
    // « Solo Leveling - S01E03 VOSTFR 1080p.mp4 »

    /** Caractères interdits par les systèmes de fichiers → espace. */
    fun sanitizeFileNamePart(part: String): String =
        part.replace(Regex("[\\\\/:*?\"<>|]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /** Numéro de saison depuis l'URL (« /saison4/ » → 4). */
    fun seasonNumberFromUrl(seasonUrl: String): Int? =
        Regex("/saison([0-9]+)", RegexOption.IGNORE_CASE).find(seasonUrl)
            ?.groupValues?.get(1)?.toIntOrNull()

    /** Code lisible de l'épisode : « S01E03 », « Film », « OAV »… */
    fun episodeCode(seasonUrl: String, episodeNumber: Int): String {
        val season = seasonNumberFromUrl(seasonUrl)
        val ep = if (episodeNumber > 0) "E" else ""
        val epNum = if (episodeNumber > 0) "%02d".format(episodeNumber) else ""
        return when {
            season != null && episodeNumber > 0 -> "S%02d".format(season) + ep + epNum
            seasonUrl.contains("/film", true) -> "Film"
            seasonUrl.contains("/oav", true) -> "OAV"
            seasonUrl.contains("/scan", true) -> "Scan"
            else -> ep + epNum // épisode sans saison connue
        }
    }

    /** « 1080P » / « 1080p » → « 1080p » ; « Auto » → omis. */
    fun normalizeQualityLabel(label: String): String {
        val cleaned = sanitizeFileNamePart(label)
        return when {
            cleaned.isEmpty() -> ""
            cleaned.equals("Auto", true) || cleaned.equals("Direct", true) -> ""
            else -> cleaned.lowercase()
        }
    }

    /**
     * Nom du fichier téléchargé — style lisible, validé par le owner :
     * « Solo Leveling - S01E03 VOSTFR 1080p » + extension.
     */
    fun buildEpisodeFileName(
        animeTitle: String,
        seasonUrl: String,
        episodeNumber: Int,
        language: String?,
        qualityLabel: String,
        extension: String = "mp4"
    ): String {
        val anime = sanitizeFileNamePart(animeTitle).ifEmpty { "Anime" }
        val code = episodeCode(seasonUrl, episodeNumber).ifEmpty { "Épisode" }
        val tail = listOfNotNull(
            language?.takeIf { it.isNotBlank() },
            normalizeQualityLabel(qualityLabel).takeIf { it.isNotEmpty() }
        ).joinToString(" ")
        val body = if (tail.isEmpty()) "$anime - $code" else "$anime - $code $tail"
        val ext = extension.trimStart('.').lowercase().ifEmpty { "mp4" }
        return "$body.$ext"
    }

    /**
     * Nom disponible (pas de collision) : « … (2).mp4 », « … (3).mp4 »…
     * pour l'écriture File directe (Android 7-9) ; MediaStore gère
     * tout seul côté Android 10+.
     */
    fun availableFileName(baseName: String, exists: (String) -> Boolean): String {
        if (!exists(baseName)) return baseName
        val dot = baseName.lastIndexOf('.')
        val stem = if (dot > 0) baseName.substring(0, dot) else baseName
        val ext = if (dot > 0) baseName.substring(dot) else ""
        var i = 2
        while (true) {
            val candidate = "$stem ($i)$ext"
            if (!exists(candidate)) return candidate
            i++
        }
    }
}
