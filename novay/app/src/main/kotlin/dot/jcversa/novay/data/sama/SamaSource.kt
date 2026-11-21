package dot.jcversa.novay.data.sama

import dot.jcversa.novay.data.AnimeEpisode
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.AnimeSeason
import dot.jcversa.novay.data.AnimeSource
import dot.jcversa.novay.data.ResolvedStream
import dot.jcversa.novay.data.StreamQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Source anime-sama — origine DYNAMIQUE (le site déménage sans cesse :
 * .com/.me/.to/.si/…) résolue via le bouton du lanceur anime-sama.pw,
 * portage de samaOrigin.ts (9.19h). Structure validée E2E le 13/11 depuis
 * un runner : recherche POST fetch.php, saisons panneauAnime, épisodes
 * inline epsN/epsMeta/epsJeton, lecteurs via /api/lien.php?j=<jeton>&l=<id>,
 * flux m3u8 en clair dans la page lecteur.
 *
 * ATTENTION : la redirection du lanceur ne préserve PAS le chemin
 * (.si → racine .to) — on requête toujours l'origine RÉSOLUE, jamais un
 * domaine codé en dur autre que le repli.
 */
class SamaSource(
    private val http: OkHttpClient = defaultClient(),
    private val clockMs: () -> Long = System::currentTimeMillis
) : AnimeSource {

    override val name: String = "anime-sama"

    companion object {
        const val ORIGIN_TTL_MS = 24L * 60 * 60 * 1000
        private const val HOME_TTL_MS = 10L * 60 * 1000
        private const val FEATURED_TTL_MS = 10L * 60 * 1000
        private const val SEARCH_TTL_MS = 3L * 60 * 1000
        private const val SEASONS_TTL_MS = 10L * 60 * 1000
        private const val EPISODES_TTL_MS = 5L * 60 * 1000
        private val EPS_DECL_RE = Regex("eps\\d+\\s*=")

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .build()
    }

    private class LruTtlCache<K, V>(
        private val maxEntries: Int,
        private val ttlMs: Long,
        private val clockMs: () -> Long
    ) {
        private data class Entry<V>(val value: V, val expiresAtMs: Long)

        private val map = object : LinkedHashMap<K, Entry<V>>(maxEntries + 1, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Entry<V>>?): Boolean =
                size > maxEntries
        }

        @Synchronized
        fun get(key: K): V? {
            val e = map[key] ?: return null
            if (clockMs() >= e.expiresAtMs) {
                map.remove(key)
                return null
            }
            return e.value
        }

        @Synchronized
        fun put(key: K, value: V) {
            map[key] = Entry(value, clockMs() + ttlMs)
        }
    }

    private val originMutex = Mutex()
    @Volatile private var cachedOrigin: String? = null
    @Volatile private var cachedAt = 0L

    private var cachedFeatured: Pair<List<AnimeSearchResult>, Long>? = null


    private var cachedHome: Pair<dot.jcversa.novay.data.CatalogHome, Long>? = null
    private val searchCache = LruTtlCache<String, List<AnimeSearchResult>>(24, SEARCH_TTL_MS, clockMs)
    private val genreCatalogCache = LruTtlCache<String, List<AnimeSearchResult>>(24, FEATURED_TTL_MS, clockMs)
    private val animeGenresCache = LruTtlCache<String, List<String>>(64, SEASONS_TTL_MS, clockMs)
    private val listingPosterCache = LruTtlCache<String, String>(128, SEASONS_TTL_MS, clockMs)
    private val showPagePosterCache = LruTtlCache<String, String>(128, SEASONS_TTL_MS, clockMs)
    private val seasonsCache = LruTtlCache<String, List<AnimeSeason>>(32, SEASONS_TTL_MS, clockMs)
    private val episodesCache = LruTtlCache<String, List<AnimeEpisode>>(32, EPISODES_TTL_MS, clockMs)

    // ── Origine dynamique ─────────────────────────────────────────────────

    private suspend fun origin(): String = withContext(Dispatchers.IO) {
        cachedOrigin?.let { cached ->
            if (clockMs() - cachedAt < ORIGIN_TTL_MS) return@withContext cached
        }
        originMutex.withLock {
            cachedOrigin?.let { cached ->
                if (clockMs() - cachedAt < ORIGIN_TTL_MS) return@withLock cached
            }
            val resolved = runCatching { resolveOriginFromLauncher() }.getOrNull()
            if (resolved != null) {
                cachedOrigin = resolved
                cachedAt = clockMs()
                dot.jcversa.novay.NovayImagePipeline.activeSiteOrigin = resolved
                return@withLock resolved
            }
            // Lanceur muet : dernier domaine connu, sinon repli validé.
            val fallback = cachedOrigin ?: SamaParsers.FALLBACK_ORIGIN
            dot.jcversa.novay.NovayImagePipeline.activeSiteOrigin = fallback
            fallback
        }
    }

    private fun resolveOriginFromLauncher(): String? {
        val launcherHtml = fetchBlocking(SamaParsers.LAUNCHER_URL, "https://anime-sama.pw/")
        val button = SamaParsers.parseLauncherOrigin(launcherHtml, SamaParsers.LAUNCHER_URL)
            ?: return null
        // Le bouton peut rediriger vers le domaine final (.si → .to) :
        // l'origine UTILE est celle de la réponse finale.
        val request = Request.Builder()
            .url(button)
            .header("User-Agent", SamaParsers.UA)
            .build()
        return http.newCall(request).execute().use { resp ->
            // Même un 403 suffit : seul le DOMAINE nous intéresse.
            SamaParsers.normalizeOrigin(resp.request.url.toString())
        }
    }

    // ── HTTP ──────────────────────────────────────────────────────────────

    private fun fetchBlocking(url: String, referer: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", SamaParsers.UA)
            .header("Referer", referer)
            .header("Accept", "text/html")
            .build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} pour $url")
            return resp.body?.string() ?: ""
        }
    }

    private fun postFormBlocking(url: String, fields: Map<String, String>, referer: String): String {
        val form = FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
        val request = Request.Builder()
            .url(url)
            .post(form)
            .header("User-Agent", SamaParsers.UA)
            .header("Referer", referer)
            .header("X-Requested-With", "XMLHttpRequest")
            .build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} pour $url")
            return resp.body?.string() ?: ""
        }
    }

    // ── À la une (catalogue réel) & Recherche ─────────────────────────────


    // ── Page d'accueil (V3) : À la une + derniers épisodes ─────────────
    // Même politique de cache que featured() : 10 minutes en mémoire.
    override suspend fun home(): dot.jcversa.novay.data.CatalogHome = withContext(Dispatchers.IO) {
        val now = clockMs()
        cachedHome?.let { (home, expiresAt) ->
            if (now < expiresAt && (home.heroes.isNotEmpty() || home.cards.isNotEmpty())) {
                return@withContext home
            }
        }
        val base = origin()
        val html = fetchBlocking("$base/", "$base/")
        val home = dot.jcversa.novay.data.CatalogHome(
            heroes = SamaParsers.parseHeroSlides(html, base),
            cards = SamaParsers.parseHomeCards(html, base)
        )
        if (home.heroes.isNotEmpty() || home.cards.isNotEmpty()) {
            home.heroes.forEach { hero ->
                val animeUrl = dot.jcversa.novay.data.ImageUrlSanitizer.extractAnimeUrlFromSeasonUrl(hero.seasonUrl)
                    ?: hero.seasonUrl
                cacheListingPoster(animeUrl, hero.posterUrl)
            }
            home.cards.forEach { card ->
                cacheListingPoster(card.animeUrl, card.posterUrl)
            }
            cachedHome = home to (clockMs() + HOME_TTL_MS)
        }
        home
    }

    override suspend fun featured(): List<AnimeSearchResult> = withContext(Dispatchers.IO) {
        val now = clockMs()
        cachedFeatured?.let { (items, expiresAt) ->
            if (now < expiresAt && items.isNotEmpty()) return@withContext items
        }
        val base = origin()
        val catalogueHtml = runCatching { fetchBlocking("$base/catalogue/", "$base/") }.getOrNull().orEmpty()
        val fromCatalogue = SamaParsers.parseSamaCatalogueCards(catalogueHtml, base)
        val result = if (fromCatalogue.isNotEmpty()) {
            fromCatalogue
        } else {
            val homeHtml = runCatching { fetchBlocking("$base/", "$base/") }.getOrNull().orEmpty()
            SamaParsers.parseSamaCatalogueCards(homeHtml, base)
        }
        if (result.isNotEmpty()) {
            result.forEach { cacheListingPoster(it.url, it.posterUrl) }
            cachedFeatured = result to (clockMs() + FEATURED_TTL_MS)
        }
        result
    }

    override suspend fun genres(anime: AnimeSearchResult): List<String> = withContext(Dispatchers.IO) {
        val cacheKey = anime.url.trimEnd('/')
        animeGenresCache.get(cacheKey)?.let { cached ->
            if (cached.isNotEmpty()) return@withContext cached
        }
        if (anime.genres.isNotEmpty()) {
            val normalized = dot.jcversa.novay.data.GenreCatalog.normalizeList(anime.genres)
            if (normalized.isNotEmpty()) {
                animeGenresCache.put(cacheKey, normalized)
                return@withContext normalized
            }
        }
        val base = origin()
        val html = runCatching { fetchBlocking(anime.url, "$base/") }.getOrNull().orEmpty()
        val parsed = SamaParsers.parseAnimeGenres(html)
        if (parsed.isNotEmpty()) {
            animeGenresCache.put(cacheKey, parsed)
        }
        parsed
    }

    override suspend fun catalogByGenre(genre: String): List<AnimeSearchResult> = withContext(Dispatchers.IO) {
        val canonical = dot.jcversa.novay.data.GenreCatalog.normalize(genre)
        if (canonical.isEmpty()) return@withContext featured()
        val cacheKey = dot.jcversa.novay.data.GenreCatalog.foldKey(canonical)
        genreCatalogCache.get(cacheKey)?.let { return@withContext it }

        val base = origin()
        val queryGenre = dot.jcversa.novay.data.GenreCatalog.samaQueryValue(canonical)
        var networkError: Exception? = null

        val liveCatalogue = try {
            val url = "$base/catalogue/?type%5B%5D=Anime&genre%5B%5D=${urlEncode(queryGenre)}"
            val html = fetchBlocking(url, "$base/catalogue/")
            SamaParsers.parseSamaCatalogueCards(html, base)
        } catch (e: Exception) {
            networkError = e
            emptyList()
        }

        val matchingCards = liveCatalogue.filter { card ->
            card.genres.isEmpty() || dot.jcversa.novay.data.GenreCatalog.matches(card.genres, canonical)
        }.map { card ->
            if (card.genres.isEmpty()) card.copy(genres = listOf(canonical)) else card
        }

        val homeHeroMatches = runCatching { home().heroes }.getOrDefault(emptyList())
            .filter { hero -> dot.jcversa.novay.data.GenreCatalog.matches(hero.genres, canonical) }
            .map { hero ->
                val animeUrl = Regex("(.*?/catalogue/[^/]+/)").find(hero.seasonUrl)?.groupValues?.get(1)
                    ?: hero.seasonUrl
                AnimeSearchResult(
                    title = hero.title,
                    url = animeUrl,
                    slug = animeUrl.trimEnd('/').substringAfterLast('/', ""),
                    language = hero.language,
                    subtitle = hero.genres.joinToString(" · ").takeIf { it.isNotBlank() },
                    posterUrl = hero.posterUrl,
                    genres = dot.jcversa.novay.data.GenreCatalog.normalizeList(hero.genres),
                )
            }

        val merged = (homeHeroMatches + matchingCards).distinctBy { it.url.trimEnd('/') }
        if (merged.isNotEmpty()) {
            genreCatalogCache.put(cacheKey, merged)
            return@withContext merged
        }

        val searchFallback = runCatching { search(queryGenre) }.getOrNull().orEmpty()
            .map { item ->
                if (item.genres.isEmpty()) item.copy(genres = listOf(canonical)) else item
            }
        if (searchFallback.isNotEmpty()) {
            genreCatalogCache.put(cacheKey, searchFallback)
            return@withContext searchFallback
        }

        if (networkError != null) {
            throw IOException("anime-sama : ${networkError.message ?: networkError.javaClass.simpleName}")
        }
        emptyList()
    }

    // ── Recherche ─────────────────────────────────────────────────────────

    override suspend fun search(query: String): List<AnimeSearchResult> = withContext(Dispatchers.IO) {
        val cacheKey = query.trim().lowercase()
        searchCache.get(cacheKey)?.let { return@withContext it }

        val base = origin()
        var networkFailure: Exception? = null
        // Variantes de requête (portage 9.18kkk) : une faute de frappe proche
        // ne doit pas donner 0 résultat.
        for (variant in SamaParsers.searchQueryVariants(query)) {
            try {
                val html = postFormBlocking(
                    "$base/template-php/defaut/fetch.php",
                    mapOf("query" to variant),
                    "$base/"
                )
                val results = SamaParsers.parseSamaSearch(html, base)
                if (results.isNotEmpty()) {
                    results.forEach { cacheListingPoster(it.url, it.posterUrl) }
                    searchCache.put(cacheKey, results)
                    return@withContext results
                }
            } catch (e: Exception) {
                networkFailure = e
            }
        }
        // Échec réseau + zéro résultat = erreur honnête avec la cause réelle.
        networkFailure?.let {
            throw IOException("anime-sama : ${it.message ?: it.javaClass.simpleName}")
        }
        emptyList()
    }

    override suspend fun resolvePosterUrl(anime: AnimeSearchResult): String? =
        withContext(Dispatchers.IO) {
            val key = posterCacheKey(anime.url)
            val failingUrl = dot.jcversa.novay.data.ImageUrlSanitizer.sanitizePosterUrl(anime.posterUrl)

            val showCached = showPagePosterCache.get(key)
            if (showCached != null && showCached != failingUrl) {
                return@withContext showCached
            }

            if (failingUrl == null) {
                val listCached = listingPosterCache.get(key)
                if (listCached != null) return@withContext listCached
            } else {
                val listCached = listingPosterCache.get(key)
                if (listCached != null && listCached != failingUrl) {
                    return@withContext listCached
                }
            }

            val base = origin()
            val showUrl = rewriteMirrorIfNeeded(anime.url, base)
            val html = runCatching { fetchBlocking(showUrl, "$base/") }.getOrNull()
                ?: if (showUrl != anime.url) runCatching { fetchBlocking(anime.url, "$base/") }.getOrNull() else null
            val parsed = html?.let { SamaParsers.parseAnimePoster(it, base) }
            if (parsed != null) {
                showPagePosterCache.put(key, parsed)
                if (parsed != failingUrl) return@withContext parsed
            }

            // Dernier repli : recherche du titre dans le catalogue courant si l'URL d'origine a changé.
            if (anime.title.isNotBlank()) {
                val matches = runCatching { search(anime.title) }.getOrDefault(emptyList())
                val matchedPoster = matches.firstOrNull { posterCacheKey(it.url) == key }?.posterUrl
                    ?: matches.firstOrNull { it.posterUrl != null }?.posterUrl
                val sanitizedMatch = dot.jcversa.novay.data.ImageUrlSanitizer.sanitizePosterUrl(matchedPoster, base)
                if (sanitizedMatch != null && sanitizedMatch != failingUrl) {
                    showPagePosterCache.put(key, sanitizedMatch)
                    return@withContext sanitizedMatch
                }
            }
            parsed ?: failingUrl
        }

    // ── Saisons ───────────────────────────────────────────────────────────

    override suspend fun seasons(anime: AnimeSearchResult): List<AnimeSeason> =
        withContext(Dispatchers.IO) {
            val cacheKey = anime.url.trimEnd('/')
            seasonsCache.get(cacheKey)?.let { return@withContext it }

            val base = origin()
            val targetUrl = rewriteMirrorIfNeeded(anime.url, base)
            val html = fetchBlocking(targetUrl, "$base/")
            val parsedPoster = SamaParsers.parseAnimePoster(html, base)
            if (parsedPoster != null) {
                showPagePosterCache.put(posterCacheKey(anime.url), parsedPoster)
            }
            val parsedGenres = SamaParsers.parseAnimeGenres(html)
            if (parsedGenres.isNotEmpty()) {
                animeGenresCache.put(cacheKey, parsedGenres)
            }
            val panels = SamaParsers.parsePanneauSeasons(html, targetUrl)

            // Terrain 13/11 : la page statique ne liste que la langue par
            // défaut (VOSTFR) — la VF vit à l'URL miroir (.../vf/), validée
            // par sonde CI. On sonde en parallèle la variante miroir de
            // chaque panneau porteur d'une langue.
            val panelUrls = panels.map { it.url }.toSet()
            val probes = panels.mapNotNull { panel ->
                val mirrorUrl = SamaParsers.mirrorLanguageUrl(panel.url) ?: return@mapNotNull null
                if (mirrorUrl in panelUrls) return@mapNotNull null // déjà listée
                async {
                    val season = if (isRealSeasonPage(mirrorUrl, base)) {
                        AnimeSeason(
                            name = panel.name,
                            url = mirrorUrl,
                            // Langue STRUCTURELLE de l'URL miroir elle-même :
                            // …/saison1/vf/ = VF. (Bug 13/11 : l'ancien
                            // mirrorLanguage retournait la langue de
                            // l'AUTRE côté — toute la VF était étiquetée
                            // VOSTFR et la section VF n'existait jamais.)
                            language = SamaParsers.samaSubPathLanguage(mirrorUrl)
                        )
                    } else {
                        null
                    }
                    panel.url to season
                }
            }
            val mirrors = mutableListOf<AnimeSeason>()
            for ((_, mirror) in probes.awaitAll()) {
                if (mirror != null) mirrors.add(mirror)
            }

            // Assemblage : panneaux listés + miroirs réels, PUIS classement
            // par catégorie — toutes les VF, puis toutes les VOSTFR (jamais
            // mélangées, cf. orderByLanguage).
            val ordered = SamaParsers.orderByLanguage(panels + mirrors)
            if (ordered.isNotEmpty()) seasonsCache.put(cacheKey, ordered)
            ordered
        }

    /**
     * Une page de saison miroir est réelle si elle répond et porte des
     * épisodes (jeton ou tableaux eps) — un soft-404 ne compte pas.
     */
    private fun isRealSeasonPage(url: String, base: String): Boolean = try {
        val html = fetchBlocking(url, "$base/")
        html.contains("epsJeton") || EPS_DECL_RE.containsMatchIn(html)
    } catch (_: Exception) {
        false
    }

    // ── Épisodes ──────────────────────────────────────────────────────────

    override suspend fun episodes(season: AnimeSeason): List<AnimeEpisode> =
        withContext(Dispatchers.IO) {
            val cacheKey = season.url.trimEnd('/')
            episodesCache.get(cacheKey)?.let { return@withContext it }

            val base = origin()
            val html = fetchBlocking(season.url, "$base/")
            val lists = SamaParsers.parseEpsArrays(html)
            val jeton = SamaParsers.parseEpsJeton(html) ?: ""
            val total = lists.values.maxOfOrNull { it.size } ?: 0
            // Positionnel : épisode N = index N-1 de chaque liste (lecteur).
            val result = (1..total).map { n ->
                val ids = lists.toSortedMap().values
                    .mapNotNull { it.getOrNull(n - 1) }
                    .filter { it.isNotBlank() }
                AnimeEpisode(
                    number = n,
                    url = season.url,
                    label = "Épisode $n",
                    lienIds = ids,
                    jeton = jeton
                )
            }
            if (result.isNotEmpty()) episodesCache.put(cacheKey, result)
            result
        }

    // ── Flux (api/lien.php → page lecteur → m3u8) ─────────────────────────

    override suspend fun stream(episode: AnimeEpisode): ResolvedStream = withContext(Dispatchers.IO) {
        val base = origin()
        var jeton = episode.jeton
        // Les jetons expirent : sur erreur, on re-charge la page de saison
        // UNE fois pour un jeton frais avant d'abandonner ce lecteur.
        var refreshed = false
        if (episode.lienIds.isEmpty()) {
            throw IOException("Aucun lecteur enregistré pour cet épisode")
        }
        for (id in episode.lienIds) {
            val playerUrl = if (id.startsWith("lien:")) {
                val lienId = id.removePrefix("lien:")
                var u = fetchLienUrl(base, lienId, jeton)
                if (u == null && !refreshed) {
                    refreshed = true
                    val fresh = runCatching { fetchBlocking(episode.url, "$base/") }.getOrNull()
                    val newJeton = fresh?.let { SamaParsers.parseEpsJeton(it) }
                    if (newJeton != null) {
                        jeton = newJeton
                        u = fetchLienUrl(base, lienId, jeton)
                    }
                }
                u
            } else {
                // Ancien format : URL de lecteur directe.
                id
            }
            if (playerUrl == null) continue
            resolveFromPlayerPage(playerUrl, base)?.let { return@withContext it }
        }
        throw IOException("Aucun lecteur n'a livré de flux vidéo pour cet épisode")
    }

    /**
     * Flux de TOUS les lecteurs qui répondent (V2.3) : chaque lecteur en
     * parallèle, l'union de leurs qualités donne le choix de résolution.
     * Un lecteur muet ou mort est simplement absent de la liste — tant
     * qu'au moins un répond, on ne échoue pas.
     */
    override suspend fun streams(episode: AnimeEpisode): List<ResolvedStream> =
        withContext(Dispatchers.IO) {
            val base = origin()
            if (episode.lienIds.isEmpty()) {
                throw IOException("Aucun lecteur enregistré pour cet épisode")
            }
            suspend fun resolveLienUrls(jeton: String): List<String?> = coroutineScope {
                episode.lienIds.map { id ->
                    async {
                        if (id.startsWith("lien:")) {
                            fetchLienUrl(base, id.removePrefix("lien:"), jeton)
                        } else {
                            id
                        }
                    }
                }.awaitAll()
            }

            var playerUrls = resolveLienUrls(episode.jeton).filterNotNull()
            // Si aucun lien n'a répondu, rafraîchir le jeton UNE seule fois et relancer en parallèle.
            if (playerUrls.isEmpty() && episode.lienIds.any { it.startsWith("lien:") }) {
                val fresh = runCatching { fetchBlocking(episode.url, "$base/") }.getOrNull()
                val newJeton = fresh?.let { SamaParsers.parseEpsJeton(it) }
                if (!newJeton.isNullOrEmpty() && newJeton != episode.jeton) {
                    playerUrls = resolveLienUrls(newJeton).filterNotNull()
                }
            }
            if (playerUrls.isEmpty()) {
                throw IOException("Aucun lecteur n'a livré de flux vidéo pour cet épisode")
            }
            val resolved = playerUrls.map { url ->
                async { runCatching { resolveFromPlayerPage(url, base) }.getOrNull() }
            }.awaitAll().filterNotNull()
            if (resolved.isEmpty()) {
                throw IOException("Aucun lecteur n'a livré de flux vidéo pour cet épisode")
            }
            resolved
        }

    /** /api/lien.php : {"u":"<embed>"} — null sur erreur de jeton/réseau. */
    private fun fetchLienUrl(base: String, lienId: String, jeton: String): String? = try {
        val url = "$base/api/lien.php?j=${urlEncode(jeton)}&l=${urlEncode(lienId)}"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", SamaParsers.UA)
            .header("Referer", "$base/")
            .header("X-Requested-With", "XMLHttpRequest")
            .build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            SamaParsers.parseLienApiResponse(resp.body?.string() ?: return null)
        }
    } catch (_: Exception) {
        null
    }

    /** Page lecteur → flux jouable (+ qualités réelles de la master). */
    private fun resolveFromPlayerPage(playerUrl: String, base: String): ResolvedStream? = try {
        val playerOrigin = SamaParsers.normalizeOrigin(playerUrl) ?: base
        val html = fetchBlocking(playerUrl, "$base/")
        val found = SamaParsers.scanPlayerForStream(html, playerOrigin) ?: return null
        val (streamUrl, isHls) = found
        val headers = mapOf(
            "User-Agent" to SamaParsers.UA,
            "Referer" to "$playerOrigin/",
            "Origin" to playerOrigin
        )
        // En-têtes enregistrés pour l'hôte du flux : les téléchargements et
        // la lecture hors-ligne (DataSource media3) les exigeront aussi.
        try {
            val host = java.net.URI(streamUrl).host ?: ""
            if (host.isNotEmpty()) {
                dot.jcversa.novay.downloads.StreamHeadersRegistry.register(host, headers)
            }
        } catch (_: Exception) {
        }
        val qualities = if (isHls) {
            // Les pistes sont optionnelles : l'URL master reste jouable.
            try {
                fetchMasterQualities(streamUrl, headers)
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
        ResolvedStream(url = streamUrl, isHls = isHls, headers = headers, qualities = qualities)
    } catch (_: Exception) {
        null
    }

    private fun fetchMasterQualities(
        masterUrl: String,
        headers: Map<String, String>
    ): List<StreamQuality> {
        val builder = Request.Builder().url(masterUrl)
        headers.forEach { (k, v) -> builder.header(k, v) }
        http.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} pour la master playlist")
            val body = resp.body?.string() ?: return emptyList()
            return SamaParsers.parseHlsMasterPlaylist(body, masterUrl)
        }
    }

    private fun cacheListingPoster(animeUrl: String, posterUrl: String?) {
        val clean = dot.jcversa.novay.data.ImageUrlSanitizer.sanitizePosterUrl(posterUrl) ?: return
        val key = posterCacheKey(animeUrl)
        if (key.isNotEmpty()) listingPosterCache.put(key, clean)
    }

    private fun posterCacheKey(animeUrl: String): String {
        val clean = animeUrl.trim().trimEnd('/')
        val idx = clean.indexOf("/catalogue/", ignoreCase = true)
        return if (idx >= 0) clean.substring(idx).lowercase() else clean
    }

    private fun rewriteMirrorIfNeeded(rawUrl: String, activeBase: String): String {
        val clean = rawUrl.trim()
        if (clean.startsWith("/")) return SamaParsers.resolveUrl(clean, "$activeBase/")
        val idx = clean.indexOf("/catalogue/", ignoreCase = true)
        if (idx > 0) {
            val host = runCatching { java.net.URI(clean).host?.lowercase() }.getOrNull()
            if (host != null && host.startsWith("anime-sama.")) {
                return "${activeBase.trimEnd('/')}${clean.substring(idx)}"
            }
        }
        return clean
    }

    private fun urlEncode(s: String): String = try {
        URLEncoder.encode(s, "UTF-8")
    } catch (_: Exception) {
        s
    }
}
