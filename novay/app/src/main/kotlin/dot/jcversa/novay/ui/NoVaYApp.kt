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
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dot.jcversa.novay.data.AnimeEpisode
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.AnimeSeason
import dot.jcversa.novay.data.Library
import dot.jcversa.novay.data.ResolvedStream
import dot.jcversa.novay.data.WatchHistoryEntry
import dot.jcversa.novay.data.sama.SamaSource
import dot.jcversa.novay.ui.components.SlateMotionProvider
import dot.jcversa.novay.ui.screens.BrowseScreen
import dot.jcversa.novay.ui.screens.DownloadsScreen
import dot.jcversa.novay.ui.screens.EpisodesScreen
import dot.jcversa.novay.ui.screens.HistoryScreen
import dot.jcversa.novay.ui.screens.LibraryScreen
import dot.jcversa.novay.ui.screens.MoreScreen
import dot.jcversa.novay.ui.screens.PlayerScreen
import dot.jcversa.novay.ui.screens.ShowScreen
import dot.jcversa.novay.ui.screens.UpdatesScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Écrans NoVaY, avec leur payload de navigation. */
sealed class Destination {
    data object Library : Destination()
    data object Updates : Destination()
    data object History : Destination()
    data object Browse : Destination()
    data object More : Destination()
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
 * survit à toute recréation de l'activité. Sans cela, le lecteur qui
 * force le paysage recréait l'activité et renvoyait à l'accueil — le
 * lecteur semblait ne jamais s'ouvrir.
 */
private val processStack = mutableStateListOf<Destination>(Destination.Library)

/** Racines d'onglet (barre de navigation basse). */
private val Destination.isTab: Boolean
    get() = this is Destination.Library || this is Destination.Updates ||
        this is Destination.History || this is Destination.Browse ||
        this is Destination.More

/** Profondeur d'un écran dans le flux (sens de la transition animée). */
private fun Destination.rank(): Int = when (this) {
    Destination.Library, Destination.Updates, Destination.History,
    Destination.Browse, Destination.More -> 0
    Destination.Downloads -> 1
    is Destination.Show -> 1
    is Destination.Episodes -> 2
    is Destination.Player -> 3
}

/** Clé stable d'un écran (préservation d'état par onglet). */
private fun Destination.key(): String = when (this) {
    Destination.Library -> "tab_library"
    Destination.Updates -> "tab_updates"
    Destination.History -> "tab_history"
    Destination.Browse -> "tab_browse"
    Destination.More -> "tab_more"
    Destination.Downloads -> "downloads"
    is Destination.Show -> "show_${anime.url}"
    is Destination.Episodes -> "episodes_${season.url}"
    is Destination.Player -> "player_${season.url}_${episode.number}"
}

/**
 * Coque de navigation NoVaY (V2.4, design Mihon HomeScreen) : CINQ onglets
 * en bas — Bibliothèque, Mises à jour, Historique, Catalogue, Plus — et un
 * flux de détail par-dessus (Fiche → Épisodes → Lecteur). Transition
 * fade-through entre onglets (matériau Mihon), axe partagé pour la pile.
 * Le lecteur passe en plein écran : la barre disparaît pendant la lecture.
 */
@Composable
fun NoVaYApp() {
    val source = remember { SamaSource() }
    val context = LocalContext.current
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
    val currentTab = stack.firstOrNull { it.isTab } ?: Destination.Library
    val showBottomBar = stack.last() !is Destination.Player

    // Badge « Mises à jour » : total des nouveaux épisodes des favoris,
    // rafraîchi quand on revient sur un onglet (les visites consomment).
    var updatesBadge by remember { mutableIntStateOf(0) }
    val lastIsTab = stack.last().isTab
    LaunchedEffect(lastIsTab, stack.size) {
        if (lastIsTab) {
            updatesBadge = withContext(Dispatchers.IO) {
                Library.all(context).sumOf { it.newEpisodes }
            }
        }
    }

    val stateHolder = rememberSaveableStateHolder()

    SlateMotionProvider {
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {
                        tabItems.forEach { (dest, label, icon) ->
                            val selected = currentTab == dest
                            NavigationBarItem(
                                selected = selected,
                                onClick = { openTab(dest) },
                                icon = {
                                    if (dest == Destination.Updates && updatesBadge > 0) {
                                        BadgedBox(
                                            badge = {
                                                Badge(
                                                    containerColor = MaterialTheme.colorScheme.secondary,
                                                    contentColor = MaterialTheme.colorScheme.onSecondary,
                                                ) {
                                                    Text(text = "$updatesBadge")
                                                }
                                            },
                                        ) {
                                            Icon(imageVector = icon, contentDescription = label)
                                        }
                                    } else {
                                        Icon(imageVector = icon, contentDescription = label)
                                    }
                                },
                                label = { Text(text = label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                ),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            AnimatedContent(
                targetState = stack.last(),
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    if (targetState.rank() == initialState.rank()) {
                        // Changement d'onglet : fade-through Mihon
                        // (sortie courte, entrée ample).
                        fadeIn(tween(200)) togetherWith fadeOut(tween(90))
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
                label = "novay_screen_stack",
            ) { screen ->
                // La flèche retour n'a de sens que si on peut revenir
                // (l'écran n'est pas la racine de son onglet).
                val canBack = stack.size > 1
                // L'état de chaque écran (scroll…) survit aux changements d'onglet.
                stateHolder.SaveableStateProvider(key = screen.key()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (screen) {
                            Destination.Library -> LibraryScreen(
                                onOpenShow = { open(Destination.Show(it)) },
                                onResume = { entry -> open(playerDestination(entry)) }
                            )
                            Destination.Updates -> UpdatesScreen(
                                source = source,
                                onOpenShow = { open(Destination.Show(it)) },
                                onResume = { entry -> open(playerDestination(entry)) }
                            )
                            Destination.History -> HistoryScreen(
                                onResume = { entry -> open(playerDestination(entry)) }
                            )
                            Destination.Browse -> BrowseScreen(
                                source = source,
                                onOpenShow = { open(Destination.Show(it)) }
                            )
                            Destination.More -> MoreScreen(
                                onOpenDownloads = { open(Destination.Downloads) }
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
                                },
                                onResume = { entry -> open(playerDestination(entry)) }
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

/** Les cinq onglets : destination, libellé, icône. */
private data class TabItem(
    val dest: Destination,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val tabItems = listOf(
    TabItem(Destination.Library, "Bibliothèque", Icons.Rounded.VideoLibrary),
    TabItem(Destination.Updates, "Mises à jour", Icons.Rounded.NewReleases),
    TabItem(Destination.History, "Historique", Icons.Rounded.History),
    TabItem(Destination.Browse, "Catalogue", Icons.Rounded.Explore),
    TabItem(Destination.More, "Plus", Icons.Rounded.MoreHoriz),
)
