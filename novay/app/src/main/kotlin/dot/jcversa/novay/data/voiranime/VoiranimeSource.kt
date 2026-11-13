package dot.jcversa.novay.data.voiranime

import dot.jcversa.novay.data.AnimeEpisode
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.AnimeSource
import dot.jcversa.novay.data.ResolvedStream
import dot.jcversa.novay.data.StreamQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Source voir-anime.to — portage du client du bot (voiranimeClient.ts)
 * sur OkHttp. L'IP résidentielle du téléphone remplace le VPS : les pages
 * HTML répondent 200 (seul /wp-json/ est bloqué Cloudflare, inutile ici).
 */
class VoiranimeSource(
    private val http: OkHttpClient = defaultClient()
) : AnimeSource {

    override val name: String = "voir-anime"

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    // ── HTTP ──────────────────────────────────────────────────────────────

    private fun fetchHtmlBlocking(url: String, referer: String = "${VoiranimeParsers.ORIGIN}/"): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", VoiranimeParsers.UA)
            .header("Referer", referer)
            .header("Accept", "text/html")
            .build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} pour $url")
            return resp.body?.string() ?: ""
        }
    }

    // ── Recherche ─────────────────────────────────────────────────────────

    /**
     * 9.18nnn (terrain) : la recherche générique `/?s=…` rate des titres
     * (« rezero » ne contient pas « Re:Zero » — les deux-points). Le
     * formulaire Madara poste `?s=…&post_type=wp-manga` : on interroge les
     * DEUX en parallèle et on fusionne (dédup par slug).
     */
    override suspend fun search(query: String): List<AnimeSearchResult> = withContext(Dispatchers.IO) {
        val enc = URLEncoder.encode(query, "UTF-8")
        val urls = listOf(
            "${VoiranimeParsers.ORIGIN}/?s=$enc&post_type=wp-manga",
            "${VoiranimeParsers.ORIGIN}/?s=$enc"
        )
        val pages = urls.map { url ->
            async { runCatching { fetchHtmlBlocking(url) }.getOrNull() }
        }.awaitAll()

        val merged = mutableListOf<AnimeSearchResult>()
        val seen = mutableSetOf<String>()
        for (html in pages) {
            if (html == null) continue
            for (entry in VoiranimeParsers.parseVoiranimeSearch(html)) {
                if (seen.add(entry.slug)) merged.add(entry)
            }
        }
        if (merged.isEmpty() && pages.any { it == null }) {
            // Au moins une requête a échoué réseau : erreur honnête plutôt
            // qu'un faux « aucun résultat ».
            throw IOException("Recherche impossible : voir-anime.to injoignable")
        }
        completeFranchises(merged)
    }

    /**
     * 9.18ppp (terrain) : la recherche du site rate des SAISONS entières
     * d'une franchise. Dès qu'une saison est trouvée, on recherche le
     * TITRE DE BASE de la franchise (le titre matche le titre) et on filtre
     * par base de slug. ≤ 3 familles (latence), un échec réseau ne bloque
     * jamais.
     */
    private suspend fun completeFranchises(results: List<AnimeSearchResult>): List<AnimeSearchResult> =
        withContext(Dispatchers.IO) {
            if (results.isEmpty()) return@withContext results
            val families = LinkedHashMap<String, MutableList<AnimeSearchResult>>()
            for (r in results) {
                val base = VoiranimeParsers.voiranimeSlugFamily(r.slug).base
                if (base.isEmpty()) continue
                families.getOrPut(base) { mutableListOf() }.add(r)
            }
            val seen = results.map { it.slug }.toMutableSet()
            val additions = families.entries.take(3).map { (base, members) ->
                async {
                    try {
                        // Titre le plus court de la famille (= titre de base).
                        val title = members
                            .map { VoiranimeParsers.stripSeasonMarker(it.title).replace(Regex("\\s*\\(vf\\)\\s*$", RegexOption.IGNORE_CASE), "").trim() }
                            .filter { it.length >= 3 }
                            .minByOrNull { it.length } ?: return@async emptyList()
                        val enc = URLEncoder.encode(title, "UTF-8")
                        val html = fetchHtmlBlocking("${VoiranimeParsers.ORIGIN}/?s=$enc&post_type=wp-manga")
                        VoiranimeParsers.parseVoiranimeSearch(html).filter { f ->
                            if (f.slug.isEmpty()) return@filter false
                            val fb = VoiranimeParsers.voiranimeSlugFamily(f.slug).base
                            fb == base || fb.startsWith("$base-")
                        }
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            }.awaitAll().flatten()
            // Dédup APRÈS awaitAll : `seen` n'est touché que par le thread
            // appelant (les asyncs parallèles ne le partagent pas).
            val flat = additions.filter { seen.add(it.slug) }.take(maxOf(0, 40 - results.size))
            if (flat.isEmpty()) results else results + flat
        }

    // ── Épisodes ──────────────────────────────────────────────────────────

    override suspend fun episodes(entry: AnimeSearchResult): List<AnimeEpisode> =
        withContext(Dispatchers.IO) {
            VoiranimeParsers.parseVoiranimeEpisodes(fetchHtmlBlocking(entry.url), entry.url)
        }

    // ── Flux (sonde Voe) ──────────────────────────────────────────────────

    /**
     * Résout le flux jouable d'un épisode : page épisode -> iframe lecteur
     * (voembed) -> payload chiffré -> URL HLS/MP4 + qualités réelles lues
     * dans la master playlist.
     */
    override suspend fun stream(episode: AnimeEpisode): ResolvedStream = withContext(Dispatchers.IO) {
        val html = fetchHtmlBlocking(episode.url)
        val playerUrl = VoiranimeParsers.parseVoiranimePlayer(html)
            ?: throw IOException("Aucun lecteur intégré trouvé sur la page de l'épisode")
        val streamUrl = extractVoeStyleStream(playerUrl, depth = 0)
            ?: throw IOException("Flux vidéo introuvable depuis le lecteur")
        val isHls = streamUrl.contains(".m3u8", ignoreCase = true)
        val origin = originOf(playerUrl) ?: VoiranimeParsers.ORIGIN
        val headers = mapOf(
            "User-Agent" to VoiranimeParsers.UA,
            "Referer" to "$origin/",
            "Origin" to origin
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
    }

    /**
     * Chaîne voe : redirections in-page (≤ 3) -> payload application/json
     * -> m3u8 en clair. Les flux leurre sont ignorés par les parseurs.
     */
    private fun extractVoeStyleStream(playerUrl: String, depth: Int): String? {
        if (depth >= 3) return null
        return try {
            val html = fetchHtmlBlocking(playerUrl, referer = "${VoiranimeParsers.ORIGIN}/")
            val loc = VoiranimeParsers.parseVoeRedirect(html)
            if (loc != null && loc != playerUrl) {
                val next = absolutize(loc, playerUrl)
                if (next != null) {
                    extractVoeStyleStream(next, depth + 1)?.let { return it }
                }
            }
            VoiranimeParsers.parseVoePayloadSource(html)?.let { return it }
            VoiranimeParsers.findPlainM3u8(html)
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchMasterQualities(masterUrl: String, headers: Map<String, String>): List<StreamQuality> {
        val builder = Request.Builder().url(masterUrl)
        headers.forEach { (k, v) -> builder.header(k, v) }
        http.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} pour la master playlist")
            val body = resp.body?.string() ?: return emptyList()
            return VoiranimeParsers.parseHlsMasterPlaylist(body, masterUrl)
        }
    }

    private fun originOf(url: String): String? = try {
        val uri = java.net.URI(url)
        "${uri.scheme}://${uri.host}"
    } catch (_: Exception) {
        null
    }

    private fun absolutize(url: String, base: String): String? = try {
        java.net.URI(base).resolve(url).toString()
    } catch (_: Exception) {
        null
    }
}
