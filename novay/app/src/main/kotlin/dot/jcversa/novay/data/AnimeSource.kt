package dot.jcversa.novay.data

/**
 * Contrat de catalogue NoVaY.
 *
 * Portage Kotlin des parseurs purs de `src/bot/services/voiranimeClient.ts`
 * (420 l. : recherche / saisons / épisodes / lecteur) : mêmes formes de
 * données, mêmes fixtures de test — seule la couche HTTP change (OkHttp
 * avec en-têtes UA/Referer, contre fetch Node côté bot). L'IP résidentielle
 * du téléphone remplace le serveur : atout anti-Cloudflare.
 *
 * V1 : implémentation voiranime. V3 : anime-sama, jetanimes…
 */
data class AnimeSearchResult(
    val title: String,
    val url: String,
    val slug: String,
    val language: String? = null, // "VF" | "VOSTFR" | null
    val isVf: Boolean = false
)

/** Une saison d'une série (le catalogue voit chaque langue comme une saison). */
data class AnimeSeason(
    val name: String,
    val url: String,
    val language: String? = null
)

/** Un épisode d'une saison. */
data class AnimeEpisode(
    val number: Int,
    val url: String
)

/** Une qualité proposée par le lecteur (lue dans la master playlist HLS). */
data class StreamQuality(
    val label: String, // "480P", "720P"…
    val playlistUrl: String
)

/** Contrat commun à tous les catalogues. */
interface AnimeSource {
    /** Nom affiché (ex. « voir-anime »). */
    val name: String

    suspend fun search(query: String): List<AnimeSearchResult>

    suspend fun seasons(show: AnimeSearchResult): List<AnimeSeason>

    suspend fun episodes(season: AnimeSeason): List<AnimeEpisode>

    /** Qualités disponibles pour un épisode (résolutions du lecteur). */
    suspend fun qualities(episode: AnimeEpisode): List<StreamQuality>
}
