package dot.jcversa.novay.ui.screens

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected as semanticsSelected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dot.jcversa.novay.R
import dot.jcversa.novay.data.AnimeDetails
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.AnimeSeason
import dot.jcversa.novay.data.WatchHistoryEntry
import dot.jcversa.novay.ui.components.BadgeGroup
import dot.jcversa.novay.ui.components.LangPill
import dot.jcversa.novay.ui.components.LocalSlateMotion
import dot.jcversa.novay.ui.components.NovayBadge
import dot.jcversa.novay.ui.components.NovayFavoriteMorphIcon
import dot.jcversa.novay.ui.components.NovayIcons
import dot.jcversa.novay.ui.components.NovaySectionHeader
import dot.jcversa.novay.ui.components.PosterImage
import dot.jcversa.novay.ui.components.novaySharedPoster
import dot.jcversa.novay.ui.theme.NovayTheme
import dot.jcversa.novay.ui.theme.header

@Composable
internal fun CinemaShowHeader(
    anime: AnimeSearchResult,
    details: AnimeDetails?,
    seasonCount: Int,
    languages: List<String>,
    parallaxOffsetProvider: () -> Float,
    onPosterResolved: (String) -> Unit,
    posterAccent: Color? = null,
) {
    val posterShape = MaterialTheme.shapes.medium
    val overlays = NovayTheme.overlays
    val motion = LocalSlateMotion.current
    val perf = dot.jcversa.novay.performance.LocalPerformanceProfile.current
    val supportsBlur = perf.allowAtmosphericBackdropBlur
    val adaptiveWash by animateColorAsState(
        targetValue = (posterAccent ?: MaterialTheme.colorScheme.primary).copy(
            alpha = if (posterAccent != null) 0.20f else 0.08f,
        ),
        animationSpec = motion.colorSpec(320),
        label = "show_header_adaptive_wash",
    )

    val metadataParts = remember(details, anime.subtitle, seasonCount) {
        buildList {
            details?.format?.takeIf { it.isNotBlank() }?.let(::add)
            details?.releaseYear?.takeIf { it.isNotBlank() }?.let(::add)
            details?.status?.takeIf { it.isNotBlank() }?.let(::add)
            if (isEmpty() && !anime.subtitle.isNullOrBlank()) {
                add(anime.subtitle)
            }
            if (seasonCount > 0) {
                add("$seasonCount saison${if (seasonCount > 1) "s" else ""}")
            }
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        PosterImage(
            url = anime.posterUrl,
            contentDescription = null,
            animeUrl = anime.url,
            animeTitle = anime.title,
            decodeWidthDp = 96.dp,
            decodeHeightDp = 144.dp,
            onPosterResolved = onPosterResolved,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = 1.08f
                    scaleY = 1.08f
                    translationY = parallaxOffsetProvider()
                }
                .then(if (supportsBlur) Modifier.blur(22.dp) else Modifier)
                .alpha(if (supportsBlur) 0.38f else 0.18f),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to adaptiveWash,
                        0.45f to MaterialTheme.colorScheme.background.copy(alpha = if (supportsBlur) 0.64f else 0.78f),
                        0.82f to MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
                        1f to MaterialTheme.colorScheme.background,
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 14.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            PosterImage(
                url = anime.posterUrl,
                contentDescription = anime.title,
                animeUrl = anime.url,
                animeTitle = anime.title,
                decodeWidthDp = 116.dp,
                decodeHeightDp = 174.dp,
                onPosterResolved = onPosterResolved,
                modifier = Modifier
                    .width(116.dp)
                    .aspectRatio(2f / 3f)
                    .novaySharedPoster(anime.url)
                    .clip(posterShape)
                    .border(0.5.dp, overlays.glassBorder, posterShape),
            )
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = anime.title,
                    style = MaterialTheme.typography.displayMedium.copy(
                        shadow = if (perf.allowCardTextShadowBlur) {
                            Shadow(color = Color.Black.copy(alpha = 0.55f), blurRadius = 8f)
                        } else {
                            null
                        },
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                details?.alternativeTitle
                    ?.takeIf { !it.equals(anime.title, ignoreCase = true) }
                    ?.let { alt ->
                        Text(
                            text = alt,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                if (metadataParts.isNotEmpty()) {
                    Text(
                        text = metadataParts.joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (languages.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        languages.forEach { lang ->
                            LangPill(text = lang, vf = lang.equals("VF", ignoreCase = true))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ShowActionBar(
    resumeEntry: WatchHistoryEntry?,
    firstSeason: AnimeSeason?,
    inLibrary: Boolean,
    hasDetails: Boolean,
    onPrimaryWatch: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenDetails: () -> Unit,
) {
    val overlays = NovayTheme.overlays
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            enabled = resumeEntry != null || firstSeason != null,
            onClick = onPrimaryWatch,
            colors = ButtonDefaults.buttonColors(
                containerColor = overlays.ctaContainer,
                contentColor = overlays.onCtaContainer,
            ),
            modifier = Modifier
                .weight(1f)
                .minimumInteractiveComponentSize()
                .testTag("show_primary_cta"),
        ) {
            Icon(
                imageVector = NovayIcons.Play,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (resumeEntry != null) {
                    "Reprendre Ep. ${resumeEntry.episodeNumber}"
                } else {
                    "Commencer"
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        val motion = LocalSlateMotion.current
        val favContainer by animateColorAsState(
            targetValue = if (inLibrary) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
            animationSpec = motion.colorSpec(200),
            label = "show_fav_bg",
        )
        val favBorder by animateColorAsState(
            targetValue = if (inLibrary) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
            animationSpec = motion.colorSpec(200),
            label = "show_fav_border",
        )
        val favContent by animateColorAsState(
            targetValue = if (inLibrary) {
                overlays.crimsonOnContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            animationSpec = motion.colorSpec(200),
            label = "show_fav_fg",
        )

        OutlinedButton(
            onClick = onToggleFavorite,
            border = BorderStroke(
                width = 1.dp,
                color = favBorder,
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = favContainer,
                contentColor = favContent,
            ),
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("show_favorite_button"),
        ) {
            NovayFavoriteMorphIcon(
                inLibrary = inLibrary,
                tint = favContent,
                size = 18.dp,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (inLibrary) "Suivi" else "Suivre",
                maxLines = 1,
            )
        }

        if (hasDetails) {
            OutlinedButton(
                onClick = onOpenDetails,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("show_details_button"),
            ) {
                Icon(
                    imageVector = NovayIcons.Info,
                    contentDescription = stringResource(R.string.show_details_action),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
internal fun ShowSynopsisAndGenres(
    synopsis: String?,
    genres: List<String>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
    if (synopsis.isNullOrBlank() && genres.isEmpty()) return
    val motion = LocalSlateMotion.current
    val overlays = NovayTheme.overlays
    var hasOverflow by remember(synopsis) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .then(
                if (motion.reduceMotion) {
                    Modifier
                } else {
                    Modifier.animateContentSize(animationSpec = motion.sizeTransitionSpec(220))
                },
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!synopsis.isNullOrBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = synopsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { result ->
                        if (!expanded) {
                            hasOverflow = result.hasVisualOverflow || synopsis.length > 150
                        }
                    },
                )
                if (hasOverflow || expanded) {
                    Text(
                        text = if (expanded) {
                            stringResource(R.string.show_synopsis_read_less)
                        } else {
                            stringResource(R.string.show_synopsis_read_more)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = overlays.crimsonOnContainer,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable(onClick = onToggleExpanded)
                            .minimumInteractiveComponentSize()
                            .padding(horizontal = 2.dp, vertical = 10.dp)
                            .testTag("show_synopsis_toggle"),
                    )
                }
            }
        }

        if (genres.isNotEmpty()) {
            NovayFlowChipRow(
                horizontalGap = 6.dp,
                verticalGap = 6.dp,
            ) {
                genres.forEach { genre ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Text(
                            text = genre,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun NovayFlowChipRow(
    modifier: Modifier = Modifier,
    horizontalGap: Dp = 6.dp,
    verticalGap: Dp = 6.dp,
    content: @Composable () -> Unit,
) {
    Layout(
        content = content,
        modifier = modifier,
    ) { measurables, constraints ->
        val hGapPx = horizontalGap.roundToPx()
        val vGapPx = verticalGap.roundToPx()
        val maxWidth = constraints.maxWidth
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)

        val rows = mutableListOf<List<Placeable>>()
        val rowWidths = mutableListOf<Int>()
        val rowHeights = mutableListOf<Int>()

        var currentRow = mutableListOf<Placeable>()
        var currentWidth = 0
        var currentHeight = 0

        for (measurable in measurables) {
            val placeable = measurable.measure(childConstraints)
            val neededWidth = if (currentRow.isEmpty()) placeable.width else currentWidth + hGapPx + placeable.width
            if (currentRow.isNotEmpty() && maxWidth != Constraints.Infinity && neededWidth > maxWidth) {
                rows += currentRow
                rowWidths += currentWidth
                rowHeights += currentHeight
                currentRow = mutableListOf(placeable)
                currentWidth = placeable.width
                currentHeight = placeable.height
            } else {
                currentRow += placeable
                currentWidth = neededWidth
                currentHeight = maxOf(currentHeight, placeable.height)
            }
        }
        if (currentRow.isNotEmpty()) {
            rows += currentRow
            rowWidths += currentWidth
            rowHeights += currentHeight
        }

        val totalWidth = (rowWidths.maxOrNull() ?: 0).coerceIn(constraints.minWidth, constraints.maxWidth)
        val totalHeight = (rowHeights.sum() + (rows.size - 1).coerceAtLeast(0) * vGapPx)
            .coerceIn(constraints.minHeight, constraints.maxHeight)

        layout(totalWidth, totalHeight) {
            var y = 0
            rows.forEachIndexed { rowIndex, row ->
                var x = 0
                val rowH = rowHeights[rowIndex]
                for (placeable in row) {
                    val yOffset = (rowH - placeable.height) / 2
                    placeable.placeRelative(x, y + yOffset)
                    x += placeable.width + hGapPx
                }
                y += rowH + vGapPx
            }
        }
    }
}

@Composable
internal fun LanguageSegmentedBar(
    grouped: List<Pair<String, List<AnimeSeason>>>,
    selectedLanguage: String?,
    onSelectLanguage: (String?) -> Unit,
) {
    if (grouped.isEmpty()) return
    val motion = LocalSlateMotion.current
    val overlays = NovayTheme.overlays

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        NovaySectionHeader(title = stringResource(R.string.show_seasons_heading))
        if (grouped.size > 1) {
            val totalCount = grouped.sumOf { it.second.size }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    grouped.forEach { (lang, list) ->
                        LanguageSegmentPill(
                            label = "$lang · ${list.size}",
                            selected = selectedLanguage == lang,
                            isVf = lang.equals("VF", ignoreCase = true),
                            reduceMotion = motion.reduceMotion,
                            overlaysCrimson = overlays.crimsonOnContainer,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("show_lang_segment_$lang"),
                            onClick = { onSelectLanguage(lang) },
                        )
                    }
                    LanguageSegmentPill(
                        label = "${stringResource(R.string.show_language_all)} · $totalCount",
                        selected = selectedLanguage == null,
                        isVf = false,
                        reduceMotion = motion.reduceMotion,
                        overlaysCrimson = overlays.crimsonOnContainer,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("show_lang_segment_ALL"),
                        onClick = { onSelectLanguage(null) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageSegmentPill(
    label: String,
    selected: Boolean,
    isVf: Boolean,
    reduceMotion: Boolean,
    overlaysCrimson: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val motion = LocalSlateMotion.current
    val targetContainer = when {
        selected && isVf -> MaterialTheme.colorScheme.secondaryContainer
        selected -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> Color.Transparent
    }
    val containerColor by animateColorAsState(
        targetValue = targetContainer,
        animationSpec = motion.colorSpec(180),
        label = "lang_segment_bg",
    )
    val textColor = when {
        selected && isVf -> overlaysCrimson
        selected -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(containerColor)
            .then(
                if (selected) {
                    Modifier.border(
                        width = 0.75.dp,
                        color = if (isVf) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    )
                } else {
                    Modifier
                },
            )
            .semantics {
                role = Role.Tab
                semanticsSelected = selected
            }
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun CollapsibleSeasonGroupHeader(
    language: String,
    seasonCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val motion = LocalSlateMotion.current
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = motion.effectFloatSpec(),
        label = "season_group_chevron",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.95f))
            .clickable(onClick = onToggle)
            .minimumInteractiveComponentSize()
            .testTag("season_group_header_$language"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = 16.dp)
                    .clip(CircleShape)
                    .background(
                        if (language == "VF") {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        },
                    ),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$language · $seasonCount saison${if (seasonCount > 1) "s" else ""}",
                style = MaterialTheme.typography.header,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = NovayIcons.ChevronDown,
                contentDescription = if (expanded) "Réduire $language" else "Développer $language",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { rotationZ = rotation },
            )
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        )
    }
}

@Composable
internal fun SeasonRow(
    season: AnimeSeason,
    downloaded: Int,
    knownEpisodeCount: Int?,
    isResumeSeason: Boolean,
    resumeEpisodeNumber: Int?,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    val motion = LocalSlateMotion.current
    val overlays = NovayTheme.overlays
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !motion.reduceMotion) 0.985f else 1f,
        animationSpec = motion.effectFloatSpec(),
        label = "season_row_scale",
    )
    val containerColor by animateColorAsState(
        targetValue = when {
            isActive -> MaterialTheme.colorScheme.secondaryContainer
            hovered -> MaterialTheme.colorScheme.surfaceContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = motion.colorSpec(180),
        label = "season_row_bg",
    )

    val secondaryMeta = remember(season.language, knownEpisodeCount, isResumeSeason, resumeEpisodeNumber) {
        buildList {
            season.language?.let { add(it.uppercase()) }
            if (knownEpisodeCount != null && knownEpisodeCount > 0) {
                add("$knownEpisodeCount ép.")
            }
            if (isResumeSeason && resumeEpisodeNumber != null) {
                add("En cours · Ep. $resumeEpisodeNumber")
            }
        }.joinToString(" · ")
    }

    Surface(
        shape = shape,
        color = containerColor,
        border = when {
            isActive -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            isResumeSeason -> BorderStroke(0.75.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
            else -> null
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .semantics(mergeDescendants = true) {}
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick,
            )
            .minimumInteractiveComponentSize(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = season.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isActive) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (downloaded > 0) {
                        BadgeGroup {
                            NovayBadge(
                                text = "$downloaded DL",
                                color = MaterialTheme.colorScheme.tertiary,
                                textColor = MaterialTheme.colorScheme.onTertiary,
                            )
                        }
                    }
                }
                if (secondaryMeta.isNotEmpty()) {
                    Text(
                        text = secondaryMeta,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isResumeSeason) {
                            overlays.crimsonOnContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = NovayIcons.ChevronRight,
                contentDescription = "Voir les épisodes de ${season.name}",
                tint = if (isActive || isResumeSeason) {
                    overlays.crimsonOnContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
internal fun RelatedAnimeRail(
    related: List<AnimeSearchResult>,
    onOpenShow: (AnimeSearchResult) -> Unit,
) {
    if (related.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        NovaySectionHeader(
            title = stringResource(R.string.show_related_heading),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(
                items = related,
                key = { _, item -> item.url },
            ) { index, item ->
                Column(
                    modifier = Modifier
                        .width(108.dp)
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onOpenShow(item) }
                        .minimumInteractiveComponentSize()
                        .testTag("show_related_card_$index"),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PosterImage(
                        url = item.posterUrl,
                        contentDescription = item.title,
                        animeUrl = item.url,
                        animeTitle = item.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .clip(MaterialTheme.shapes.small)
                            .border(
                                width = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = MaterialTheme.shapes.small,
                            ),
                    )
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun CollapsibleSectionVisibility(
    visible: Boolean,
    reduceMotion: Boolean,
    content: @Composable () -> Unit,
) {
    if (reduceMotion) {
        if (visible) content()
    } else {
        AnimatedVisibility(
            visible = visible,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            content()
        }
    }
}
