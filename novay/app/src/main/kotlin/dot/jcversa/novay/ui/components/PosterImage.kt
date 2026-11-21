package dot.jcversa.novay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.ImageUrlSanitizer
import kotlinx.coroutines.delay

/**
 * Affiche d'un anime : utilise [AsyncImage] (sans sous-composition coûteuse
 * en LazyGrid/LazyColumn) avec normalisation d'URL, clés de cache Coil
 * explicites, réessai automatique sur échec transitoire et repli vers la
 * fiche anime si [animeUrl] + [LocalPosterResolver] sont disponibles.
 */
@Composable
fun PosterImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    spinnerSize: Dp = 14.dp,
    animeUrl: String? = null,
    animeTitle: String? = null,
    onPosterResolved: ((String) -> Unit)? = null,
) {
    val sanitizedInput = remember(url) { ImageUrlSanitizer.sanitizePosterUrl(url) }
    val posterResolver = LocalPosterResolver.current

    var recoveredUrl by remember(sanitizedInput, animeUrl) { mutableStateOf<String?>(null) }
    var retryAttempt by remember(sanitizedInput, animeUrl, recoveredUrl) { mutableIntStateOf(0) }
    var isRecovering by remember(sanitizedInput, animeUrl) { mutableStateOf(false) }
    var exhaustedFallback by remember(sanitizedInput, animeUrl) { mutableStateOf(false) }

    val activeUrl = recoveredUrl ?: sanitizedInput

    if (activeUrl == null) {
        val canResolve = !animeUrl.isNullOrBlank() && posterResolver != null && !exhaustedFallback
        if (canResolve) {
            LaunchedEffect(animeUrl, animeTitle, posterResolver) {
                isRecovering = true
                val found = posterResolver?.resolvePoster(
                    AnimeSearchResult(
                        title = animeTitle.orEmpty(),
                        url = animeUrl.orEmpty(),
                        posterUrl = null,
                    )
                )
                isRecovering = false
                if (found != null) {
                    recoveredUrl = found
                    onPosterResolved?.invoke(found)
                } else {
                    exhaustedFallback = true
                }
            }
            ShimmerBox(
                modifier = modifier,
                shape = MaterialTheme.shapes.extraSmall,
            )
        } else {
            PosterErrorPlaceholder(
                modifier = modifier,
                contentDescription = contentDescription,
            )
        }
        return
    }

    val context = LocalContext.current
    val reduceMotion = LocalSlateMotion.current.reduceMotion
    val request = remember(context, activeUrl, reduceMotion, retryAttempt) {
        ImageRequest.Builder(context)
            .data(activeUrl)
            .memoryCacheKey(activeUrl)
            .diskCacheKey(activeUrl)
            .crossfade(if (reduceMotion) 0 else 160)
            .apply {
                if (retryAttempt > 0) {
                    memoryCachePolicy(CachePolicy.WRITE_ONLY)
                }
            }
            .build()
    }

    var state by remember(activeUrl, retryAttempt) {
        mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Loading(null))
    }

    if (state is AsyncImagePainter.State.Error) {
        val canRetry = retryAttempt < 1
        val canResolveFallback = !animeUrl.isNullOrBlank() && posterResolver != null && !exhaustedFallback
        if (canRetry || canResolveFallback) {
            LaunchedEffect(activeUrl, retryAttempt, exhaustedFallback, animeUrl, posterResolver) {
                isRecovering = true
                if (canRetry) {
                    delay(350L)
                    isRecovering = false
                    retryAttempt++
                } else if (canResolveFallback) {
                    exhaustedFallback = true
                    val found = posterResolver?.resolvePoster(
                        AnimeSearchResult(
                            title = animeTitle.orEmpty(),
                            url = animeUrl.orEmpty(),
                            posterUrl = activeUrl,
                        )
                    )
                    isRecovering = false
                    if (found != null && found != activeUrl) {
                        recoveredUrl = found
                        onPosterResolved?.invoke(found)
                    }
                } else {
                    isRecovering = false
                }
            }
        }
    }

    Box(modifier = modifier) {
        key(activeUrl, retryAttempt) {
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                onState = { state = it },
                modifier = Modifier.fillMaxSize(),
            )
        }
        val showShimmer = state is AsyncImagePainter.State.Loading ||
            (state is AsyncImagePainter.State.Error && (isRecovering || retryAttempt < 1))
        when {
            showShimmer -> {
                ShimmerBox(
                    modifier = Modifier.fillMaxSize(),
                    shape = MaterialTheme.shapes.extraSmall,
                )
            }
            state is AsyncImagePainter.State.Error -> {
                PosterErrorPlaceholder(
                    modifier = Modifier.fillMaxSize(),
                    contentDescription = contentDescription,
                )
            }
        }
    }
}

@Composable
private fun PosterErrorPlaceholder(
    modifier: Modifier,
    contentDescription: String?,
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = NovayIcons.ClapperFilm,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
