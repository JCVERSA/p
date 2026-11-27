@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dot.jcversa.novay.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dot.jcversa.novay.R
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.GenreCatalog
import dot.jcversa.novay.ui.components.LocalSlateMotion
import dot.jcversa.novay.ui.components.NovayIcons
import dot.jcversa.novay.ui.state.CatalogAvailableFacets
import dot.jcversa.novay.ui.state.CatalogFilterChipItem
import dot.jcversa.novay.ui.state.CatalogFilterEngine
import dot.jcversa.novay.ui.state.CatalogFilterState
import dot.jcversa.novay.ui.state.CatalogSortOption
import dot.jcversa.novay.ui.theme.NovayTheme
import dot.jcversa.novay.ui.theme.header

@Composable
internal fun CatalogFilterButton(
    activeCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalSlateMotion.current
    val overlays = NovayTheme.overlays
    val isActive = activeCount > 0
    val containerColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = motion.colorSpec(180),
        label = "filter_btn_bg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = motion.colorSpec(180),
        label = "filter_btn_border",
    )
    val contentTint by animateColorAsState(
        targetValue = if (isActive) {
            overlays.crimsonOnContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = motion.colorSpec(180),
        label = "filter_btn_tint",
    )
    val cdText = if (isActive) {
        "${stringResource(R.string.catalog_filter_button_cd)} ($activeCount actifs)"
    } else {
        stringResource(R.string.catalog_filter_button_cd)
    }

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag("browse_filter_button"),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = containerColor,
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier
                .size(52.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .semantics {
                    role = Role.Button
                    contentDescription = cdText
                }
                .clickable(onClick = onClick),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = NovayIcons.FilterFunnel,
                    contentDescription = null,
                    tint = contentTint,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (isActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .border(1.5.dp, MaterialTheme.colorScheme.background, CircleShape)
                    .testTag("browse_filter_badge"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = activeCount.coerceAtMost(99).toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun ActiveFilterChipsRow(
    chips: List<CatalogFilterChipItem>,
    onRemoveChip: (CatalogFilterChipItem) -> Unit,
    onResetAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalSlateMotion.current
    val overlays = NovayTheme.overlays

    if (motion.reduceMotion) {
        if (chips.isNotEmpty()) {
            ActiveFilterChipsContent(
                chips = chips,
                overlays = overlays,
                onRemoveChip = onRemoveChip,
                onResetAll = onResetAll,
                modifier = modifier,
            )
        }
    } else {
        AnimatedVisibility(
            visible = chips.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            ActiveFilterChipsContent(
                chips = chips,
                overlays = overlays,
                onRemoveChip = onRemoveChip,
                onResetAll = onResetAll,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun ActiveFilterChipsContent(
    chips: List<CatalogFilterChipItem>,
    overlays: dot.jcversa.novay.ui.theme.NovayOverlayColors,
    onRemoveChip: (CatalogFilterChipItem) -> Unit,
    onResetAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .testTag("browse_active_filters_row"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(
            items = chips,
            key = { it.key },
            contentType = { "active_filter_chip" },
        ) { chip ->
            val chipMod = if (LocalSlateMotion.current.reduceMotion) Modifier else Modifier.animateItem()
            InputChip(
                selected = true,
                onClick = { onRemoveChip(chip) },
                modifier = chipMod
                    .minimumInteractiveComponentSize()
                    .testTag("browse_active_chip_${chip.key}"),
                label = {
                    Text(
                        text = chip.label,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingIcon = {
                    Icon(
                        imageVector = NovayIcons.Close,
                        contentDescription = "Retirer ${chip.label}",
                        modifier = Modifier.size(14.dp),
                    )
                },
                colors = InputChipDefaults.inputChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = overlays.crimsonOnContainer,
                    selectedTrailingIconColor = overlays.crimsonOnContainer,
                ),
                border = InputChipDefaults.inputChipBorder(
                    enabled = true,
                    selected = true,
                    selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                    selectedBorderWidth = 1.dp,
                ),
            )
        }
        item(key = "__reset_all__", contentType = "reset_chip") {
            TextButton(
                onClick = onResetAll,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("browse_filters_reset_chip"),
            ) {
                Text(
                    text = stringResource(R.string.catalog_filter_reset),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun CatalogFilterBottomSheet(
    initialFilter: CatalogFilterState,
    facets: CatalogAvailableFacets,
    previewItems: List<AnimeSearchResult>,
    recentOrderUrls: Map<String, Int>,
    onDismiss: () -> Unit,
    onApply: (CatalogFilterState) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val overlays = NovayTheme.overlays
    val motion = LocalSlateMotion.current
    var draft by remember(initialFilter) { mutableStateOf(initialFilter) }

    val matchingPreviewCount = remember(draft, previewItems, recentOrderUrls) {
        CatalogFilterEngine.filterAndSort(
            items = previewItems,
            filter = draft,
            recentOrderUrls = recentOrderUrls,
        ).size
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.testTag("catalog_filter_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            // ── En-tête feuille ───────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.catalog_filter_sheet_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    androidx.compose.animation.AnimatedContent(
                        targetState = matchingPreviewCount,
                        transitionSpec = { motion.stateCrossfade() },
                        label = "filter_preview_count_anim",
                    ) { count ->
                        val countText = if (count == 1) {
                            "1 animé correspondant"
                        } else {
                            "$count animés correspondants"
                        }
                        Text(
                            text = countText,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (draft.hasAnyActiveFilter) {
                                overlays.crimsonOnContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.testTag("filter_sheet_preview_count"),
                        )
                    }
                }
                if (draft.activeCount > 0) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    ) {
                        Text(
                            text = "${draft.activeCount} actif${if (draft.activeCount > 1) "s" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = overlays.crimsonOnContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            )

            // ── Groupes de filtres défilables ─────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                // Tri
                FilterSectionGroup(title = stringResource(R.string.catalog_filter_sort)) {
                    NovayFlowChipRow(horizontalGap = 8.dp, verticalGap = 8.dp) {
                        facets.sortOptions.forEach { option ->
                            val selected = draft.sort == option
                            SheetFilterPill(
                                label = option.label,
                                isSelected = selected,
                                tag = "filter_sort_${option.name.lowercase()}",
                                onClick = { draft = draft.copy(sort = option) },
                            )
                        }
                    }
                }

                // Langue (VF / VOSTFR)
                if (facets.languages.isNotEmpty()) {
                    FilterSectionGroup(title = stringResource(R.string.catalog_filter_language)) {
                        NovayFlowChipRow(horizontalGap = 8.dp, verticalGap = 8.dp) {
                            SheetFilterPill(
                                label = stringResource(R.string.filter_all),
                                isSelected = draft.language == null,
                                tag = "filter_lang_all",
                                onClick = { draft = draft.copy(language = null) },
                            )
                            facets.languages.forEach { lang ->
                                val isSelected = draft.language.equals(lang, ignoreCase = true)
                                SheetFilterPill(
                                    label = lang,
                                    isSelected = isSelected,
                                    tag = "filter_lang_${lang.lowercase()}",
                                    onClick = {
                                        draft = draft.copy(language = if (isSelected) null else lang)
                                    },
                                )
                            }
                        }
                    }
                }

                // Format / Type (Anime / Film / OAV / Scans)
                if (facets.formats.isNotEmpty()) {
                    FilterSectionGroup(title = stringResource(R.string.catalog_filter_format)) {
                        NovayFlowChipRow(horizontalGap = 8.dp, verticalGap = 8.dp) {
                            SheetFilterPill(
                                label = stringResource(R.string.filter_all),
                                isSelected = draft.format == null,
                                tag = "filter_format_all",
                                onClick = { draft = draft.copy(format = null) },
                            )
                            facets.formats.forEach { fmt ->
                                val isSelected = draft.format.equals(fmt, ignoreCase = true)
                                SheetFilterPill(
                                    label = fmt,
                                    isSelected = isSelected,
                                    tag = "filter_format_${fmt.lowercase()}",
                                    onClick = {
                                        draft = draft.copy(format = if (isSelected) null else fmt)
                                    },
                                )
                            }
                        }
                    }
                }

                // Année (affichée uniquement si de vraies années HTML sont présentes)
                if (facets.years.isNotEmpty()) {
                    FilterSectionGroup(title = stringResource(R.string.catalog_filter_year)) {
                        NovayFlowChipRow(horizontalGap = 8.dp, verticalGap = 8.dp) {
                            SheetFilterPill(
                                label = stringResource(R.string.filter_all),
                                isSelected = draft.releaseYear == null,
                                tag = "filter_year_all",
                                onClick = { draft = draft.copy(releaseYear = null) },
                            )
                            facets.years.forEach { year ->
                                val isSelected = draft.releaseYear == year
                                SheetFilterPill(
                                    label = year,
                                    isSelected = isSelected,
                                    tag = "filter_year_$year",
                                    onClick = {
                                        draft = draft.copy(releaseYear = if (isSelected) null else year)
                                    },
                                )
                            }
                        }
                    }
                }

                // Statut (affiché uniquement si de vrais statuts HTML sont présents)
                if (facets.statuses.isNotEmpty()) {
                    FilterSectionGroup(title = stringResource(R.string.catalog_filter_status)) {
                        NovayFlowChipRow(horizontalGap = 8.dp, verticalGap = 8.dp) {
                            SheetFilterPill(
                                label = stringResource(R.string.filter_all),
                                isSelected = draft.status == null,
                                tag = "filter_status_all",
                                onClick = { draft = draft.copy(status = null) },
                            )
                            facets.statuses.forEach { st ->
                                val isSelected = draft.status.equals(st, ignoreCase = true)
                                SheetFilterPill(
                                    label = st,
                                    isSelected = isSelected,
                                    tag = "filter_status_${GenreCatalog.slug(st)}",
                                    onClick = {
                                        draft = draft.copy(status = if (isSelected) null else st)
                                    },
                                )
                            }
                        }
                    }
                }

                // Genres multi-sélection
                FilterSectionGroup(
                    title = if (draft.genres.isEmpty()) {
                        stringResource(R.string.catalog_filter_genres)
                    } else {
                        "${stringResource(R.string.catalog_filter_genres)} (${draft.genres.size})"
                    },
                ) {
                    NovayFlowChipRow(horizontalGap = 8.dp, verticalGap = 8.dp) {
                        facets.genres.forEach { genre ->
                            val isSelected = draft.genres.any {
                                GenreCatalog.foldKey(it) == GenreCatalog.foldKey(genre)
                            }
                            SheetFilterPill(
                                label = genre,
                                isSelected = isSelected,
                                tag = "filter_genre_${GenreCatalog.slug(genre)}",
                                onClick = {
                                    val nextGenres = if (isSelected) {
                                        draft.genres.filterNot {
                                            GenreCatalog.foldKey(it) == GenreCatalog.foldKey(genre)
                                        }.toSet()
                                    } else {
                                        draft.genres + genre
                                    }
                                    draft = draft.copy(genres = nextGenres)
                                },
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            )

            // ── Actions Réinitialiser / Appliquer ─────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { draft = CatalogFilterState() },
                    enabled = draft.hasAnyActiveFilter,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("filter_sheet_reset"),
                ) {
                    Text(stringResource(R.string.catalog_filter_reset))
                }
                Button(
                    onClick = {
                        onApply(draft)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = overlays.ctaContainer,
                        contentColor = overlays.onCtaContainer,
                    ),
                    modifier = Modifier
                        .weight(1.4f)
                        .minimumInteractiveComponentSize()
                        .testTag("filter_sheet_apply"),
                ) {
                    Text(stringResource(R.string.catalog_filter_apply))
                }
            }
        }
    }
}

@Composable
private fun FilterSectionGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.header,
            color = MaterialTheme.colorScheme.onBackground,
        )
        content()
    }
}

@Composable
private fun SheetFilterPill(
    label: String,
    isSelected: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    val motion = LocalSlateMotion.current
    val overlays = NovayTheme.overlays
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = motion.colorSpec(180),
        label = "sheet_pill_bg_$tag",
    )
    val labelColor by animateColorAsState(
        targetValue = if (isSelected) {
            overlays.crimsonOnContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = motion.colorSpec(180),
        label = "sheet_pill_fg_$tag",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = motion.colorSpec(180),
        label = "sheet_pill_border_$tag",
    )

    FilterChip(
        selected = isSelected,
        onClick = onClick,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .semantics {
                selected = isSelected
            }
            .testTag(tag),
        leadingIcon = if (isSelected) {
            {
                Icon(
                    imageVector = NovayIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        } else {
            null
        },
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = containerColor,
            labelColor = labelColor,
            selectedContainerColor = containerColor,
            selectedLabelColor = labelColor,
            selectedLeadingIconColor = labelColor,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = isSelected,
            borderColor = borderColor,
            selectedBorderColor = borderColor,
            borderWidth = 1.dp,
            selectedBorderWidth = 1.dp,
        ),
    )
}
