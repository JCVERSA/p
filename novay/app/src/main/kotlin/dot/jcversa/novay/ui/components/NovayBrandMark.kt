package dot.jcversa.novay.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dot.jcversa.novay.ui.theme.NovayTheme

private val BrandNoirBase = Color(0xFF09090D)
private val BrandCrimsonDeep = Color(0xFFBE123C)
private val BrandCrimsonPillar = Color(0xFFE11D48)
private val BrandCrimsonRibbon = Color(0xFFFB3758)
private val BrandSakuraGlint = Color(0xFFFDA4AF)

/**
 * Sceau japonais modernisé NoVaY : reprend l'emblème "N" crimson avec
 * biseaux katana, fentes optiques nettes et double arc de sceau hanko /
 * diaphragme cinéma. Conserve sa lisibilité de 16dp à 108dp.
 */
@Composable
fun NovayBrandSeal(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    monochromeTint: Color? = null,
    showNoirTile: Boolean = true,
    showHankoRing: Boolean = true,
    accessibleLabel: String? = "NoVaY",
) {
    val leftPillar = remember {
        Path().apply {
            moveTo(34f, 34f)
            lineTo(42f, 34f)
            lineTo(42f, 69.5f)
            lineTo(34f, 74f)
            close()
        }
    }
    val rightPillar = remember {
        Path().apply {
            moveTo(66f, 38.5f)
            lineTo(74f, 34f)
            lineTo(74f, 74f)
            lineTo(66f, 74f)
            close()
        }
    }
    val diagonalRibbon = remember {
        Path().apply {
            moveTo(45f, 34f)
            lineTo(52f, 34f)
            lineTo(63f, 59.5f)
            lineTo(63f, 74f)
            lineTo(56f, 74f)
            lineTo(45f, 48.5f)
            close()
        }
    }
    val glint = remember {
        Path().apply {
            moveTo(34f, 34f)
            lineTo(42f, 34f)
            lineTo(42f, 35.8f)
            lineTo(34f, 35.8f)
            close()
            moveTo(66f, 38.5f)
            lineTo(74f, 34f)
            lineTo(74f, 36.1f)
            lineTo(66f, 40.6f)
            close()
        }
    }

    val ringColor = monochromeTint?.copy(alpha = 0.72f) ?: BrandCrimsonDeep
    val pillarColor = monochromeTint ?: BrandCrimsonPillar
    val ribbonColor = monochromeTint ?: BrandCrimsonRibbon
    val glintColor = monochromeTint?.copy(alpha = 0.55f) ?: BrandSakuraGlint

    val containerShape = RoundedCornerShape(size * 0.28f)
    val baseModifier = modifier
        .size(size)
        .let { m ->
            if (accessibleLabel != null) {
                m.semantics { contentDescription = accessibleLabel }
            } else {
                m
            }
        }
        .let { m ->
            if (showNoirTile) {
                m.clip(containerShape)
                    .background(BrandNoirBase)
                    .border(0.75.dp, BrandCrimsonDeep.copy(alpha = 0.45f), containerShape)
            } else {
                m
            }
        }

    Canvas(modifier = baseModifier) {
        // Mise à l'échelle optique sur la zone active 72x72 centrée (18..90 du repère 108)
        val canvasSize = this.size.minDimension
        val scaleFactor = canvasSize / 72f
        scale(scale = scaleFactor, pivot = Offset.Zero) {
            val ox = -18f
            val oy = -18f

            if (showHankoRing) {
                val strokeW = 2.8f
                drawArc(
                    color = ringColor,
                    startAngle = -68f,
                    sweepAngle = 104f,
                    useCenter = false,
                    topLeft = Offset(21.6f + ox, 21.6f + oy),
                    size = Size(64.8f, 64.8f),
                    style = Stroke(width = strokeW, cap = StrokeCap.Butt),
                )
                drawArc(
                    color = ringColor,
                    startAngle = 112f,
                    sweepAngle = 104f,
                    useCenter = false,
                    topLeft = Offset(21.6f + ox, 21.6f + oy),
                    size = Size(64.8f, 64.8f),
                    style = Stroke(width = strokeW, cap = StrokeCap.Butt),
                )
            }

            // Décalage vers la zone centrale 72x72
            translate(left = ox, top = oy) {
                drawPath(leftPillar, color = pillarColor)
                drawPath(rightPillar, color = pillarColor)
                drawPath(diagonalRibbon, color = ribbonColor)
                drawPath(glint, color = glintColor)
            }
        }
    }
}

/**
 * En-tête éditorial de marque NoVaY pour la section « À propos » :
 * associe le sceau japonais Midnight Noir & Crimson, le titre typographique
 * et un sous-titre discret inspiré des affiches cinéma japonaises.
 */
@Composable
fun NovayBrandEditorialHeader(
    modifier: Modifier = Modifier,
) {
    val overlays = NovayTheme.overlays
    val primary = MaterialTheme.colorScheme.primary
    val shape = MaterialTheme.shapes.large

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        primary.copy(alpha = 0.14f),
                        MaterialTheme.colorScheme.surfaceContainer,
                        MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ),
            )
            .border(0.5.dp, overlays.glassBorder, shape)
            .padding(16.dp)
            .testTag("about_brand_header"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier.size(64.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                primary.copy(alpha = 0.30f),
                                Color.Transparent,
                            ),
                        ),
                    )
                }
                NovayBrandSeal(
                    size = 56.dp,
                    showNoirTile = true,
                    showHankoRing = true,
                    accessibleLabel = "Logo NoVaY",
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "NoVaY",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.4.sp,
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = "ノヴァイ",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = overlays.crimsonOnContainer,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(dot.jcversa.novay.R.string.brand_version),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.height(1.dp))
                Text(
                    text = androidx.compose.ui.res.stringResource(dot.jcversa.novay.R.string.brand_tagline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
