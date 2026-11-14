@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package dot.jcversa.novay.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Cartes animés style Aniyomi & Midnight Noir :
 *  - BadgeGroup fusionné en haut à gauche des affiches (hors-ligne + nouveaux) ;
 *  - micro-interaction GPU pure au toucher / survol (0.965f / 1.015f) ;
 *  - support natif de Modifier (dont LazyItemScope.animateItem()) ;
 *  - transition partagée de l'affiche vers ShowScreen (novaySharedPoster).
 */

@Composable
fun ContinueReadingButton(
    size: Dp,
    iconSize: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.minimumInteractiveComponentSize(),
        contentAlignment = Alignment.Center,
    ) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier
                .size(size)
                .testTag("continue_reading_button"),
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
}

@Composable
fun AnimeComfortableGridItem(
    title: String,
    posterUrl: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    animeUrl: String = "",
    sharedTransitionEnabled: Boolean = true,
    isSelected: Boolean = false,
    newEpisodes: Int = 0,
    downloadedCount: Int = 0,
    onClickContinue: (() -> Unit)? = null,
) {
    val shape = MaterialTheme.shapes.small
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val motion = LocalSlateMotion.current
    val scale by animateFloatAsState(
        targetValue = when {
            motion.reduceMotion -> 1f
            pressed -> 0.965f
            hovered -> 1.015f
            else -> 1f
        },
        animationSpec = motion.effectFloatSpec(),
        label = "grid_card_scale",
    )

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .selectedBackground(isSelected)
            .semantics(mergeDescendants = true) {}
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(2.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .novaySharedPoster(animeUrl = animeUrl, enabled = sharedTransitionEnabled)
                .clip(shape)
                .then(
                    if (isSelected) {
                        Modifier.border(
                            BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                            shape,
                        )
                    } else {
                        Modifier
                    }
                ),
        ) {
            PosterImage(
                url = posterUrl,
                contentDescription = title,
                modifier = Modifier
                    .matchParentSize()
                    .alpha(if (isSelected) 0.72f else 1f),
            )
            AnimeCoverBadges(
                newEpisodes = newEpisodes,
                downloadedCount = downloadedCount,
                modifier = Modifier.align(Alignment.TopStart),
            )
            if (onClickContinue != null) {
                ContinueReadingButton(
                    size = 32.dp,
                    iconSize = 18.dp,
                    onClick = onClickContinue,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp),
                )
            }
        }
        Text(
            text = title,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp, bottom = 2.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun AnimeCompactGridItem(
    title: String,
    posterUrl: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    animeUrl: String = "",
    sharedTransitionEnabled: Boolean = true,
    isSelected: Boolean = false,
    newEpisodes: Int = 0,
    downloadedCount: Int = 0,
    onClickContinue: (() -> Unit)? = null,
) {
    val shape = MaterialTheme.shapes.small
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val motion = LocalSlateMotion.current
    val scale by animateFloatAsState(
        targetValue = when {
            motion.reduceMotion -> 1f
            pressed -> 0.965f
            hovered -> 1.015f
            else -> 1f
        },
        animationSpec = motion.effectFloatSpec(),
        label = "compact_card_scale",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .novaySharedPoster(animeUrl = animeUrl, enabled = sharedTransitionEnabled)
            .clip(shape)
            .then(
                if (isSelected) {
                    Modifier.border(
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                        shape,
                    )
                } else {
                    Modifier
                }
            )
            .selectedBackground(isSelected)
            .semantics(mergeDescendants = true) {}
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        PosterImage(
            url = posterUrl,
            contentDescription = title,
            modifier = Modifier
                .matchParentSize()
                .alpha(if (isSelected) 0.72f else 1f),
        )
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to MaterialTheme.colorScheme.background.copy(alpha = 0.48f),
                        1f to MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
                    ),
                )
                .fillMaxHeight(0.55f)
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
        )
        AnimeCoverBadges(
            newEpisodes = newEpisodes,
            downloadedCount = downloadedCount,
            modifier = Modifier.align(Alignment.TopStart),
        )
        Text(
            text = title,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, end = if (onClickContinue != null) 46.dp else 8.dp, bottom = 8.dp),
            style = MaterialTheme.typography.titleSmall.copy(
                color = MaterialTheme.colorScheme.onBackground,
                shadow = Shadow(color = Color.Black, blurRadius = 6f),
            ),
            minLines = 1,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (onClickContinue != null) {
            ContinueReadingButton(
                size = 30.dp,
                iconSize = 17.dp,
                onClick = onClickContinue,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }
}

@Composable
fun AnimeListItem(
    title: String,
    posterUrl: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    animeUrl: String = "",
    sharedTransitionEnabled: Boolean = true,
    isSelected: Boolean = false,
    newEpisodes: Int = 0,
    downloadedCount: Int = 0,
    onClickContinue: (() -> Unit)? = null,
    subtitle: String? = null,
) {
    val shape = MaterialTheme.shapes.medium
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val motion = LocalSlateMotion.current
    val containerColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            hovered -> MaterialTheme.colorScheme.surfaceContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = motion.colorSpec(180),
        label = "list_item_bg",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed && !motion.reduceMotion) 0.985f else 1f,
        animationSpec = motion.effectFloatSpec(),
        label = "list_item_scale",
    )

    Surface(
        shape = shape,
        color = containerColor,
        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .semantics(mergeDescendants = true) {}
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PosterImage(
                url = posterUrl,
                contentDescription = title,
                modifier = Modifier
                    .size(width = 58.dp, height = 84.dp)
                    .novaySharedPoster(animeUrl = animeUrl, enabled = sharedTransitionEnabled)
                    .clip(MaterialTheme.shapes.extraSmall),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
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
                    BadgeGroup {
                        if (downloadedCount > 0) {
                            NovayBadge(
                                text = "$downloadedCount DL",
                                color = MaterialTheme.colorScheme.tertiary,
                                textColor = MaterialTheme.colorScheme.onTertiary,
                            )
                        }
                        if (newEpisodes > 0) {
                            NovayBadge(text = "+$newEpisodes")
                        }
                    }
                }
            }
            if (onClickContinue != null) {
                ContinueReadingButton(
                    size = 36.dp,
                    iconSize = 20.dp,
                    onClick = onClickContinue,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.AnimeCoverBadges(
    newEpisodes: Int,
    downloadedCount: Int,
    modifier: Modifier = Modifier,
) {
    if (newEpisodes <= 0 && downloadedCount <= 0) return
    BadgeGroup(
        modifier = modifier.padding(6.dp),
    ) {
        if (downloadedCount > 0) {
            NovayBadge(
                text = "$downloadedCount",
                color = MaterialTheme.colorScheme.tertiary,
                textColor = MaterialTheme.colorScheme.onTertiary,
            )
        }
        if (newEpisodes > 0) {
            NovayBadge(text = "+$newEpisodes")
        }
    }
}
