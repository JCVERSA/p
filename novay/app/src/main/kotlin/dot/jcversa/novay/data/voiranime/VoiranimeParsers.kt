package dot.jcversa.novay.data.voiranime

import dot.jcversa.novay.data.AnimeEpisode
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.StreamQuality

/** Une entrée organisée (saison déduite, null pour les films). */
data class FranchiseEntry(
    val result: AnimeSearchResult,
    val season: Int?,
    val isFilm: Boolean,
    val baseTitle: String,
    val label: String
)

/** Une franchise (anime) regroupant ses saisons et films. */
data class Franchise(
    val baseTitle: String,
    val entries: List<FranchiseEntry>
)
import java.net.URI
import java.util.Base64

/**
 * Parseurs VOIRANIME — portage 1:1 des fonctions pures de
 * `src/bot/services/voiranimeClient.ts` (mêmes regex, mêmes sémantiques,
 * fixtures de test partagées) + sonde Voe de `animeStreamExtractor.ts`.
 *
 * Ground truth (bot, vérifié du VPS 2026-08-31) :
 *  - voir-anime.to répond 200 aux IP résidentielles ; thème Madara.
 *  - Recherche : GET /?s=<q> -> cartes pointant /anime/<slug>/.
 *    VF STRUCTUREL : slug en « -vf ». Chaque saison = une entrée.
 *  - Page anime : liens d'épisodes /anime/<slug>/<ep-slug>-NN-vf/.
 *  - Page épisode : iframe player voembed.net/embed-<code>.html, flux réel
 *    dans un payload chiffré (rot13 + double base64 + ord-3 + reverse).
 *
 * AUCUNE dépendance Android ici : testable en JVM pur.
 */
object VoiranimeParsers {

    const val ORIGIN = "https://voir-anime.to"
    const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    // ── parseVoiranimeSearch ──────────────────────────────────────────────

    private val ANCHOR_RE = Regex("<a\\b([^>]*)>([\\s\\S]*?)</a>", RegexOption.IGNORE_CASE)
    private val HREF_ATTR_RE = Regex("href\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
    private val TITLE_ATTR_RE = Regex("title\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
    private val ANIME_SLUG_URL_RE =
        Regex("^(?:${Regex.escape(ORIGIN)})?/anime/([a-z0-9-]+)/?$", RegexOption.IGNORE_CASE)
    private val GENRE_TITLE_RE =
        Regex("^(action|adventure|comedy|drama|fantasy|horror|mecha|mystery|romance|sci-fi|thriller|vostfr|vf)$", RegexOption.IGNORE_CASE)
    private val VF_TITLE_SUFFIX_RE = Regex("\\s*\\(VF\\)\\s*$", RegexOption.IGNORE_CASE)

    /** True quand un slug/href identifie une entrée VF (structurel, pas un label). */
    fun isVoiranimeVfSlug(slugOrUrl: String): Boolean =
        Regex("-vf/?$").containsMatchIn(slugOrUrl.lowercase())

    /**
     * Parse une page de résultats Madara en entrées anime : liens
     * /anime/<slug>/ (absolus OU relatifs) avec leur titre, dédupliqués,
     * ordre préservé (pertinence), max 30.
     */
    fun parseVoiranimeSearch(html: String): List<AnimeSearchResult> {
        if (html.isEmpty()) return emptyList()
        val out = mutableListOf<AnimeSearchResult>()
        val seen = mutableSetOf<String>()
        for (anchor in ANCHOR_RE.findAll(html)) {
            val attrs = anchor.groupValues[1]
            val href = HREF_ATTR_RE.find(attrs)?.groupValues?.get(1) ?: continue
            if (!href.contains("/anime/")) continue
            val m = ANIME_SLUG_URL_RE.find(href) ?: continue
            val slug = m.groupValues[1]
            if (slug == "anime" || !seen.add(slug)) continue
            val titleAttr = TITLE_ATTR_RE.find(attrs)?.groupValues?.get(1)?.trim() ?: ""
            val text = anchor.groupValues[2].replace(Regex("\\s+"), " ").trim()
            val title = if (titleAttr.isNotEmpty()) titleAttr else text
            if (title.length < 2) continue
            if (GENRE_TITLE_RE.matches(title)) continue
            val isVf = slug.endsWith("-vf")
            out.add(
                AnimeSearchResult(
                    title = VF_TITLE_SUFFIX_RE.replace(title, "").trim(),
                    url = "$ORIGIN/anime/$slug/",
                    slug = slug,
                    language = if (isVf) "VF" else "VOSTFR",
                    isVf = isVf
                )
            )
            if (out.size >= 30) break
        }
        return out
    }

    // ── voiranimeSlugFamily (9.18ppp) ─────────────────────────────────────

    data class SlugFamily(
        val base: String,
        val season: Int?,
        val language: String? // "VF" | "VOSTFR" | null
    )

    private val VOSTFR_TAIL_RE = Regex("(^|[-_])vost(?:fr)?$")
    private val VF_TAIL_RE = Regex("(^|[-_])vf$")
    private val VOSTFR_STRIP_RE = Regex("[-_]vost(?:fr)?$")
    private val VF_STRIP_RE = Regex("[-_]vf$")
    private val SEASON_TAIL_RES = listOf(
        Regex("-saison-(\\d+)$"),
        Regex("-s(\\d+)$"),
        Regex("-r(\\d+)$"),
        Regex("-(\\d+)$")
    )

    /**
     * Décompose un slug en franchise : base (sans langue ni marqueur de
     * saison), saison détectée, langue structurelle.
     * « …-s4-vf » → base « … », saison 4, VF ; « …-vf » → saison null
     * (convention S1) ; « …-3-vf » → saison 3.
     */
    fun voiranimeSlugFamily(slug: String): SlugFamily {
        val s = slug.lowercase().replace(Regex("/+$"), "")
        var language: String? = null
        var rest = s
        if (VOSTFR_TAIL_RE.containsMatchIn(rest)) {
            language = "VOSTFR"
            rest = VOSTFR_STRIP_RE.replace(rest, "")
        } else if (VF_TAIL_RE.containsMatchIn(rest)) {
            language = "VF"
            rest = VF_STRIP_RE.replace(rest, "")
        }
        var season: Int? = null
        val m = SEASON_TAIL_RES.firstNotNullOfOrNull { it.find(rest) }
        if (m != null) {
            season = m.groupValues[1].toIntOrNull()
            rest = rest.substring(0, m.range.first)
        }
        return SlugFamily(base = rest, season = season, language = language)
    }

    // ── organizeVfSelection (9.9) ─────────────────────────────────────────

    private val FILM_SLUG_RE = Regex("(^|[-_])(film|movie|oav)([-_]|$)", RegexOption.IGNORE_CASE)
    private val FILM_TITLE_RE = Regex("\\b(film|movie|oav)\\b", RegexOption.IGNORE_CASE)
    private val COLON_TITLE_RE = Regex("^([^:]{4,}):\\s*(.+)$")
    private val SLUG_SEASON_RES = listOf(
        Regex("-(\\d+)$"),
        Regex("-r(\\d+)$"),
        Regex("-s(\\d+)$"),
        Regex("-saison-(\\d+)$")
    )
    private val TITLE_SEASON_RES = listOf(
        Regex("\\sR(\\d)\\s*$", RegexOption.IGNORE_CASE),
        Regex("\\sS(\\d+)\\s*$", RegexOption.IGNORE_CASE),
        Regex("\\s(?:season|saison)\\s+(\\d+)\\s*$", RegexOption.IGNORE_CASE),
        Regex("\\s(\\d+)\\s*$")
    )
    private val SEASON_MARKER_RE = Regex("^(.*?)[\\s]+[RrSs]?(\\d+)\\s*$")

    /**
     * Retire le marqueur de saison final (« Kimetsu no Yaiba 2 », « … R2 »,
     * « … S2 ») — le « S2 » n'était PAS dépouillé avant 9.18nnn côté bot.
     */
    fun stripSeasonMarker(title: String): String {
        val m = SEASON_MARKER_RE.find(title)
        if (m != null && m.groupValues[1].trim().length >= 2) return m.groupValues[1].trim()
        return title.trim()
    }

    /**
     * Regroupe et étiquette les entrées : par anime (ordre d'apparition),
     * saisons croissantes puis films/OAV. Sans marqueur = saison 1
     * (convention voir-anime).
     */
    fun organizeVfSelection(entries: List<AnimeSearchResult>): List<FranchiseEntry> {
        val analyzed = entries.map { e ->
            val bare = e.slug.replace(Regex("-vf$"), "")
            val isFilm = FILM_SLUG_RE.containsMatchIn(bare) || FILM_TITLE_RE.containsMatchIn(e.title)
            // Préfixe de colon >= 4 caractères — « Re:Zero … » ne doit pas se
            // grouper sous la base « re » avec tous les « Re:… » du catalogue.
            val colon = COLON_TITLE_RE.find(e.title)
            val slugSeason = SLUG_SEASON_RES.firstNotNullOfOrNull { it.find(bare) }
            val titleSeason = TITLE_SEASON_RES.firstNotNullOfOrNull { it.find(e.title) }
            val seasonMatch = slugSeason ?: titleSeason
            val explicit = seasonMatch != null
            val season = if (explicit) seasonMatch!!.groupValues[1].toIntOrNull() ?: 1 else 1

            val groupKey = stripSeasonMarker(if (colon != null) colon.groupValues[1] else e.title)
            val label = when {
                isFilm -> {
                    val sub = if (colon != null) colon.groupValues[2].trim() else ""
                    if (sub.isNotEmpty()) "$groupKey — Film : $sub" else "$groupKey — Film"
                }
                explicit -> "${stripSeasonMarker(e.title)} — Saison $season"
                colon != null -> e.title // sous-titre distinctif SANS marqueur : titre complet, honnête
                else -> "$groupKey — Saison 1" // convention voir-anime
            }
            FranchiseEntry(
                result = e,
                season = if (isFilm) null else season,
                isFilm = isFilm,
                baseTitle = groupKey,
                label = label
            )
        }

        // Groupement par anime (clé = base en minuscules, ordre de 1re apparition).
        val groups = LinkedHashMap<String, MutableList<FranchiseEntry>>()
        for (a in analyzed) {
            groups.getOrPut(a.baseTitle.lowercase()) { mutableListOf() }.add(a)
        }
        val out = mutableListOf<FranchiseEntry>()
        for (g in groups.values) {
            g.sortWith { x, y ->
                when {
                    x.isFilm != y.isFilm -> if (x.isFilm) 1 else -1
                    !x.isFilm -> (x.season ?: 99).compareTo(y.season ?: 99)
                    else -> x.label.compareTo(y.label)
                }
            }
            out.addAll(g)
        }
        return out
    }

    /** Plie les entrées organisées en franchises (saisons + films par anime). */
    fun groupFranchises(results: List<AnimeSearchResult>): List<Franchise> {
        val organized = organizeVfSelection(results)
        val franchises = mutableListOf<Franchise>()
        for (entry in organized) {
            val last = franchises.lastOrNull()
            if (last != null && last.baseTitle.equals(entry.baseTitle, ignoreCase = true)) {
                franchises[franchises.lastIndex] = last.copy(entries = last.entries + entry)
            } else {
                franchises.add(Franchise(baseTitle = entry.baseTitle, entries = listOf(entry)))
            }
        }
        return franchises
    }

    // ── parseVoiranimeEpisodes ────────────────────────────────────────────

    private val EP_NUM_RE = Regex("-(\\d+)-(?:vf|vostfr)$", RegexOption.IGNORE_CASE)
    private val FILM_PREFIX_RE = Regex("^(film|oav|movie)-")
    private val FILM_SUFFIX_RE = Regex("-(film|oav|movie)$", RegexOption.IGNORE_CASE)

    /**
     * Parse une page anime en liste d'épisodes : les URLs finissent en
     * -NN-vf/ ou -NN-vostfr/ ; films/OAV en préfixe sans numéro.
     */
    fun parseVoiranimeEpisodes(html: String, animeUrl: String): List<AnimeEpisode> {
        if (html.isEmpty()) return emptyList()
        val base = animeUrl.trimEnd('/')
        val slug = base.substringAfterLast("/")
        val baseRe = Regex("^${Regex.escape(base)}/(.+)$", RegexOption.IGNORE_CASE)
        val out = mutableListOf<AnimeEpisode>()
        val seen = mutableSetOf<String>()
        for (anchor in ANCHOR_RE.findAll(html)) {
            val href = HREF_ATTR_RE.find(anchor.groupValues[1])?.groupValues?.get(1) ?: continue
            if (!href.contains("/$slug/")) continue
            val m = baseRe.find(href) ?: continue
            val tail = m.groupValues[1].trimEnd('/')
            if (tail.contains("/")) continue // sous-chemin, pas un épisode
            val key = tail.lowercase()
            if (!seen.add(key)) continue

            val epNum = EP_NUM_RE.find(tail)
            if (epNum != null) {
                val n = epNum.groupValues[1].toIntOrNull() ?: continue
                out.add(AnimeEpisode(n, "$base/$tail/", "Épisode $n"))
                continue
            }
            if (FILM_PREFIX_RE.containsMatchIn(key) || FILM_SUFFIX_RE.containsMatchIn(key)) {
                val label = if (key.startsWith("film") || key.contains("film")) "Film" else "OAV"
                out.add(AnimeEpisode(0, "$base/$tail/", label))
            }
        }
        out.sortBy { it.number }
        return out
    }

    // ── parseVoiranimePlayer ──────────────────────────────────────────────

    private val IFRAME_RE = Regex("<iframe\\b([^>]*)>", RegexOption.IGNORE_CASE)
    private val SRC_ATTR_RE = Regex("\\bsrc\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
    private val DATA_SRC_ATTR_RE = Regex("\\bdata-src\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
    private val EMBED_SRC_RE_1 = Regex("^https?://[^/]+/embed-", RegexOption.IGNORE_CASE)
    private val EMBED_SRC_RE_2 = Regex("(embed|player)[^/]*\\.[a-z]{2,}/", RegexOption.IGNORE_CASE)
    private val EMBED_URL_FALLBACK_RE = Regex("https?://[^\\s\"'<>]+/embed-[a-z0-9-]+\\.html", RegexOption.IGNORE_CASE)

    /**
     * Parse une page épisode et retourne l'URL du lecteur intégré
     * (voembed.net ou tout hôte d'embed).
     */
    fun parseVoiranimePlayer(html: String): String? {
        if (html.isEmpty()) return null
        for (iframe in IFRAME_RE.findAll(html)) {
            val attrs = iframe.groupValues[1]
            val src = SRC_ATTR_RE.find(attrs)?.groupValues?.get(1)
                ?: DATA_SRC_ATTR_RE.find(attrs)?.groupValues?.get(1)
                ?: continue
            if (EMBED_SRC_RE_1.containsMatchIn(src) || EMBED_SRC_RE_2.containsMatchIn(src)) return src
        }
        return EMBED_URL_FALLBACK_RE.find(html)?.value
    }

    // ── Sonde Voe (portage de animeStreamExtractor.ts) ────────────────────

    private val NOISE_OPS = listOf("@$", "^^", "~@", "%?", "*~", "!!", "#&")
    private val DUMMY_STREAM_RE = Regex("bigbuckbunny|sample", RegexOption.IGNORE_CASE)
    private val VOE_JSON_SCRIPT_RE = Regex("<script type=\"application/json\">\\s*(\\[[\\s\\S]*?\\])\\s*</script>")
    private val FIRST_JSON_STRING_RE = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"")
    private val VOE_SOURCE_RES = listOf(
        Regex("\"source\"\\s*:\\s*\"([^\"]+)\""),
        Regex("\"direct_access_url\"\\s*:\\s*\"([^\"]+)\"")
    )
    private val PLAIN_M3U8_RE = Regex("[\"'](https?://[^\\s\"']+\\.m3u8[^\\s\"']*)[\"']", RegexOption.IGNORE_CASE)
    private val LOCATION_HREF_RE = Regex("window\\.location\\.href\\s*=\\s*[\"']([^\"']+)[\"']")

    private val KNOWN_NON_VOE = listOf(
        "sibnet", "vidmoly", "lulustream", "luluvdo", "vidzy", "filemoon", "bysesukior",
        "uqload", "ansembed", "embed4me", "sendvid", "oneupload", "movearnpre"
    )

    /** Décodeur base64 tolérant URL-safe (identique b64UrlSafeDecode du bot). */
    private fun b64UrlSafeDecode(s: String): ByteArray {
        var t = s.replace("-", "+").replace("_", "/")
        val r = t.length % 4
        if (r != 0) t += "=".repeat(4 - r)
        return Base64.getDecoder().decode(t)
    }

    private fun rot13(s: String): String {
        val out = StringBuilder(s.length)
        for (c in s) {
            val o = c.code
            out.append(
                when {
                    o in 65..90 -> (((o - 65 + 13) % 26) + 65).toChar()
                    o in 97..122 -> (((o - 97 + 13) % 26) + 97).toChar()
                    else -> c
                }
            )
        }
        return out.toString()
    }

    /**
     * Décodeur de payload voe : rot13 -> strip bruit -> base64 -> (ord-3)
     * -> reverse -> base64 -> JSON. Retourne le JSON décodé en chaîne
     * (l'extraction du champ source se fait par [voeSourceFromJson]).
     */
    fun decodeVoePayload(payload: String): String? {
        return try {
            if (payload.isEmpty()) return null
            var s = rot13(payload)
            for (op in NOISE_OPS) s = s.replace(op, "")
            s = String(b64UrlSafeDecode(s), Charsets.UTF_8)
            s = String(s.map { (it.code - 3).toChar() }.toCharArray())
            s = s.reversed()
            String(b64UrlSafeDecode(s), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    /** Extrait l'URL du flux du JSON décodé (source ou direct_access_url). */
    fun voeSourceFromJson(json: String): String? {
        for (re in VOE_SOURCE_RES) {
            val m = re.find(json) ?: continue
            val src = m.groupValues[1].replace("\\/", "/").replace("\\\"", "\"")
            if (src.isNotEmpty()) return src
        }
        return null
    }

    /** URL de redirection in-page (voe chaîne des pages d'atterrissage). */
    fun parseVoeRedirect(html: String): String? =
        LOCATION_HREF_RE.find(html)?.groupValues?.get(1)

    /**
     * Extrait le flux du payload chiffré d'une page voe : script
     * application/json -> tableau -> 1re chaîne -> décodage -> source.
     * Les flux leurre (bigbuckbunny/sample) sont ignorés.
     */
    fun parseVoePayloadSource(html: String): String? {
        val m = VOE_JSON_SCRIPT_RE.find(html) ?: return null
        val first = FIRST_JSON_STRING_RE.find(m.groupValues[1])?.groupValues?.get(1) ?: return null
        val unescaped = first.replace("\\/", "/").replace("\\\"", "\"").replace("\\\\", "\\")
        val decoded = decodeVoePayload(unescaped) ?: return null
        val src = voeSourceFromJson(decoded) ?: return null
        return if (DUMMY_STREAM_RE.containsMatchIn(src)) null else src
    }

    /** m3u8 « en clair » dans la page (flux leurre ignorés). */
    fun findPlainM3u8(html: String): String? {
        for (m in PLAIN_M3U8_RE.findAll(html)) {
            if (!DUMMY_STREAM_RE.containsMatchIn(m.groupValues[1])) return m.groupValues[1]
        }
        return null
    }

    /** Domains voe / voembed (rotation) — cf. isVoeStyleUrl du bot. */
    fun isVoeStyleUrl(url: String): Boolean {
        val l = url.lowercase()
        if (l.isEmpty()) return false
        if (l.contains("voe")) return true
        if (Regex("^https?://[^/]+/e/[a-z0-9]+").containsMatchIn(l)) {
            return !KNOWN_NON_VOE.any { l.contains(it) }
        }
        return false
    }

    // ── Master playlist HLS ───────────────────────────────────────────────

    private val RESOLUTION_ATTR_RE = Regex("RESOLUTION=(\\d+)x(\\d+)", RegexOption.IGNORE_CASE)

    /**
     * Parse une master playlist HLS en variantes triées par hauteur
     * décroissante — les VRAIES résolutions du lecteur (audit 8.11 côté
     * bot : ne jamais synthesizer des pistes).
     */
    fun parseHlsMasterPlaylist(master: String, masterUrl: String): List<StreamQuality> {
        val lines = master.lines()
        val out = mutableListOf<StreamQuality>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                var j = i + 1
                while (j < lines.size && (lines[j].isBlank() || lines[j].startsWith("#"))) j++
                if (j < lines.size) {
                    val variantUrl = resolveUrl(lines[j].trim(), masterUrl)
                    val height = RESOLUTION_ATTR_RE.find(line)?.groupValues?.get(2)?.toIntOrNull() ?: 0
                    val label = if (height > 0) "${height}P" else "Flux"
                    out.add(StreamQuality(label = label, url = variantUrl, height = height))
                    i = j
                }
            }
            i++
        }
        return out.sortedByDescending { it.height }
    }

    /** Résout une URL de variante (souvent relative) contre la master. */
    fun resolveUrl(url: String, base: String): String = try {
        URI(base).resolve(url).toString()
    } catch (_: Exception) {
        url
    }
}
