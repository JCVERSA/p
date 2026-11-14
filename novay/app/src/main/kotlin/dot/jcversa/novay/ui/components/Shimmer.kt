package dot.jcversa.novay.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dot.jcversa.novay.ui.theme.NovayTheme

/**
 * Squelettes de chargement animés (Shimmer) — respectent intégralement
 * reduceMotion en basculant vers une surface statique douce.
 */
@Composable
fun rememberShimmerBrush(): Brush {
    val motion = LocalSlateMotion.current
    val base = NovayTheme.overlays.shimmerBase
    val highlight = NovayTheme.overlays.shimmerHighlight
    if (motion.reduceMotion) {
        return Brush.linearGradient(colors = listOf(base, base))
    }
    val transition = rememberInfiniteTransition(label = "novay_shimmer")
    val shift by transition.animateFloat(
        initialValue = -400f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "novay_shimmer_shift",
    )
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(shift, 0f),
        end = Offset(shift + 360f, 220f),
    )
}

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(rememberShimmerBrush()),
    )
}

@Composable
fun PosterGridSkeleton(
    modifier: Modifier = Modifier,
    minCellSize: Dp = 140.dp,
    itemCount: Int = 9,
    compact: Boolean = false,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minCellSize),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        userScrollEnabled = false,
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = "Chargement du catalogue" },
    ) {
        items(itemCount) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f),
                    shape = MaterialTheme.shapes.small,
                )
                if (!compact) {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.78f)
                            .height(14.dp),
                        shape = MaterialTheme.shapes.extraSmall,
                    )
                }
            }
        }
    }
}

@Composable
fun RowListSkeleton(
    modifier: Modifier = Modifier,
    itemCount: Int = 6,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = "Chargement des éléments" },
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            userScrollEnabled = false,
        ) {
            items(itemCount) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShimmerBox(
                        modifier = Modifier.size(width = 58.dp, height = 84.dp),
                        shape = MaterialTheme.shapes.extraSmall,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .height(16.dp),
                            shape = MaterialTheme.shapes.extraSmall,
                        )
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth(0.45f)
                                .height(12.dp),
                            shape = MaterialTheme.shapes.extraSmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ShowScreenSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 840.dp)
            .padding(16.dp)
            .semantics { contentDescription = "Chargement de la fiche" },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            ShimmerBox(
                modifier = Modifier.size(width = 112.dp, height = 168.dp),
                shape = MaterialTheme.shapes.medium,
            )
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.85f).height(26.dp))
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.55f).height(16.dp))
                ShimmerBox(modifier = Modifier.width(88.dp).height(22.dp))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ShimmerBox(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = MaterialTheme.shapes.extraLarge,
            )
            ShimmerBox(
                modifier = Modifier
                    .width(110.dp)
                    .height(48.dp),
                shape = MaterialTheme.shapes.extraLarge,
            )
        }
        repeat(4) {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp),
                shape = MaterialTheme.shapes.medium,
            )
        }
    }
}

@Composable
fun EpisodesListSkeleton(
    modifier: Modifier = Modifier,
    itemCount: Int = 8,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 840.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics { contentDescription = "Chargement des épisodes" },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(itemCount) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 14.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShimmerBox(
                    modifier = Modifier.size(width = 48.dp, height = 22.dp),
                    shape = MaterialTheme.shapes.extraSmall,
                )
                Spacer(Modifier.width(12.dp))
                ShimmerBox(
                    modifier = Modifier
                        .weight(1f)
                        .height(16.dp),
                    shape = MaterialTheme.shapes.extraSmall,
                )
                Spacer(Modifier.width(16.dp))
                ShimmerBox(
                    modifier = Modifier.size(24.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                )
            }
        }
    }
}
