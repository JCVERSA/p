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
import kotlinx.coroutines.withContext
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
    private val http: OkHttpClient = defaultClient()
) : AnimeSource {

    override val name: String = "anime-sama"

    companion object {
        const val ORIGIN_TTL_MS = 24L * 60 * 60 * 1000

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private var cachedOrigin: String? = null
    private var cachedAt = 0L

    // ── Origine dynamique ─────────────────────────────────────────────────

    private suspend fun origin(): String = withContext(Dispatchers.IO) {
        cachedOrigin?.let { cached ->
            if (System.currentTimeMillis() - cachedAt < ORIGIN_TTL_MS) return@withContext cached
        }
        val resolved = runCatching { resolveOriginFromLauncher() }.getOrNull()
        if (resolved != null) {
            cachedOrigin = resolved
            cachedAt = System.currentTimeMillis()
            return@withContext resolved
        }
        // Lanceur muet : dernier domaine connu, sinon repli validé.
        cachedOrigin ?: SamaParsers.FALLBACK_ORIGIN
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

    // ── Recherche ─────────────────────────────────────────────────────────

    override suspend fun search(query: String): List<AnimeSearchResult> = withContext(Dispatchers.IO) {
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
                if (results.isNotEmpty()) return@withContext results
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

    // ── Saisons ───────────────────────────────────────────────────────────

    override suspend fun seasons(anime: AnimeSearchResult): List<AnimeSeason> =
        withContext(Dispatchers.IO) {
            val base = origin()
            val html = fetchBlocking(anime.url, "$base/")
            val panels = SamaParsers.parsePanneauSeasons(html, anime.url)

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
            SamaParsers.orderByLanguage(panels + mirrors)
        }

    /**
     * Une page de saison miroir est réelle si elle répond et porte des
     * épisodes (jeton ou tableaux eps) — un soft-404 ne compte pas.
     */
    private fun isRealSeasonPage(url: String, base: String): Boolean = try {
        val html = fetchBlocking(url, "$base/")
        html.contains("epsJeton") || Regex("eps\\d+\\s*=").containsMatchIn(html)
    } catch (_: Exception) {
        false
    }

    // ── Épisodes ──────────────────────────────────────────────────────────

    override suspend fun episodes(season: AnimeSeason): List<AnimeEpisode> =
        withContext(Dispatchers.IO) {
            val base = origin()
            val html = fetchBlocking(season.url, "$base/")
            val lists = SamaParsers.parseEpsArrays(html)
            val jeton = SamaParsers.parseEpsJeton(html) ?: ""
            val total = lists.values.maxOfOrNull { it.size } ?: 0
            // Positionnel : épisode N = index N-1 de chaque liste (lecteur).
            (1..total).map { n ->
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

    private fun urlEncode(s: String): String = try {
        URLEncoder.encode(s, "UTF-8")
    } catch (_: Exception) {
        s
    }
}
