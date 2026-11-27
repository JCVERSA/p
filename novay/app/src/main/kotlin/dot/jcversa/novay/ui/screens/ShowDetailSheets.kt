package dot.jcversa.novay.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dot.jcversa.novay.R
import dot.jcversa.novay.data.AnimeDetails
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.AnimeSeason
import dot.jcversa.novay.data.WatchHistoryEntry
import dot.jcversa.novay.ui.components.NovayFavoriteMorphIcon
import dot.jcversa.novay.ui.components.NovayIcons
import dot.jcversa.novay.ui.theme.NovayTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShowActionsBottomSheet(
    animeTitle: String,
    inLibrary: Boolean,
    firstSeason: AnimeSeason?,
    resumeEntry: WatchHistoryEntry?,
    hasSeasons: Boolean,
    hasDetails: Boolean,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onResumeOrOpenFirst: () -> Unit,
    onMarkAllSeen: () -> Unit,
    onOpenDetails: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = animeTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            ShowSheetActionRow(
                label = if (inLibrary) "Retirer de la bibliothèque" else "Ajouter à la bibliothèque",
                icon = NovayIcons.Play,
                customLeading = {
                    NovayFavoriteMorphIcon(
                        inLibrary = inLibrary,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                onClick = onToggleFavorite,
            )
            firstSeason?.let { first ->
                ShowSheetActionRow(
                    label = resumeEntry?.let { "Reprendre Épisode ${it.episodeNumber}" }
                        ?: "Ouvrir ${first.name}",
                    icon = NovayIcons.Play,
                    onClick = onResumeOrOpenFirst,
                )
            }
            if (hasSeasons) {
                ShowSheetActionRow(
                    label = "Marquer les saisons comme vues",
                    icon = NovayIcons.MarkAllSeen,
                    onClick = onMarkAllSeen,
                )
            }
            if (hasDetails) {
                ShowSheetActionRow(
                    label = stringResource(R.string.show_details_sheet_title),
                    icon = NovayIcons.Info,
                    onClick = onOpenDetails,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShowDetailsBottomSheet(
    anime: AnimeSearchResult,
    details: AnimeDetails,
    genres: List<String>,
    seasons: List<AnimeSeason>,
    languages: List<String>,
    totalDownloads: Int,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 20.dp)
                .testTag("show_details_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.show_details_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                androidx.compose.material3.IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("show_details_close_button"),
                ) {
                    Icon(
                        imageVector = NovayIcons.Close,
                        contentDescription = stringResource(R.string.action_dismiss),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = anime.title,
                style = MaterialTheme.typography.titleMedium,
                color = NovayTheme.overlays.crimsonOnContainer,
            )
            details.alternativeTitle
                ?.takeIf { !it.equals(anime.title, ignoreCase = true) }
                ?.let { alt ->
                    DetailMetaRow(label = "Titre alternatif", value = alt)
                }
            details.format?.takeIf { it.isNotBlank() }?.let {
                DetailMetaRow(label = "Format", value = it)
            }
            details.releaseYear?.takeIf { it.isNotBlank() }?.let {
                DetailMetaRow(label = "Année", value = it)
            }
            details.status?.takeIf { it.isNotBlank() }?.let {
                DetailMetaRow(label = "Statut", value = it)
            }
            if (languages.isNotEmpty()) {
                DetailMetaRow(label = "Audio / Langues", value = languages.joinToString(" · "))
            }
            if (seasons.isNotEmpty()) {
                DetailMetaRow(
                    label = "Saisons listées",
                    value = "${seasons.size}",
                )
            }
            if (totalDownloads > 0) {
                DetailMetaRow(
                    label = "Hors-ligne",
                    value = "$totalDownloads téléchargement${if (totalDownloads > 1) "s" else ""}",
                )
            }
            if (genres.isNotEmpty()) {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
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
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
            }
            anime.synopsis?.takeIf { it.isNotBlank() }?.let { synopsis ->
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Text(
                    text = "Synopsis",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = synopsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DetailMetaRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ShowSheetActionRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    customLeading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (customLeading != null) {
            customLeading()
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
