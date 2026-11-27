package dot.jcversa.novay.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dot.jcversa.novay.R
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.ui.components.EmptyScreen
import dot.jcversa.novay.ui.components.LocalSlateMotion
import dot.jcversa.novay.ui.components.NovayBadge
import dot.jcversa.novay.ui.components.NovayIcons
import dot.jcversa.novay.ui.components.PosterImage
import dot.jcversa.novay.ui.components.ShimmerBox
import dot.jcversa.novay.ui.components.novaySharedPoster
import dot.jcversa.novay.ui.state.AnimeSuggestionOrigin
import dot.jcversa.novay.ui.state.AnimeSuggestionUi
import dot.jcversa.novay.ui.state.HighlightSpan
import dot.jcversa.novay.ui.state.QuerySuggestionUi
import dot.jcversa.novay.ui.state.SmartSearchSuggestionsUi
import dot.jcversa.novay.ui.state.SuggestionQuerySource
import dot.jcversa.novay.ui.theme.NovayTheme
import dot.jcversa.novay.ui.theme.header

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BrowseSuggestionsContent(
    isTyping: Boolean,
    query: String,
    suggestions: SmartSearchSuggestionsUi,
    bottomChrome: Dp,
    onSelectQuery: (String) -> Unit,
    onFillQuery: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearHistory: () -> Unit,
    onOpenShow: (AnimeSearchResult) -> Unit,
) {
    val motion = LocalSlateMotion.current
    val overlays = NovayTheme.overlays

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .imePadding()
                .testTag("browse_suggestions_list"),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = 28.dp + bottomChrome,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── 1. Recherches récentes / Requêtes suggérées ─────────────────
            if (suggestions.querySuggestions.isNotEmpty()) {
                item(key = "header_queries", contentType = "section_header") {
                    SuggestionSectionHeader(
                        title = if (isTyping) "Suggestions" else "Recherches récentes",
                        actionLabel = if (!isTyping) "Tout effacer" else null,
                        actionTag = "browse_clear_history_button",
                        onAction = onClearHistory,
                    )
                }

                items(
                    items = suggestions.querySuggestions,
                    key = { "q_${it.source.name}_${it.query}" },
                    contentType = { "query_row" },
                ) { item ->
                    val mod = if (motion.reduceMotion) Modifier else Modifier.animateItem()
                    QuerySuggestionRow(
                        item = item,
                        showRemove = item.source == SuggestionQuerySource.RECENT,
                        onSelect = { onSelectQuery(item.query) },
                        onFill = { onFillQuery(item.query) },
                        onRemove = { onRemoveRecent(item.query) },
                        modifier = mod,
                    )
                }
            }

            // ── 2. Correspondances instantanées Bibliothèque / Historique / À la une ─
            if (suggestions.localAnime.isNotEmpty()) {
                item(key = "header_local_anime", contentType = "section_header") {
                    SuggestionSectionHeader(
                        title = if (isTyping) "Dans ta collection & à la une" else "Dans ta bibliothèque & Historique",
                    )
                }

                if (!isTyping) {
                    item(key = "local_anime_rail", contentType = "local_rail") {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(
                                items = suggestions.localAnime,
                                key = { "rail_${it.anime.url}" },
                                contentType = { "local_rail_card" },
                            ) { animeUi ->
                                val mod = if (motion.reduceMotion) Modifier else Modifier.animateItem()
                                QuickJumpAnimeChipCard(
                                    item = animeUi,
                                    onClick = { onOpenShow(animeUi.anime) },
                                    modifier = mod,
                                )
                            }
                        }
                    }
                } else {
                    items(
                        items = suggestions.localAnime,
                        key = { "local_${it.anime.url}" },
                        contentType = { "anime_suggestion" },
                    ) { animeUi ->
                        val mod = if (motion.reduceMotion) Modifier else Modifier.animateItem()
                        AnimeSuggestionRow(
                            item = animeUi,
                            onClick = { onOpenShow(animeUi.anime) },
                            modifier = mod.testTag("browse_local_suggestion_card"),
                        )
                    }
                }
            }

            // ── 3. Thèmes populaires (Focus + champ vide) ───────────────────
            if (!isTyping && suggestions.popularTags.isNotEmpty()) {
                item(key = "header_popular_tags", contentType = "section_header") {
                    SuggestionSectionHeader(title = "Thèmes populaires")
                }
                item(key = "popular_tags_flow", contentType = "popular_chips") {
                    TagFlowWrap(
                        horizontalSpacing = 8.dp,
                        verticalSpacing = 6.dp,
                    ) {
                        suggestions.popularTags.forEach { tag ->
                            FilterChip(
                                selected = false,
                                onClick = { onSelectQuery(tag) },
                                modifier = Modifier.testTag("browse_suggestion_chip_${tag.lowercase()}"),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    labelColor = MaterialTheme.colorScheme.onSurface,
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = false,
                                    borderColor = overlays.glassBorder,
                                ),
                                label = { Text(tag) },
                            )
                        }
                    }
                }
            }

            // ── 4. Catalogue en direct (debounce 280ms) ─────────────────────
            if (isTyping && (suggestions.isLiveLoading || suggestions.liveAnime.isNotEmpty())) {
                item(key = "header_live_catalog", contentType = "section_header") {
                    SuggestionSectionHeader(
                        title = if (suggestions.isLiveLoading && suggestions.liveAnime.isEmpty()) {
                            "Recherche en direct…"
                        } else {
                            "Animés en direct"
                        },
                    )
                }

                if (suggestions.isLiveLoading && suggestions.liveAnime.isEmpty()) {
                    items(count = 3, key = { "live_shimmer_$it" }, contentType = { "live_shimmer" }) {
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp),
                            shape = MaterialTheme.shapes.medium,
                        )
                    }
                } else {
                    items(
                        items = suggestions.liveAnime,
                        key = { "live_${it.anime.url}" },
                        contentType = { "live_anime_suggestion" },
                    ) { animeUi ->
                        val mod = if (motion.reduceMotion) Modifier else Modifier.animateItem()
                        AnimeSuggestionRow(
                            item = animeUi,
                            onClick = { onOpenShow(animeUi.anime) },
                            modifier = mod.testTag("browse_live_suggestion_card"),
                        )
                    }
                }
            }

            // ── 5. État discret si aucun résultat pendant la saisie ─────────
            val noTypingHits = isTyping &&
                !suggestions.isLiveLoading &&
                suggestions.querySuggestions.isEmpty() &&
                suggestions.localAnime.isEmpty() &&
                suggestions.liveAnime.isEmpty()

            if (noTypingHits) {
                item(key = "typing_empty_hint", contentType = "typing_empty") {
                    EmptyScreen(
                        icon = NovayIcons.Search,
                        lottieRes = R.raw.lottie_explore,
                        headingText = if (query.trim().length < 2) {
                            "Continue à taper…"
                        } else {
                            "Aucune suggestion directe"
                        },
                        subtitleText = if (query.trim().length < 2) {
                            "Saisis au moins 2 caractères pour interroger le catalogue en direct."
                        } else {
                            "Appuie sur Rechercher pour lancer une recherche complète pour « ${query.trim()} »."
                        },
                        modifier = Modifier.fillParentMaxHeight(0.65f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionSectionHeader(
    title: String,
    actionLabel: String? = null,
    actionTag: String = "",
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.header,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (actionLabel != null && onAction != null) {
            TextButton(
                onClick = onAction,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .then(if (actionTag.isNotEmpty()) Modifier.testTag(actionTag) else Modifier),
            ) {
                Icon(
                    imageVector = NovayIcons.DeleteTrash,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun QuerySuggestionRow(
    item: QuerySuggestionUi,
    showRemove: Boolean,
    onSelect: () -> Unit,
    onFill: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val highlightColor = NovayTheme.overlays.crimsonOnContainer
    val annotatedTitle = remember(item.query, item.spans, highlightColor) {
        buildHighlightedText(item.query, item.spans, highlightColor)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onSelect)
                .padding(start = 8.dp, end = 2.dp, top = 4.dp, bottom = 4.dp)
                .testTag(
                    if (item.source == SuggestionQuerySource.RECENT) {
                        "browse_recent_row"
                    } else {
                        "browse_query_suggestion_row"
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (item.source == SuggestionQuerySource.RECENT) {
                    NovayIcons.HistoryClock
                } else {
                    NovayIcons.Search
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = annotatedTitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (showRemove) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("browse_remove_recent_button"),
                ) {
                    Icon(
                        imageVector = NovayIcons.Close,
                        contentDescription = "Supprimer cette recherche récente",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            IconButton(
                onClick = onFill,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("browse_fill_query_button"),
            ) {
                Icon(
                    imageVector = NovayIcons.FillQuery,
                    contentDescription = "Insérer dans la barre de recherche",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        )
    }
}

@Composable
private fun AnimeSuggestionRow(
    item: AnimeSuggestionUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val overlays = NovayTheme.overlays
    val highlightColor = overlays.crimsonOnContainer
    val annotatedTitle = remember(item.anime.title, item.spans, highlightColor) {
        buildHighlightedText(item.anime.title, item.spans, highlightColor)
    }
    val shape = MaterialTheme.shapes.medium

    val badgeColors = when (item.origin) {
        AnimeSuggestionOrigin.LIBRARY ->
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) to overlays.crimsonOnContainer
        AnimeSuggestionOrigin.HISTORY ->
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.20f) to MaterialTheme.colorScheme.secondary
        AnimeSuggestionOrigin.FEATURED ->
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.20f) to MaterialTheme.colorScheme.tertiary
        AnimeSuggestionOrigin.LIVE_CATALOG ->
            MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(0.5.dp, overlays.glassBorder, shape)
            .semantics(mergeDescendants = true) {}
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PosterImage(
            url = item.anime.posterUrl,
            contentDescription = item.anime.title,
            animeUrl = item.anime.url,
            animeTitle = item.anime.title,
            modifier = Modifier
                .size(width = 46.dp, height = 64.dp)
                .novaySharedPoster(item.anime.url)
                .clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = annotatedTitle,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NovayBadge(
                    text = item.origin.label,
                    color = badgeColors.first,
                    textColor = badgeColors.second,
                    shape = RoundedCornerShape(6.dp),
                )
                val sub = item.anime.subtitle ?: item.anime.language
                if (!sub.isNullOrBlank()) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = NovayIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun QuickJumpAnimeChipCard(
    item: AnimeSuggestionUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val overlays = NovayTheme.overlays
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = modifier
            .widthIn(min = 196.dp, max = 250.dp)
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(0.5.dp, overlays.glassBorder, shape)
            .semantics(mergeDescendants = true) {}
            .clickable(onClick = onClick)
            .padding(8.dp)
            .testTag("browse_quick_jump_card"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PosterImage(
            url = item.anime.posterUrl,
            contentDescription = item.anime.title,
            animeUrl = item.anime.url,
            animeTitle = item.anime.title,
            modifier = Modifier
                .size(width = 40.dp, height = 56.dp)
                .novaySharedPoster(item.anime.url)
                .clip(RoundedCornerShape(6.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = item.anime.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.origin.label,
                style = MaterialTheme.typography.labelSmall,
                color = overlays.crimsonOnContainer,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TagFlowWrap(
    horizontalSpacing: Dp,
    verticalSpacing: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = content,
    ) { measurables, constraints ->
        val hSpace = horizontalSpacing.roundToPx()
        val vSpace = verticalSpacing.roundToPx()
        val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)

        val rows = ArrayList<List<Placeable>>()
        val rowHeights = ArrayList<Int>()
        var currentRow = ArrayList<Placeable>()
        var currentRowWidth = 0
        var currentRowHeight = 0
        var widestRow = 0

        for (measurable in measurables) {
            val placeable = measurable.measure(childConstraints)
            val neededWidth = if (currentRow.isEmpty()) placeable.width else currentRowWidth + hSpace + placeable.width
            if (currentRow.isNotEmpty() && neededWidth > maxWidth) {
                rows.add(currentRow)
                rowHeights.add(currentRowHeight)
                widestRow = maxOf(widestRow, currentRowWidth)
                currentRow = ArrayList()
                currentRowWidth = placeable.width
                currentRowHeight = placeable.height
                currentRow.add(placeable)
            } else {
                currentRow.add(placeable)
                currentRowWidth = neededWidth
                currentRowHeight = maxOf(currentRowHeight, placeable.height)
            }
        }
        if (currentRow.isNotEmpty()) {
            rows.add(currentRow)
            rowHeights.add(currentRowHeight)
            widestRow = maxOf(widestRow, currentRowWidth)
        }

        val totalContentHeight = rowHeights.sum() + (rows.size - 1).coerceAtLeast(0) * vSpace
        val width = constraints.constrainWidth(if (constraints.hasBoundedWidth) constraints.maxWidth else widestRow)
        val height = constraints.constrainHeight(totalContentHeight)

        layout(width, height) {
            var y = 0
            for (rowIndex in rows.indices) {
                val row = rows[rowIndex]
                val rowH = rowHeights[rowIndex]
                var x = 0
                for (placeable in row) {
                    val yOffset = (rowH - placeable.height) / 2
                    placeable.placeRelative(x = x, y = y + yOffset)
                    x += placeable.width + hSpace
                }
                y += rowH + vSpace
            }
        }
    }
}

@Composable
private fun SmartSearchPreviewCard(
    item: AnimeSuggestionUi,
    onClick: () -> Unit,
) {
    val overlays = NovayTheme.overlays
    val shape = MaterialTheme.shapes.large
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                        MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ),
            )
            .border(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), shape)
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .padding(12.dp)
            .testTag("browse_smart_preview_card"),
    ) {
        PosterImage(
            url = item.anime.posterUrl,
            contentDescription = item.anime.title,
            animeUrl = item.anime.url,
            animeTitle = item.anime.title,
            modifier = Modifier
                .size(width = 56.dp, height = 82.dp)
                .novaySharedPoster(item.anime.url)
                .clip(MaterialTheme.shapes.small),
        )
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            NovayBadge(
                text = "Aperçu rapide · ${item.origin.label}",
                color = MaterialTheme.colorScheme.secondaryContainer,
                textColor = overlays.crimsonOnContainer,
                shape = RoundedCornerShape(6.dp),
            )
            Text(
                text = item.anime.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = item.anime.subtitle ?: item.anime.genres.take(2).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = NovayIcons.Play,
            contentDescription = "Ouvrir la fiche",
            tint = overlays.crimsonOnContainer,
            modifier = Modifier.size(20.dp),
        )
    }
}

internal fun buildHighlightedText(
    text: String,
    spans: List<HighlightSpan>,
    highlightColor: Color,
): AnnotatedString {
    if (spans.isEmpty() || text.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        for (span in spans) {
            val safeStart = span.start.coerceIn(0, text.length)
            val safeEnd = span.endExclusive.coerceIn(safeStart, text.length)
            if (safeEnd > safeStart) {
                addStyle(
                    style = SpanStyle(
                        color = highlightColor,
                        fontWeight = FontWeight.SemiBold,
                        background = highlightColor.copy(alpha = 0.14f),
                    ),
                    start = safeStart,
                    end = safeEnd,
                )
            }
        }
    }
}
