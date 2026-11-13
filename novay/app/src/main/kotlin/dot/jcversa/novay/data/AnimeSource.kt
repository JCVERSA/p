package dot.jcversa.novay.data

/**
 * Contrat de catalogue NoVaY.
 *
 * Portage Kotlin des parseurs de `src/bot/services/voiranimeClient.ts` et
 * de la sonde Voe de `animeStreamExtractor.ts` : mêmes formes de données,
 * mêmes fixtures de test. L'IP résidentielle du téléphone remplace le
 * serveur du bot (atout anti-Cloudflare).
 *
 * V1 : voiranime. V3 : autres catalogues (anime-sama, jetanimes…).
 */

/** Une entrée du catalogue (= une « saison » publiée par le site). */
data class AnimeSearchResult(
    val title: String,
    val url: String,
    val slug: String,
    val language: String? = null, // "VF" | "VOSTFR" | null
    val isVf: Boolean = false
)

/** Un épisode d'une entrée. */
data class AnimeEpisode(
    val number: Int, // 0 pour un film/OAV
    val url: String,
    val label: String // « Épisode N » | « Film » | « OAV »
)

/** Une qualité proposée par le lecteur (variante de la master playlist HLS). */
data class StreamQuality(
    val label: String, // "480P", "720P"…
    val url: String,
    val height: Int = 0
)

/** Flux jouable résolu pour un épisode, avec les en-têtes requis. */
data class ResolvedStream(
    val url: String,
    val isHls: Boolean,
    val headers: Map<String, String>,
    val qualities: List<StreamQuality>
)

/**
 * Une entrée organisée : saison déduite (null pour les films), étiquette
 * prête pour la liste (portage de OrganizedVfEntry côté bot).
 */
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

/** Contrat commun à tous les catalogues. */
interface AnimeSource {
    /** Nom affiché (ex. « voir-anime »). */
    val name: String

    /** Recherche et retourne les entrées du catalogue. */
    suspend fun search(query: String): List<AnimeSearchResult>

    /** Liste les épisodes d'une entrée (page anime du site). */
    suspend fun episodes(entry: AnimeSearchResult): List<AnimeEpisode>

    /** Résout le flux jouable d'un épisode (lecteur + qualités HLS). */
    suspend fun stream(episode: AnimeEpisode): ResolvedStream
}
