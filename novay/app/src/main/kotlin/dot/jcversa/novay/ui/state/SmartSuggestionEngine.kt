package dot.jcversa.novay.ui.state

import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.LibraryEntry
import dot.jcversa.novay.data.SearchHistoryEntry
import dot.jcversa.novay.data.WatchHistoryEntry
import java.text.Normalizer

/**
 * Moteur pur Kotlin de suggestions intelligentes pour le Catalogue NoVaY :
 *  - normalisation insensible à la casse et aux accents (NFD) ;
 *  - calcul des intervalles de surbrillance alignés sur le texte d'origine ;
 *  - classement multi-sources (recherches récentes, bibliothèque, historique,
 *    sélection À la une et résultats catalogue en direct).
 */
object SmartSuggestionEngine {

    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val WORD_SPLIT = Regex("[\\s\\-:;'’/]+")
    private val MULTI_SPACE = Regex("\\s+")

    private const val MAX_EMPTY_RECENTS = 6
    private const val MAX_EMPTY_LOCAL_ANIME = 6
    private const val MAX_TYPING_QUERY_SUGGESTIONS = 4
    private const val MAX_TYPING_LOCAL_ANIME = 5
    private const val MAX_TYPING_LIVE_ANIME = 8

    private data class NormalizedText(
        val value: String,
        val originalStart: IntArray,
        val originalEnd: IntArray,
    )

    private fun normalizeWithIndexMap(raw: String): NormalizedText {
        if (raw.isEmpty()) {
            return NormalizedText("", IntArray(0), IntArray(0))
        }
        val sb = StringBuilder(raw.length)
        val starts = ArrayList<Int>(raw.length)
        val ends = ArrayList<Int>(raw.length)
        var lastWasSpace = true

        var i = 0
        while (i < raw.length) {
            val cp = Character.codePointAt(raw, i)
            val charCount = Character.charCount(cp)
            val slice = raw.substring(i, i + charCount)
            val decomposed = Normalizer.normalize(slice, Normalizer.Form.NFD)
            val stripped = COMBINING_MARKS.replace(decomposed, "").lowercase()

            for (ch in stripped) {
                val mapped = when {
                    ch.isWhitespace() -> ' '
                    ch == '’' -> '\''
                    else -> ch
                }
                if (mapped == ' ') {
                    if (!lastWasSpace) {
                        sb.append(' ')
                        starts.add(i)
                        ends.add(i + charCount)
                        lastWasSpace = true
                    }
                } else {
                    sb.append(mapped)
                    starts.add(i)
                    ends.add(i + charCount)
                    lastWasSpace = false
                }
            }
            i += charCount
        }

        var len = sb.length
        while (len > 0 && sb[len - 1] == ' ') {
            len--
        }
        return NormalizedText(
            value = sb.substring(0, len),
            originalStart = starts.take(len).toIntArray(),
            originalEnd = ends.take(len).toIntArray(),
        )
    }

    fun normalizeForMatch(raw: String): String {
        val trimmed = raw.trim().replace(MULTI_SPACE, " ")
        if (trimmed.isEmpty()) return ""
        val decomposed = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
        return COMBINING_MARKS.replace(decomposed, "")
            .replace('’', '\'')
            .lowercase()
    }

    fun findHighlightSpans(text: String, query: String): List<HighlightSpan> {
        val normQuery = normalizeForMatch(query)
        if (text.isEmpty() || normQuery.isEmpty()) return emptyList()

        val normText = normalizeWithIndexMap(text)
        if (normText.value.isEmpty()) return emptyList()

        val rawSpans = ArrayList<HighlightSpan>(2)

        val fullIndex = normText.value.indexOf(normQuery)
        if (fullIndex >= 0) {
            val lastIdx = (fullIndex + normQuery.length - 1).coerceAtMost(normText.originalEnd.lastIndex)
            rawSpans.add(
                HighlightSpan(
                    start = normText.originalStart[fullIndex],
                    endExclusive = normText.originalEnd[lastIdx],
                ),
            )
        } else {
            val tokens = normQuery.split(' ').filter { it.isNotEmpty() }
            if (tokens.size > 1 && tokens.all { normText.value.contains(it) }) {
                for (token in tokens) {
                    val idx = normText.value.indexOf(token)
                    if (idx >= 0) {
                        val lastIdx = (idx + token.length - 1).coerceAtMost(normText.originalEnd.lastIndex)
                        rawSpans.add(
                            HighlightSpan(
                                start = normText.originalStart[idx],
                                endExclusive = normText.originalEnd[lastIdx],
                            ),
                        )
                    }
                }
            }
        }

        if (rawSpans.isEmpty()) return emptyList()
        val sorted = rawSpans.sortedWith(compareBy<HighlightSpan> { it.start }.thenBy { it.endExclusive })
        val merged = ArrayList<HighlightSpan>(sorted.size)
        for (span in sorted) {
            val prev = merged.lastOrNull()
            if (prev == null || span.start > prev.endExclusive) {
                merged.add(span)
            } else {
                merged[merged.lastIndex] = prev.copy(
                    endExclusive = maxOf(prev.endExclusive, span.endExclusive),
                )
            }
        }
        return merged
    }

    fun scoreMatch(
        candidateNormalized: String,
        queryNormalized: String,
        queryTokens: List<String> = queryNormalized.split(' ').filter { it.isNotEmpty() },
    ): Int {
        if (candidateNormalized.isEmpty() || queryNormalized.isEmpty()) return 0
        if (candidateNormalized == queryNormalized) return 100
        if (candidateNormalized.startsWith(queryNormalized)) return 85
        val words = candidateNormalized.split(WORD_SPLIT).filter { it.isNotEmpty() }
        if (words.any { it.startsWith(queryNormalized) }) return 68
        if (candidateNormalized.contains(queryNormalized)) return 45
        if (queryTokens.size > 1 && queryTokens.all { candidateNormalized.contains(it) }) return 32
        return 0
    }

    fun buildSuggestions(
        query: String,
        recentSearches: List<SearchHistoryEntry>,
        libraryEntries: List<LibraryEntry>,
        watchHistory: List<WatchHistoryEntry>,
        featuredAnime: List<AnimeSearchResult>,
        liveCatalog: List<AnimeSearchResult>,
        discoveryChips: List<String>,
        isLiveLoading: Boolean,
    ): SmartSearchSuggestionsUi {
        val normQuery = normalizeForMatch(query)

        if (normQuery.isEmpty()) {
            val recents = recentSearches
                .take(MAX_EMPTY_RECENTS)
                .map { QuerySuggestionUi(query = it.query, source = SuggestionQuerySource.RECENT) }

            val localPool = buildLocalPool(
                libraryEntries = libraryEntries,
                watchHistory = watchHistory,
                featuredAnime = emptyList(),
            ).take(MAX_EMPTY_LOCAL_ANIME)
                .map { AnimeSuggestionUi(anime = it.anime, origin = it.origin) }

            return SmartSearchSuggestionsUi(
                querySuggestions = recents,
                localAnime = localPool,
                liveAnime = emptyList(),
                popularTags = discoveryChips,
                isLiveLoading = false,
            )
        }

        val queryTokens = normQuery.split(' ').filter { it.isNotEmpty() }

        // 1. Recherches récentes + thèmes populaires correspondants
        val seenQueries = HashSet<String>()
        val scoredRecentQueries = recentSearches.mapNotNull { entry ->
            val candNorm = normalizeForMatch(entry.query)
            val score = scoreMatch(candNorm, normQuery, queryTokens)
            if (score <= 0 || !seenQueries.add(candNorm)) return@mapNotNull null
            Triple(score, entry.searchedAt, QuerySuggestionUi(
                query = entry.query,
                source = SuggestionQuerySource.RECENT,
                spans = findHighlightSpans(entry.query, query),
            ))
        }.sortedWith(
            compareByDescending<Triple<Int, Long, QuerySuggestionUi>> { it.first }
                .thenByDescending { it.second },
        ).map { it.third }

        val scoredChipQueries = discoveryChips.mapNotNull { chip ->
            val candNorm = normalizeForMatch(chip)
            val score = scoreMatch(candNorm, normQuery, queryTokens)
            if (score <= 0 || !seenQueries.add(candNorm)) return@mapNotNull null
            score to QuerySuggestionUi(
                query = chip,
                source = SuggestionQuerySource.POPULAR,
                spans = findHighlightSpans(chip, query),
            )
        }.sortedByDescending { it.first }.map { it.second }

        val querySuggestions = (scoredRecentQueries + scoredChipQueries).take(MAX_TYPING_QUERY_SUGGESTIONS)

        // 2. Correspondances instantanées : Bibliothèque > Historique > À la une
        val localPool = buildLocalPool(
            libraryEntries = libraryEntries,
            watchHistory = watchHistory,
            featuredAnime = featuredAnime,
        )
        val matchedLocal = localPool.mapNotNull { candidate ->
            val titleNorm = normalizeForMatch(candidate.anime.title)
            val subNorm = candidate.anime.subtitle?.let(::normalizeForMatch).orEmpty()
            val bestBase = maxOf(
                scoreMatch(titleNorm, normQuery, queryTokens),
                scoreMatch(subNorm, normQuery, queryTokens) - 10,
            )
            if (bestBase <= 0) return@mapNotNull null
            val boosted = bestBase + candidate.priorityBoost
            Pair(
                boosted,
                AnimeSuggestionUi(
                    anime = candidate.anime,
                    origin = candidate.origin,
                    spans = findHighlightSpans(candidate.anime.title, query),
                ),
            )
        }.sortedWith(
            compareByDescending<Pair<Int, AnimeSuggestionUi>> { it.first }
                .thenBy { it.second.anime.title.lowercase() },
        ).take(MAX_TYPING_LOCAL_ANIME)
            .map { it.second }

        // 3. Catalogue en direct (en conservant aussi les correspondances alias/slug du serveur)
        val shownUrls = matchedLocal.mapTo(HashSet()) { it.anime.url.trimEnd('/') }
        val liveSeen = HashSet<String>()
        val matchedLive = liveCatalog.mapNotNull { anime ->
            val key = anime.url.trimEnd('/')
            if (key in shownUrls || !liveSeen.add(key)) return@mapNotNull null
            val score = scoreMatch(normalizeForMatch(anime.title), normQuery, queryTokens)
            Pair(
                score,
                AnimeSuggestionUi(
                    anime = anime,
                    origin = AnimeSuggestionOrigin.LIVE_CATALOG,
                    spans = findHighlightSpans(anime.title, query),
                ),
            )
        }.sortedByDescending { it.first }
            .take(MAX_TYPING_LIVE_ANIME)
            .map { it.second }

        return SmartSearchSuggestionsUi(
            querySuggestions = querySuggestions,
            localAnime = matchedLocal,
            liveAnime = matchedLive,
            popularTags = emptyList(),
            isLiveLoading = isLiveLoading,
        )
    }

    private data class LocalCandidate(
        val anime: AnimeSearchResult,
        val origin: AnimeSuggestionOrigin,
        val priorityBoost: Int,
    )

    private fun buildLocalPool(
        libraryEntries: List<LibraryEntry>,
        watchHistory: List<WatchHistoryEntry>,
        featuredAnime: List<AnimeSearchResult>,
    ): List<LocalCandidate> {
        val seen = HashSet<String>()
        val out = ArrayList<LocalCandidate>()

        for (entry in libraryEntries) {
            val urlKey = entry.url.trimEnd('/')
            if (urlKey.isNotEmpty() && seen.add(urlKey)) {
                out.add(
                    LocalCandidate(
                        anime = entry.toSearchResult(),
                        origin = AnimeSuggestionOrigin.LIBRARY,
                        priorityBoost = 14,
                    ),
                )
            }
        }
        for (h in watchHistory) {
            val urlKey = h.animeUrl.trimEnd('/')
            if (urlKey.isNotEmpty() && seen.add(urlKey)) {
                out.add(
                    LocalCandidate(
                        anime = AnimeSearchResult(
                            title = h.animeTitle,
                            url = h.animeUrl,
                            subtitle = h.seasonName.ifBlank { null },
                            posterUrl = h.posterUrl,
                        ),
                        origin = AnimeSuggestionOrigin.HISTORY,
                        priorityBoost = 9,
                    ),
                )
            }
        }
        for (f in featuredAnime) {
            val urlKey = f.url.trimEnd('/')
            if (urlKey.isNotEmpty() && seen.add(urlKey)) {
                out.add(
                    LocalCandidate(
                        anime = f,
                        origin = AnimeSuggestionOrigin.FEATURED,
                        priorityBoost = 4,
                    ),
                )
            }
        }
        return out
    }
}
