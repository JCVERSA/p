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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dot.jcversa.novay.data.AnimeEpisode
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.AnimeSeason
import dot.jcversa.novay.data.ResolvedStream
import dot.jcversa.novay.data.WatchHistoryEntry
import dot.jcversa.novay.data.sama.SamaSource
import dot.jcversa.novay.ui.components.SlateMotionProvider
import dot.jcversa.novay.ui.screens.DownloadsScreen
import dot.jcversa.novay.ui.screens.EpisodesScreen
import dot.jcversa.novay.ui.screens.HomeScreen
import dot.jcversa.novay.ui.screens.PlayerScreen
import dot.jcversa.novay.ui.screens.SearchScreen
import dot.jcversa.novay.ui.screens.ShowScreen
import dot.jcversa.novay.ui.theme.Amber500
import dot.jcversa.novay.ui.theme.DarkBackground
import dot.jcversa.novay.ui.theme.DarkSurfaceCard
import dot.jcversa.novay.ui.theme.TextMuted

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

/**
 * Pile de navigation AU NIVEAU DU PROCESSUS (pas de remember) : elle
 * survit a toute recreation de l activite. Sans cela, le lecteur qui
 * force le paysage recreait l activite et renvoyait a l accueil - le
 * lecteur semblait ne jamais s ouvrir.
 */
private val processStack = mutableStateListOf<Destination>(Destination.Home)

/** Racines d'onglet (barre de navigation basse). */
private val Destination.isTab: Boolean
    get() = this is Destination.Home || this is Destination.Search || this is Destination.Downloads

/** Profondeur d'un écran dans le flux (sens de la transition animée). */
private fun Destination.rank(): Int = when (this) {
    Destination.Home, Destination.Search, Destination.Downloads -> 0
    is Destination.Show -> 1
    is Destination.Episodes -> 2
    is Destination.Player -> 3
}

/**
 * Coque de navigation NoVaY (V2.1) : trois onglets en bas — Accueil,
 * Recherche, Téléchargements — et un flux de détail par-dessus
 * (Fiche → Épisodes → Lecteur). Le lecteur passe en plein écran : la
 * barre disparaît pendant la lecture.
 */
@Composable
fun NoVaYApp() {
    val source = remember { SamaSource() }
    val stack = processStack

    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }

    val open: (Destination) -> Unit = { stack.add(it) }
    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

    // Changer d'onglet remplace la pile : les détails de l'onglet
    // précédent ne survivent pas (comportement standard des onglets).
    val openTab: (Destination) -> Unit = { dest ->
        if (stack.firstOrNull() != dest || stack.size > 1) {
            stack.clear()
            stack.add(dest)
        }
    }

    // Onglet courant = racine de la pile ; masqué en pleine lecture.
    val currentTab = stack.firstOrNull { it.isTab } ?: Destination.Home
    val showBottomBar = stack.last() !is Destination.Player

    SlateMotionProvider {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = DarkBackground,
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar(
                        containerColor = DarkSurfaceCard,
                        contentColor = TextMuted
                    ) {
                        tabItems.forEach { (dest, label, selectedIcon, unselectedIcon) ->
                            val selected = currentTab == dest
                            NavigationBarItem(
                                selected = selected,
                                onClick = { openTab(dest) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) selectedIcon else unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(text = label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Amber500,
                                    selectedTextColor = Amber500,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted,
                                    indicatorColor = Color(0x26F59E0B)
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            AnimatedContent(
                targetState = stack.last(),
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    if (targetState.rank() == initialState.rank()) {
                        // Même niveau (changement d'onglet) : fondu simple.
                        fadeIn(tween(180)) togetherWith fadeOut(tween(180))
                    } else if (targetState.rank() > initialState.rank()) {
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
                // La flèche retour n'a de sens que si on peut revenir
                // (l'écran n'est pas la racine de son onglet).
                val canBack = stack.size > 1
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (screen) {
                        Destination.Home -> HomeScreen(
                            onOpenSearch = { openTab(Destination.Search) },
                            onOpenDownloads = { openTab(Destination.Downloads) },
                            onResume = { entry ->
                                open(playerDestination(entry))
                            }
                        )
                        Destination.Search -> SearchScreen(
                            source = source,
                            canBack = canBack,
                            onBack = back,
                            onOpenShow = { open(Destination.Show(it)) }
                        )
                        Destination.Downloads -> DownloadsScreen(
                            canBack = canBack,
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

/** Reconstruit la destination Lecteur d'une entrée d'historique. */
private fun playerDestination(entry: WatchHistoryEntry) = Destination.Player(
    season = AnimeSeason(name = entry.seasonName, url = entry.seasonUrl),
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

/** Les trois onglets : destination, libellé, icônes. */
private data class TabItem(
    val dest: Destination,
    val label: String,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector
)

private val tabItems = listOf(
    TabItem(Destination.Home, "Accueil", Icons.Filled.Home, Icons.Outlined.Home),
    TabItem(Destination.Search, "Recherche", Icons.Filled.Search, Icons.Outlined.Search),
    TabItem(
        Destination.Downloads,
        "Téléchargements",
        Icons.Filled.DownloadDone,
        Icons.Outlined.DownloadDone
    )
)
