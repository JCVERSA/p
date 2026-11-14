package dot.jcversa.novay.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.AnimeSeason
import dot.jcversa.novay.data.AnimeSource
import dot.jcversa.novay.data.Library
import dot.jcversa.novay.data.LibraryEntry
import dot.jcversa.novay.data.WatchHistory
import dot.jcversa.novay.downloads.Downloads
import dot.jcversa.novay.ui.components.AnimateEntrance
import dot.jcversa.novay.ui.components.AppBarTitle
import dot.jcversa.novay.ui.components.ErrorView
import dot.jcversa.novay.ui.components.LangPill
import dot.jcversa.novay.ui.components.LoadingView
import dot.jcversa.novay.ui.components.NovayAppBar
import dot.jcversa.novay.ui.components.NovayBadge
import dot.jcversa.novay.ui.components.PosterImage
import dot.jcversa.novay.ui.theme.header
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Écran Fiche (V2.4, design Mihon MangaScreen) :
 *  - backdrop : affiche floutée 4 dp, alpha 0,2, fondu vers le fond ;
 *  - en-tête : couverture + titre + badges de langue ;
 *  - rangée d'actions Mihon : cœur « Ajouter à la bibliothèque » ;
 *  - FAB « Continuer » (reprise de l'épisode en cours), qui se rétracte
 *    au défilement (pattern animateFloatingActionButton Mihon) ;
 *  - saisons listées comme les chapitres Mihon, CLASSÉES PAR LANGUE :
 *    toutes les VF d'abord, toutes les VOSTFR ensuite (contrainte
 *    permanente), en-têtes de section épinglés.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ShowScreen(
    source: AnimeSource,
    anime: AnimeSearchResult,
    onBack: () -> Unit,
    onOpenEpisodes: (AnimeSeason) -> Unit,
    onResume: (dot.jcversa.novay.data.WatchHistoryEntry) -> Unit
) {
    val context = LocalContext.current
    var seasons by remember { mutableStateOf<List<AnimeSeason>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }
    var inLibrary by remember { mutableStateOf(false) }
    var resumeEntry by remember {
        mutableStateOf<dot.jcversa.novay.data.WatchHistoryEntry?>(null)
    }
    // Téléchargés par saison (badge des lignes).
    var downloadedBySeason by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    LaunchedEffect(anime.url, retryKey) {
        error = null
        seasons = null
        try {
            seasons = source.seasons(anime)
        } catch (e: Exception) {
            error = e.message ?: "Erreur inconnue"
        }
    }

    LaunchedEffect(anime.url) {
        inLibrary = withContext(Dispatchers.IO) { Library.isInLibrary(context, anime.url) }
        resumeEntry = withContext(Dispatchers.IO) {
            WatchHistory.recent(context)
                .firstOrNull { it.animeUrl.trimEnd('/') == anime.url.trimEnd('/') }
        }
        downloadedBySeason = withContext(Dispatchers.IO) {
            Downloads.snapshot(context)
                .filter { it.isCompleted }
                .groupBy { it.meta?.seasonUrl?.trimEnd('/') ?: "" }
                .mapValues { it.value.size }
        }
    }

    fun toggleFavorite() {
        val entry = LibraryEntry(
            title = anime.title,
            url = anime.url,
            slug = anime.slug,
            posterUrl = anime.posterUrl,
            languages = seasons.orEmpty().mapNotNull { it.language?.uppercase() }.distinct(),
            addedAt = System.currentTimeMillis()
        )
        inLibrary = Library.toggle(context, entry)
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            NovayAppBar(
                titleContent = { AppBarTitle(title = anime.title) },
                navigateUp = onBack,
                scrollBehavior = scrollBehavior,
            )

            when {
                error != null -> ErrorView(message = error!!, onRetry = { retryKey++ })
                seasons == null -> LoadingView(message = "Chargement des saisons…")
                seasons!!.isEmpty() -> Text(
                    text = "Aucune saison listée sur la page de cet anime.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // ── En-tête : backdrop flouté + couverture + titres ──
                    item(key = "header") { Header(anime = anime) }

                    // ── Rangée d'actions Mihon ──
                    item(key = "actions") {
                        Row(
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp),
                        ) {
                            MangaActionButton(
                                title = if (inLibrary) "En bibliothèque" else "Ajouter",
                                icon = if (inLibrary) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                color = if (inLibrary) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                },
                                onClick = { toggleFavorite() },
                            )
                            MangaActionButton(
                                title = "${seasons!!.size} saison${if (seasons!!.size > 1) "s" else ""}",
                                icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                onClick = {},
                            )
                        }
                    }

                    // ── Saisons classées par langue : VF d'abord ──
                    val grouped = orderByLanguage(seasons!!)
                    grouped.forEach { (lang, langSeasons) ->
                        stickyHeader(key = "sticky_${lang}") {
                            Text(
                                text = lang,
                                style = MaterialTheme.typography.header,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                        itemsIndexed(langSeasons, key = { _, s -> s.url }) { index, season ->
                            val dl = downloadedBySeason[season.url.trimEnd('/')] ?: 0
                            AnimateEntrance(index = index) {
                                SeasonRow(
                                    season = season,
                                    episodeCount = null,
                                    downloaded = dl,
                                    onClick = {
                                        // Ouvre la saison : ses nouveaux épisodes sont vus.
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                runCatching {
                                                    val eps = source.episodes(season)
                                                    Library.markSeen(context, season.url, eps.size)
                                                }
                                            }
                                        }
                                        onOpenEpisodes(season)
                                    },
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(96.dp)) }
                }
            }
        }

        // ── FAB « Continuer » (se rétracte au défilement — pattern Mihon) ──
        val fabVisible = resumeEntry != null
        val idle by remember {
            derivedStateOf { !listState.isScrollInProgress }
        }
        val fabScale by animateFloatAsState(
            targetValue = if (fabVisible && idle) 1f else 0f,
            animationSpec = tween(200),
            label = "fab_visibility",
        )
        if (fabVisible && fabScale > 0.01f) {
            ExtendedFloatingActionButton(
                onClick = { resumeEntry?.let(onResume) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .alpha(fabScale),
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                )
                Text(text = "Continuer")
            }
        }
    }
}

/** En-tête Mihon : backdrop flouté + couverture + titre + langues. */
@Composable
private fun Header(anime: AnimeSearchResult) {
    Box(modifier = Modifier.fillMaxWidth()) {
        // Backdrop : l'affiche floutée qui fond dans le fond (Mihon).
        PosterImage(
            url = anime.posterUrl,
            contentDescription = null,
            modifier = Modifier
                .matchParentSize()
                .blur(4.dp)
                .alpha(0.2f),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to MaterialTheme.colorScheme.background,
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            PosterImage(
                url = anime.posterUrl,
                contentDescription = anime.title,
                modifier = Modifier
                    .size(width = 96.dp, height = 144.dp)
                    .clip(MaterialTheme.shapes.small),
            )
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = anime.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!anime.subtitle.isNullOrBlank()) {
                    Text(
                        text = anime.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                anime.language?.let { lang ->
                    LangPill(text = lang, vf = lang.equals("VF", true))
                }
            }
        }
    }
}

/** Bouton d'action Mihon (colonne icône + libellé). */
@Composable
private fun MangaActionButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(end = 24.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color)
        Spacer(Modifier.height(2.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Ligne de saison style chapitre Mihon. */
@Composable
private fun SeasonRow(
    season: AnimeSeason,
    episodeCount: Int?,
    downloaded: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = season.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(6.dp))
                if (downloaded > 0) {
                    NovayBadge(
                        text = "$downloaded",
                        color = MaterialTheme.colorScheme.tertiary,
                        textColor = MaterialTheme.colorScheme.onTertiary,
                    )
                }
            }
            Row {
                val subtitle = buildString {
                    episodeCount?.let { append("$it épisodes") }
                    season.language?.let { lang ->
                        if (isNotEmpty()) append(" · ")
                        append(lang.uppercase())
                    }
                }
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** VF d'abord, VOSTFR ensuite (contrainte permanente NoVaY). */
internal fun orderByLanguage(seasons: List<AnimeSeason>): List<Pair<String, List<AnimeSeason>>> {
    val groups = linkedMapOf<String, MutableList<AnimeSeason>>()
    for (s in seasons) {
        val lang = s.language?.uppercase() ?: when {
            s.url.contains("/vf/") -> "VF"
            s.url.contains("/vostfr/") -> "VOSTFR"
            else -> "AUTRE"
        }
        groups.getOrPut(lang) { mutableListOf() }.add(s)
    }
    val ordered = groups.entries.sortedWith(
        compareBy { (lang, _) ->
            when (lang) {
                "VF" -> 0
                "VOSTFR" -> 1
                else -> 2
            }
        }
    )
    return ordered.map { it.key to it.value.toList() }
}
