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
import dot.jcversa.novay.data.WatchHistoryEntry
import dot.jcversa.novay.ui.components.SlateMotionProvider
import dot.jcversa.novay.ui.screens.DownloadsScreen
import dot.jcversa.novay.ui.screens.HomeScreen
import dot.jcversa.novay.ui.screens.EpisodesScreen
import dot.jcversa.novay.ui.screens.PlayerScreen
import dot.jcversa.novay.ui.screens.SearchScreen
import dot.jcversa.novay.ui.screens.ShowScreen
import dot.jcversa.novay.ui.theme.DarkBackground

/** Écrans NoVaY, avec leur payload de navigation. */
sealed class Destination {
    data object Home : Destination()
    data object Search : Destination()
    data object Downloads : Destination()
    data class Show(val anime: AnimeSearchResult) : Destination()
    data class Episodes(
        val animeTitle: String,
        val posterUrl: String?,
        val season: AnimeSeason
    ) : Destination()

    data class Player(
        val season: AnimeSeason,
        val episode: AnimeEpisode,
        val animeTitle: String,
        val posterUrl: String?,
        /** Flux déjà résolu (lecture hors-ligne) : null = résoudre en ligne. */
        val initialStream: ResolvedStream? = null
    ) : Destination()
}

/** Profondeur d'un écran dans le flux (sens de la transition animée). */
private fun Destination.rank(): Int = when (this) {
    Destination.Home -> 0
    Destination.Search -> 1
    Destination.Downloads -> 1
    is Destination.Show -> 2
    is Destination.Episodes -> 3
    is Destination.Player -> 4
}

/**
 * Coque de navigation NoVaY : flux linéaire
 * Accueil → Recherche → Fiche/Saisons → Épisodes → Lecteur
 * (+ écran Téléchargements), animé suivant la direction (slide horizontal + fondu — canon SwiftSlate).
 */
@Composable
fun NoVaYApp() {
    val source = remember { SamaSource() }
    val stack = remember { mutableStateListOf<Destination>(Destination.Home) }

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
                        Destination.Home -> HomeScreen(
                            onOpenSearch = { open(Destination.Search) },
                            onOpenDownloads = { open(Destination.Downloads) },
                            onResume = { entry ->
                                open(
                                    Destination.Player(
                                        season = AnimeSeason(
                                            name = entry.seasonName,
                                            url = entry.seasonUrl
                                        ),
                                        episode = AnimeEpisode(
                                            number = entry.episodeNumber,
                                            url = entry.seasonUrl,
                                            label = entry.episodeLabel,
                                            lienIds = entry.lienIds,
                                            jeton = entry.jeton
                                        ),
                                        animeTitle = entry.animeTitle,
                                        posterUrl = entry.posterUrl,
                                        initialStream = null
                                    )
                                )
                            }
                        )
                        Destination.Search -> SearchScreen(
                            source = source,
                            onOpenShow = { open(Destination.Show(it)) },
                            onOpenDownloads = { open(Destination.Downloads) }
                        )
                        Destination.Downloads -> DownloadsScreen(
                            onBack = back,
                            onPlay = { season, episode, stream ->
                                open(
                                    Destination.Player(
                                        season = season,
                                        episode = episode,
                                        animeTitle = episode.label,
                                        posterUrl = null,
                                        initialStream = stream
                                    )
                                )
                            }
                        )
                        is Destination.Show -> ShowScreen(
                            source = source,
                            anime = screen.anime,
                            onBack = back,
                            onOpenEpisodes = { season ->
                                open(
                                    Destination.Episodes(
                                        animeTitle = screen.anime.title,
                                        posterUrl = screen.anime.posterUrl,
                                        season = season
                                    )
                                )
                            }
                        )
                        is Destination.Episodes -> EpisodesScreen(
                            source = source,
                            animeTitle = screen.animeTitle,
                            season = screen.season,
                            onBack = back,
                            onOpenPlayer = { episode, offlineStream ->
                                open(
                                    Destination.Player(
                                        season = screen.season,
                                        episode = episode,
                                        animeTitle = screen.animeTitle,
                                        posterUrl = screen.posterUrl,
                                        initialStream = offlineStream
                                    )
                                )
                            }
                        )
                        is Destination.Player -> PlayerScreen(
                            source = source,
                            season = screen.season,
                            episode = screen.episode,
                            animeTitle = screen.animeTitle,
                            posterUrl = screen.posterUrl,
                            initialStream = screen.initialStream,
                            onBack = back
                        )
                    }
                }
            }
        }
    }
}
