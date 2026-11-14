@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package dot.jcversa.novay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Items de bibliothèque — portés de Mihon/Tachiyomi
 * (CommonMangaItem.kt, Apache-2.0), adaptés à NoVaY :
 *  - grille compacte  : titre en surimpression sur la couverture ;
 *  - grille confortable : titre SOUS la couverture ;
 *  - liste            : couverture à gauche, titres à droite.
 *
 * Badges : bas-droite = nouveaux épisodes (secondary), bas-gauche =
 * téléchargés (tertiary) — exactement les rôles Mihon.
 */

/** Bouton « Continuer » (disque plein, coin bas-droit). */
@Composable
fun ContinueReadingButton(
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.size(size),
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = "Reprendre la lecture",
            modifier = Modifier.size(iconSize),
        )
    }
}

/** Grille confortable : couverture + titre dessous + badges. */
@Composable
fun AnimeComfortableGridItem(
    title: String,
    posterUrl: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isSelected: Boolean = false,
    newEpisodes: Int = 0,
    downloadedCount: Int = 0,
    onClickContinue: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .selectedBackground(isSelected)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box {
            PosterImage(
                url = posterUrl,
                contentDescription = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .alpha(if (isSelected) 0.76f else 1f),
            )
            AnimeCoverBadges(
                newEpisodes = newEpisodes,
                downloadedCount = downloadedCount,
            )
            if (onClickContinue != null) {
                ContinueReadingButton(
                    size = 28.dp,
                    iconSize = 16.dp,
                    onClick = onClickContinue,
                    modifier = Modifier
                        .padding(6.dp)
                        .align(Alignment.BottomEnd),
                )
            }
        }
        Text(
            text = title,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Grille compacte : titre en surimpression (dégradé bas). */
@Composable
fun AnimeCompactGridItem(
    title: String,
    posterUrl: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isSelected: Boolean = false,
    newEpisodes: Int = 0,
    downloadedCount: Int = 0,
    onClickContinue: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .selectedBackground(isSelected)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        PosterImage(
            url = posterUrl,
            contentDescription = title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(MaterialTheme.shapes.extraSmall)
                .alpha(if (isSelected) 0.76f else 1f),
        )
        AnimeCoverBadges(
            newEpisodes = newEpisodes,
            downloadedCount = downloadedCount,
        )
        // Surimpression : dégradé noir + titre blanc (ombre).
        Box(
            modifier = Modifier
                .clip(MaterialTheme.shapes.extraSmall)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color(0xAA000000),
                    ),
                )
                .fillMaxHeight(0.33f)
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
        )
        Text(
            text = title,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp),
            style = MaterialTheme.typography.titleSmall.copy(
                color = Color.White,
                shadow = Shadow(color = Color.Black, blurRadius = 4f),
            ),
            minLines = 1,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (onClickContinue != null) {
            ContinueReadingButton(
                size = 28.dp,
                iconSize = 16.dp,
                onClick = onClickContinue,
                modifier = Modifier
                    .padding(6.dp)
                    .align(Alignment.BottomEnd),
            )
        }
    }
}

/** Item de liste : couverture 56×80 + titres. */
@Composable
fun AnimeListItem(
    title: String,
    posterUrl: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isSelected: Boolean = false,
    newEpisodes: Int = 0,
    downloadedCount: Int = 0,
    onClickContinue: (() -> Unit)? = null,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier
            .selectedBackground(isSelected)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            PosterImage(
                url = posterUrl,
                contentDescription = title,
                modifier = Modifier
                    .size(width = 56.dp, height = 80.dp)
                    .clip(MaterialTheme.shapes.extraSmall),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (newEpisodes > 0 || downloadedCount > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (newEpisodes > 0) {
                        NovayBadge(text = "$newEpisodes")
                    }
                    if (downloadedCount > 0) {
                        NovayBadge(
                            text = "$downloadedCount",
                            color = MaterialTheme.colorScheme.tertiary,
                            textColor = MaterialTheme.colorScheme.onTertiary,
                        )
                    }
                }
            }
        }
        if (onClickContinue != null) {
            ContinueReadingButton(
                size = 32.dp,
                iconSize = 20.dp,
                onClick = onClickContinue,
            )
        }
    }
}

/** Badges Mihon sur une couverture (coin bas). */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.AnimeCoverBadges(
    newEpisodes: Int,
    downloadedCount: Int,
) {
    Row(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (downloadedCount > 0) {
            NovayBadge(
                text = "$downloadedCount",
                color = MaterialTheme.colorScheme.tertiary,
                textColor = MaterialTheme.colorScheme.onTertiary,
                shape = MaterialTheme.shapes.extraSmall,
            )
        }
        if (newEpisodes > 0) {
            NovayBadge(text = "$newEpisodes")
        }
    }
}
