package dot.jcversa.novay.ui.state

import androidx.compose.runtime.Immutable
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.GenreCatalog

/**
 * Options de tri honnêtes pour le catalogue : uniquement des critères réels
 * (aucun tri factice par note ou popularité inventée).
 */
enum class CatalogSortOption(val label: String) {
    DEFAULT("Par défaut"),
    RECENT("Ajoutés récemment"),
    TITLE_ASC("A–Z"),
    TITLE_DESC("Z–A"),
    YEAR_DESC("Année (récent)"),
}

@Immutable
data class CatalogFilterState(
    val genres: Set<String> = emptySet(),
    val language: String? = null,
    val format: String? = null,
    val releaseYear: String? = null,
    val status: String? = null,
    val sort: CatalogSortOption = CatalogSortOption.DEFAULT,
) {
    val activeCount: Int
        get() = genres.size +
            (if (language != null) 1 else 0) +
            (if (format != null) 1 else 0) +
            (if (releaseYear != null) 1 else 0) +
            (if (status != null) 1 else 0) +
            (if (sort != CatalogSortOption.DEFAULT) 1 else 0)

    val hasAnyActiveFilter: Boolean
        get() = activeCount > 0

    val hasServerFilter: Boolean
        get() = genres.isNotEmpty() || language != null || format != null

    val hasAdvancedCriteriaBeyondSingleGenre: Boolean
        get() = genres.size > 1 ||
            language != null ||
            format != null ||
            releaseYear != null ||
            status != null ||
            sort != CatalogSortOption.DEFAULT

    fun serverQuerySignature(): String = buildString {
        append(genres.map(GenreCatalog::normalize).sorted().joinToString(","))
        append('|')
        append(language.orEmpty())
        append('|')
        append(format.orEmpty())
    }

    fun toSavedString(): String = listOf(
        genres.joinToString(","),
        language.orEmpty(),
        format.orEmpty(),
        releaseYear.orEmpty(),
        status.orEmpty(),
        sort.name,
    ).joinToString(";;")

    companion object {
        fun fromSavedString(raw: String): CatalogFilterState {
            if (raw.isBlank()) return CatalogFilterState()
            val parts = raw.split(";;")
            if (parts.size < 6) return CatalogFilterState()
            val genres = parts[0]
                .split(",")
                .mapNotNull { it.trim().takeIf { g -> g.isNotEmpty() } }
                .map(GenreCatalog::normalize)
                .filter { it.isNotEmpty() }
                .toSet()
            val sort = runCatching { CatalogSortOption.valueOf(parts[5]) }
                .getOrDefault(CatalogSortOption.DEFAULT)
            return CatalogFilterState(
                genres = genres,
                language = parts[1].takeIf { it.isNotBlank() },
                format = parts[2].takeIf { it.isNotBlank() },
                releaseYear = parts[3].takeIf { it.isNotBlank() },
                status = parts[4].takeIf { it.isNotBlank() },
                sort = sort,
            )
        }
    }
}

@Immutable
data class CatalogAvailableFacets(
    val genres: List<String> = GenreCatalog.CORE_GENRES,
    val languages: List<String> = listOf("VF", "VOSTFR"),
    val formats: List<String> = listOf("Anime", "Film", "OAV", "Scans"),
    val years: List<String> = emptyList(),
    val statuses: List<String> = emptyList(),
    val sortOptions: List<CatalogSortOption> = listOf(
        CatalogSortOption.DEFAULT,
        CatalogSortOption.RECENT,
        CatalogSortOption.TITLE_ASC,
        CatalogSortOption.TITLE_DESC,
    ),
)

@Immutable
data class CatalogFilterChipItem(
    val key: String,
    val label: String,
    val kind: Kind,
    val value: String,
) {
    enum class Kind {
        GENRE,
        LANGUAGE,
        FORMAT,
        YEAR,
        STATUS,
        SORT,
    }
}

@Immutable
data class CatalogPageUiState(
    val items: List<AnimeSearchResult> = emptyList(),
    val page: Int = 0,
    val hasNextPage: Boolean = false,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val paginationError: String? = null,
)

object CatalogFilterEngine {

    private val PROVIDER_TYPES = listOf("Anime", "Film", "OAV", "Scans")
    private val PROVIDER_LANGUAGES = listOf("VF", "VOSTFR")

    fun normalizeUrlKey(url: String): String =
        url.trim().trimEnd('/').lowercase()

    fun extractAvailableFacets(
        items: List<AnimeSearchResult>,
        extraGenres: List<String> = emptyList(),
    ): CatalogAvailableFacets {
        val mergedGenres = GenreCatalog.mergeGenresForBar(
            items.flatMap { it.genres } + extraGenres,
        )
        val extraLanguages = items.mapNotNull { it.language?.trim()?.uppercase() }
            .filter { it.isNotEmpty() && it !in PROVIDER_LANGUAGES }
            .distinct()
        val formats = (PROVIDER_TYPES + items.mapNotNull { it.format?.trim()?.takeIf(String::isNotEmpty) })
            .distinct()
        val years = items.mapNotNull { it.releaseYear?.trim()?.takeIf { y -> y.length == 4 } }
            .distinct()
            .sortedByDescending { it.toIntOrNull() ?: 0 }
        val statuses = items.mapNotNull { it.status?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
            .sorted()
        val sortOptions = buildList {
            add(CatalogSortOption.DEFAULT)
            add(CatalogSortOption.RECENT)
            add(CatalogSortOption.TITLE_ASC)
            add(CatalogSortOption.TITLE_DESC)
            if (years.isNotEmpty()) {
                add(CatalogSortOption.YEAR_DESC)
            }
        }
        return CatalogAvailableFacets(
            genres = mergedGenres,
            languages = PROVIDER_LANGUAGES + extraLanguages,
            formats = formats,
            years = years,
            statuses = statuses,
            sortOptions = sortOptions,
        )
    }

    fun filterAndSort(
        items: List<AnimeSearchResult>,
        filter: CatalogFilterState,
        recentOrderUrls: Map<String, Int> = emptyMap(),
    ): List<AnimeSearchResult> {
        val filtered = items.filter { item ->
            val genreOk = if (filter.genres.isEmpty()) {
                true
            } else {
                filter.genres.all { g -> GenreCatalog.matches(item.genres, g) }
            }
            val langOk = if (filter.language == null) {
                true
            } else {
                item.language.equals(filter.language, ignoreCase = true) ||
                    (filter.language.equals("VF", ignoreCase = true) && item.isVf)
            }
            val formatOk = if (filter.format == null) {
                true
            } else {
                item.format.equals(filter.format, ignoreCase = true) ||
                    (item.subtitle?.contains(filter.format, ignoreCase = true) == true)
            }
            val yearOk = if (filter.releaseYear == null) {
                true
            } else {
                item.releaseYear == filter.releaseYear
            }
            val statusOk = if (filter.status == null) {
                true
            } else {
                item.status.equals(filter.status, ignoreCase = true)
            }
            genreOk && langOk && formatOk && yearOk && statusOk
        }

        return when (filter.sort) {
            CatalogSortOption.DEFAULT -> filtered
            CatalogSortOption.RECENT -> filtered.sortedWith(
                compareBy<AnimeSearchResult> { item ->
                    recentOrderUrls[normalizeUrlKey(item.url)] ?: Int.MAX_VALUE
                },
            )
            CatalogSortOption.TITLE_ASC -> filtered.sortedBy { GenreCatalog.foldKey(it.title) }
            CatalogSortOption.TITLE_DESC -> filtered.sortedByDescending { GenreCatalog.foldKey(it.title) }
            CatalogSortOption.YEAR_DESC -> filtered.sortedWith(
                compareByDescending<AnimeSearchResult> { it.releaseYear?.toIntOrNull() ?: Int.MIN_VALUE }
                    .thenBy { GenreCatalog.foldKey(it.title) },
            )
        }
    }

    fun toRemovableChips(filter: CatalogFilterState): List<CatalogFilterChipItem> = buildList {
        filter.genres.sortedBy { GenreCatalog.foldKey(it) }.forEach { genre ->
            add(
                CatalogFilterChipItem(
                    key = "genre_${GenreCatalog.slug(genre)}",
                    label = genre,
                    kind = CatalogFilterChipItem.Kind.GENRE,
                    value = genre,
                ),
            )
        }
        filter.language?.let { lang ->
            add(
                CatalogFilterChipItem(
                    key = "lang_${lang.lowercase()}",
                    label = lang.uppercase(),
                    kind = CatalogFilterChipItem.Kind.LANGUAGE,
                    value = lang,
                ),
            )
        }
        filter.format?.let { format ->
            add(
                CatalogFilterChipItem(
                    key = "format_${format.lowercase()}",
                    label = format,
                    kind = CatalogFilterChipItem.Kind.FORMAT,
                    value = format,
                ),
            )
        }
        filter.releaseYear?.let { year ->
            add(
                CatalogFilterChipItem(
                    key = "year_$year",
                    label = year,
                    kind = CatalogFilterChipItem.Kind.YEAR,
                    value = year,
                ),
            )
        }
        filter.status?.let { status ->
            add(
                CatalogFilterChipItem(
                    key = "status_${status.lowercase()}",
                    label = status,
                    kind = CatalogFilterChipItem.Kind.STATUS,
                    value = status,
                ),
            )
        }
        if (filter.sort != CatalogSortOption.DEFAULT) {
            add(
                CatalogFilterChipItem(
                    key = "sort_${filter.sort.name.lowercase()}",
                    label = "Tri : ${filter.sort.label}",
                    kind = CatalogFilterChipItem.Kind.SORT,
                    value = filter.sort.name,
                ),
            )
        }
    }

    fun removeChip(filter: CatalogFilterState, chip: CatalogFilterChipItem): CatalogFilterState =
        when (chip.kind) {
            CatalogFilterChipItem.Kind.GENRE -> filter.copy(genres = filter.genres - chip.value)
            CatalogFilterChipItem.Kind.LANGUAGE -> filter.copy(language = null)
            CatalogFilterChipItem.Kind.FORMAT -> filter.copy(format = null)
            CatalogFilterChipItem.Kind.YEAR -> filter.copy(releaseYear = null)
            CatalogFilterChipItem.Kind.STATUS -> filter.copy(status = null)
            CatalogFilterChipItem.Kind.SORT -> filter.copy(sort = CatalogSortOption.DEFAULT)
        }

    fun mergeUniqueItems(
        existing: List<AnimeSearchResult>,
        incoming: List<AnimeSearchResult>,
    ): List<AnimeSearchResult> {
        if (incoming.isEmpty()) return existing
        val merged = LinkedHashMap<String, AnimeSearchResult>(existing.size + incoming.size)
        fun putRich(item: AnimeSearchResult) {
            val key = normalizeUrlKey(item.url)
            val prev = merged[key]
            if (prev == null) {
                merged[key] = item
            } else {
                merged[key] = prev.copy(
                    language = prev.language ?: item.language,
                    subtitle = prev.subtitle ?: item.subtitle,
                    isVf = prev.isVf || item.isVf,
                    posterUrl = prev.posterUrl ?: item.posterUrl,
                    genres = if (prev.genres.isNotEmpty()) prev.genres else item.genres,
                    synopsis = prev.synopsis ?: item.synopsis,
                    format = prev.format ?: item.format,
                    releaseYear = prev.releaseYear ?: item.releaseYear,
                    status = prev.status ?: item.status,
                )
            }
        }
        existing.forEach(::putRich)
        incoming.forEach(::putRich)
        return merged.values.toList()
    }
}
