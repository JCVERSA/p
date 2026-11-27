package dot.jcversa.novay.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * Portées de transition partagée (SharedTransitionLayout) exposées aux
 * cartes de grille / historique / spotlight et à l'affiche de ShowScreen.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

val LocalAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

fun sharedPosterKey(animeUrl: String): String =
    "poster_${animeUrl.trimEnd('/')}"

fun sharedTitleKey(animeUrl: String): String =
    "title_${animeUrl.trimEnd('/')}"

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.novaySharedPoster(
    animeUrl: String,
    enabled: Boolean = true,
): Modifier {
    if (!enabled || animeUrl.isBlank()) return this
    val motion = LocalSlateMotion.current
    if (motion.reduceMotion) return this
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val animScope = LocalAnimatedVisibilityScope.current ?: return this
    return with(sharedScope) {
        this@novaySharedPoster.sharedElement(
            rememberSharedContentState(key = sharedPosterKey(animeUrl)),
            animatedVisibilityScope = animScope,
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.novaySharedTitle(
    animeUrl: String,
    enabled: Boolean = true,
): Modifier {
    if (!enabled || animeUrl.isBlank()) return this
    val motion = LocalSlateMotion.current
    if (motion.reduceMotion) return this
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val animScope = LocalAnimatedVisibilityScope.current ?: return this
    return with(sharedScope) {
        this@novaySharedTitle.sharedBounds(
            rememberSharedContentState(key = sharedTitleKey(animeUrl)),
            animatedVisibilityScope = animScope,
        )
    }
}
