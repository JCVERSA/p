package dot.jcversa.novay.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import dot.jcversa.novay.performance.NovayPerformanceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Immutable
data class PosterPalette(
    val accent: Color,
    val atmosphericTint: Color,
    val softGlow: Color,
)

/**
 * Extracteur léger et mis en cache des couleurs dominantes d'une affiche d'animé
 * via le cache Coil existant (aucune dépendance Palette supplémentaire).
 * Lecture groupée via [Bitmap.getPixels] + recyclage immédiat du bitmap temporaire.
 */
object PosterPaletteExtractor {

    private val cache = LruCache<String, PosterPalette>(64)

    fun cached(posterUrl: String?): PosterPalette? {
        val clean = posterUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return cache.get(clean)
    }

    fun cacheSize(): Int = cache.size()

    fun trimToSize(maxSize: Int) {
        if (maxSize <= 0) {
            cache.evictAll()
        } else {
            cache.trimToSize(maxSize)
        }
    }

    suspend fun extract(context: Context, posterUrl: String?): PosterPalette? {
        val clean = posterUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        cache.get(clean)?.let { return it }

        return withContext(Dispatchers.IO) {
            runCatching {
                val loader = Coil.imageLoader(context.applicationContext)
                val req = ImageRequest.Builder(context.applicationContext)
                    .data(clean)
                    .size(48, 72)
                    .allowHardware(false)
                    .allowRgb565(true)
                    .build()
                val result = loader.execute(req)
                val drawable = (result as? SuccessResult)?.drawable ?: return@withContext null
                val (bitmap, createdScratch) = drawableToSmallBitmap(drawable) ?: return@withContext null
                val palette = try {
                    extractFromBitmap(bitmap)
                } finally {
                    if (createdScratch && !bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                }
                val maxEntries = NovayPerformanceState.currentProfile.paletteCacheEntries
                if (cache.size() >= maxEntries) {
                    cache.trimToSize((maxEntries - 1).coerceAtLeast(1))
                }
                cache.put(clean, palette)
                palette
            }.getOrNull()
        }
    }

    internal fun extractFromBitmap(bitmap: Bitmap): PosterPalette {
        val w = bitmap.width.coerceAtLeast(1)
        val h = bitmap.height.coerceAtLeast(1)
        val stepX = (w / 12).coerceAtLeast(1)
        val stepY = (h / 16).coerceAtLeast(1)

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        var bestScore = -1f
        var bestColor = AndroidColor.rgb(251, 55, 88)
        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var count = 0
        val hsv = FloatArray(3)

        var y = 0
        while (y < h) {
            val rowOffset = y * w
            var x = 0
            while (x < w) {
                val px = pixels[rowOffset + x]
                val a = AndroidColor.alpha(px)
                if (a >= 128) {
                    val r = AndroidColor.red(px)
                    val g = AndroidColor.green(px)
                    val b = AndroidColor.blue(px)
                    rSum += r
                    gSum += g
                    bSum += b
                    count++

                    AndroidColor.RGBToHSV(r, g, b, hsv)
                    val sat = hsv[1]
                    val value = hsv[2]
                    if (value in 0.16f..0.94f && sat >= 0.18f) {
                        val centerValWeight = 1f - kotlin.math.abs(value - 0.56f)
                        val score = (sat * 2.2f) + centerValWeight
                        if (score > bestScore) {
                            bestScore = score
                            bestColor = px
                        }
                    }
                }
                x += stepX
            }
            y += stepY
        }

        val fallbackMean = if (count > 0) {
            AndroidColor.rgb(
                (rSum / count).toInt().coerceIn(0, 255),
                (gSum / count).toInt().coerceIn(0, 255),
                (bSum / count).toInt().coerceIn(0, 255),
            )
        } else {
            AndroidColor.rgb(251, 55, 88)
        }

        val rawAccent = Color(if (bestScore > 0f) bestColor else fallbackMean)
        val accent = lerp(rawAccent, Color(0xFFF4F4F6), 0.14f)
        val atmosphericTint = lerp(Color(0xFF09090D), rawAccent, 0.30f)
        val softGlow = rawAccent.copy(alpha = 0.24f)
        return PosterPalette(
            accent = accent,
            atmosphericTint = atmosphericTint,
            softGlow = softGlow,
        )
    }

    private fun drawableToSmallBitmap(drawable: Drawable): Pair<Bitmap, Boolean>? {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap to false
        }
        val w = drawable.intrinsicWidth.coerceIn(16, 48)
        val h = drawable.intrinsicHeight.coerceIn(24, 72)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bmp to true
    }
}

@Composable
fun rememberPosterPalette(
    posterUrl: String?,
    syncGlobalDynamicTheme: Boolean = false,
): PosterPalette? {
    val context = LocalContext.current
    var palette by remember(posterUrl) { mutableStateOf(PosterPaletteExtractor.cached(posterUrl)) }

    LaunchedEffect(posterUrl, syncGlobalDynamicTheme) {
        val extracted = PosterPaletteExtractor.extract(context, posterUrl)
        palette = extracted
        if (syncGlobalDynamicTheme && extracted != null) {
            NovayThemeState.updateDynamicAccent(extracted.accent)
        }
    }
    return palette
}
