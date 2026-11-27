package dot.jcversa.novay.data.sama

import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.AnimeSeason
import dot.jcversa.novay.data.ScanChapter
import dot.jcversa.novay.data.ScanPanel
import dot.jcversa.novay.data.CatalogPage
import dot.jcversa.novay.data.GenreCatalog
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * Parseurs ANIME-SAMA — structure validée E2E depuis un runner GitHub
 * Actions le 13/11/2026 (chaîne complète recherche → saisons → épisodes →
 * lecteur → m3u8). Portage des fonctions pures du bot (samaOrigin.ts,
 * animeSources.ts, novabox.ts) adaptées à la NOUVELLE structure du site :
 *
 *  - Recherche : POST /template-php/defaut/fetch.php (form « query ») →
 *    ancres .asn-search-result (href + .asn-search-result-title/subtitle).
 *  - Page anime : appels panneauAnime("Nom", "sous-chemin") — les
 *    placeholders ("nom"/"url") sont ignorés, comme dans le bot.
 *  - Page de saison : tableaux INLINE epsN = ["lien:186672", …] (une liste
 *    par lecteur, positionnels), epsMeta = {"186672":["ansembed.net"], …}
 *    (hôtes par épisode) et var epsJeton = "…" (jeton de session).
 *    NB : episodes.js externe = 404 désormais (les données sont inline).
 *  - URL du lecteur : GET /api/lien.php?j=<jeton>&l=<id> → {"u":"…"}.
 *
 * Le domaine déménage souvent (.com/.me/.to/.si/…) : l'origine se résout
 * via le bouton .btn-primary du lanceur anime-sama.pw (portage de
 * samaOrigin.ts, 9.19h). ATTENTION : la redirection du lanceur ne préserve
 * PAS le chemin (.si → racine .to) — toujours requêter l'origine résolue.
 *
 * AUCUNE dépendance Android ici : testable en JVM pur.
 */
object SamaParsers {

    const val LAUNCHER_URL = "https://anime-sama.pw"

    /** Repli si le lanceur est muet (domaine courant, validé 13/11). */
    const val FALLBACK_ORIGIN = "https://anime-sama.to"

    const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    // ── Origine dynamique (portage samaOrigin.ts) ─────────────────────────

    /** Pur : normalise une URL en origine (protocole + hôte, sans slash final). */
    fun normalizeOrigin(raw: String): String? = try {
        val u = URI(raw.trim())
        if (u.scheme != "http" && u.scheme != "https") null else "${u.scheme}://${u.host}"
    } catch (_: Exception) {
        null
    }

    private val ANCHOR_OPEN_RE = Regex("<a\\b([^>]*)>", RegexOption.IGNORE_CASE)
    private val HREF_ATTR_RE = Regex("href\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
    private val BTN_PRIMARY_CLASS_RE = Regex("class\\s*=\\s*[\"'][^\"']*btn-primary")
    private val TAGS_RE = Regex("<[^>]*>")
    private val MULTI_SPACE_RE = Regex("\\s+")
    private val SEARCH_RESULT_CLASS_RE = Regex("class\\s*=\\s*[\"'][^\"']*asn-search-result")
    private val HEADING_RE = Regex("<h[1-4]\\b[^>]*>([\\s\\S]*?)</h[1-4]>", RegexOption.IGNORE_CASE)
    private val TITLE_CLASS_RE = Regex(
        "<([a-z0-9]+)\\b[^>]*class=[\"'][^\"']*(?:card-title|asn-search-result-title|titre)[^\"']*[\"'][^>]*>([\\s\\S]*?)</\\1>",
        RegexOption.IGNORE_CASE
    )
    private val DATA_IMG_ATTR_RE = Regex("data-img\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
    private val COVER_OEUVRE_RE = Regex(
        "<(?:img|div|section)\\b[^>]*(?:id\\s*=\\s*[\"']coverOeuvre[\"']|class\\s*=\\s*[\"'][^\"']*(?:coverOeuvre|cover-oeuvre|anime-cover)[^\"']*[\"'])[^>]*>",
        RegexOption.IGNORE_CASE,
    )
    private val META_OG_IMAGE_RE = Regex(
        "<meta\\b[^>]+(?:property|name)\\s*=\\s*[\"'](?:og:image(?::secure_url)?|twitter:image)[\"'][^>]*>",
        RegexOption.IGNORE_CASE,
    )
    private val CONTENT_ATTR_RE = Regex("content\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
    private val SLIDE_BG_RE = Regex(
        "<div\\b[^>]*class\\s*=\\s*[\"'][^\"']*ak-slide-bg[^\"']*[\"'][^>]*>([\\s\\S]*?)</div>",
        RegexOption.IGNORE_CASE,
    )
    private val classTextRegexCache = ConcurrentHashMap<String, Regex>()
    private val classTagRegexCache = ConcurrentHashMap<String, Regex>()
    private val attrRegexCache = ConcurrentHashMap<String, Regex>()

    /**
     * Pur : extrait l'origine du bouton lanceur (`.btn-primary`) d'une page.
     */
    fun parseLauncherOrigin(html: String, baseUrl: String): String? {
        for (anchor in ANCHOR_OPEN_RE.findAll(html)) {
            val attrs = anchor.groupValues[1]
            if (!BTN_PRIMARY_CLASS_RE.containsMatchIn(attrs)) continue
            val href = HREF_ATTR_RE.find(attrs)?.groupValues?.get(1) ?: continue
            if (href.isEmpty()) continue
            val abs = try {
                URI(baseUrl).resolve(href).toString()
            } catch (_: Exception) {
                href
            }
            return normalizeOrigin(abs)
        }
        return null
    }

    // ── Recherche (POST fetch.php → .asn-search-result) ───────────────────

    private val ANCHOR_FULL_RE = Regex("<a\\b([^>]*)>([\\s\\S]*?)</a>", RegexOption.IGNORE_CASE)

    /** Texte d'un élément par sa classe (quelle que soit la balise). */
    private fun classText(html: String, className: String): String {
        // Structure réelle (sonde 13/11) : le titre est un <h3>, le
        // sous-titre un <p>, le contenu un <div> — on accepte TOUTE balise
        // (backreference sur la balise ouvrante/fermante).
        val re = classTextRegexCache.getOrPut(className) {
            Regex(
                "<([a-z0-9]+)\\b[^>]*class=[\"'][^\"']*$className[^\"']*[\"'][^>]*>([\\s\\S]*?)</\\1>",
                RegexOption.IGNORE_CASE
            )
        }
        return re.find(html)?.groupValues?.get(2)?.let { stripTags(it) } ?: ""
    }

    private fun stripTags(html: String): String =
        html.replace(TAGS_RE, " ").replace(MULTI_SPACE_RE, " ").trim()

    /**
     * Attribut d'une balise SANS fermeture (img, source…) repérée par sa
     * classe — ex. src de l'affiche .asn-search-result-img. Essaie [attrs]
     * dans l'ordre (src puis data-src : certains sites lazy-loadent).
     */
    private fun classTagAttr(html: String, className: String, vararg attrs: String): String {
        val re = classTagRegexCache.getOrPut(className) {
            Regex(
                "<([a-z0-9]+)\\b[^>]*class=[\"'][^\"']*$className[^\"']*[\"'][^>]*>",
                RegexOption.IGNORE_CASE
            )
        }
        val tag = re.find(html)?.value ?: return ""
        for (attr in attrs) {
            val attrRe = attrRegexCache.getOrPut(attr) {
                Regex("$attr\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
            }
            val v = attrRe.find(tag)?.groupValues?.get(1) ?: continue
            // Un placeholder de lazy-loading (src="data:image/gif;base64…")
            // n'est PAS l'affiche : le vrai chemin vit dans data-src.
            if (v.isNotEmpty() && !v.startsWith("data:")) return v
        }
        return ""
    }

    /**
     * Parse la réponse du fetch.php : ancres .asn-search-result avec leur
     * titre et sous-titre. Les hrefs sont absolus sur le site ; un href
     * relatif est résolu contre [origin].
     */
    fun parseSamaSearch(html: String, origin: String): List<AnimeSearchResult> {
        if (html.isEmpty()) return emptyList()
        val out = mutableListOf<AnimeSearchResult>()
        val seen = mutableSetOf<String>()
        for (anchor in ANCHOR_FULL_RE.findAll(html)) {
            val attrs = anchor.groupValues[1]
            if (!SEARCH_RESULT_CLASS_RE.containsMatchIn(attrs)) continue
            val href = HREF_ATTR_RE.find(attrs)?.groupValues?.get(1) ?: continue
            if (href.isEmpty()) continue
            val url = if (href.startsWith("http")) href else try {
                URI("$origin/").resolve(href).toString()
            } catch (_: Exception) {
                href
            }
            if (!seen.add(url)) continue
            // Titre réel : <h3 class="asn-search-result-title"><mark>…</mark></h3>.
            val title = classText(anchor.groupValues[2], "asn-search-result-title")
            if (title.length < 2) continue
            val rawSubtitle = classText(anchor.groupValues[2], "asn-search-result-subtitle")
            val rawMeta = classText(anchor.groupValues[2], "asn-search-meta")
            val subtitle = rawSubtitle.ifEmpty { rawMeta }
            val combinedMeta = listOf(rawMeta, rawSubtitle).filter { it.isNotBlank() }.joinToString(" · ")
            val extractedMeta = extractCardMetadata(combinedMeta, anchor.groupValues[2])
            // Affiche réelle : <img class="asn-search-result-img" ...>
            // (data-src / data-lazy-src / data-original / src / srcset via extractBestImageUrl).
            val rawPoster = classTagAttr(
                anchor.groupValues[2],
                "asn-search-result-img",
                "data-src",
                "data-lazy-src",
                "data-original",
                "src",
            )
            val posterUrl = dot.jcversa.novay.data.ImageUrlSanitizer.sanitizePosterUrl(rawPoster, origin)
                ?: dot.jcversa.novay.data.ImageUrlSanitizer.extractBestImageUrl(anchor.groupValues[2], origin)
            out.add(
                AnimeSearchResult(
                    title = title,
                    url = url,
                    slug = url.trimEnd('/').substringAfterLast('/', ""),
                    language = extractedMeta.language,
                    subtitle = subtitle.ifEmpty { null },
                    isVf = extractedMeta.language.equals("VF", ignoreCase = true),
                    posterUrl = posterUrl,
                    genres = extractedMeta.genres,
                    format = extractedMeta.format,
                    releaseYear = extractedMeta.releaseYear,
                    status = extractedMeta.status,
                )
            )
            if (out.size >= 30) break
        }
        return out
    }

    /**
     * Parse les cartes d'animés d'une page catalogue / accueil anime-sama
     * (`.../catalogue/<slug>/`) pour alimenter le carrousel « À la une » et
     * la grille complète paginée.
     */
    fun parseSamaCatalogueCards(
        html: String,
        origin: String,
        limit: Int = 60,
    ): List<AnimeSearchResult> {
        if (html.isEmpty()) return emptyList()
        val out = mutableListOf<AnimeSearchResult>()
        val seen = mutableSetOf<String>()

        for (anchor in ANCHOR_FULL_RE.findAll(html)) {
            val attrs = anchor.groupValues[1]
            val body = anchor.groupValues[2]
            val href = HREF_ATTR_RE.find(attrs)?.groupValues?.get(1) ?: continue
            if (href.isEmpty() || !href.contains("/catalogue/", ignoreCase = true)) continue

            val resolved = if (href.startsWith("http")) href else resolveUrl(href, "$origin/")
            val clean = resolved.trimEnd('/')
            // Garder uniquement les fiches racines `/catalogue/<slug>` (pas `/saison1/vf`)
            val afterCat = clean.substringAfter("/catalogue/", missingDelimiterValue = "")
            if (afterCat.isEmpty() || afterCat.contains('/')) continue
            val url = "$clean/"
            if (!seen.add(url)) continue

            val rawTitle = TITLE_CLASS_RE.find(body)?.groupValues?.get(2)?.let { stripTags(it) }
                ?: HEADING_RE.find(body)?.groupValues?.get(1)?.let { stripTags(it) }
                ?: stripTags(body)
            val title = decodeHtmlEntities(rawTitle.trim())
            if (title.length < 2) continue

            val posterUrl = dot.jcversa.novay.data.ImageUrlSanitizer.extractBestImageUrl(body, origin)
                ?: DATA_IMG_ATTR_RE.find(attrs)?.groupValues?.get(1)?.let {
                    dot.jcversa.novay.data.ImageUrlSanitizer.sanitizePosterUrl(it, origin)
                }
            val cardGenres = extractGenresFromChunk(body)
            val bodyWithoutTitle = body.replace(rawTitle, " ")
            val bodyText = decodeHtmlEntities(stripTags(bodyWithoutTitle))
            val extractedMeta = extractCardMetadata(bodyText, body)
            val mergedGenres = if (cardGenres.isNotEmpty()) cardGenres else extractedMeta.genres
            val subtitle = listOfNotNull(
                extractedMeta.format,
                extractedMeta.releaseYear,
                extractedMeta.status,
            ).distinct().joinToString(" · ").takeIf { it.isNotBlank() }

            out.add(
                AnimeSearchResult(
                    title = title,
                    url = url,
                    slug = afterCat,
                    language = extractedMeta.language,
                    subtitle = subtitle,
                    isVf = extractedMeta.language.equals("VF", ignoreCase = true),
                    posterUrl = posterUrl,
                    genres = mergedGenres,
                    format = extractedMeta.format,
                    releaseYear = extractedMeta.releaseYear,
                    status = extractedMeta.status,
                )
            )
            if (out.size >= limit) break
        }
        return out
    }

    /**
     * Parse une page paginée du catalogue (`/catalogue/?page=N`) et détecte
     * s'il existe une page suivante réelle dans les contrôles de pagination.
     */
    fun parseCatalogPage(
        html: String,
        origin: String,
        page: Int,
    ): CatalogPage {
        val items = parseSamaCatalogueCards(html, origin, limit = 60)
        val hasNext = items.isNotEmpty() && detectHasNextCatalogPage(html, page)
        return CatalogPage(
            items = items,
            page = page,
            hasNextPage = hasNext,
        )
    }

    private fun detectHasNextCatalogPage(html: String, currentPage: Int): Boolean {
        if (html.isBlank()) return false
        val nextPage = currentPage + 1
        val nextPageParamRe = Regex("""[?&]page=$nextPage(?:[&#"'\s]|$)""", RegexOption.IGNORE_CASE)
        if (nextPageParamRe.containsMatchIn(html)) return true
        if (Regex("""<link\b[^>]*rel\s*=\s*["']next["']|<a\b[^>]*rel\s*=\s*["']next["']""", RegexOption.IGNORE_CASE).containsMatchIn(html)) {
            return true
        }
        val paginationSection = Regex(
            """<(?:div|nav|ul)\b[^>]*(?:id|class)\s*=\s*["'][^"']*(?:pagination|list_pagination|page-numbers)[^"']*["'][^>]*>([\s\S]{0,4000}?)</(?:div|nav|ul)>""",
            RegexOption.IGNORE_CASE,
        ).find(html)?.groupValues?.get(1) ?: return false
        if (Regex(""">\s*$nextPage\s*<""").containsMatchIn(paginationSection)) return true
        val activeNextLink = Regex(
            """<a\b(?![^>]*disabled)[^>]*>\s*(?:Suivant|Next|»|›)\s*</a>""",
            RegexOption.IGNORE_CASE,
        )
        return activeNextLink.containsMatchIn(paginationSection)
    }

    private data class CardExtractedMeta(
        val language: String? = null,
        val format: String? = null,
        val releaseYear: String? = null,
        val status: String? = null,
        val genres: List<String> = emptyList(),
    )

    private val CARD_LANG_RE = Regex("\\b(VOSTFR|VF)\\b", RegexOption.IGNORE_CASE)
    private val CARD_FORMAT_RE = Regex("\\b(Anime|Film|OAV|Scans|Série)\\b", RegexOption.IGNORE_CASE)
    private val CARD_STATUS_RE = Regex(
        "\\b(En\\s+cours|Termin[ée]|À\\s+venir|A\\s+venir|En\\s+pause)\\b",
        RegexOption.IGNORE_CASE,
    )

    private fun extractCardMetadata(text: String, rawHtml: String): CardExtractedMeta {
        val cleanText = decodeHtmlEntities(text).trim()
        val lang = CARD_LANG_RE.find(cleanText)?.groupValues?.get(1)?.uppercase()
            ?: Regex("""class\s*=\s*["'][^"']*badge[^"']*["'][^>]*>\s*(VOSTFR|VF)\s*<""", RegexOption.IGNORE_CASE)
                .find(rawHtml)?.groupValues?.get(1)?.uppercase()
        val formatRaw = FORMAT_LABEL_RE.find(rawHtml)?.groupValues?.get(1)
            ?.let { decodeHtmlEntities(stripTags(it)).trim() }
            ?: CARD_FORMAT_RE.find(cleanText)?.groupValues?.get(1)
        val format = formatRaw?.let { normalizeFormatLabel(it) }
        val year = YEAR_LABEL_RE.find(rawHtml)?.groupValues?.get(1)
            ?: YEAR_RE.find(cleanText)?.groupValues?.get(1)
        val statusRaw = STATUS_LABEL_RE.find(rawHtml)?.groupValues?.get(1)
            ?.let { decodeHtmlEntities(stripTags(it)).trim() }
            ?: CARD_STATUS_RE.find(cleanText)?.groupValues?.get(1)
        val status = statusRaw?.let { normalizeStatusLabel(it) }
        val knownGenres = cleanText.split(",", "·", "•", "|", " - ", "/")
            .map { it.trim() }
            .filter { GenreCatalog.isKnownCanonicalOrAlias(it) }
            .let { GenreCatalog.normalizeList(it) }
        return CardExtractedMeta(
            language = lang,
            format = format,
            releaseYear = year,
            status = status,
            genres = knownGenres,
        )
    }

    private fun normalizeFormatLabel(raw: String): String? = when {
        raw.equals("anime", ignoreCase = true) || raw.equals("série", ignoreCase = true) -> "Anime"
        raw.equals("film", ignoreCase = true) -> "Film"
        raw.equals("oav", ignoreCase = true) -> "OAV"
        raw.equals("scans", ignoreCase = true) -> "Scans"
        else -> raw.takeIf { it.length in 2..24 }
    }

    private fun normalizeStatusLabel(raw: String): String? = when {
        raw.contains("cours", ignoreCase = true) -> "En cours"
        raw.contains("termin", ignoreCase = true) -> "Terminé"
        raw.contains("venir", ignoreCase = true) -> "À venir"
        raw.contains("pause", ignoreCase = true) -> "En pause"
        else -> raw.takeIf { it.length in 2..28 }
    }

    /**
     * Extrait l'URL canonique de l'affiche depuis une page fiche anime
     * (`/catalogue/<slug>/`) anime-sama (`#coverOeuvre`, `og:image` ou image principale).
     */
    fun parseAnimePoster(html: String, origin: String): String? {
        if (html.isBlank()) return null
        for (coverMatch in COVER_OEUVRE_RE.findAll(html)) {
            val resolved = dot.jcversa.novay.data.ImageUrlSanitizer.extractBestImageUrl(coverMatch.value, origin)
            if (resolved != null) return resolved
        }
        for (metaMatch in META_OG_IMAGE_RE.findAll(html)) {
            val content = CONTENT_ATTR_RE.find(metaMatch.value)?.groupValues?.get(1) ?: continue
            val resolved = dot.jcversa.novay.data.ImageUrlSanitizer.sanitizePosterUrl(content, origin)
            if (resolved != null) return resolved
        }
        return dot.jcversa.novay.data.ImageUrlSanitizer.extractBestImageUrl(html, origin, skipFlagIcons = true)
    }

    private val CATALOG_GENRE_SECTION_RE = Regex(
        "(?:<h[2-4][^>]*>\\s*Genres?\\s*</h[2-4]>|Genres?\\s*:)\\s*([\\s\\S]{0,600}?)(?:</p>|</div>|</section>|<h[2-4])",
        RegexOption.IGNORE_CASE,
    )
    private val CARD_GENRE_P_RE = Regex(
        "<(?:p|span|div)\\b[^>]*class\\s*=\\s*[\"'][^\"']*genre[^\"']*[\"'][^>]*>([\\s\\S]*?)</(?:p|span|div)>",
        RegexOption.IGNORE_CASE,
    )

    private fun extractGenresFromChunk(html: String): List<String> {
        val raw = mutableListOf<String>()
        GENRE_TAG_RE.findAll(html).forEach { m ->
            raw += decodeHtmlEntities(stripTags(m.groupValues[1]))
        }
        CARD_GENRE_P_RE.findAll(html).forEach { m ->
            val txt = decodeHtmlEntities(stripTags(m.groupValues[1]))
            txt.split(",", "·", "•", "/", "|").forEach { raw += it }
        }
        return dot.jcversa.novay.data.GenreCatalog.normalizeList(raw)
    }

    /**
     * Extrait la liste normalisée des genres depuis une page fiche anime
     * (`/catalogue/<slug>/`) anime-sama.
     */
    fun parseAnimeGenres(html: String): List<String> {
        if (html.isEmpty()) return emptyList()
        val raw = mutableListOf<String>()
        raw += extractGenresFromChunk(html)
        CATALOG_GENRE_SECTION_RE.findAll(html).forEach { match ->
            val section = decodeHtmlEntities(stripTags(match.groupValues[1]))
            section.split(",", "·", "•", "|").forEach { token ->
                val cleaned = token.trim()
                if (cleaned.length in 2..36) raw += cleaned
            }
        }
        return dot.jcversa.novay.data.GenreCatalog.normalizeList(raw)
    }

    private val ALT_TITLE_RE = Regex(
        "<(?:h[1-4]|p|span|div)\\b[^>]*(?:id\\s*=\\s*[\"']titreAlter[\"']|class\\s*=\\s*[\"'][^\"']*titreAlter[^\"']*[\"'])[^>]*>([\\s\\S]*?)</(?:h[1-4]|p|span|div)>",
        RegexOption.IGNORE_CASE,
    )
    private val SYNOPSIS_HEADING_P_RE = Regex(
        "<h[2-4][^>]*>\\s*Synopsis\\s*</h[2-4]>\\s*(?:<[^>]+>\\s*)*?<p\\b[^>]*>([\\s\\S]*?)</p>",
        RegexOption.IGNORE_CASE,
    )
    private val SYNOPSIS_CLASS_RE = Regex(
        "<(?:p|div)\\b[^>]*class\\s*=\\s*[\"'][^\"']*(?:synopsis|resume|description-oeuvre)[^\"']*[\"'][^>]*>([\\s\\S]*?)</(?:p|div)>",
        RegexOption.IGNORE_CASE,
    )
    private val RELATED_SECTION_RE = Regex(
        "<h[2-4][^>]*>\\s*(?:(?:Œuvres?|Oeuvres?|Titres?)\\s*(?:associ[ée]e?s?|similaires?|recommand[ée]e?s?)|Recommandations?|Suggestions?)\\s*</h[2-4]>([\\s\\S]{0,6000}?)(?:<h2\\b|</main>|</footer>|$)",
        RegexOption.IGNORE_CASE,
    )
    private val YEAR_RE = Regex("\\b(19[6-9]\\d|20[0-3]\\d)\\b")
    private val STATUS_LABEL_RE = Regex(
        "(?:Statut|Status|État|Etat)\\s*[:\\-]\\s*([^<\\n\\r]{2,40})",
        RegexOption.IGNORE_CASE,
    )
    private val FORMAT_LABEL_RE = Regex(
        "(?:Format|Type|Cat[ée]gorie)\\s*[:\\-]\\s*([^<\\n\\r]{2,36})",
        RegexOption.IGNORE_CASE,
    )
    private val YEAR_LABEL_RE = Regex(
        "(?:Ann[ée]e|Sortie|Date)\\s*[:\\-]\\s*[^<\\n\\r]{0,16}?\\b(19[6-9]\\d|20[0-3]\\d)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val META_SHOW_BLOCK_RE = Regex(
        "<(?:p|div|span)\\b[^>]*class\\s*=\\s*[\"'][^\"']*(?:asn-search-meta|anime-meta|oeuvre-meta|info-oeuvre|badge-info)[^\"']*[\"'][^>]*>([\\s\\S]*?)</(?:p|div|span)>",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Extrait les métadonnées réelles de la page fiche (`/catalogue/<slug>/`)
     * anime-sama sans jamais inventer les champs manquants.
     */
    fun parseAnimeDetails(
        html: String,
        animeUrl: String,
        origin: String,
    ): dot.jcversa.novay.data.AnimeDetails {
        if (html.isBlank()) return dot.jcversa.novay.data.AnimeDetails()
        val posterUrl = parseAnimePoster(html, origin)
        val genres = parseAnimeGenres(html)

        val altTitle = ALT_TITLE_RE.find(html)?.groupValues?.get(1)
            ?.let { decodeHtmlEntities(stripTags(it)).trim() }
            ?.takeIf {
                it.length >= 2 &&
                    !it.equals("nom", ignoreCase = true) &&
                    !it.equals("...", ignoreCase = true)
            }

        val rawSynopsis = SYNOPSIS_HEADING_P_RE.find(html)?.groupValues?.get(1)
            ?: SYNOPSIS_CLASS_RE.find(html)?.groupValues?.get(1)
        val synopsis = rawSynopsis
            ?.let { decodeHtmlEntities(stripTags(it)).trim() }
            ?.takeIf {
                it.length >= 20 &&
                    !it.equals("synopsis", ignoreCase = true) &&
                    !it.contains("aucun synopsis", ignoreCase = true)
            }

        val status = STATUS_LABEL_RE.find(html)?.groupValues?.get(1)
            ?.let { decodeHtmlEntities(stripTags(it)).trim() }
            ?.takeIf { it.length in 2..36 }

        val metaBlockText = META_SHOW_BLOCK_RE.find(html)?.groupValues?.get(1)
            ?.let { decodeHtmlEntities(stripTags(it)).trim() }

        val format = FORMAT_LABEL_RE.find(html)?.groupValues?.get(1)
            ?.let { decodeHtmlEntities(stripTags(it)).trim() }
            ?.takeIf { it.length in 2..32 }
            ?: metaBlockText?.split("·", "|", "•")
                ?.map { it.trim() }
                ?.firstOrNull { token ->
                    token.equals("Anime", ignoreCase = true) ||
                        token.equals("Film", ignoreCase = true) ||
                        token.equals("OAV", ignoreCase = true) ||
                        token.equals("Série", ignoreCase = true)
                }

        val releaseYear = YEAR_LABEL_RE.find(html)?.groupValues?.get(1)
            ?: metaBlockText?.let { YEAR_RE.find(it)?.groupValues?.get(1) }

        val selfKey = animeUrl.trim().trimEnd('/').substringAfter("/catalogue/", "").lowercase()
        val relatedChunk = RELATED_SECTION_RE.find(html)?.groupValues?.get(1).orEmpty()
        val related = if (relatedChunk.isNotBlank()) {
            parseSamaCatalogueCards(relatedChunk, origin)
                .filter { card ->
                    val cardKey = card.url.trim().trimEnd('/').substringAfter("/catalogue/", "").lowercase()
                    cardKey.isNotEmpty() && cardKey != selfKey
                }
                .take(12)
        } else {
            emptyList()
        }

        return dot.jcversa.novay.data.AnimeDetails(
            synopsis = synopsis,
            genres = genres,
            status = status,
            releaseYear = releaseYear,
            format = format,
            alternativeTitle = altTitle,
            posterUrl = posterUrl,
            related = related,
        )
    }

    // ── Variantes de requête (portage 9.18kkk de animeSources.ts) ─────────

    private val QUERY_LEADING_ARTICLE_RE =
        Regex("^(?:the|le|la|les|l'|l’|un|une|des|a|an)\\s+", RegexOption.IGNORE_CASE)
    private val QUERY_NO_INFLECT = setOf(
        "the", "le", "la", "les", "l", "un", "une", "des", "a", "an", "of", "de", "du",
        "et", "and", "no", "not", "in", "en", "to", "au", "aux", "dans", "sur", "for"
    )
    private val RE_ZERO_RE = Regex("^re[a-z]{4,}$", RegexOption.IGNORE_CASE)

    /**
     * Variantes de requête : l'originale TOUJOURS en premier, puis sans
     * l'article initial, « rezero » → « re zero », et fléchis
     * singulier/pluriel des mots significatifs (≥ 4 lettres, hors mots
     * vides). Dédupliquée, bornée à 4.
     */
    fun searchQueryVariants(query: String): List<String> {
        val q = query.trim().replace(MULTI_SPACE_RE, " ")
        if (q.isEmpty()) return emptyList()
        val variants = mutableListOf(q)
        fun push(v: String) {
            val t = v.trim().replace(MULTI_SPACE_RE, " ")
            if (t.isNotEmpty() && !variants.contains(t)) variants.add(t)
        }
        val bare = QUERY_LEADING_ARTICLE_RE.replace(q, "").trim()
        if (bare.isNotEmpty()) push(bare)
        if (RE_ZERO_RE.matches(q)) push("re " + q.substring(2))
        val words = (if (bare.isNotEmpty()) bare else q).split(" ")
        fun inflectable(w: String) = w.length >= 4 && !QUERY_NO_INFLECT.contains(w.lowercase())
        push(
            words.joinToString(" ") { w ->
                val lower = w.lowercase()
                if (inflectable(w) && lower.endsWith("s") &&
                    !(lower.endsWith("ss") || lower.endsWith("us") || lower.endsWith("is"))
                ) w.dropLast(1) else w
            }
        )
        push(
            words.joinToString(" ") { w ->
                if (inflectable(w) && !w.lowercase().endsWith("s")) w + "s" else w
            }
        )
        return variants.take(4)
    }

    // ── Saisons (panneauAnime) ────────────────────────────────────────────

    private val PANNEAU_RE =
        Regex("panneauAnime\\s*\\(\\s*[\"']([^\"']+)[\"']\\s*,\\s*[\"']([^\"']+)[\"']\\s*\\)")
    private val VOSTFR_PATH_RE = Regex("(^|[-/_])vost(?:fr)?([-/_]|$)")
    private val VF_PATH_RE = Regex("(^|[-/_])vf([-/_]|$)")

    /**
     * Langue encodée dans un sous-chemin (« saison1/vostfr » → VOSTFR).
     * Portage exact de samaSubPathLanguage (animeSources.ts).
     */
    fun samaSubPathLanguage(subPath: String): String? {
        val s = subPath.lowercase()
        if (VOSTFR_PATH_RE.containsMatchIn(s)) return "VOSTFR"
        if (VF_PATH_RE.containsMatchIn(s)) return "VF"
        return null
    }

    /**
     * URL de la variante de langue MIROIR d'une saison :
     * « …/saison1/vostfr/ » ⇄ « …/saison1/vf/ ». Null si l'URL ne porte
     * pas de langue structurelle.
     *
     * Terrain 13/11 (sonde CI) : la page anime statique ne liste que la
     * langue par défaut (VOSTFR) — la VF vit à l'URL miroir, qui répond
     * 200 avec tableaux eps + jeton. La bascule du site est client-side ;
     * nous, on sonde l'URL directement.
     *
     * NB : la langue de la saison miroir se lit avec
     * [samaSubPathLanguage] sur l'URL miroir elle-même (…/vf/ = VF).
     */
    fun mirrorLanguageUrl(seasonUrl: String): String? {
        val trimmed = seasonUrl.trimEnd('/')
        return when {
            trimmed.endsWith("/vostfr") -> trimmed.dropLast("vostfr".length) + "vf/"
            trimmed.endsWith("/vf") -> trimmed.dropLast("vf".length) + "vostfr/"
            else -> null
        }
    }

    /**
     * Ordonne les saisons par langue, SANS les mélanger : toutes les VF
     * d'abord (politique du bot : langue de navigation par défaut), puis
     * toutes les VOSTFR, puis les saisons sans langue structurelle.
     * Tri STABLE : l'ordre relatif au sein de chaque groupe est préservé.
     */
    fun orderByLanguage(seasons: List<AnimeSeason>): List<AnimeSeason> {
        val vf = seasons.filter { it.language == "VF" }
        val vostfr = seasons.filter { it.language == "VOSTFR" }
        val others = seasons.filter { it.language == null }
        return vf + vostfr + others
    }

    /**
     * Parse les appels panneauAnime("Nom", "sous-chemin") d'une page anime.
     * Les placeholders de template ("nom"/"url") sont ignorés (comme le
     * bot) ; l'URL de saison = page anime + sous-chemin + "/".
     */
    fun parsePanneauSeasons(html: String, animeUrl: String): List<AnimeSeason> {
        if (html.isEmpty()) return emptyList()
        val base = animeUrl.trimEnd('/') + "/"
        val out = mutableListOf<AnimeSeason>()
        val seen = mutableSetOf<String>()
        for (m in PANNEAU_RE.findAll(html)) {
            val name = m.groupValues[1]
            val subPath = m.groupValues[2]
            if (name.lowercase() == "nom" || subPath.lowercase() == "url") continue
            if (!seen.add(subPath)) continue
            out.add(
                AnimeSeason(
                    name = name,
                    url = base + subPath.trim('/') + "/",
                    language = samaSubPathLanguage(subPath)
                )
            )
        }
        return out
    }

    // ── Scans (panneaux + chapitres) ─────────────────────────────────────

    private val PANNEAU_SCAN_RE =
        Regex("panneauScan\\s*\\(\\s*[\"']([^\"']+)[\"']\\s*,\\s*[\"']([^\"']+)[\"']\\s*\\)")
    private val SCAN_CREATE_LIST_RE = Regex("creerListe\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)")
    private val SCAN_NEW_SP_RE = Regex("newSP\\s*\\(\\s*(?:\"([^\"]*)\"|(\\d+(?:\\.\\d+)?))\\s*\\)")
    private val TITRE_OEUVRE_RE =
        Regex("<h1[^>]*id=\"titreOeuvre\"[^>]*>(.*?)</h1>", RegexOption.DOT_MATCHES_ALL)

    /**
     * Appels panneauScan("Nom", "scan/vf") d'une page d'œuvre. Les
     * sous-chemins « va » (version animée — lien vers l'animé du même
     * univers) sont ignorés, comme le fait l'extension Mihon du site.
     */
    fun parseScanPanels(html: String, oeuvreUrl: String): List<ScanPanel> {
        if (html.isEmpty()) return emptyList()
        val base = oeuvreUrl.trimEnd('/') + "/"
        val out = mutableListOf<ScanPanel>()
        val seen = mutableSetOf<String>()
        for (m in PANNEAU_SCAN_RE.findAll(html)) {
            val name = m.groupValues[1]
            val subPath = m.groupValues[2]
            if (name.lowercase() == "nom" || subPath.lowercase() == "url") continue
            // Dernier segment « va » = lien version animée, pas un scan.
            if (subPath.trim('/').substringAfterLast('/').equals("va", ignoreCase = true)) continue
            if (!seen.add(subPath)) continue
            // Groupe scantrad : contenu de la dernière parenthèse du
            // libellé (« Scans VF (Japscan) » → « Japscan »), sinon le
            // libellé nettoyé des mots Scans/VF/VOSTFR.
            val scanlator = Regex("\\(([^)]*)\\)").findAll(name)
                .lastOrNull()?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
                ?: name.replace(Regex("(Scans|VF|VOSTFR|VA|\\(|\\))"), "").trim()
                    .takeIf { it.isNotEmpty() }
            out.add(
                ScanPanel(
                    name = name,
                    url = base + subPath.trim('/') + "/",
                    language = samaSubPathLanguage(subPath),
                    scanlator = scanlator?.ifEmpty { null }
                )
            )
        }
        return out
    }

    /**
     * Titre d'œuvre réel d'une page de scan : premier nœud texte du
     * `h1#titreOeuvre` (les badges/langues vivent dans des enfants).
     * C'est ce titre que l'API d'images attend en paramètre `oeuvre`.
     */
    fun parseScanOeuvreTitle(html: String): String? {
        val inner = TITRE_OEUVRE_RE.find(html)?.groupValues?.get(1) ?: return null
        val text = inner.substringBefore('<')
            .replace("&amp;", "&")
            .replace("&#39;", "'")
            .replace("&quot;", "\"")
            .trim()
        return text.takeIf { it.isNotEmpty() }
    }

    /**
     * Réponse de /s2/scans/get_nb_chap_et_img.php?oeuvre=… :
     * { "1": 18, "2": 20 } — identifiant de chapitre → nombre de pages.
     */
    fun parseScanImageCounts(json: String): Map<String, Int> {
        if (json.isBlank()) return emptyMap()
        return try {
            val obj = org.json.JSONObject(json)
            val out = mutableMapOf<String, Int>()
            for (key in obj.keys()) {
                val count = obj.optInt(key, 0)
                if (count > 0) out[key] = count
            }
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /**
     * Chapitres d'une page de scan : commandes `creerListe(début, fin)` et
     * `newSP(…)` après `resetListe()`, complétées par le compte de chapitres
     * de l'API (des chapitres existent même quand la page ne les liste pas).
     * Les identifiants sont POSITIONNELS (1 = premier chapitre listé) —
     * c'est la clé du serveur d'images s2/scans/<oeuvre>/<id>/<n>.jpg.
     */
    fun parseScanChapters(
        scanHtml: String,
        imageCounts: Map<String, Int>,
        imageBase: String,
    ): List<ScanChapter> {
        val out = mutableListOf<ScanChapter>()
        var chapterDelay = 0
        if (scanHtml.contains("resetListe()")) {
            for (command in scanHtml.split(";")) {
                val createMatch = SCAN_CREATE_LIST_RE.find(command)
                if (createMatch != null) {
                    val start = createMatch.groupValues[1].toIntOrNull()
                    val end = createMatch.groupValues[2].toIntOrNull()
                    if (start != null && end != null && end >= start) {
                        for (i in start..end) {
                            out += scanChapter(out.size + 1, i.toString(), imageCounts, imageBase)
                        }
                    }
                } else {
                    val spMatch = SCAN_NEW_SP_RE.find(command)
                    if (spMatch != null) {
                        val name = spMatch.groupValues[1].ifEmpty { spMatch.groupValues[2] }
                        if (name.isNotEmpty()) {
                            out += scanChapter(out.size + 1, name, imageCounts, imageBase)
                            chapterDelay++
                        }
                    }
                }
            }
        }
        // Chapitres présents dans l'API mais pas dans les commandes listées.
        while (out.size < imageCounts.size) {
            val number = (out.size + 1 - chapterDelay).toString()
            out += scanChapter(out.size + 1, number, imageCounts, imageBase)
        }
        return out
    }

    private fun scanChapter(
        id: Int,
        number: String,
        imageCounts: Map<String, Int>,
        imageBase: String,
    ) = ScanChapter(
        id = id,
        number = number,
        label = "Chapitre $number",
        pageCount = imageCounts[id.toString()] ?: 0,
        imageUrlPrefix = imageBase.trimEnd('/') + "/$id/"
    )

    // ── Épisodes (tableaux inline + meta + jeton) ─────────────────────────

    private val EPS_ARRAY_RE = Regex("(?:var\\s+)?eps(\\d+)\\s*=\\s*\\[([^\\]]*)\\]")
    private val QUOTED_RE = Regex("'([^']*)'|\"([^\"]*)\"")

    /**
     * Tableaux epsN d'une page de saison : une liste par lecteur,
     * POSITIONNELLE (épisode N = index N-1). Les valeurs sont des
     * « lien:186672 » (nouveau format, résolus via /api/lien.php) ou des
     * URLs de lecteur directes (ancien format episodes.js).
     */
    fun parseEpsArrays(html: String): Map<Int, List<String>> {
        if (html.isEmpty()) return emptyMap()
        val out = LinkedHashMap<Int, List<String>>()
        for (m in EPS_ARRAY_RE.findAll(html)) {
            val id = m.groupValues[1].toIntOrNull() ?: continue
            val values = QUOTED_RE.findAll(m.groupValues[2])
                .mapNotNull { q -> q.groupValues[1].ifEmpty { q.groupValues[2] } }
                .filter { it.isNotBlank() }
                .toList()
            if (values.isNotEmpty()) out[id] = values
        }
        return out
    }

    private val EPS_META_START_RE = Regex("epsMeta\\s*=\\s*\\{")
    private val EPS_META_ENTRY_RE = Regex("\"(\\d+)\"\\s*:\\s*\\[([^]]*)\\]")
    private val LIEN_API_U_RE = Regex("\"u\"\\s*:\\s*\"([^\"]+)\"")

    /**
     * epsMeta = {"186672":["ansembed.net"], …} : hôtes disponibles par
     * identifiant de lien (pour étiqueter les lecteurs).
     */
    fun parseEpsMeta(html: String): Map<String, List<String>> {
        val start = EPS_META_START_RE.find(html)?.range?.last ?: return emptyMap()
        val end = html.indexOf('}', start)
        if (end < 0) return emptyMap()
        val body = html.substring(start + 1, end)
        val out = mutableMapOf<String, List<String>>()
        for (m in EPS_META_ENTRY_RE.findAll(body)) {
            val hosts = QUOTED_RE.findAll(m.groupValues[2])
                .mapNotNull { q -> q.groupValues[1].ifEmpty { q.groupValues[2] } }
                .filter { it.isNotBlank() }
                .toList()
            out[m.groupValues[1]] = hosts
        }
        return out
    }

    private val EPS_JETON_RE = Regex("epsJeton\\s*=\\s*[\"']([^\"']+)[\"']")

    /** Jeton de session de la page de saison (pour /api/lien.php). */
    fun parseEpsJeton(html: String): String? =
        EPS_JETON_RE.find(html)?.groupValues?.get(1)

    // ── API lecteur ───────────────────────────────────────────────────────

    /**
     * Réponse de /api/lien.php : {"u":"https://ansembed.net/embed-….html"}.
     * Une erreur de jeton ({"erreur":"jeton"}) donne null.
     */
    fun parseLienApiResponse(json: String): String? {
        val m = LIEN_API_U_RE.find(json) ?: return null
        return m.groupValues[1].replace("\\/", "/")
    }

    // ── Scan de page lecteur (portage scanPlayerHtmlForStreams) ──────────

    private val DUMMY_STREAM_RE = Regex("bigbuckbunny|sample", RegexOption.IGNORE_CASE)
    private val ABS_M3U8_RE = Regex("https?://[^\\s\"'<>|]+\\.(?:m3u8|txt)(?:[^\\s\"'<>|]*)", RegexOption.IGNORE_CASE)
    private val REL_M3U8_RE = Regex("[\"'\\s=(](/[^\\s\"'<>|]+\\.(?:m3u8|txt)(?:\\?[^\\s\"'<>|]*)?)")
    private val MP4_RE = Regex("https?://[^\\s\"'<>|]+\\.mp4(?:[^\\s\"'<>|]*)", RegexOption.IGNORE_CASE)

    /**
     * Cherche un flux jouable dans une page de lecteur, par fiabilité
     * décroissante : 1. m3u8 absolu (master.m3u8 préféré), 2. m3u8 relatif
     * résolu contre l'origine du lecteur, 3. mp4 direct. Les leurres
     * (bigbuckbunny/sample) sont ignorés.
     */
    fun scanPlayerForStream(html: String, playerOrigin: String): Pair<String, Boolean>? {
        if (html.isEmpty()) return null
        val abs = ABS_M3U8_RE.findAll(html).map { it.value }
            .filter { !DUMMY_STREAM_RE.containsMatchIn(it) }
            .toList()
        abs.firstOrNull { it.contains("master.m3u8", ignoreCase = true) }?.let { return it to true }
        abs.firstOrNull()?.let { return it to true }
        val rel = REL_M3U8_RE.find(html)?.groupValues?.get(1)
        if (rel != null && playerOrigin.isNotEmpty() &&
            !DUMMY_STREAM_RE.containsMatchIn(rel)
        ) {
            return playerOrigin.trimEnd('/') + rel to true
        }
        MP4_RE.findAll(html).map { it.value }
            .firstOrNull { !DUMMY_STREAM_RE.containsMatchIn(it) }
            ?.let { return it to false }
        return null
    }

    // ── Master playlist HLS (délégué à VoiranimeParsers, déjà porté) ─────

    fun parseHlsMasterPlaylist(master: String, masterUrl: String) =
        dot.jcversa.novay.data.voiranime.VoiranimeParsers.parseHlsMasterPlaylist(master, masterUrl)

    /**
     * Union des qualités de plusieurs lecteurs (V2.3) — PUR.
     *
     * Chaque lecteur a sa propre échelle (ex. 1080/480 chez l'un, 720
     * chez l'autre) : on fusionne, on déduplique par hauteur (le
     * PREMIER lecteur qui propose une hauteur gagne — ordre du site),
     * tri par hauteur décroissante.
     */
    fun mergePlayerQualities(streams: List<dot.jcversa.novay.data.ResolvedStream>):
        List<dot.jcversa.novay.data.QualityOption> {
        val seen = mutableMapOf<Int, Int>() // height -> playerIndex
        for ((index, stream) in streams.withIndex()) {
            for (q in stream.qualities) {
                if (q.height > 0 && !seen.containsKey(q.height)) {
                    seen[q.height] = index
                }
            }
        }
        return seen.map { (height, playerIndex) ->
            dot.jcversa.novay.data.QualityOption(
                height = height,
                label = "${height}P",
                playerIndex = playerIndex
            )
        }.sortedByDescending { it.height }
    }

    // ── Page d'accueil : À la une + derniers épisodes (V3) ───────────────
    // Structure réelle (sonde CI 14/11, home 590 Ko aplatie) :
    //  - carrousel héros : <div class="ak-slide"> avec ak-slide-bg (img),
    //    ak-badge--saison, h2.ak-slide-title, ak-genre-tag (genres),
    //    p.ak-slide-synopsis, a.ak-slide-cta (lien de saison) ; le DERNIER
    //    slide est cloné en aria-hidden pour la boucle infinie — ignoré ;
    //  - cartes : <div class="as-carte as-carte-anime" data-url data-nom
    //    data-img> + a.as-carte-lien (saison), as-carte-temps (heure).

    /** Entités HTML courantes (titres réels : « &#039;Tis Time… »). */
    fun decodeHtmlEntities(input: String): String =
        dot.jcversa.novay.data.ImageUrlSanitizer.decodeHtmlEntities(input)

    /** Libellé de saison déduit d'une URL : « Saison 4 », « Film », « OAV ». */
    fun seasonLabelFromUrl(url: String): String? {
        val saison = Regex("/saison([0-9]+)", RegexOption.IGNORE_CASE).find(url)
        if (saison != null) return "Saison " + saison.groupValues[1]
        return when {
            url.contains("/film", ignoreCase = true) -> "Film"
            url.contains("/oav", ignoreCase = true) -> "OAV"
            url.contains("/scan", ignoreCase = true) -> "Scans"
            else -> null
        }
    }

    /**
     * Parse le carrousel « À la une » : chaque ak-slide donne le titre,
     * le badge de saison, les genres, le synopsis et le lien du CTA.
     * Les clones aria-hidden (boucle infinie du carrousel) sont ignorés.
     */
    fun parseHeroSlides(html: String, origin: String): List<dot.jcversa.novay.data.CatalogHero> {
        if (html.isEmpty()) return emptyList()
        val out = mutableListOf<dot.jcversa.novay.data.CatalogHero>()
        val seen = mutableSetOf<String>()
        // Découpe sur l'ouverture des slides ; le 1er morceau précède le
        // carrousel et est ignoré.
        val chunks = html.split(Regex("<div class=\"ak-slide\""))
        for (chunk in chunks.drop(1)) {
            val head = chunk.substringBefore(">")
            if (head.contains("aria-hidden")) continue
            // Titre.
            val title = decodeHtmlEntities(classText(chunk, "ak-slide-title"))
            if (title.length < 2) continue
            // Lien du CTA : l'ancre porte ak-slide-cta (href avant OU
            // après la classe selon le slide).
            val cta = ANCHOR_FULL_RE.findAll(chunk).firstOrNull { a ->
                Regex("class\\s*=\\s*[\"'][^\"']*ak-slide-cta").containsMatchIn(a.groupValues[1])
            }
            val href = cta?.let { HREF_ATTR_RE.find(it.groupValues[1])?.groupValues?.get(1) } ?: ""
            if (href.isEmpty() || seen.add(href) == false) continue
            val url = resolveUrl(href, origin)
            // Image de fond : conteneur .ak-slide-bg prioritaire, sinon première image non-drapeau du slide.
            val bgSection = SLIDE_BG_RE.find(chunk)?.groupValues?.get(1) ?: chunk
            val poster = dot.jcversa.novay.data.ImageUrlSanitizer.extractBestImageUrl(bgSection, origin)
                ?: dot.jcversa.novay.data.ImageUrlSanitizer.extractBestImageUrl(chunk, origin)
            out.add(
                dot.jcversa.novay.data.CatalogHero(
                    title = title,
                    seasonLabel = decodeHtmlEntities(classText(chunk, "ak-badge--saison"))
                        .takeIf { it.isNotEmpty() },
                    genres = GENRE_TAG_RE.findAll(chunk)
                        .map { decodeHtmlEntities(it.groupValues[1].trim()) }
                        .filter { it.isNotEmpty() }
                        .take(6)
                        .toList(),
                    synopsis = decodeHtmlEntities(classText(chunk, "ak-slide-synopsis"))
                        .takeIf { it.isNotEmpty() },
                    seasonUrl = url,
                    posterUrl = poster,
                    language = samaSubPathLanguage(url)
                )
            )
            if (out.size >= 10) break
        }
        return out
    }

    private val GENRE_TAG_RE = Regex(
        "<span\\b[^>]*class=\\s*[\"'][^\"']*ak-genre-tag[\"'][^>]*>([\\s\\S]*?)</span>",
        RegexOption.IGNORE_CASE
    )

    /**
     * Parse les cartes d'animés de la page d'accueil : chaque
     * as-carte-anime porte data-url/data-nom/data-img et un lien vers SA
     * saison la plus récente. Dédupliqué par animé (une carte par animé :
     * VF et VOSTFR arrivent en cartes séparées sur le site).
     */
    fun parseHomeCards(html: String, origin: String): List<dot.jcversa.novay.data.CatalogCard> {
        if (html.isEmpty()) return emptyList()
        val out = mutableListOf<dot.jcversa.novay.data.CatalogCard>()
        val seen = mutableSetOf<String>()
        val chunks = html.split(Regex("<div class=\"as-carte "))
        for (chunk in chunks.drop(1)) {
            if (!chunk.startsWith("as-carte-anime")) continue
            val opening = chunk.substringBefore(">")
            val animeUrl = DATA_URL_ATTR_RE.find(opening)?.groupValues?.get(1) ?: continue
            if (animeUrl.isEmpty() || !seen.add(animeUrl)) continue
            val title = decodeHtmlEntities(
                DATA_NOM_ATTR_RE.find(opening)?.groupValues?.get(1) ?: ""
            )
            if (title.length < 2) continue
            // Lien de saison (as-carte-lien) + miniature (img ou repli data-img) + heure.
            val lien = ANCHOR_FULL_RE.findAll(chunk).firstOrNull { a ->
                Regex("class\\s*=\\s*[\"'][^\"']*as-carte-lien").containsMatchIn(a.groupValues[1])
            }
            val seasonHref = lien?.let { HREF_ATTR_RE.find(it.groupValues[1])?.groupValues?.get(1) }
                ?: animeUrl
            val seasonUrl = resolveUrl(seasonHref, origin)
            val innerImg = dot.jcversa.novay.data.ImageUrlSanitizer.extractBestImageUrl(chunk, origin)
            val dataImg = DATA_IMG_ATTR_RE.find(opening)?.groupValues?.get(1)
                ?.let { dot.jcversa.novay.data.ImageUrlSanitizer.sanitizePosterUrl(it, origin) }
            val thumb = innerImg ?: dataImg
            val time = Regex(
                "<span\\b[^>]*class=\\s*[\"'][^\"']*as-carte-temps[\"'][^>]*>([^<]*)</span>",
                RegexOption.IGNORE_CASE
            ).find(chunk)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
            out.add(
                dot.jcversa.novay.data.CatalogCard(
                    title = title,
                    animeUrl = resolveUrl(animeUrl, origin),
                    seasonUrl = seasonUrl,
                    posterUrl = thumb,
                    timeLabel = time,
                    language = samaSubPathLanguage(seasonUrl)
                )
            )
            if (out.size >= 60) break
        }
        return out
    }

    private val DATA_URL_ATTR_RE = Regex("data-url\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
    private val DATA_NOM_ATTR_RE = Regex("data-nom\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)

        fun resolveUrl(url: String, base: String): String = try {
        URI(base).resolve(url).toString()
    } catch (_: Exception) {
        url
    }
}
