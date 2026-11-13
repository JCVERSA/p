package dot.jcversa.novay.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dot.jcversa.novay.data.AnimeEpisode
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.AnimeSeason
import dot.jcversa.novay.data.ResolvedStream
import dot.jcversa.novay.data.sama.SamaSource
import dot.jcversa.novay.ui.components.SlateMotionProvider
import dot.jcversa.novay.ui.screens.DownloadsScreen
import dot.jcversa.novay.ui.screens.EpisodesScreen
import dot.jcversa.novay.ui.screens.PlayerScreen
import dot.jcversa.novay.ui.screens.SearchScreen
import dot.jcversa.novay.ui.screens.ShowScreen
import dot.jcversa.novay.ui.theme.DarkBackground

/** Écrans NoVaY, avec leur payload de navigation. */
sealed class Destination {
    data object Search : Destination()
    data object Downloads : Destination()
    data class Show(val anime: AnimeSearchResult) : Destination()
    data class Episodes(val animeTitle: String, val season: AnimeSeason) : Destination()
    data class Player(
        val season: AnimeSeason,
        val episode: AnimeEpisode,
        /** Flux déjà résolu (lecture hors-ligne) : null = résoudre en ligne. */
        val initialStream: ResolvedStream? = null
    ) : Destination()
}

/** Profondeur d'un écran dans le flux (sens de la transition animée). */
private fun Destination.rank(): Int = when (this) {
    Destination.Search -> 0
    Destination.Downloads -> 1
    is Destination.Show -> 1
    is Destination.Episodes -> 2
    is Destination.Player -> 3
}

/**
 * Coque de navigation NoVaY : flux linéaire
 * Recherche → Fiche/Saisons → Épisodes → Lecteur (+ écran Téléchargements),
 * animé suivant la direction (slide horizontal + fondu — canon SwiftSlate).
 */
@Composable
fun NoVaYApp() {
    val source = remember { SamaSource() }
    val stack = remember { mutableStateListOf<Destination>(Destination.Search) }

    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }

    val open: (Destination) -> Unit = { stack.add(it) }
    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

    SlateMotionProvider {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = DarkBackground
        ) { innerPadding ->
            AnimatedContent(
                targetState = stack.last(),
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    if (targetState.rank() > initialState.rank()) {
                        (slideInHorizontally(tween(260)) { it / 4 } + fadeIn(tween(220)))
                            .togetherWith(
                                slideOutHorizontally(tween(200)) { -it / 4 } + fadeOut(tween(180))
                            )
                    } else {
                        (slideInHorizontally(tween(260)) { -it / 4 } + fadeIn(tween(220)))
                            .togetherWith(
                                slideOutHorizontally(tween(200)) { it / 4 } + fadeOut(tween(180))
                            )
                    }
                },
                label = "novay_screen_stack"
            ) { screen ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (screen) {
                        Destination.Search -> SearchScreen(
                            source = source,
                            onOpenShow = { open(Destination.Show(it)) },
                            onOpenDownloads = { open(Destination.Downloads) }
                        )
                        Destination.Downloads -> DownloadsScreen(
                            onBack = back,
                            onPlay = { season, episode, stream ->
                                open(Destination.Player(season, episode, stream))
                            }
                        )
                        is Destination.Show -> ShowScreen(
                            source = source,
                            anime = screen.anime,
                            onBack = back,
                            onOpenEpisodes = { season ->
                                open(Destination.Episodes(screen.anime.title, season))
                            }
                        )
                        is Destination.Episodes -> EpisodesScreen(
                            source = source,
                            animeTitle = screen.animeTitle,
                            season = screen.season,
                            onBack = back,
                            onOpenPlayer = { episode, offlineStream ->
                                open(Destination.Player(screen.season, episode, offlineStream))
                            }
                        )
                        is Destination.Player -> PlayerScreen(
                            source = source,
                            season = screen.season,
                            episode = screen.episode,
                            initialStream = screen.initialStream,
                            onBack = back
                        )
                    }
                }
            }
        }
    }
}
