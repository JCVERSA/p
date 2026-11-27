package dot.jcversa.novay.data

import java.text.Normalizer

/**
 * Catalogue canonique et normalisation des genres d'animés pour les filtres
 * Bibliothèque & Catalogue.
 */
object GenreCatalog {

    val CORE_GENRES: List<String> = listOf(
        "Action",
        "Romance",
        "Comédie",
        "Slice of Life",
        "Aventure",
        "Drame",
        "Fantastique",
        "Isekai",
        "Shonen",
        "Seinen",
        "Sci-Fi",
        "Mystère",
        "Horreur",
        "Sport",
    )

    private val ALIASES: Map<String, String> = mapOf(
        "action" to "Action",
        "romance" to "Romance",
        "comedie" to "Comédie",
        "comedy" to "Comédie",
        "slice of life" to "Slice of Life",
        "slice-of-life" to "Slice of Life",
        "tranche de vie" to "Slice of Life",
        "aventure" to "Aventure",
        "adventure" to "Aventure",
        "drame" to "Drame",
        "drama" to "Drame",
        "fantastique" to "Fantastique",
        "fantasy" to "Fantastique",
        "isekai" to "Isekai",
        "shonen" to "Shonen",
        "shounen" to "Shonen",
        "seinen" to "Seinen",
        "shojo" to "Shojo",
        "shoujo" to "Shojo",
        "sci-fi" to "Sci-Fi",
        "scifi" to "Sci-Fi",
        "science-fiction" to "Sci-Fi",
        "science fiction" to "Sci-Fi",
        "mystere" to "Mystère",
        "mystery" to "Mystère",
        "horreur" to "Horreur",
        "horror" to "Horreur",
        "sport" to "Sport",
        "sports" to "Sport",
        "surnaturel" to "Surnaturel",
        "supernatural" to "Surnaturel",
        "psychologique" to "Psychologique",
        "psychological" to "Psychologique",
        "thriller" to "Thriller",
        "mecha" to "Mecha",
        "ecchi" to "Ecchi",
        "historique" to "Historique",
        "historical" to "Historique",
        "musique" to "Musique",
        "music" to "Musique",
        "school life" to "School Life",
    )

    fun normalize(raw: String): String {
        val cleaned = raw.trim().replace(Regex("\\s+"), " ")
        if (cleaned.isEmpty()) return ""
        val key = foldKey(cleaned)
        ALIASES[key]?.let { return it }
        return cleaned.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase() else ch.toString()
            }
        }
    }

    fun normalizeList(rawGenres: Iterable<String>): List<String> =
        rawGenres.asSequence()
            .map(::normalize)
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()

    fun isKnownCanonicalOrAlias(raw: String): Boolean {
        val cleaned = raw.trim().replace(Regex("\\s+"), " ")
        if (cleaned.isEmpty()) return false
        val key = foldKey(cleaned)
        return ALIASES.containsKey(key) || CORE_GENRES.any { foldKey(it) == key }
    }

    fun matches(entryGenres: List<String>, selectedGenre: String?): Boolean {
        val target = selectedGenre?.let(::normalize)?.takeIf { it.isNotEmpty() } ?: return true
        val targetKey = foldKey(target)
        return entryGenres.any { g ->
            val norm = normalize(g)
            norm.equals(target, ignoreCase = true) || foldKey(norm) == targetKey
        }
    }

    fun mergeGenresForBar(discoveredGenres: Iterable<String>): List<String> {
        val normalizedExtras = normalizeList(discoveredGenres)
            .filter { g -> CORE_GENRES.none { it.equals(g, ignoreCase = true) } }
            .sorted()
        return CORE_GENRES + normalizedExtras
    }

    fun slug(genre: String): String =
        foldKey(normalize(genre))
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')

    /** Requête envoyée au catalogue anime-sama (`?genre[]=...`). */
    fun samaQueryValue(genre: String): String = when (normalize(genre)) {
        "Slice of Life" -> "Slice of Life"
        "Comédie" -> "Comédie"
        "Mystère" -> "Mystère"
        else -> normalize(genre)
    }

    internal fun foldKey(text: String): String {
        val decomposed = Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }
}
