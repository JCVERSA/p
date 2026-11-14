package dot.jcversa.novay.downloads

import dot.jcversa.novay.data.ResolvedStream
import dot.jcversa.novay.data.sama.SamaParsers
import org.json.JSONObject

/**
 * Métadonnées d'un épisode téléchargé, persistées dans le download
 * (request.data) — PURES (testables JVM, aucune dépendance media3).
 *
 * Format JSON plat : la relecture après redémarrage reconstruit le flux
 * (URL + en-têtes) sans aucune résolution réseau.
 */
data class EpisodeDownloadMeta(
    val animeTitle: String,
    val seasonName: String,
    val seasonUrl: String,
    val episodeNumber: Int,
    val episodeLabel: String,
    val streamUrl: String,
    val isHls: Boolean,
    val headers: Map<String, String>,
    /** V2.5 novabox : URI du fichier publié (content:// ou file://). */
    val localUri: String? = null,
    /** Nom du fichier publié dans Download/NoVaY/. */
    val fileName: String? = null,
    /** Résolution choisie (« 1080p ») — pour le nommage. */
    val qualityLabel: String = ""
) {
    fun toJson(): String = JSONObject().apply {
        put("animeTitle", animeTitle)
        put("seasonName", seasonName)
        put("seasonUrl", seasonUrl)
        put("episodeNumber", episodeNumber)
        put("episodeLabel", episodeLabel)
        put("streamUrl", streamUrl)
        put("isHls", isHls)
        put("ua", headers["User-Agent"] ?: "")
        put("referer", headers["Referer"] ?: "")
        put("origin", headers["Origin"] ?: "")
        put("localUri", localUri ?: "")
        put("fileName", fileName ?: "")
        put("qualityLabel", qualityLabel)
    }.toString()

    fun toStream(): ResolvedStream {
        // V2.5 : l'épisode téléchargé vit dans Download/NoVaY/ du
        // téléphone — on lit LE FICHIER, jamais le réseau.
        val local = localUri?.takeIf { it.isNotBlank() }
        if (local != null) {
            return ResolvedStream(
                url = local,
                isHls = false,
                headers = emptyMap(),
                qualities = emptyList()
            )
        }
        return ResolvedStream(
            url = streamUrl,
            isHls = isHls,
            headers = mapOf(
                "User-Agent" to (headers["User-Agent"] ?: SamaParsers.UA),
                "Referer" to (headers["Referer"] ?: ""),
                "Origin" to (headers["Origin"] ?: "")
            ),
            qualities = emptyList()
        )
    }

    companion object {
        fun fromJson(raw: ByteArray?): EpisodeDownloadMeta? {
            if (raw == null) return null
            return try {
                val o = JSONObject(String(raw, Charsets.UTF_8))
                val headers = buildMap {
                    if (o.optString("ua").isNotEmpty()) put("User-Agent", o.optString("ua"))
                    if (o.optString("referer").isNotEmpty()) put("Referer", o.optString("referer"))
                    if (o.optString("origin").isNotEmpty()) put("Origin", o.optString("origin"))
                }
                EpisodeDownloadMeta(
                    animeTitle = o.optString("animeTitle"),
                    seasonName = o.optString("seasonName"),
                    seasonUrl = o.optString("seasonUrl"),
                    episodeNumber = o.optInt("episodeNumber"),
                    episodeLabel = o.optString("episodeLabel"),
                    streamUrl = o.optString("streamUrl"),
                    isHls = o.optBoolean("isHls"),
                    headers = headers,
                    localUri = o.optString("localUri").ifEmpty { null },
                    fileName = o.optString("fileName").ifEmpty { null },
                    qualityLabel = o.optString("qualityLabel")
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
