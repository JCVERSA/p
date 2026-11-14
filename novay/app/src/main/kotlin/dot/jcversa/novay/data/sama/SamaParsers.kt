package dot.jcversa.novay.data.sama

import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.AnimeSeason
import java.net.URI

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

    /**
     * Pur : extrait l'origine du bouton lanceur (`.btn-primary`) d'une page.
     */
    fun parseLauncherOrigin(html: String, baseUrl: String): String? {
        for (anchor in ANCHOR_OPEN_RE.findAll(html)) {
            val attrs = anchor.groupValues[1]
            if (!Regex("class\\s*=\\s*[\"'][^\"']*btn-primary").containsMatchIn(attrs)) continue
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
        val re = Regex(
            "<([a-z0-9]+)\\b[^>]*class=[\"'][^\"']*$className[^\"']*[\"'][^>]*>([\\s\\S]*?)</\\1>",
            RegexOption.IGNORE_CASE
        )
        return re.find(html)?.groupValues?.get(2)?.let { stripTags(it) } ?: ""
    }

    private fun stripTags(html: String): String =
        html.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()

    /**
     * Attribut d'une balise SANS fermeture (img, source…) repérée par sa
     * classe — ex. src de l'affiche .asn-search-result-img. Essaie [attrs]
     * dans l'ordre (src puis data-src : certains sites lazy-loadent).
     */
    private fun classTagAttr(html: String, className: String, vararg attrs: String): String {
        val re = Regex(
            "<([a-z0-9]+)\\b[^>]*class=[\"'][^\"']*$className[^\"']*[\"'][^>]*>",
            RegexOption.IGNORE_CASE
        )
        val tag = re.find(html)?.value ?: return ""
        for (attr in attrs) {
            val v = Regex("$attr\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
                .find(tag)?.groupValues?.get(1) ?: continue
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
            if (!Regex("class\\s*=\\s*[\"'][^\"']*asn-search-result").containsMatchIn(attrs)) continue
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
            // Sous-titre réel : <p class="asn-search-result-subtitle">…</p>,
            // repli sur la méta (<p class="asn-search-meta">Anime · 2002</p>).
            val subtitle = classText(anchor.groupValues[2], "asn-search-result-subtitle")
                .ifEmpty { classText(anchor.groupValues[2], "asn-search-meta") }
            // Affiche réelle : <img class="asn-search-result-img" src="…">
            // (data-src en repli — lazy-loading éventuel).
            val posterPath = classTagAttr(
                anchor.groupValues[2], "asn-search-result-img", "src", "data-src"
            )
            val posterUrl = if (posterPath.isEmpty()) null else try {
                URI("$origin/").resolve(posterPath).toString()
            } catch (_: Exception) {
                posterPath
            }
            out.add(
                AnimeSearchResult(
                    title = title,
                    url = url,
                    slug = url.trimEnd('/').substringAfterLast('/', ""),
                    subtitle = subtitle.ifEmpty { null },
                    posterUrl = posterUrl
                )
            )
            if (out.size >= 30) break
        }
        return out
    }

    // ── Variantes de requête (portage 9.18kkk de animeSources.ts) ─────────

    private val QUERY_LEADING_ARTICLE_RE =
        Regex("^(?:the|le|la|les|l'|l’|un|une|des|a|an)\\s+", RegexOption.IGNORE_CASE)
    private val QUERY_NO_INFLECT = setOf(
        "the", "le", "la", "les", "l", "un", "une", "des", "a", "an", "of", "de", "du",
        "et", "and", "no", "not", "in", "en", "to", "au", "aux", "dans", "sur", "for"
    )

    /**
     * Variantes de requête : l'originale TOUJOURS en premier, puis sans
     * l'article initial, « rezero » → « re zero », et fléchis
     * singulier/pluriel des mots significatifs (≥ 4 lettres, hors mots
     * vides). Dédupliquée, bornée à 4.
     */
    fun searchQueryVariants(query: String): List<String> {
        val q = query.trim().replace(Regex("\\s+"), " ")
        if (q.isEmpty()) return emptyList()
        val variants = mutableListOf(q)
        fun push(v: String) {
            val t = v.trim().replace(Regex("\\s+"), " ")
            if (t.isNotEmpty() && !variants.contains(t)) variants.add(t)
        }
        val bare = QUERY_LEADING_ARTICLE_RE.replace(q, "").trim()
        if (bare.isNotEmpty()) push(bare)
        if (Regex("^re[a-z]{4,}$", RegexOption.IGNORE_CASE).matches(q)) push("re " + q.substring(2))
        val words = (if (bare.isNotEmpty()) bare else q).split(" ")
        fun inflectable(w: String) = w.length >= 4 && !QUERY_NO_INFLECT.contains(w.lowercase())
        push(
            words.joinToString(" ") { w ->
                val lower = w.lowercase()
                if (inflectable(w) && lower.endsWith("s") &&
                    !listOf("ss", "us", "is").any { lower.endsWith(it) }
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

    /**
     * Langue encodée dans un sous-chemin (« saison1/vostfr » → VOSTFR).
     * Portage exact de samaSubPathLanguage (animeSources.ts).
     */
    fun samaSubPathLanguage(subPath: String): String? {
        val s = subPath.lowercase()
        if (Regex("(^|[-/_])vost(?:fr)?([-/_]|$)").containsMatchIn(s)) return "VOSTFR"
        if (Regex("(^|[-/_])vf([-/_]|$)").containsMatchIn(s)) return "VF"
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

    private val EPS_META_ENTRY_RE = Regex("\"(\\d+)\"\\s*:\\s*\\[([^]]*)\\]")

    /**
     * epsMeta = {"186672":["ansembed.net"], …} : hôtes disponibles par
     * identifiant de lien (pour étiqueter les lecteurs).
     */
    fun parseEpsMeta(html: String): Map<String, List<String>> {
        val start = Regex("epsMeta\\s*=\\s*\\{").find(html)?.range?.last ?: return emptyMap()
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
        val m = Regex("\"u\"\\s*:\\s*\"([^\"]+)\"").find(json) ?: return null
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

    fun resolveUrl(url: String, base: String): String = try {
        URI(base).resolve(url).toString()
    } catch (_: Exception) {
        url
    }
}
