package dot.jcversa.novay.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

/**
 * Icônes morphing inspirées d'Aniyomi (dessinées en GPU Canvas avec
 * interpolation fluide contour <-> plein, rotation d'aiguille et ressort
 * sub-300ms). Respectent intégralement reduceMotion.
 */
enum class MorphTabKind {
    LIBRARY,
    UPDATES,
    HISTORY,
    BROWSE,
    MORE,
}

@Composable
fun NovayTabMorphIcon(
    kind: MorphTabKind,
    selected: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    onTint: Color = Color.Black.copy(alpha = 0.85f),
) {
    val motion = LocalSlateMotion.current
    val progress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = motion.morphFloatSpec(),
        label = "tab_morph_${kind.name}",
    )

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val strokeWidth = lerp(1.9.dp.toPx(), 1.5.dp.toPx(), progress)
        val fillAlpha = (progress * 0.96f).coerceIn(0f, 1f)

        when (kind) {
            MorphTabKind.LIBRARY -> {
                // Style Aniyomi anim_library : 2 volumes droits + 1 volume incliné qui s'ouvre et se remplit.
                val r = 2.dp.toPx()
                val bookW = w * 0.19f
                val h1 = h * 0.68f
                val h2 = h * 0.68f
                val yBase = h * 0.84f

                // Livre gauche
                drawBook(
                    left = w * 0.14f,
                    bottom = yBase,
                    width = bookW,
                    height = h1,
                    corner = r,
                    tiltDeg = 0f,
                    fillAlpha = fillAlpha,
                    tint = tint,
                    strokeWidth = strokeWidth,
                )
                // Livre centre
                drawBook(
                    left = w * 0.39f,
                    bottom = yBase,
                    width = bookW,
                    height = h2,
                    corner = r,
                    tiltDeg = 0f,
                    fillAlpha = fillAlpha,
                    tint = tint,
                    strokeWidth = strokeWidth,
                )
                // Livre droit incliné (s'incline et s'anime à l'activation comme Aniyomi)
                drawBook(
                    left = w * 0.64f + lerp(0f, 1.2.dp.toPx(), progress),
                    bottom = yBase,
                    width = bookW,
                    height = lerp(h * 0.64f, h * 0.70f, progress),
                    corner = r,
                    tiltDeg = lerp(0f, -14f, progress),
                    fillAlpha = fillAlpha,
                    tint = tint,
                    strokeWidth = strokeWidth,
                )
            }

            MorphTabKind.UPDATES -> {
                // Style Aniyomi anim_updates : étoile / sceau de nouveautés qui tourne + cœur central rempli.
                val center = Offset(w / 2f, h / 2f)
                val outerR = w * lerp(0.36f, 0.39f, progress)
                val innerR = w * lerp(0.28f, 0.31f, progress)
                val points = 8
                val path = Path().apply {
                    for (i in 0 until points * 2) {
                        val angle = (i * Math.PI / points) - Math.PI / 2.0
                        val radius = if (i % 2 == 0) outerR else innerR
                        val px = center.x + (radius * kotlin.math.cos(angle)).toFloat()
                        val py = center.y + (radius * kotlin.math.sin(angle)).toFloat()
                        if (i == 0) moveTo(px, py) else lineTo(px, py)
                    }
                    close()
                }
                rotate(degrees = lerp(0f, 22.5f, progress), pivot = center) {
                    if (fillAlpha > 0.02f) {
                        drawPath(path = path, color = tint.copy(alpha = fillAlpha))
                    }
                    drawPath(
                        path = path,
                        color = tint,
                        style = Stroke(width = strokeWidth, join = StrokeJoin.Round),
                    )
                }
                // Point d'exclamation / noyau de nouveauté contrasté
                val coreColor = lerp(tint, onTint, progress)
                drawLine(
                    color = coreColor,
                    start = Offset(center.x, center.y - h * 0.14f),
                    end = Offset(center.x, center.y + h * 0.03f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawCircle(
                    color = coreColor,
                    radius = 1.3.dp.toPx(),
                    center = Offset(center.x, center.y + h * 0.14f),
                )
            }

            MorphTabKind.HISTORY -> {
                // Style Aniyomi anim_history : cadran qui s'épaissit + aiguille des minutes qui remonte le temps.
                val center = Offset(w / 2f, h / 2f)
                val radius = w * 0.37f
                if (fillAlpha > 0.02f) {
                    drawCircle(
                        color = tint.copy(alpha = fillAlpha * 0.20f),
                        radius = radius,
                        center = center,
                    )
                }
                drawArc(
                    color = tint,
                    startAngle = lerp(-240f, -310f, progress),
                    sweepAngle = lerp(295f, 325f, progress),
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = lerp(1.9.dp.toPx(), 2.4.dp.toPx(), progress), cap = StrokeCap.Round),
                )
                // Flèche de retour sur l'arc
                val arrowTip = Offset(center.x - radius * 0.82f, center.y - radius * 0.42f)
                drawLine(
                    color = tint,
                    start = arrowTip,
                    end = Offset(arrowTip.x + 3.5.dp.toPx(), arrowTip.y - 2.dp.toPx()),
                    strokeWidth = 1.8.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = tint,
                    start = arrowTip,
                    end = Offset(arrowTip.x + 0.5.dp.toPx(), arrowTip.y + 4.dp.toPx()),
                    strokeWidth = 1.8.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                // Aiguille rotative
                rotate(degrees = lerp(0f, -360f, progress), pivot = center) {
                    drawLine(
                        color = tint,
                        start = center,
                        end = Offset(center.x, center.y - radius * 0.52f),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                drawLine(
                    color = tint,
                    start = center,
                    end = Offset(center.x + radius * 0.38f, center.y + radius * 0.24f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            MorphTabKind.BROWSE -> {
                // Style Aniyomi anim_browse : boussole dont l'aiguille losange pivote et se remplit.
                val center = Offset(w / 2f, h / 2f)
                val radius = w * 0.38f
                if (fillAlpha > 0.02f) {
                    drawCircle(
                        color = tint.copy(alpha = fillAlpha),
                        radius = radius,
                        center = center,
                    )
                }
                drawCircle(
                    color = tint,
                    radius = radius,
                    center = center,
                    style = Stroke(width = strokeWidth),
                )
                val needleColor = lerp(tint, onTint, progress)
                val needleLen = radius * 0.62f
                val needleW = radius * 0.26f
                val needle = Path().apply {
                    moveTo(center.x, center.y - needleLen)
                    lineTo(center.x + needleW, center.y)
                    lineTo(center.x, center.y + needleLen)
                    lineTo(center.x - needleW, center.y)
                    close()
                }
                rotate(degrees = lerp(45f, 25f, progress), pivot = center) {
                    if (progress > 0.05f) {
                        drawPath(needle, color = needleColor)
                    } else {
                        drawPath(needle, color = tint, style = Stroke(width = 1.5.dp.toPx(), join = StrokeJoin.Round))
                    }
                }
            }

            MorphTabKind.MORE -> {
                // Style Aniyomi anim_more : trois pastilles horizontales qui s'étirent en grille/capsule.
                val cy = h / 2f
                val xs = listOf(w * 0.24f, w * 0.50f, w * 0.76f)
                val dotRadius = lerp(2.2.dp.toPx(), 2.8.dp.toPx(), progress)
                val pillHeight = dotRadius * 2f
                xs.forEachIndexed { idx, cx ->
                    val extraH = if (idx == 1) lerp(0f, 5.dp.toPx(), progress) else lerp(0f, 2.dp.toPx(), progress)
                    val rectH = pillHeight + extraH
                    val rectW = dotRadius * 2f
                    if (fillAlpha > 0.05f) {
                        drawRoundRect(
                            color = tint,
                            topLeft = Offset(cx - rectW / 2f, cy - rectH / 2f),
                            size = Size(rectW, rectH),
                            cornerRadius = CornerRadius(dotRadius, dotRadius),
                        )
                    } else {
                        drawRoundRect(
                            color = tint,
                            topLeft = Offset(cx - rectW / 2f, cy - rectH / 2f),
                            size = Size(rectW, rectH),
                            cornerRadius = CornerRadius(dotRadius, dotRadius),
                            style = Stroke(width = 1.7.dp.toPx()),
                        )
                    }
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBook(
    left: Float,
    bottom: Float,
    width: Float,
    height: Float,
    corner: Float,
    tiltDeg: Float,
    fillAlpha: Float,
    tint: Color,
    strokeWidth: Float,
) {
    val top = bottom - height
    rotate(degrees = tiltDeg, pivot = Offset(left + width / 2f, bottom)) {
        if (fillAlpha > 0.02f) {
            drawRoundRect(
                color = tint.copy(alpha = fillAlpha),
                topLeft = Offset(left, top),
                size = Size(width, height),
                cornerRadius = CornerRadius(corner, corner),
            )
        }
        drawRoundRect(
            color = tint,
            topLeft = Offset(left, top),
            size = Size(width, height),
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = strokeWidth),
        )
    }
}

/**
 * Icône morphing Play <-> Pause GPU (interpolation géométrique entre le
 * triangle Play et deux barres parallèles Pause).
 */
@Composable
fun NovayPlayPauseMorphIcon(
    isPlaying: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    val motion = LocalSlateMotion.current
    val t by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = motion.morphFloatSpec(),
        label = "play_pause_morph",
    )

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Moitié gauche : trapèze -> barre gauche de Pause
        val leftTopY = lerp(h * 0.20f, h * 0.22f, t)
        val leftBottomY = lerp(h * 0.80f, h * 0.78f, t)
        val leftX = lerp(w * 0.28f, w * 0.24f, t)
        val midX = lerp(w * 0.56f, w * 0.42f, t)
        val midTopY = lerp(h * 0.35f, h * 0.22f, t)
        val midBottomY = lerp(h * 0.65f, h * 0.78f, t)

        val leftHalf = Path().apply {
            moveTo(leftX, leftTopY)
            lineTo(midX, midTopY)
            lineTo(midX, midBottomY)
            lineTo(leftX, leftBottomY)
            close()
        }

        // Moitié droite : pointe du triangle -> barre droite de Pause
        val rightStartX = lerp(w * 0.54f, w * 0.58f, t)
        val rightEndX = lerp(w * 0.82f, w * 0.76f, t)
        val rightStartTopY = lerp(h * 0.34f, h * 0.22f, t)
        val rightStartBottomY = lerp(h * 0.66f, h * 0.78f, t)
        val tipTopY = lerp(h * 0.50f, h * 0.22f, t)
        val tipBottomY = lerp(h * 0.50f, h * 0.78f, t)

        val rightHalf = Path().apply {
            moveTo(rightStartX, rightStartTopY)
            lineTo(rightEndX, tipTopY)
            lineTo(rightEndX, tipBottomY)
            lineTo(rightStartX, rightStartBottomY)
            close()
        }

        drawPath(leftHalf, color = tint)
        drawPath(rightHalf, color = tint)
    }
}

/**
 * Cœur morphing (contour <-> rempli) avec léger rebond tactile à l'ajout.
 */
@Composable
fun NovayFavoriteMorphIcon(
    inLibrary: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
) {
    val motion = LocalSlateMotion.current
    val fillProgress by animateFloatAsState(
        targetValue = if (inLibrary) 1f else 0f,
        animationSpec = motion.morphFloatSpec(),
        label = "favorite_fill",
    )

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val s = lerp(0.92f, 1.04f, fillProgress)
        val cx = w / 2f
        val cy = h / 2f
        val heart = Path().apply {
            val scaleW = w * s
            val scaleH = h * s
            val ox = cx - scaleW / 2f
            val oy = cy - scaleH / 2f
            moveTo(ox + scaleW * 0.5f, oy + scaleH * 0.84f)
            cubicTo(
                ox + scaleW * 0.12f, oy + scaleH * 0.60f,
                ox + scaleW * 0.08f, oy + scaleH * 0.28f,
                ox + scaleW * 0.30f, oy + scaleH * 0.18f,
            )
            cubicTo(
                ox + scaleW * 0.42f, oy + scaleH * 0.13f,
                ox + scaleW * 0.48f, oy + scaleH * 0.22f,
                ox + scaleW * 0.50f, oy + scaleH * 0.27f,
            )
            cubicTo(
                ox + scaleW * 0.52f, oy + scaleH * 0.22f,
                ox + scaleW * 0.58f, oy + scaleH * 0.13f,
                ox + scaleW * 0.70f, oy + scaleH * 0.18f,
            )
            cubicTo(
                ox + scaleW * 0.92f, oy + scaleH * 0.28f,
                ox + scaleW * 0.88f, oy + scaleH * 0.60f,
                ox + scaleW * 0.50f, oy + scaleH * 0.84f,
            )
            close()
        }
        if (fillProgress > 0.02f) {
            drawPath(heart, color = tint.copy(alpha = fillProgress))
        }
        drawPath(
            heart,
            color = tint,
            style = Stroke(width = 1.8.dp.toPx(), join = StrokeJoin.Round),
        )
    }
}
