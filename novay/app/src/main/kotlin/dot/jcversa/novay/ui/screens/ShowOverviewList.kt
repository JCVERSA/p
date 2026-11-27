package dot.jcversa.novay.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dot.jcversa.novay.data.AnimeDetails
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.AnimeSeason
import dot.jcversa.novay.data.WatchHistoryEntry

@OptIn(ExperimentalFoundationApi::class)
internal fun LazyListScope.showOverviewAndSeasons(
    anime: AnimeSearchResult,
    details: AnimeDetails?,
    seasons: List<AnimeSeason>,
    grouped: List<Pair<String, List<AnimeSeason>>>,
    visibleGroups: List<Pair<String, List<AnimeSeason>>>,
    selectedLanguage: String?,
    collapsedGroups: Set<String>,
    synopsisExpanded: Boolean,
    genres: List<String>,
    firstSeason: AnimeSeason?,
    resumeEntry: WatchHistoryEntry?,
    inLibrary: Boolean,
    hasDetails: Boolean,
    downloadedBySeason: Map<String, Int>,
    knownEpisodeCounts: Map<String, Int>,
    activeSeasonUrl: String?,
    parallaxOffsetProvider: () -> Float,
    animateItems: Boolean,
    posterAccent: Color? = null,
    onResume: (WatchHistoryEntry) -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenDetails: () -> Unit,
    onToggleSynopsis: () -> Unit,
    onSelectLanguage: (String?) -> Unit,
    onToggleGroup: (String) -> Unit,
    onPosterResolved: (String) -> Unit,
    onSelectSeason: (AnimeSeason) -> Unit,
    onOpenShow: (AnimeSearchResult) -> Unit,
) {
    item(key = "header", contentType = "hero") {
        CinemaShowHeader(
            anime = anime,
            details = details,
            seasonCount = seasons.size,
            languages = grouped.map { it.first },
            parallaxOffsetProvider = parallaxOffsetProvider,
            onPosterResolved = onPosterResolved,
            posterAccent = posterAccent,
        )
    }

    item(key = "actions", contentType = "actions") {
        ShowActionBar(
            resumeEntry = resumeEntry,
            firstSeason = firstSeason,
            inLibrary = inLibrary,
            hasDetails = hasDetails,
            onPrimaryWatch = {
                if (resumeEntry != null) {
                    onResume(resumeEntry)
                } else if (firstSeason != null) {
                    onSelectSeason(firstSeason)
                }
            },
            onToggleFavorite = onToggleFavorite,
            onOpenDetails = onOpenDetails,
        )
    }

    if (!anime.synopsis.isNullOrBlank() || genres.isNotEmpty()) {
        item(key = "synopsis_genres", contentType = "synopsis_genres") {
            ShowSynopsisAndGenres(
                synopsis = anime.synopsis,
                genres = genres,
                expanded = synopsisExpanded,
                onToggleExpanded = onToggleSynopsis,
            )
        }
    }

    item(key = "language_selector", contentType = "language_selector") {
        LanguageSegmentedBar(
            grouped = grouped,
            selectedLanguage = selectedLanguage,
            onSelectLanguage = onSelectLanguage,
        )
    }

    visibleGroups.forEach { (lang, langSeasons) ->
        val isExpanded = !collapsedGroups.contains(lang)
        stickyHeader(key = "sticky_${lang}", contentType = "lang_header") {
            CollapsibleSeasonGroupHeader(
                language = lang,
                seasonCount = langSeasons.size,
                expanded = isExpanded,
                onToggle = { onToggleGroup(lang) },
            )
        }
        if (isExpanded) {
            items(
                items = langSeasons,
                key = { it.url },
                contentType = { "season_row" },
            ) { season ->
                val normalizedSeasonUrl = season.url.trimEnd('/')
                val dl = downloadedBySeason[normalizedSeasonUrl] ?: 0
                val knownCount = knownEpisodeCounts[normalizedSeasonUrl]
                val isResumeSeason = resumeEntry?.seasonUrl?.trimEnd('/') == normalizedSeasonUrl
                val active = activeSeasonUrl != null && season.url == activeSeasonUrl
                val rowMod = if (animateItems) Modifier.animateItem() else Modifier
                SeasonRow(
                    season = season,
                    downloaded = dl,
                    knownEpisodeCount = knownCount,
                    isResumeSeason = isResumeSeason,
                    resumeEpisodeNumber = if (isResumeSeason) resumeEntry.episodeNumber else null,
                    isActive = active,
                    modifier = rowMod,
                    onClick = { onSelectSeason(season) },
                )
            }
        }
    }

    val related = details?.related.orEmpty()
    if (related.isNotEmpty()) {
        item(key = "related_rail", contentType = "related_rail") {
            RelatedAnimeRail(
                related = related,
                onOpenShow = onOpenShow,
            )
        }
    }
}

/** VF d'abord, VOSTFR ensuite (contrainte permanente NoVaY). */
internal fun orderByLanguage(seasons: List<AnimeSeason>): List<Pair<String, List<AnimeSeason>>> {
    val groups = linkedMapOf<String, MutableList<AnimeSeason>>()
    for (s in seasons) {
        val lang = s.language?.uppercase() ?: when {
            s.url.contains("/vf/") -> "VF"
            s.url.contains("/vostfr/") -> "VOSTFR"
            else -> "AUTRE"
        }
        groups.getOrPut(lang) { mutableListOf() }.add(s)
    }
    val ordered = groups.entries.sortedWith(
        compareBy { (lang, _) ->
            when (lang) {
                "VF" -> 0
                "VOSTFR" -> 1
                else -> 2
            }
        },
    )
    return ordered.map { it.key to it.value.toList() }
}
