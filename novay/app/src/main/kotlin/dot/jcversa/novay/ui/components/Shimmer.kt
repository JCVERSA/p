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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dot.jcversa.novay.performance.LocalPerformanceProfile
import dot.jcversa.novay.ui.theme.NovayTheme

val LocalSharedShimmerProgress = staticCompositionLocalOf<(() -> Float)?> { null }

@Composable
fun SharedShimmerScope(content: @Composable () -> Unit) {
    val motion = LocalSlateMotion.current
    val perf = LocalPerformanceProfile.current
    val sharedProvider: (() -> Float)? = if (motion.reduceMotion || !perf.allowAnimatedShimmer) {
        remember { { -1f } }
    } else {
        val transition = rememberInfiniteTransition(label = "novay_shared_shimmer")
        val progressState = transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1250, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "novay_shared_shimmer_progress",
        )
        remember(progressState) { { progressState.value } }
    }
    CompositionLocalProvider(
        LocalSharedShimmerProgress provides sharedProvider,
        content = content,
    )
}

/**
 * Squelettes de chargement animés (Shimmer) — zéro recomposition et zéro
 * allocation par image : la progression (0f..1f) est lue exclusivement en
 * phase Draw (`drawWithCache` + `translate`) et partagée au sein d'un même
 * squelette ou de [SharedShimmerScope]. Bascule en teinte statique sur
 * appareils modestes ou lorsque `reduceMotion` est actif.
 */
@Composable
fun rememberShimmerProgress(): () -> Float {
    val motion = LocalSlateMotion.current
    val perf = LocalPerformanceProfile.current
    if (motion.reduceMotion || !perf.allowAnimatedShimmer) {
        return remember { { -1f } }
    }
    LocalSharedShimmerProgress.current?.let { return it }
    val transition = rememberInfiniteTransition(label = "novay_shimmer")
    val progressState = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "novay_shimmer_progress",
    )
    return remember(progressState) { { progressState.value } }
}

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
    progressProvider: (() -> Float)? = null,
) {
    val resolvedProvider = progressProvider ?: rememberShimmerProgress()
    val base = NovayTheme.overlays.shimmerBase
    val highlight = NovayTheme.overlays.shimmerHighlight
    Box(
        modifier = modifier
            .clip(shape)
            .drawWithCache {
                val shimmerWidth = (size.width * 0.7f).coerceAtLeast(180f)
                val gradient = Brush.linearGradient(
                    colors = listOf(Color.Transparent, highlight, Color.Transparent),
                    start = Offset.Zero,
                    end = Offset(shimmerWidth, size.height * 0.45f),
                )
                val bandSize = Size(shimmerWidth, size.height)
                onDrawBehind {
                    drawRect(color = base)
                    val progress = resolvedProvider()
                    if (progress >= 0f) {
                        val totalSpan = size.width + shimmerWidth
                        val dx = -shimmerWidth + progress * totalSpan
                        translate(left = dx) {
                            drawRect(brush = gradient, size = bandSize)
                        }
                    }
                }
            },
    )
}

@Composable
fun PosterGridSkeleton(
    modifier: Modifier = Modifier,
    minCellSize: Dp = 140.dp,
    itemCount: Int = 9,
    compact: Boolean = false,
) {
    val shimmerProgress = rememberShimmerProgress()
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
                    progressProvider = shimmerProgress,
                )
                if (!compact) {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.78f)
                            .height(14.dp),
                        shape = MaterialTheme.shapes.extraSmall,
                        progressProvider = shimmerProgress,
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
    val shimmerProgress = rememberShimmerProgress()
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
                        progressProvider = shimmerProgress,
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
                            progressProvider = shimmerProgress,
                        )
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth(0.45f)
                                .height(12.dp),
                            shape = MaterialTheme.shapes.extraSmall,
                            progressProvider = shimmerProgress,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ShowScreenSkeleton(modifier: Modifier = Modifier) {
    val shimmerProgress = rememberShimmerProgress()
    Column(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 840.dp)
            .padding(16.dp)
            .semantics { contentDescription = "Chargement de la fiche" },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            ShimmerBox(
                modifier = Modifier.size(width = 116.dp, height = 174.dp),
                shape = MaterialTheme.shapes.medium,
                progressProvider = shimmerProgress,
            )
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ShimmerBox(
                    modifier = Modifier.fillMaxWidth(0.88f).height(28.dp),
                    progressProvider = shimmerProgress,
                )
                ShimmerBox(
                    modifier = Modifier.fillMaxWidth(0.62f).height(15.dp),
                    progressProvider = shimmerProgress,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ShimmerBox(
                        modifier = Modifier.width(46.dp).height(22.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        progressProvider = shimmerProgress,
                    )
                    ShimmerBox(
                        modifier = Modifier.width(64.dp).height(22.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        progressProvider = shimmerProgress,
                    )
                }
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
                progressProvider = shimmerProgress,
            )
            ShimmerBox(
                modifier = Modifier
                    .width(106.dp)
                    .height(48.dp),
                shape = MaterialTheme.shapes.extraLarge,
                progressProvider = shimmerProgress,
            )
            ShimmerBox(
                modifier = Modifier
                    .size(48.dp),
                shape = MaterialTheme.shapes.extraLarge,
                progressProvider = shimmerProgress,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ShimmerBox(
                modifier = Modifier.fillMaxWidth().height(13.dp),
                shape = MaterialTheme.shapes.extraSmall,
                progressProvider = shimmerProgress,
            )
            ShimmerBox(
                modifier = Modifier.fillMaxWidth(0.82f).height(13.dp),
                shape = MaterialTheme.shapes.extraSmall,
                progressProvider = shimmerProgress,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShimmerBox(
                modifier = Modifier.width(86.dp).height(34.dp),
                shape = MaterialTheme.shapes.extraLarge,
                progressProvider = shimmerProgress,
            )
            ShimmerBox(
                modifier = Modifier.width(96.dp).height(34.dp),
                shape = MaterialTheme.shapes.extraLarge,
                progressProvider = shimmerProgress,
            )
        }
        repeat(4) {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = MaterialTheme.shapes.medium,
                progressProvider = shimmerProgress,
            )
        }
    }
}

@Composable
fun EpisodesListSkeleton(
    modifier: Modifier = Modifier,
    itemCount: Int = 8,
) {
    val shimmerProgress = rememberShimmerProgress()
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
                    progressProvider = shimmerProgress,
                )
                Spacer(Modifier.width(12.dp))
                ShimmerBox(
                    modifier = Modifier
                        .weight(1f)
                        .height(16.dp),
                    shape = MaterialTheme.shapes.extraSmall,
                    progressProvider = shimmerProgress,
                )
                Spacer(Modifier.width(16.dp))
                ShimmerBox(
                    modifier = Modifier.size(24.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    progressProvider = shimmerProgress,
                )
            }
        }
    }
}

@Composable
fun BrowseDiscoverySkeleton(
    modifier: Modifier = Modifier,
) {
    val shimmerProgress = rememberShimmerProgress()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { contentDescription = "Chargement de la découverte" },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp),
            shape = MaterialTheme.shapes.large,
            progressProvider = shimmerProgress,
        )
        ShimmerBox(
            modifier = Modifier
                .width(170.dp)
                .height(16.dp),
            shape = MaterialTheme.shapes.extraSmall,
            progressProvider = shimmerProgress,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            repeat(3) {
                ShimmerBox(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(2f / 3f),
                    shape = MaterialTheme.shapes.small,
                    progressProvider = shimmerProgress,
                )
            }
        }
        ShimmerBox(
            modifier = Modifier
                .width(140.dp)
                .height(16.dp),
            shape = MaterialTheme.shapes.extraSmall,
            progressProvider = shimmerProgress,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            repeat(3) {
                ShimmerBox(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(2f / 3f),
                    shape = MaterialTheme.shapes.small,
                    progressProvider = shimmerProgress,
                )
            }
        }
    }
}

@Composable
fun DownloadsDashboardSkeleton(
    modifier: Modifier = Modifier,
) {
    val shimmerProgress = rememberShimmerProgress()
    Column(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 840.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { contentDescription = "Chargement des téléchargements" },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp),
            shape = MaterialTheme.shapes.large,
            progressProvider = shimmerProgress,
        )
        repeat(4) {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(78.dp),
                shape = MaterialTheme.shapes.medium,
                progressProvider = shimmerProgress,
            )
        }
    }
}

