package dot.jcversa.novay.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected as semanticsSelected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.rememberHazeState
import dot.jcversa.novay.data.AnimeEpisode
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.AnimeSeason
import dot.jcversa.novay.data.Library
import dot.jcversa.novay.data.ResolvedStream
import dot.jcversa.novay.data.WatchHistoryEntry
import dot.jcversa.novay.data.sama.SamaSource
import dot.jcversa.novay.ui.components.LocalAnimatedVisibilityScope
import dot.jcversa.novay.ui.components.LocalNovayHazeState
import dot.jcversa.novay.ui.components.LocalSharedTransitionScope
import dot.jcversa.novay.ui.components.LocalSlateMotion
import dot.jcversa.novay.ui.components.NovayTabMorphIcon
import dot.jcversa.novay.ui.components.PredictiveBackContainer
import dot.jcversa.novay.ui.components.SlateMotion
import dot.jcversa.novay.ui.components.SlateMotionProvider
import dot.jcversa.novay.ui.components.novayCinemaGlass
import dot.jcversa.novay.ui.components.novayHazeSource
import dot.jcversa.novay.ui.screens.BrowseScreen
import dot.jcversa.novay.ui.screens.DownloadsScreen
import dot.jcversa.novay.ui.screens.EpisodesScreen
import dot.jcversa.novay.ui.screens.HistoryScreen
import dot.jcversa.novay.ui.screens.LibraryScreen
import dot.jcversa.novay.ui.screens.MoreScreen
import dot.jcversa.novay.ui.screens.PlayerScreen
import dot.jcversa.novay.ui.screens.ShowScreen
import dot.jcversa.novay.ui.screens.UpdatesScreen
import dot.jcversa.novay.ui.state.TopLevelDestination
import dot.jcversa.novay.ui.theme.NovayTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Marge inférieure exacte réservée sous les listes lorsque le dock icônes
 * verre dépoli flotte en bas (<600dp).
 */
val LocalBottomChromePadding = staticCompositionLocalOf<Dp> { 0.dp }

private val DOCK_CONTENT_HEIGHT = 60.dp

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
 * Pile de navigation AU NIVEAU DU PROCESSUS : survit à la rotation paysage
 * du lecteur vidéo sans perte de contexte.
 */
private val processStack = mutableStateListOf<Destination>(Destination.Library)

private val Destination.isTab: Boolean
    get() = this is Destination.Library || this is Destination.Updates ||
        this is Destination.History || this is Destination.Browse ||
        this is Destination.More

private fun Destination.toTopLevel(): TopLevelDestination? = when (this) {
    Destination.Library -> TopLevelDestination.LIBRARY
    Destination.Updates -> TopLevelDestination.UPDATES
    Destination.History -> TopLevelDestination.HISTORY
    Destination.Browse -> TopLevelDestination.BROWSE
    Destination.More -> TopLevelDestination.MORE
    else -> null
}

private fun TopLevelDestination.toRoute(): Destination = when (this) {
    TopLevelDestination.LIBRARY -> Destination.Library
    TopLevelDestination.UPDATES -> Destination.Updates
    TopLevelDestination.HISTORY -> Destination.History
    TopLevelDestination.BROWSE -> Destination.Browse
    TopLevelDestination.MORE -> Destination.More
}

private fun Destination.rank(): Int = when (this) {
    Destination.Library, Destination.Updates, Destination.History,
    Destination.Browse, Destination.More -> 0
    Destination.Downloads -> 1
    is Destination.Show -> 1
    is Destination.Episodes -> 2
    is Destination.Player -> 3
}

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
 * État UDF central de l'application (inspiré de NiaAppState / Now in Android).
 */
@Stable
class NovayAppState(
    private val stack: MutableList<Destination>,
) {
    val currentDestination: Destination
        get() = stack.last()

    val currentTab: Destination by derivedStateOf {
        stack.firstOrNull { it.isTab } ?: Destination.Library
    }

    val currentTopLevel: TopLevelDestination by derivedStateOf {
        currentTab.toTopLevel() ?: TopLevelDestination.LIBRARY
    }

    val showChrome: Boolean by derivedStateOf {
        stack.last() !is Destination.Player
    }

    val canNavigateBack: Boolean by derivedStateOf {
        stack.size > 1
    }

    val isSecondaryScreen: Boolean by derivedStateOf {
        val last = stack.last()
        stack.size > 1 && last !is Destination.Player && !last.isTab
    }

    fun open(dest: Destination) {
        stack.add(dest)
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun openTab(topLevel: TopLevelDestination) {
        val dest = topLevel.toRoute()
        if (stack.firstOrNull() != dest || stack.size > 1) {
            stack.clear()
            stack.add(dest)
        }
    }
}

@Composable
private fun rememberNovayAppState(): NovayAppState =
    remember { NovayAppState(processStack) }

/**
 * Coque adaptative NoVaY — Aniyomi Icon-Only Morph Dock & Now in Android UDF :
 *  - Dock bas sans texte (<600dp) avec capsule glissante & 5 icônes morphing ;
 *  - Rail latéral sans texte sur tablettes (>=600dp) ;
 *  - PredictiveBackHandler en temps réel sur les écrans secondaires ;
 *  - Transitions d'affiches partagées + courbes EaseOutExpo < 300ms.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun NoVaYApp() {
    val source = remember { SamaSource() }
    val context = LocalContext.current
    val appState = rememberNovayAppState()

    BackHandler(enabled = appState.canNavigateBack && !appState.isSecondaryScreen) {
        appState.back()
    }

    var updatesBadge by remember { mutableIntStateOf(0) }
    val lastIsTab = appState.currentDestination.isTab
    val stackSize = processStack.size
    LaunchedEffect(lastIsTab, stackSize) {
        if (lastIsTab) {
            updatesBadge = withContext(Dispatchers.IO) {
                Library.all(context).sumOf { it.newEpisodes }
            }
        }
    }

    val stateHolder = rememberSaveableStateHolder()
    val shellHazeState = rememberHazeState()

    SlateMotionProvider {
        val motion = LocalSlateMotion.current
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useRail = maxWidth >= 600.dp
                val navBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val bottomChromePadding = if (appState.showChrome && !useRail) {
                    DOCK_CONTENT_HEIGHT + navBarBottomInset
                } else {
                    0.dp
                }

                CompositionLocalProvider(
                    LocalNovayHazeState provides shellHazeState,
                    LocalBottomChromePadding provides bottomChromePadding,
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            if (appState.showChrome && useRail) {
                                NavigationRail(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .windowInsetsPadding(WindowInsets.safeDrawing),
                                    header = {
                                        Text(
                                            text = "NV",
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(vertical = 14.dp),
                                        )
                                    },
                                ) {
                                    Spacer(Modifier.weight(0.15f))
                                    TopLevelDestination.entries.forEach { dest ->
                                        val selected = appState.currentTopLevel == dest
                                        NavigationRailItem(
                                            selected = selected,
                                            onClick = { appState.openTab(dest) },
                                            alwaysShowLabel = false,
                                            label = null,
                                            modifier = Modifier
                                                .semantics { contentDescription = dest.contentDescription }
                                                .testTag("nav_rail_${dest.testTagKey}"),
                                            icon = {
                                                TabMorphIconWithBadge(
                                                    dest = dest,
                                                    selected = selected,
                                                    updatesBadge = updatesBadge,
                                                    tint = if (selected) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    },
                                                )
                                            },
                                            colors = NavigationRailItemDefaults.colors(
                                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                            ),
                                        )
                                    }
                                    Spacer(Modifier.weight(1f))
                                }
                                VerticalDivider(
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }

                            SharedTransitionLayout(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .novayHazeSource(shellHazeState),
                            ) {
                                CompositionLocalProvider(
                                    LocalSharedTransitionScope provides this@SharedTransitionLayout,
                                ) {
                                    AnimatedContent(
                                        targetState = appState.currentDestination,
                                        modifier = Modifier.fillMaxSize(),
                                        transitionSpec = {
                                            when {
                                                motion.reduceMotion ->
                                                    EnterTransition.None togetherWith ExitTransition.None
                                                targetState.rank() == initialState.rank() ->
                                                    (fadeIn(tween(200, easing = SlateMotion.EaseOutExpo)) +
                                                        scaleIn(
                                                            initialScale = 0.988f,
                                                            animationSpec = tween(200, easing = SlateMotion.EaseOutExpo),
                                                        )) togetherWith
                                                        fadeOut(tween(120, easing = SlateMotion.FastOutExit))
                                                targetState.rank() > initialState.rank() ->
                                                    (slideInHorizontally(tween(240, easing = SlateMotion.EaseOutExpo)) { it / 5 } +
                                                        fadeIn(tween(200, easing = SlateMotion.EaseOutExpo))) togetherWith
                                                        (slideOutHorizontally(tween(150, easing = SlateMotion.FastOutExit)) { -it / 6 } +
                                                            fadeOut(tween(140, easing = SlateMotion.FastOutExit)))
                                                else ->
                                                    (slideInHorizontally(tween(240, easing = SlateMotion.EaseOutExpo)) { -it / 5 } +
                                                        fadeIn(tween(200, easing = SlateMotion.EaseOutExpo))) togetherWith
                                                        (slideOutHorizontally(tween(150, easing = SlateMotion.FastOutExit)) { it / 6 } +
                                                            fadeOut(tween(140, easing = SlateMotion.FastOutExit)))
                                            }
                                        },
                                        label = "novay_screen_stack",
                                    ) { screen ->
                                        val canBack = processStack.size > 1
                                        val enablePredictive = canBack && !screen.isTab && screen !is Destination.Player
                                        CompositionLocalProvider(
                                            LocalAnimatedVisibilityScope provides this@AnimatedContent,
                                        ) {
                                            PredictiveBackContainer(
                                                enabled = enablePredictive,
                                                onBack = appState::back,
                                            ) {
                                                stateHolder.SaveableStateProvider(key = screen.key()) {
                                                    Box(modifier = Modifier.fillMaxSize()) {
                                                        when (screen) {
                                                            Destination.Library -> LibraryScreen(
                                                                onOpenShow = { appState.open(Destination.Show(it)) },
                                                                onResume = { entry -> appState.open(playerDestination(entry)) }
                                                            )
                                                            Destination.Updates -> UpdatesScreen(
                                                                source = source,
                                                                onOpenShow = { appState.open(Destination.Show(it)) },
                                                                onResume = { entry -> appState.open(playerDestination(entry)) }
                                                            )
                                                            Destination.History -> HistoryScreen(
                                                                onResume = { entry -> appState.open(playerDestination(entry)) }
                                                            )
                                                            Destination.Browse -> BrowseScreen(
                                                                source = source,
                                                                onOpenShow = { appState.open(Destination.Show(it)) },
                                                                onOpenEpisodes = { animeTitle, posterUrl, season ->
                                                                    appState.open(
                                                                        Destination.Episodes(
                                                                            animeTitle = animeTitle,
                                                                            posterUrl = posterUrl,
                                                                            season = season
                                                                        )
                                                                    )
                                                                }
                                                            )
                                                            Destination.More -> MoreScreen(
                                                                onOpenDownloads = { appState.open(Destination.Downloads) }
                                                            )
                                                            Destination.Downloads -> DownloadsScreen(
                                                                canBack = canBack,
                                                                onBack = appState::back,
                                                                onPlay = { season, episode, stream ->
                                                                    appState.open(
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
                                                                onBack = appState::back,
                                                                onOpenEpisodes = { season ->
                                                                    appState.open(
                                                                        Destination.Episodes(
                                                                            animeTitle = screen.anime.title,
                                                                            posterUrl = screen.anime.posterUrl,
                                                                            season = season
                                                                        )
                                                                    )
                                                                },
                                                                onOpenPlayer = { season, episode, offlineStream ->
                                                                    appState.open(
                                                                        Destination.Player(
                                                                            season = season,
                                                                            episode = episode,
                                                                            animeTitle = screen.anime.title,
                                                                            posterUrl = screen.anime.posterUrl,
                                                                            initialStream = offlineStream
                                                                        )
                                                                    )
                                                                },
                                                                onResume = { entry -> appState.open(playerDestination(entry)) }
                                                            )
                                                            is Destination.Episodes -> EpisodesScreen(
                                                                source = source,
                                                                animeTitle = screen.animeTitle,
                                                                season = screen.season,
                                                                onBack = appState::back,
                                                                onOpenPlayer = { episode, offlineStream ->
                                                                    appState.open(
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
                                                                onBack = appState::back
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (appState.showChrome && !useRail) {
                            AniyomiIconOnlyDock(
                                current = appState.currentTopLevel,
                                updatesBadge = updatesBadge,
                                onSelect = appState::openTab,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .novayCinemaGlass(
                                        hazeState = shellHazeState,
                                        tint = NovayTheme.overlays.glassSurface,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dock bas Aniyomi — uniquement des icônes morphing sans texte, capsule
 * active animée au GPU et badge de mises à jour.
 */
@Composable
private fun AniyomiIconOnlyDock(
    current: TopLevelDestination,
    updatesBadge: Int,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalSlateMotion.current
    val destinations = TopLevelDestination.entries
    val selectedIndex = destinations.indexOf(current).coerceAtLeast(0)
    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = motion.dockIndicatorSpec(),
        label = "dock_pill_index",
    )
    val pillWidth = 58.dp
    val pillHeight = 36.dp

    Column(modifier = modifier) {
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
        )
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(DOCK_CONTENT_HEIGHT),
        ) {
            val density = LocalDensity.current
            val count = destinations.size
            val slotWidthPx = with(density) { (maxWidth / count).toPx() }
            val pillWidthPx = with(density) { pillWidth.toPx() }
            val indicatorOffsetX = (slotWidthPx * animatedIndex) + ((slotWidthPx - pillWidthPx) / 2f)

            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .graphicsLayer {
                        translationX = indicatorOffsetX
                    }
                    .size(width = pillWidth, height = pillHeight)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .border(
                        width = 0.5.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                        shape = CircleShape,
                    ),
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                destinations.forEach { dest ->
                    val selected = dest == current
                    val tint by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = motion.colorSpec(180),
                        label = "dock_tint_${dest.testTagKey}",
                    )
                    val interactionSource = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .minimumInteractiveComponentSize()
                            .semantics {
                                contentDescription = dest.contentDescription
                                role = Role.Tab
                                semanticsSelected = selected
                            }
                            .clickable(
                                interactionSource = interactionSource,
                                indication = ripple(bounded = false, radius = 28.dp),
                                onClick = { onSelect(dest) },
                            )
                            .testTag("nav_bar_${dest.testTagKey}"),
                        contentAlignment = Alignment.Center,
                    ) {
                        TabMorphIconWithBadge(
                            dest = dest,
                            selected = selected,
                            updatesBadge = updatesBadge,
                            tint = tint,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabMorphIconWithBadge(
    dest: TopLevelDestination,
    selected: Boolean,
    updatesBadge: Int,
    tint: Color,
) {
    val onTint = MaterialTheme.colorScheme.secondaryContainer
    if (dest == TopLevelDestination.UPDATES && updatesBadge > 0) {
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
            NovayTabMorphIcon(
                kind = dest.morphKind,
                selected = selected,
                tint = tint,
                onTint = onTint,
                size = 24.dp,
            )
        }
    } else {
        NovayTabMorphIcon(
            kind = dest.morphKind,
            selected = selected,
            tint = tint,
            onTint = onTint,
            size = 24.dp,
        )
    }
}

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
