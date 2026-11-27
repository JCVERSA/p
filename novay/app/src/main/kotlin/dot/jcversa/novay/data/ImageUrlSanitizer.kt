package dot.jcversa.novay.data

import java.net.URI
import java.nio.charset.StandardCharsets

/**
 * Normalisation et extraction centrale des URLs d'affiches / miniatures :
 *  - décode les entités HTML (`&amp;`, `&#039;`, numériques) ;
 *  - rejette les placeholders (`data:image/...`, `spacer.gif`, `"x"`, vide) ;
 *  - résout les URLs relatives (`/img/...`) et sans schéma (`//cdn...`) contre [baseOrigin] ;
 *  - encode en pourcentage les caractères bruts (espaces, apostrophes, accents UTF-8)
 *    sans double-encoder les séquences `%XX` déjà échappées.
 *
 * Pur JVM : réutilisé par les parseurs HTML, le pipeline Coil et les écrans.
 */
object ImageUrlSanitizer {

    private val NAMED_ENTITIES = mapOf(
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to " ",
        "#039" to "'",
        "#39" to "'",
    )
    private val HTML_ENTITY_RE = Regex("&(#?x?[0-9a-zA-Z]+);")

    private val IMG_TAG_RE = Regex("<img\\b([^>]*)>", RegexOption.IGNORE_CASE)
    private val STYLE_BG_RE = Regex(
        "background(?:-image)?\\s*:[^;\"']*url\\(\\s*[\"']?([^\"')]+)[\"']?\\s*\\)",
        RegexOption.IGNORE_CASE,
    )
    private val SRCSET_WHITESPACE_RE = Regex("\\s+")
    private val FLAG_OR_ICON_RE = Regex("(?:flag[a-z0-9_-]*|emoji|icon|avatar-default)\\.svg(?:[?#].*)?$", RegexOption.IGNORE_CASE)
    private val PLACEHOLDER_FILE_RE = Regex("(?:^|/)(?:spacer|pixel|blank|transparent|placeholder|loading|spinner)\\.(?:gif|png|svg)$", RegexOption.IGNORE_CASE)

    private val ATTR_PATTERNS = listOf(
        "data-src",
        "data-lazy-src",
        "data-original",
        "data-img",
        "data-url",
        "src",
    ).map { name ->
        name to Regex("(?:^|\\s)$name\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))", RegexOption.IGNORE_CASE)
    }

    private val SRCSET_ATTR_PATTERNS = listOf(
        "data-srcset",
        "srcset",
    ).map { name ->
        name to Regex("(?:^|\\s)$name\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')", RegexOption.IGNORE_CASE)
    }

    /** Décode les entités HTML nommées et numériques courantes. */
    fun decodeHtmlEntities(input: String): String {
        if (!input.contains('&')) return input
        return HTML_ENTITY_RE.replace(input) { m ->
            val key = m.groupValues[1]
            val known = NAMED_ENTITIES[key] ?: NAMED_ENTITIES[key.lowercase()]
            if (known != null) {
                known
            } else {
                val body = key.removePrefix("#")
                val isHex = body.startsWith("x", ignoreCase = true)
                val digits = if (isHex) body.substring(1) else body
                val code = digits.toIntOrNull(if (isHex) 16 else 10)
                if (code != null && code in 1..0x10FFFF) {
                    String(Character.toChars(code))
                } else {
                    m.value
                }
            }
        }
    }

    /** True si la valeur est vide, factice (`"x"`, `#`) ou un placeholder inline (`data:`). */
    fun isPlaceholderImageUrl(raw: String?): Boolean {
        val cleaned = raw?.trim()?.trim('"', '\'')?.lowercase() ?: return true
        if (cleaned.isEmpty()) return true
        if (cleaned == "x" || cleaned == "#" || cleaned == "null" || cleaned == "undefined" || cleaned == "none") {
            return true
        }
        if (cleaned.startsWith("data:") || cleaned.startsWith("javascript:") || cleaned.startsWith("about:")) {
            return true
        }
        val pathOnly = cleaned.substringBefore('?').substringBefore('#')
        return PLACEHOLDER_FILE_RE.containsMatchIn(pathOnly)
    }

    /**
     * Normalise et sécurise une URL d'image en URL HTTP/HTTPS valide avec encodage
     * RFC-3986 des caractères non échappés (sans double-encoder `%XX`).
     */
    fun sanitizePosterUrl(raw: String?, baseOrigin: String? = null): String? {
        if (raw.isNullOrBlank()) return null
        val decoded = decodeHtmlEntities(raw)
            .replace("\r", "")
            .replace("\n", "")
            .replace("\t", "")
            .trim()
            .trim('"', '\'')
            .trim()
        if (isPlaceholderImageUrl(decoded)) return null

        val cleanBase = normalizeOrigin(baseOrigin)
        val baseScheme = cleanBase?.substringBefore("://") ?: "https"

        val preResolved = when {
            decoded.startsWith("//") -> "$baseScheme:$decoded"
            decoded.startsWith("http://", ignoreCase = true) ||
                decoded.startsWith("https://", ignoreCase = true) -> decoded
            decoded.contains("://") -> return null // Schéma non HTTP (ftp, file, etc.)
            cleanBase != null -> resolveRelativeUrl(decoded, cleanBase) ?: return null
            else -> return null
        }

        val encoded = percentEncodeUnsafe(preResolved)
        return try {
            val uri = URI(encoded)
            val scheme = uri.scheme?.lowercase()
            val host = uri.host
            if ((scheme != "http" && scheme != "https") || host.isNullOrBlank()) {
                null
            } else {
                encoded
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extrait la meilleure URL candidate depuis un attribut `srcset`
     * (privilégie la densité/largeur la plus élevée non placeholder).
     */
    fun parseSrcsetFirstUrl(srcset: String?, baseOrigin: String? = null): String? {
        if (srcset.isNullOrBlank()) return null
        val candidates = splitSrcsetCandidates(srcset)
        var bestUrl: String? = null
        var bestScore = -1
        for (candidate in candidates) {
            val parts = candidate.trim().split(SRCSET_WHITESPACE_RE).filter { it.isNotEmpty() }
            if (parts.isEmpty()) continue
            val sanitized = sanitizePosterUrl(parts[0], baseOrigin) ?: continue
            val descriptor = parts.getOrNull(1)?.lowercase() ?: ""
            val score = when {
                descriptor.endsWith("w") -> descriptor.dropLast(1).toIntOrNull() ?: 1
                descriptor.endsWith("x") -> ((descriptor.dropLast(1).toFloatOrNull() ?: 1f) * 100f).toInt()
                else -> 100
            }
            if (score > bestScore) {
                bestScore = score
                bestUrl = sanitized
            }
        }
        return bestUrl
    }

    /**
     * Extrait la meilleure image depuis un fragment HTML (`<img>` avec `data-src`,
     * `data-lazy-src`, `data-original`, `data-img`, `src`, `srcset` ou CSS `background-image`).
     */
    fun extractBestImageUrl(
        htmlChunk: String,
        baseOrigin: String?,
        skipFlagIcons: Boolean = true,
    ): String? {
        if (htmlChunk.isBlank()) return null
        var svgFallback: String? = null

        for (imgMatch in IMG_TAG_RE.findAll(htmlChunk)) {
            val attrs = imgMatch.groupValues[1]
            val candidate = extractUrlFromImgAttrs(attrs, baseOrigin) ?: continue
            if (skipFlagIcons && isFlagOrIconUrl(candidate)) {
                if (svgFallback == null) svgFallback = candidate
                continue
            }
            return candidate
        }

        for (bgMatch in STYLE_BG_RE.findAll(htmlChunk)) {
            val rawBg = bgMatch.groupValues[1]
            val candidate = sanitizePosterUrl(rawBg, baseOrigin) ?: continue
            if (skipFlagIcons && isFlagOrIconUrl(candidate)) {
                if (svgFallback == null) svgFallback = candidate
                continue
            }
            return candidate
        }

        return if (skipFlagIcons) null else svgFallback
    }

    /**
     * Extrait l'URL racine de la fiche animé (`/catalogue/<slug>/`) depuis une URL de saison.
     */
    fun extractAnimeUrlFromSeasonUrl(seasonUrl: String?): String? {
        if (seasonUrl.isNullOrBlank()) return null
        val m = Regex("(.*?/catalogue/[^/]+/)", RegexOption.IGNORE_CASE).find(seasonUrl.trim())
        if (m != null) return m.groupValues[1]
        val rel = Regex("^(.*?/catalogue/[^/]+)(?:/|$)", RegexOption.IGNORE_CASE).find(seasonUrl.trim())
        return rel?.let { "${it.groupValues[1].trimEnd('/')}/" }
    }

    private fun extractUrlFromImgAttrs(attrs: String, baseOrigin: String?): String? {
        for ((_, regex) in ATTR_PATTERNS) {
            val m = regex.find(attrs) ?: continue
            val raw = m.groupValues[1].ifEmpty { m.groupValues[2].ifEmpty { m.groupValues[3] } }
            val sanitized = sanitizePosterUrl(raw, baseOrigin)
            if (sanitized != null) return sanitized
        }
        for ((_, regex) in SRCSET_ATTR_PATTERNS) {
            val m = regex.find(attrs) ?: continue
            val raw = m.groupValues[1].ifEmpty { m.groupValues[2] }
            val sanitized = parseSrcsetFirstUrl(raw, baseOrigin)
            if (sanitized != null) return sanitized
        }
        return null
    }

    private fun isFlagOrIconUrl(url: String): Boolean {
        val path = url.substringBefore('?').substringBefore('#')
        return FLAG_OR_ICON_RE.containsMatchIn(path) || path.endsWith(".svg", ignoreCase = true)
    }

    private fun splitSrcsetCandidates(srcset: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < srcset.length) {
            val c = srcset[i]
            if (c == ',') {
                val nextNonSpace = srcset.drop(i + 1).trimStart()
                val looksLikeNewEntry = sb.toString().trim().contains(' ') ||
                    nextNonSpace.startsWith("http://", ignoreCase = true) ||
                    nextNonSpace.startsWith("https://", ignoreCase = true) ||
                    nextNonSpace.startsWith("//") ||
                    nextNonSpace.startsWith("/")
                if (looksLikeNewEntry) {
                    out += sb.toString()
                    sb.setLength(0)
                    i++
                    continue
                }
            }
            sb.append(c)
            i++
        }
        if (sb.isNotEmpty()) out += sb.toString()
        return out
    }

    private fun normalizeOrigin(raw: String?): String? {
        val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            val uri = URI(trimmed)
            val scheme = uri.scheme?.lowercase()
            val host = uri.host
            if ((scheme == "http" || scheme == "https") && !host.isNullOrBlank()) {
                val portPart = if (uri.port != -1) ":${uri.port}" else ""
                "$scheme://$host$portPart"
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveRelativeUrl(relative: String, origin: String): String? {
        val cleanRel = relative.trim()
        return try {
            URI("$origin/").resolve(cleanRel).toString()
        } catch (_: Exception) {
            val encodedRel = percentEncodePathFragment(cleanRel)
            try {
                URI("$origin/").resolve(encodedRel).toString()
            } catch (_: Exception) {
                if (cleanRel.startsWith('/')) "$origin$encodedRel" else "$origin/$encodedRel"
            }
        }
    }

    private fun percentEncodeUnsafe(url: String): String {
        val schemeIdx = url.indexOf("://")
        if (schemeIdx <= 0) return percentEncodePathFragment(url)
        val afterScheme = schemeIdx + 3
        val pathStart = url.indexOfAny(charArrayOf('/', '?', '#'), startIndex = afterScheme)
        if (pathStart < 0) return url
        val head = url.substring(0, pathStart)
        val tail = url.substring(pathStart)
        return head + percentEncodePathFragment(tail)
    }

    private fun percentEncodePathFragment(segment: String): String {
        val sb = StringBuilder(segment.length + 8)
        var i = 0
        while (i < segment.length) {
            val c = segment[i]
            if (c == '%' && i + 2 < segment.length &&
                isHexDigit(segment[i + 1]) && isHexDigit(segment[i + 2])
            ) {
                sb.append(segment, i, i + 3)
                i += 3
                continue
            }
            val cp = segment.codePointAt(i)
            val charCount = Character.charCount(cp)
            if (cp < 0x80 && isSafeUrlTailChar(c)) {
                sb.append(c)
            } else {
                val bytes = segment.substring(i, i + charCount).toByteArray(StandardCharsets.UTF_8)
                for (b in bytes) {
                    val v = b.toInt() and 0xFF
                    sb.append('%')
                    sb.append(HEX_CHARS[v ushr 4])
                    sb.append(HEX_CHARS[v and 0x0F])
                }
            }
            i += charCount
        }
        return sb.toString()
    }

    private fun isHexDigit(c: Char): Boolean =
        (c in '0'..'9') || (c in 'a'..'f') || (c in 'A'..'F')

    private fun isSafeUrlTailChar(c: Char): Boolean = when (c) {
        in 'a'..'z', in 'A'..'Z', in '0'..'9',
        '-', '.', '_', '~',
        '/', '?', '#', '&', '=', '+', ',', ';', ':', '@', '!', '$', '(', ')', '*' -> true
        else -> false
    }

    private val HEX_CHARS = "0123456789ABCDEF".toCharArray()
}
