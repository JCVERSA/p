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
    val isVf: Boolean = false,
    // Affiche du catalogue (URL absolue, résolue contre l'origine).
    val posterUrl: String? = null,
    val genres: List<String> = emptyList(),
    val synopsis: String? = null,
    val format: String? = null,
    val releaseYear: String? = null,
    val status: String? = null,
)

/** Page paginée du catalogue complet d'une source. */
data class CatalogPage(
    val items: List<AnimeSearchResult> = emptyList(),
    val page: Int = 1,
    val hasNextPage: Boolean = false,
)

/** Métadonnées détaillées extraites de la fiche d'un anime (champs absents = null/vides). */
data class AnimeDetails(
    val synopsis: String? = null,
    val genres: List<String> = emptyList(),
    val status: String? = null,
    val releaseYear: String? = null,
    val format: String? = null,
    val alternativeTitle: String? = null,
    val posterUrl: String? = null,
    val related: List<AnimeSearchResult> = emptyList(),
)

/**
 * Une saison/film d'un anime (langue structurelle du sous-chemin).
 */
data class AnimeSeason(
    val name: String,
    val url: String,
    val language: String? = null
)

/**
 * Un panneau de scans d'une œuvre (page catalogue : appels
 * `panneauScan("Nom", "scan/vf")`), équivalent manga d'une saison.
 */
data class ScanPanel(
    val name: String,            // libellé complet du panneau (« Scans VF (…) »)
    val url: String,             // page du scan (…/catalogue/<slug>/scan/vf/)
    val language: String? = null, // VF/VOSTFR déduit du sous-chemin
    val scanlator: String? = null // groupe scantrad (libellé nettoyé)
)

/**
 * Un chapitre d'un panneau de scans, auto-suffisant pour la lecture :
 * les pages sont `<imageUrlPrefix><n>.jpg` pour n de 1 à [pageCount].
 */
data class ScanChapter(
    val id: Int,                 // identifiant du chapitre côté serveur d'images
    val number: String,          // « 12 », « 5.5 » ou libellé spécial
    val label: String,           // « Chapitre 12 »
    val pageCount: Int,          // nombre de pages (0 = inconnu)
    val imageUrlPrefix: String   // …/s2/scans/<oeuvre>/<id>/ — pages <n>.jpg
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

/**
 * Une hauteur proposée par AU MOINS UN lecteur de l'épisode (V2.3 :
 * union des échelles de tous les lecteurs — un lecteur peut offrir
 * 1080/480 et un autre 720).
 */
data class QualityOption(
    val height: Int,
    val label: String,
    /** Index du lecteur (dans la liste renvoyée par [AnimeSource.streams])
     *  qui propose cette hauteur — le premier gagne en cas de doublon. */
    val playerIndex: Int
)

/**
 * Une carte « À la une » du catalogue (carrousel de la page d'accueil) :
 * l'animé mis en avant par le site, avec son synopsis et son lien direct
 * de saison.
 */
data class CatalogHero(
    val title: String,
    /** « Saison 2 en cours » — libellé du badge du site, s'il existe. */
    val seasonLabel: String? = null,
    val genres: List<String> = emptyList(),
    val synopsis: String? = null,
    /** Lien direct du bouton « Visionner » (page de saison). */
    val seasonUrl: String,
    val posterUrl: String? = null,
    /** Langue du lien (VF | VOSTFR), déduite du sous-chemin. */
    val language: String? = null
)

/**
 * Un animé récemment mis à jour sur la page d'accueil du catalogue :
 * carte avec l'heure d'ajout et le lien de SA dernière saison.
 */
data class CatalogCard(
    val title: String,
    /** Page de l'animé (/catalogue/<slug>/). */
    val animeUrl: String,
    /** Dernière saison mise à jour (lien direct de la carte). */
    val seasonUrl: String,
    val posterUrl: String? = null,
    /** Heure d'ajout affichée par le site (« 11h30 »). */
    val timeLabel: String? = null,
    val language: String? = null
)

/** Page d'accueil d'un catalogue : à la une + derniers ajouts. */
data class CatalogHome(
    val heroes: List<CatalogHero> = emptyList(),
    val cards: List<CatalogCard> = emptyList()
)

/** Contrat commun à tous les catalogues. */
interface AnimeSource {
    /** Nom affiché (ex. « anime-sama »). */
    val name: String

    /** Sélection mise en avant du catalogue (facultatif selon la source). */
    suspend fun featured(): List<AnimeSearchResult> = emptyList()

    /** Recherche et retourne les animes du catalogue. */
    suspend fun search(query: String): List<AnimeSearchResult>

    /** Saisons/films d'un anime (langue structurelle quand le site l'encode). */
    suspend fun seasons(anime: AnimeSearchResult): List<AnimeSeason>

    /** Épisodes d'une saison. */
    suspend fun episodes(season: AnimeSeason): List<AnimeEpisode>

    /**
     * Panneaux de scans listés sur la page d'une œuvre (manga).
     * Vide = l'œuvre n'a pas de scans (animé pur).
     */
    suspend fun scanPanels(anime: AnimeSearchResult): List<ScanPanel> = emptyList()

    /**
     * Chapitres d'un panneau de scans, avec leur nombre de pages.
     */
    suspend fun scanChapters(anime: AnimeSearchResult, panel: ScanPanel): List<ScanChapter> = emptyList()

    /** Résout le flux jouable d'un épisode (lecteur + qualités HLS). */
    suspend fun stream(episode: AnimeEpisode): ResolvedStream

    /**
     * Flux de TOUS les lecteurs qui répondent pour cet épisode (V2.3) —
     * l'union de leurs qualités donne le choix de résolution au
     * téléchargement. Repli par défaut : le premier lecteur qui marche.
     */
    suspend fun streams(episode: AnimeEpisode): List<ResolvedStream> =
        listOf(stream(episode))

    /**
     * Page d'accueil du catalogue : animés à la une + derniers épisodes
     * ajoutés. Un catalogue sans page d'accueil exploitable rend une
     * liste vide — l'onglet retombe sur [featured]/la recherche seule.
     */
    suspend fun home(): CatalogHome = CatalogHome()

    /** Genres normalisés de l'anime (lus depuis sa fiche quand disponibles). */
    suspend fun genres(anime: AnimeSearchResult): List<String> = anime.genres

    /** Catalogue filtré par genre (vide si non supporté par la source). */
    suspend fun catalogByGenre(genre: String): List<AnimeSearchResult> = emptyList()

    /**
     * Page paginée du catalogue complet avec filtres pris en charge par la source.
     * Repli par défaut sur [featured] (page 1 uniquement, sans pagination).
     */
    suspend fun catalogPage(
        page: Int = 1,
        genres: Set<String> = emptySet(),
        language: String? = null,
        format: String? = null,
    ): CatalogPage {
        if (page > 1) return CatalogPage(items = emptyList(), page = page, hasNextPage = false)
        val items = if (genres.size == 1) {
            runCatching { catalogByGenre(genres.first()) }.getOrDefault(emptyList())
        } else {
            featured()
        }
        return CatalogPage(items = items, page = 1, hasNextPage = false)
    }

    /**
     * Métadonnées détaillées de la fiche anime (synopsis, statut, année,
     * œuvres associées) lues depuis les données réelles de la source.
     */
    suspend fun details(anime: AnimeSearchResult): AnimeDetails = AnimeDetails(
        synopsis = anime.synopsis,
        genres = anime.genres,
        posterUrl = anime.posterUrl,
    )

    /**
     * Résout l'affiche canonique depuis la fiche de l'animé quand [anime.posterUrl]
     * est absente, cassée ou issue d'un miroir obsolète.
     */
    suspend fun resolvePosterUrl(anime: AnimeSearchResult): String? =
        ImageUrlSanitizer.sanitizePosterUrl(anime.posterUrl)
}
