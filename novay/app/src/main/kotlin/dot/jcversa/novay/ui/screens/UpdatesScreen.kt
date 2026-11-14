package dot.jcversa.novay.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.AnimeSource
import dot.jcversa.novay.data.Library
import dot.jcversa.novay.data.LibraryEntry
import dot.jcversa.novay.data.WatchHistoryEntry
import dot.jcversa.novay.ui.LocalBottomChromePadding
import dot.jcversa.novay.ui.components.AppBarAction
import dot.jcversa.novay.ui.components.AppBarTitle
import dot.jcversa.novay.ui.components.EmptyScreen
import dot.jcversa.novay.ui.components.LocalSlateMotion
import dot.jcversa.novay.ui.components.NovayAppBar
import dot.jcversa.novay.ui.components.NovayBadge
import dot.jcversa.novay.ui.components.Pill
import dot.jcversa.novay.ui.components.PosterImage
import dot.jcversa.novay.ui.components.RowListSkeleton
import dot.jcversa.novay.ui.components.SwiftSlateSpinner
import dot.jcversa.novay.ui.components.novaySharedPoster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Écran Mises à jour — Cinema Glassmorphism & Editorial Noir :
 * animés suivis ayant de nouveaux épisodes, squelettes Shimmer, lignes
 * distillées sans bordure superflue et transition d'affiche partagée.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(
    source: AnimeSource,
    onOpenShow: (AnimeSearchResult) -> Unit,
    onResume: (WatchHistoryEntry) -> Unit
) {
    val context = LocalContext.current
    val bottomChrome = LocalBottomChromePadding.current
    var entries by remember { mutableStateOf<List<LibraryEntry>?>(null) }
    var checking by remember { mutableStateOf(false) }
    var checkError by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(refreshKey) {
        entries = withContext(Dispatchers.IO) { Library.all(context) }
    }

    fun checkAll() {
        if (checking) return
        checkError = null
        val favorites = Library.all(context)
        if (favorites.isEmpty()) {
            refreshKey++
            return
        }
        checking = true
        scope.launch {
            try {
                for (entry in favorites) {
                    val counts = mutableMapOf<String, Int>()
                    val seasons = runCatching {
                        source.seasons(entry.toSearchResult())
                    }.getOrDefault(emptyList())
                    for (season in seasons) {
                        val eps = runCatching {
                            source.episodes(season)
                        }.getOrDefault(emptyList())
                        counts[season.url.trimEnd('/')] = eps.size
                    }
                    Library.recordCounts(context, entry.url, counts)
                }
            } catch (e: Exception) {
                checkError = e.message ?: "Vérification impossible"
            } finally {
                checking = false
                refreshKey++
            }
        }
    }

    val motion = LocalSlateMotion.current
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val news = remember(entries) { entries.orEmpty().sumOf { it.newEpisodes } }
    val withNews = remember(entries) {
        entries.orEmpty().filter { it.newEpisodes > 0 }.sortedByDescending { it.addedAt }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
    ) {
        NovayAppBar(
            titleContent = {
                AppBarTitle(title = "Mises à jour")
                if (news > 0) {
                    Pill(
                        text = "+$news",
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                        contentColor = MaterialTheme.colorScheme.secondary,
                    )
                }
            },
            scrollBehavior = scrollBehavior,
            actions = {
                if (checking) {
                    Box(
                        modifier = Modifier.minimumInteractiveComponentSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        SwiftSlateSpinner(size = 20.dp)
                    }
                } else {
                    AppBarAction(
                        contentDescription = "Vérifier les nouveaux épisodes",
                        icon = Icons.Rounded.Refresh,
                        modifier = Modifier.testTag("updates_refresh_button"),
                        onClick = { checkAll() },
                    )
                }
            },
        )

        val viewKey = when {
            entries == null || (checking && entries.orEmpty().isEmpty()) -> "loading"
            entries.orEmpty().isEmpty() -> "no_followed"
            withNews.isEmpty() -> "up_to_date"
            else -> "list"
        }

        AnimatedContent(
            targetState = viewKey,
            transitionSpec = { motion.stateCrossfade() },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            label = "updates_body_state",
        ) { stateKey ->
            when (stateKey) {
                "loading" -> RowListSkeleton(modifier = Modifier.padding(bottom = bottomChrome))
                "no_followed" -> EmptyScreen(
                    icon = Icons.Rounded.NewReleases,
                    headingText = "Aucun animé suivi",
                    subtitleText = "Ajoute des animés en bibliothèque depuis le catalogue pour recevoir ici les nouveaux épisodes.",
                    modifier = Modifier.padding(bottom = bottomChrome),
                )
                "up_to_date" -> EmptyScreen(
                    icon = Icons.Rounded.NewReleases,
                    headingText = "Tout est à jour",
                    subtitleText = if (checkError != null) {
                        "Dernière vérification incomplète : $checkError"
                    } else {
                        "Aucun nouvel épisode détecté. Touche Actualiser en haut à droite pour vérifier."
                    },
                    modifier = Modifier.padding(bottom = bottomChrome),
                )
                else -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 840.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp + bottomChrome),
                    ) {
                        items(
                            withNews,
                            key = { it.url },
                            contentType = { "update_row" },
                        ) { entry ->
                            UpdateRow(
                                modifier = Modifier.animateItem(),
                                entry = entry,
                                onClick = { onOpenShow(entry.toSearchResult()) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateRow(
    entry: LibraryEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.medium
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val containerColor by animateColorAsState(
        targetValue = if (hovered) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        label = "update_row_bg",
    )

    Surface(
        shape = shape,
        color = containerColor,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(shape)
            .semantics(mergeDescendants = true) {}
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick,
            )
            .minimumInteractiveComponentSize(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PosterImage(
                url = entry.posterUrl,
                contentDescription = entry.title,
                modifier = Modifier
                    .size(width = 58.dp, height = 84.dp)
                    .novaySharedPoster(entry.url)
                    .clip(MaterialTheme.shapes.extraSmall),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${entry.lastCounts.size} saison${if (entry.lastCounts.size > 1) "s" else ""} suivie${if (entry.lastCounts.size > 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            NovayBadge(text = "+${entry.newEpisodes}")
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "Ouvrir ${entry.title}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
