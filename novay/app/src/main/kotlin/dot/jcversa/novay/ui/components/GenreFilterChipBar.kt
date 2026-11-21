package dot.jcversa.novay.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dot.jcversa.novay.data.GenreCatalog
import dot.jcversa.novay.ui.theme.NovayTheme

/**
 * Barre horizontale défilable de filtres par genre (Cinema Midnight Noir) :
 * sélection unique avec puce « Tout » en tête pour réinitialiser le filtre.
 */
@Composable
fun GenreFilterChipBar(
    genres: List<String>,
    selectedGenre: String?,
    onSelectGenre: (String?) -> Unit,
    modifier: Modifier = Modifier,
    allLabel: String = "Tout",
    testTagPrefix: String = "genre_chip",
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
) {
    val listState = rememberLazyListState()
    val motion = LocalSlateMotion.current
    val overlays = NovayTheme.overlays

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .testTag("${testTagPrefix}_row"),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "__all__", contentType = "genre_chip") {
            val isAllSelected = selectedGenre == null
            GenreFilterPill(
                label = allLabel,
                isSelected = isAllSelected,
                tag = "${testTagPrefix}_all",
                onClick = { onSelectGenre(null) },
                motion = motion,
                overlays = overlays,
            )
        }

        items(
            items = genres,
            key = { GenreCatalog.slug(it) },
            contentType = { "genre_chip" },
        ) { genre ->
            val isSelected = selectedGenre != null &&
                GenreCatalog.foldKey(selectedGenre) == GenreCatalog.foldKey(genre)
            GenreFilterPill(
                label = genre,
                isSelected = isSelected,
                tag = "${testTagPrefix}_${GenreCatalog.slug(genre)}",
                onClick = {
                    onSelectGenre(if (isSelected) null else genre)
                },
                motion = motion,
                overlays = overlays,
            )
        }
    }
}

@Composable
private fun GenreFilterPill(
    label: String,
    isSelected: Boolean,
    tag: String,
    onClick: () -> Unit,
    motion: SlateMotion,
    overlays: dot.jcversa.novay.ui.theme.NovayOverlayColors,
) {
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = motion.colorSpec(180),
        label = "genre_chip_container_$tag",
    )
    val labelColor by animateColorAsState(
        targetValue = if (isSelected) {
            overlays.crimsonOnContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = motion.colorSpec(180),
        label = "genre_chip_label_$tag",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = motion.colorSpec(180),
        label = "genre_chip_border_$tag",
    )

    FilterChip(
        selected = isSelected,
        onClick = onClick,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .semantics {
                role = Role.Tab
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
