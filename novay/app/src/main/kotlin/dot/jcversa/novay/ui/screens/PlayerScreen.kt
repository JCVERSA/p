package dot.jcversa.novay.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dot.jcversa.novay.data.AnimeEpisode
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.AnimeSeason
import dot.jcversa.novay.data.AnimeSource
import dot.jcversa.novay.data.Library
import dot.jcversa.novay.data.LibraryEntry
import dot.jcversa.novay.data.ResolvedStream
import dot.jcversa.novay.data.WatchHistory
import dot.jcversa.novay.downloads.Downloads
import dot.jcversa.novay.ui.components.AnimeComfortableGridItem
import dot.jcversa.novay.ui.components.EpisodesListSkeleton
import dot.jcversa.novay.ui.components.ErrorView
import dot.jcversa.novay.ui.components.LangPill
import dot.jcversa.novay.ui.components.LocalSlateMotion
import dot.jcversa.novay.ui.components.NovayBadge
import dot.jcversa.novay.ui.components.NovayFavoriteMorphIcon
import dot.jcversa.novay.ui.components.NovayIcons
import dot.jcversa.novay.ui.components.PosterImage
import dot.jcversa.novay.ui.components.novaySharedPoster
import dot.jcversa.novay.ui.player.EmbeddedVideoPlayer
import dot.jcversa.novay.ui.player.MiniPlayerGeometry
import dot.jcversa.novay.ui.player.PlayerPresentationMode
import dot.jcversa.novay.ui.player.PlayerStateManager
import dot.jcversa.novay.ui.theme.NovayTheme
import dot.jcversa.novay.ui.theme.header
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Écran de lecture adaptatif (Watch Hub Portrait) :
 *  - démarre toujours en portrait avec la vidéo réactive en tête de page ;
 *  - expose infos de l'animé, actions Suivre/Réduire, sélecteur de saisons/langues,
 *    liste d'épisodes (changement instantané sur le même moteur) et recommandations ;
 *  - détecte le défilement via LazyListState pour basculer automatiquement vers
 *    le mini-lecteur flottant sans coupure de lecture.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(
    playerManager: PlayerStateManager,
    source: AnimeSource,
    season: AnimeSeason,
    episode: AnimeEpisode,
    animeTitle: String = "",
    posterUrl: String? = null,
    anime: AnimeSearchResult? = null,
    initialStream: ResolvedStream? = null,
    videoSurfaceSlot: @Composable () -> Unit,
    onOpenShow: (AnimeSearchResult) -> Unit = {},
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val motion = LocalSlateMotion.current
    val overlays = NovayTheme.overlays
    val listState = rememberLazyListState()

    LaunchedEffect(season.url, episode.number, initialStream) {
        playerManager.openEpisode(
            source = source,
            season = season,
            episode = episode,
            animeTitle = animeTitle,
            posterUrl = posterUrl,
            anime = anime,
            initialStream = initialStream,
        )
    }

    val watchToken = remember { Any() }
    DisposableEffect(playerManager, watchToken) {
        playerManager.onWatchScreenEnter(watchToken)
        onDispose {
            playerManager.onWatchScreenExit(watchToken)
        }
    }

    LaunchedEffect(playerManager.scrollToTopSignal) {
        if (playerManager.scrollToTopSignal > 0) {
            listState.animateScrollToItem(0)
        }
    }

    val embeddedVisibleFraction by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val item0 = info.visibleItemsInfo.firstOrNull { it.index == 0 }
            if (item0 == null) {
                0f
            } else {
                MiniPlayerGeometry.computeVisibleFraction(
                    itemOffset = item0.offset,
                    itemSize = item0.size,
                    viewportStart = info.viewportStartOffset,
                    viewportEnd = info.viewportEndOffset,
                )
            }
        }
    }

    LaunchedEffect(embeddedVisibleFraction) {
        playerManager.updateInlineSlotVisibility(embeddedVisibleFraction)
    }

    val currentSession = playerManager.session
    val activeAnime = currentSession?.anime ?: anime
    val activeAnimeTitle = currentSession?.animeTitle?.ifBlank { currentSession.season.name }
        ?: animeTitle.ifBlank { season.name }
    val activePosterUrl = currentSession?.posterUrl ?: posterUrl
    val activePlayingSeason = currentSession?.season ?: season
    val activePlayingEpisode = currentSession?.episode ?: episode

    var browsingSeason by remember(activePlayingSeason.url) { mutableStateOf(activePlayingSeason) }
    var seasonsList by remember(activeAnime?.url) { mutableStateOf<List<AnimeSeason>>(listOf(activePlayingSeason)) }
    var episodes by remember(browsingSeason.url) { mutableStateOf<List<AnimeEpisode>?>(null) }
    var episodesError by remember(browsingSeason.url) { mutableStateOf<String?>(null) }
    var episodeRetry by remember { mutableIntStateOf(0) }
    var recommendations by remember { mutableStateOf<List<AnimeSearchResult>>(emptyList()) }
    var downloadedByEpisode by remember(browsingSeason.url) { mutableStateOf<Map<Int, Downloads.DownloadInfo>>(emptyMap()) }
    var resumePositions by remember(browsingSeason.url) { mutableStateOf<Map<Int, Long>>(emptyMap()) }
    var inLibrary by remember(activeAnime?.url) { mutableStateOf(false) }

    LaunchedEffect(activeAnime?.url) {
        if (activeAnime != null) {
            inLibrary = withContext(Dispatchers.IO) { Library.isInLibrary(context, activeAnime.url) }
            val loadedSeasons = withContext(Dispatchers.IO) {
                runCatching { source.seasons(activeAnime) }.getOrDefault(emptyList())
            }
            if (loadedSeasons.isNotEmpty()) {
                seasonsList = loadedSeasons
            }
        }
    }

    LaunchedEffect(browsingSeason.url, episodeRetry) {
        episodesError = null
        episodes = null
        try {
            episodes = withContext(Dispatchers.IO) { source.episodes(browsingSeason) }
        } catch (e: Exception) {
            episodesError = e.message ?: "Impossible de charger les épisodes"
        }
    }

    LaunchedEffect(browsingSeason.url) {
        resumePositions = withContext(Dispatchers.IO) {
            WatchHistory.recent(context)
                .filter { it.seasonUrl.trimEnd('/') == browsingSeason.url.trimEnd('/') }
                .associate { it.episodeNumber to it.positionMs }
        }
    }

    LaunchedEffect(context, browsingSeason.url) {
        val seasonKey = browsingSeason.url.trimEnd('/')
        Downloads.observeSnapshots(context).collect { all ->
            downloadedByEpisode = all
                .filter { it.isCompleted && it.meta?.seasonUrl?.trimEnd('/') == seasonKey }
                .mapNotNull { info -> info.meta?.episodeNumber?.let { it to info } }
                .toMap()
        }
    }

    LaunchedEffect(source, activeAnime?.url) {
        val featured = withContext(Dispatchers.IO) {
            runCatching { source.featured() }.getOrDefault(emptyList())
        }
        val currentUrl = activeAnime?.url?.trimEnd('/')
        recommendations = featured.filter { it.url.trimEnd('/') != currentUrl }
    }

    fun toggleFollow() {
        val targetAnime = activeAnime ?: return
        val entry = LibraryEntry(
            title = targetAnime.title,
            url = targetAnime.url,
            slug = targetAnime.slug,
            posterUrl = targetAnime.posterUrl,
            languages = seasonsList.mapNotNull { it.language?.uppercase() }.distinct(),
            addedAt = System.currentTimeMillis(),
        )
        inLibrary = Library.toggle(context, entry)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        // Barre supérieure compacte en mode Watch Portrait
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("watch_back_button"),
            ) {
                Icon(
                    imageVector = NovayIcons.Back,
                    contentDescription = "Retour",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activeAnimeTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${activePlayingSeason.name} · ${activePlayingEpisode.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = { playerManager.minimizeToFloating() },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("watch_minimize_button"),
            ) {
                Icon(
                    imageVector = NovayIcons.MinimizePlayer,
                    contentDescription = "Réduire en mini-lecteur",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp)
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                // ── 1. Emplacement du lecteur vidéo portrait ──────────────────
                item(key = "embedded_player_slot", contentType = "video_player") {
                    EmbeddedVideoPlayer(
                        playerManager = playerManager,
                        showSurface = playerManager.presentationMode == PlayerPresentationMode.EMBEDDED_PORTRAIT,
                        videoSurfaceSlot = videoSurfaceSlot,
                        onRetry = { playerManager.retryResolve(source) },
                    )
                }

                // ── 2. Informations de l'animé & actions ──────────────────────
                item(key = "anime_info_header", contentType = "anime_info") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (!activePosterUrl.isNullOrBlank()) {
                                PosterImage(
                                    url = activePosterUrl,
                                    contentDescription = activeAnimeTitle,
                                    modifier = Modifier
                                        .size(width = 52.dp, height = 74.dp)
                                        .then(
                                            if (activeAnime != null) {
                                                Modifier.novaySharedPoster(activeAnime.url)
                                            } else {
                                                Modifier
                                            },
                                        )
                                        .clip(MaterialTheme.shapes.small)
                                        .border(0.5.dp, overlays.glassBorder, MaterialTheme.shapes.small),
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = activePlayingEpisode.label,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = activeAnimeTitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    activePlayingSeason.language?.let { lang ->
                                        LangPill(text = lang, vf = lang.equals("VF", true))
                                    }
                                    if (currentSession?.offline == true) {
                                        NovayBadge(
                                            text = "Hors-ligne",
                                            color = MaterialTheme.colorScheme.tertiary,
                                            textColor = MaterialTheme.colorScheme.onTertiary,
                                        )
                                    }
                                }
                            }
                        }

                        if (activeAnime != null) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutlinedButton(
                                    onClick = ::toggleFollow,
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (inLibrary) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        },
                                    ),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (inLibrary) {
                                            MaterialTheme.colorScheme.secondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerLow
                                        },
                                        contentColor = if (inLibrary) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    ),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("watch_follow_button"),
                                ) {
                                    NovayFavoriteMorphIcon(
                                        inLibrary = inLibrary,
                                        tint = if (inLibrary) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        size = 18.dp,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (inLibrary) "Suivi" else "Suivre")
                                }
                                OutlinedButton(
                                    onClick = { onOpenShow(activeAnime) },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("watch_open_show_button"),
                                ) {
                                    Icon(
                                        imageVector = NovayIcons.SeasonReel,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Fiche animé")
                                }
                            }
                        }
                    }
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }

                // ── 3. Sélection de saison / langue ───────────────────────────
                if (seasonsList.size > 1) {
                    item(key = "season_selector", contentType = "season_chips") {
                        Column(
                            modifier = Modifier.padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "Saisons & Langues",
                                style = MaterialTheme.typography.header,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(seasonsList, key = { it.url }) { s ->
                                    val selected = s.url.trimEnd('/') == browsingSeason.url.trimEnd('/')
                                    val label = buildString {
                                        append(s.name)
                                        s.language?.let { append(" (${it.uppercase()})") }
                                    }
                                    FilterChip(
                                        selected = selected,
                                        onClick = { browsingSeason = s },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                        ),
                                        label = { Text(label, maxLines = 1) },
                                    )
                                }
                            }
                        }
                    }
                }

                // ── 4. Liste d'épisodes ───────────────────────────────────────
                item(key = "episodes_header", contentType = "section_header") {
                    Text(
                        text = "Épisodes · ${browsingSeason.name}",
                        style = MaterialTheme.typography.header,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
                    )
                }

                when {
                    episodesError != null -> item(key = "episodes_error") {
                        ErrorView(
                            message = episodesError ?: "Erreur inconnue",
                            onRetry = { episodeRetry++ },
                            modifier = Modifier.padding(vertical = 20.dp),
                        )
                    }
                    episodes == null -> item(key = "episodes_skeleton") {
                        EpisodesListSkeleton()
                    }
                    else -> items(
                        items = episodes.orEmpty(),
                        key = { "${browsingSeason.url}_${it.number}" },
                        contentType = { "watch_episode_row" },
                    ) { ep ->
                        val isCurrent = browsingSeason.url.trimEnd('/') == activePlayingSeason.url.trimEnd('/') &&
                            ep.number == activePlayingEpisode.number
                        val dlInfo = downloadedByEpisode[ep.number]
                        val resumeMs = resumePositions[ep.number] ?: 0L
                        val itemMod = if (motion.reduceMotion) Modifier else Modifier.animateItem()
                        WatchEpisodeRow(
                            episode = ep,
                            isCurrent = isCurrent,
                            isDownloaded = dlInfo != null,
                            resumeMs = resumeMs,
                            onClick = {
                                val offline = dlInfo?.meta?.toStream()
                                playerManager.openEpisode(
                                    source = source,
                                    season = browsingSeason,
                                    episode = ep,
                                    animeTitle = activeAnimeTitle,
                                    posterUrl = activePosterUrl,
                                    anime = activeAnime,
                                    initialStream = offline,
                                )
                            },
                            modifier = itemMod,
                        )
                    }
                }

                // ── 5. Recommandations ────────────────────────────────────────
                if (recommendations.isNotEmpty()) {
                    item(key = "recommendations_header", contentType = "section_header") {
                        Text(
                            text = "Recommandations",
                            style = MaterialTheme.typography.header,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 10.dp),
                        )
                    }
                    item(key = "recommendations_rail", contentType = "recommendations") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(
                                items = recommendations,
                                key = { "rec_${it.url}" },
                                contentType = { "rec_card" },
                            ) { rec ->
                                val mod = if (motion.reduceMotion) Modifier.width(134.dp) else Modifier
                                    .width(134.dp)
                                    .animateItem()
                                Box(modifier = mod) {
                                    AnimeComfortableGridItem(
                                        title = rec.title,
                                        posterUrl = rec.posterUrl,
                                        animeUrl = rec.url,
                                        onClick = { onOpenShow(rec) },
                                        onLongClick = {},
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

@Composable
private fun WatchEpisodeRow(
    episode: AnimeEpisode,
    isCurrent: Boolean,
    isDownloaded: Boolean,
    resumeMs: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.medium
    Surface(
        shape = shape,
        color = if (isCurrent) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = if (isCurrent) BorderStroke(1.25.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(shape)
            .semantics(mergeDescendants = true) {}
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .testTag("watch_episode_row_${episode.number}"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
            ) {
                Text(
                    text = "EP ${episode.number.toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = episode.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isCurrent) {
                    Text(
                        text = "En cours de lecture",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else if (resumeMs > 0L) {
                    Text(
                        text = "Reprise à ${formatPosition(resumeMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (isDownloaded) {
                Icon(
                    imageVector = NovayIcons.CheckCircle,
                    contentDescription = "Téléchargé hors-ligne",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Icon(
                imageVector = if (isCurrent) NovayIcons.PlayCircle else NovayIcons.Play,
                contentDescription = if (isCurrent) "Lecture en cours" else "Lire l'épisode",
                tint = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
