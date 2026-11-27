package dot.jcversa.novay.ui.components

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.AnimeSource
import dot.jcversa.novay.data.ImageUrlSanitizer
import dot.jcversa.novay.data.Library
import dot.jcversa.novay.data.WatchHistory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

fun interface PosterResolver {
    suspend fun resolvePoster(anime: AnimeSearchResult): String?
}

val LocalPosterResolver = staticCompositionLocalOf<PosterResolver?> { null }

/**
 * Coordinateur single-flight de récupération et réparation des affiches :
 *  - déduplique les appels concurrents pour un même animé across écrans ;
 *  - évite de marteler le réseau en cas d'absence confirmée (cooldown 5 min) ;
 *  - persiste automatiquement les URLs réparées dans [Library] et [WatchHistory].
 */
object PosterRecoveryCoordinator {

    private const val NEGATIVE_TTL_MS = 5L * 60 * 1000
    private val resolvedCache = ConcurrentHashMap<String, String>()
    private val negativeTimestamps = ConcurrentHashMap<String, Long>()
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<String?>>()

    internal fun identityKey(animeUrl: String, title: String? = null): String {
        val clean = animeUrl.trim().trimEnd('/')
        if (clean.isNotEmpty()) {
            val idx = clean.indexOf("/catalogue/", ignoreCase = true)
            return if (idx >= 0) clean.substring(idx).lowercase() else clean
        }
        return title?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { "title:$it" } ?: ""
    }

    fun clearForTest() {
        resolvedCache.clear()
        negativeTimestamps.clear()
        inFlight.clear()
    }

    suspend fun resolveAndPersist(
        context: Context,
        source: AnimeSource,
        anime: AnimeSearchResult,
        clockMs: () -> Long = System::currentTimeMillis,
    ): String? {
        val key = identityKey(anime.url, anime.title)
        if (key.isEmpty()) return null
        val failingInput = ImageUrlSanitizer.sanitizePosterUrl(anime.posterUrl)

        val cached = resolvedCache[key]
        if (cached != null && cached != failingInput) {
            return cached
        }

        val negKey = "$key|${failingInput.orEmpty()}"
        negativeTimestamps[negKey]?.let { failedAt ->
            if (clockMs() - failedAt < NEGATIVE_TTL_MS) return null
            negativeTimestamps.remove(negKey)
        }

        val existingDeferred = inFlight[negKey]
        if (existingDeferred != null) {
            return runCatching { existingDeferred.await() }.getOrNull()
        }

        val created = CompletableDeferred<String?>()
        val race = inFlight.putIfAbsent(negKey, created)
        if (race != null) {
            return runCatching { race.await() }.getOrNull()
        }

        try {
            val resolved = withContext(Dispatchers.IO) {
                val fetched = runCatching { source.resolvePosterUrl(anime) }.getOrNull()
                val clean = ImageUrlSanitizer.sanitizePosterUrl(fetched)
                if (clean != null && clean != failingInput) {
                    resolvedCache[key] = clean
                    if (anime.url.isNotBlank()) {
                        Library.updatePoster(context, anime.url, clean)
                        WatchHistory.updatePoster(context, anime.url, clean)
                    }
                    clean
                } else {
                    negativeTimestamps[negKey] = clockMs()
                    null
                }
            }
            created.complete(resolved)
            return resolved
        } catch (_: Throwable) {
            created.complete(null)
            return null
        } finally {
            inFlight.remove(negKey, created)
        }
    }
}
