package dot.jcversa.novay.data

/**
 * Contrat de catalogue NoVaY.
 *
 * V1 : anime-sama (structure validée E2E depuis un runner GitHub Actions
 * le 13/11 — cf. SamaParsers pour le détail). voiranime est mort (domaine
 * déréférencé) ; ses parseurs restent portés dans data/voiranime pour un
 * éventuel retour.
 */

/** Une entrée du catalogue (un anime). */
data class AnimeSearchResult(
    val title: String,
    val url: String,
    val slug: String = "",
    val language: String? = null, // "VF" | "VOSTFR" | null
    val subtitle: String? = null,
    val isVf: Boolean = false
)

/** Une saison/film d'un anime (langue structurelle du sous-chemin). */
data class AnimeSeason(
    val name: String,
    val url: String,
    val language: String? = null
)

/** Un épisode d'une saison. */
data class AnimeEpisode(
    val number: Int, // 0 pour un film/OAV
    val url: String, // page de saison (rafraîchissement du jeton) ou page épisode
    val label: String, // « Épisode N » | « Film » | « OAV »
    // anime-sama : identifiants de lecteurs positionnels (« lien:186672 »)
    // + jeton de session pour /api/lien.php. Autres sources : vide.
    val lienIds: List<String> = emptyList(),
    val jeton: String = ""
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

/** Contrat commun à tous les catalogues. */
interface AnimeSource {
    /** Nom affiché (ex. « anime-sama »). */
    val name: String

    /** Recherche et retourne les animes du catalogue. */
    suspend fun search(query: String): List<AnimeSearchResult>

    /** Saisons/films d'un anime (langue structurelle quand le site l'encode). */
    suspend fun seasons(anime: AnimeSearchResult): List<AnimeSeason>

    /** Épisodes d'une saison. */
    suspend fun episodes(season: AnimeSeason): List<AnimeEpisode>

    /** Résout le flux jouable d'un épisode (lecteur + qualités HLS). */
    suspend fun stream(episode: AnimeEpisode): ResolvedStream
}
