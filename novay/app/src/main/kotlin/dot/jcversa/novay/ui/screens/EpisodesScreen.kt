package dot.jcversa.novay.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dot.jcversa.novay.R
import dot.jcversa.novay.data.AnimeEpisode
import dot.jcversa.novay.data.AnimeSeason
import dot.jcversa.novay.data.AnimeSource
import dot.jcversa.novay.data.QualityOption
import dot.jcversa.novay.data.ResolvedStream
import dot.jcversa.novay.data.WatchHistory
import dot.jcversa.novay.data.sama.SamaParsers
import dot.jcversa.novay.downloads.Downloads
import dot.jcversa.novay.ui.LocalBottomChromePadding
import dot.jcversa.novay.ui.components.AppBarTitle
import dot.jcversa.novay.ui.components.NovayLottieIcon
import dot.jcversa.novay.ui.components.EmptyScreen
import dot.jcversa.novay.ui.components.EpisodesListSkeleton
import dot.jcversa.novay.ui.components.ErrorView
import dot.jcversa.novay.ui.components.LangPill
import dot.jcversa.novay.ui.components.LocalNovayToast
import dot.jcversa.novay.ui.components.LocalSlateMotion
import dot.jcversa.novay.ui.components.NovayAppBar
import dot.jcversa.novay.ui.components.NovayIcons
import dot.jcversa.novay.ui.components.NovayToastTone
import dot.jcversa.novay.ui.components.OfflineStateView
import dot.jcversa.novay.ui.components.Pill
import dot.jcversa.novay.ui.components.SlateMotion
import dot.jcversa.novay.ui.components.SwiftSlateSpinner
import dot.jcversa.novay.ui.components.bounceClick
import dot.jcversa.novay.ui.components.isDeviceOnline
import dot.jcversa.novay.ui.components.novayCinemaGlass
import dot.jcversa.novay.ui.theme.NovayTheme
import dot.jcversa.novay.ui.theme.header
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class EpDl { NONE, RESOLVING, QUEUED, DOWNLOADING, DONE, FAILED }

private data class QualityDialogState(
    val episodes: List<AnimeEpisode>,
    val options: List<QualityOption>,
    val streamsByEpisode: Map<Int, List<ResolvedStream>>
)

/**
 * Écran Épisodes (téléphone) — encadre EpisodesPane avec NovayAppBar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodesScreen(
    source: AnimeSource,
    animeTitle: String,
    season: AnimeSeason,
    onBack: () -> Unit,
    onOpenPlayer: (episode: AnimeEpisode, offlineStream: ResolvedStream?) -> Unit
) {
    var seasonTotal by remember(season.url) { mutableStateOf<Int?>(null) }
    var doneCount by remember(season.url) { mutableIntStateOf(0) }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
    ) {
        NovayAppBar(
            titleContent = {
                AppBarTitle(title = season.name)
                if (doneCount > 0) {
                    Pill(
                        text = "$doneCount/${seasonTotal ?: doneCount}",
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            },
            navigateUp = onBack,
            scrollBehavior = scrollBehavior,
        )

        EpisodesPane(
            source = source,
            animeTitle = animeTitle,
            season = season,
            onOpenPlayer = onOpenPlayer,
            onLoadedCount = { seasonTotal = it },
            onDoneCountChange = { doneCount = it },
            showSeasonHeader = false,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
    }
}

/**
 * Panneau Épisodes réutilisable (plein écran sur téléphone, panneau droit sur
 * tablette >= 840dp). Conserve le sélecteur explicite de résolution.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EpisodesPane(
    source: AnimeSource,
    animeTitle: String,
    season: AnimeSeason,
    onOpenPlayer: (episode: AnimeEpisode, offlineStream: ResolvedStream?) -> Unit,
    modifier: Modifier = Modifier,
    showSeasonHeader: Boolean = false,
    onLoadedCount: (Int) -> Unit = {},
    onDoneCountChange: (Int) -> Unit = {},
) {
    val context = LocalContext.current
    val bottomChrome = LocalBottomChromePadding.current
    var episodes by remember(season.url) { mutableStateOf<List<AnimeEpisode>?>(null) }
    var error by remember(season.url) { mutableStateOf<String?>(null) }
    var retryKey by remember(season.url) { mutableIntStateOf(0) }
    var toast by remember { mutableStateOf<String?>(null) }

    var infos by remember { mutableStateOf<List<Downloads.DownloadInfo>>(emptyList()) }
    var resolving by remember { mutableStateOf<Int?>(null) }
    var resolvingBatch by remember { mutableStateOf(false) }
    var enqueueLabel by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    var positions by remember(season.url) { mutableStateOf<Map<Int, Long>>(emptyMap()) }
    var durations by remember(season.url) { mutableStateOf<Map<Int, Long>>(emptyMap()) }
    var activeResumeEpisode by remember(season.url) { mutableStateOf<Int?>(null) }
    var selectionMode by remember(season.url) { mutableStateOf(false) }
    val selected = remember(season.url) { mutableStateListOf<Int>() }
    var dialog by remember { mutableStateOf<QualityDialogState?>(null) }

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            toast = context.getString(R.string.permission_notifications_denied_notice)
        }
    }

    val storagePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            toast = context.getString(R.string.permission_storage_denied_notice)
        }
    }

    LaunchedEffect(season.url, retryKey) {
        error = null
        episodes = null
        try {
            val loaded = source.episodes(season)
            episodes = loaded
            onLoadedCount(loaded.size)
        } catch (e: Exception) {
            error = e.message ?: "Erreur inconnue"
        }
    }

    LaunchedEffect(season.url) {
        val seasonEntries = withContext(Dispatchers.IO) {
            WatchHistory.recent(context)
                .filter { it.seasonUrl.trimEnd('/') == season.url.trimEnd('/') }
        }
        positions = seasonEntries.associate { it.episodeNumber to it.positionMs }
        durations = seasonEntries.associate { it.episodeNumber to it.durationMs }
        activeResumeEpisode = seasonEntries.firstOrNull()?.episodeNumber
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(4000)
            toast = null
        }
    }

    LaunchedEffect(context) {
        Downloads.observeSnapshots(context).collect { infos = it }
    }

    val motion = LocalSlateMotion.current
    val overlays = NovayTheme.overlays
    val toastController = LocalNovayToast.current

    BackHandler(enabled = selectionMode) {
        selectionMode = false
        selected.clear()
    }
    val seasonUrl = season.url.trimEnd('/')
    val seasonInfos = remember(infos, seasonUrl) {
        infos.filter { it.meta?.seasonUrl?.trimEnd('/') == seasonUrl }
    }
    val infoByEpisode = remember(seasonInfos) {
        seasonInfos.associateBy { it.meta?.episodeNumber }
    }

    fun infoFor(episode: AnimeEpisode): Downloads.DownloadInfo? =
        infoByEpisode[episode.number]

    fun ensurePermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Android 7-9 : vrais fichiers dans Download/NoVaY/ -> permission
        // de stockage demandee au premier telechargement.
        if (Build.VERSION.SDK_INT <= 28 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    fun download(episode: AnimeEpisode) {
        if (resolving != null || resolvingBatch) return
        ensurePermission()
        scope.launch {
            resolving = episode.number
            try {
                val streams = source.streams(episode)
                val options = SamaParsers.mergePlayerQualities(streams)
                dialog = QualityDialogState(
                    episodes = listOf(episode),
                    options = options,
                    streamsByEpisode = mapOf(episode.number to streams)
                )
            } catch (e: Exception) {
                toast = e.message ?: "Résolution du flux impossible"
            } finally {
                resolving = null
            }
        }
    }

    fun downloadSelected() {
        val eps = episodes?.filter { selected.contains(it.number) } ?: return
        if (eps.isEmpty() || resolvingBatch || resolving != null) return
        ensurePermission()
        scope.launch {
            resolvingBatch = true
            try {
                val streams = source.streams(eps.first())
                val options = SamaParsers.mergePlayerQualities(streams)
                dialog = QualityDialogState(
                    episodes = eps,
                    options = options,
                    streamsByEpisode = mapOf(eps.first().number to streams)
                )
            } catch (e: Exception) {
                toast = e.message ?: "Résolution du flux impossible"
            } finally {
                resolvingBatch = false
            }
        }
    }

    fun confirm(option: QualityOption) {
        val state = dialog ?: return
        dialog = null
        selectionMode = false
        selected.clear()
        scope.launch {
            state.episodes.forEachIndexed { index, episode ->
                enqueueLabel = "${index + 1}/${state.episodes.size}"
                val streams = state.streamsByEpisode[episode.number]
                    ?: runCatching { source.streams(episode) }.getOrDefault(emptyList())
                val stream = streams.getOrNull(option.playerIndex) ?: streams.firstOrNull()
                if (stream == null) {
                    toast = "Épisode ${episode.number} : aucun flux disponible"
                } else {
                    Downloads.startEpisodeDownload(
                        context = context,
                        animeTitle = animeTitle,
                        seasonName = season.name,
                        seasonUrl = season.url,
                        episodeNumber = episode.number,
                        episodeLabel = episode.label,
                        stream = stream,
                        preferredHeight = option.height,
                        onError = { msg -> toast = msg }
                    )
                }
            }
            enqueueLabel = null
            if (state.episodes.isNotEmpty()) {
                val msg = if (state.episodes.size == 1) {
                    "Téléchargement ajouté à la file"
                } else {
                    "${state.episodes.size} épisodes ajoutés à la file"
                }
                toastController.show(msg, NovayToastTone.SUCCESS)
            }
        }
    }

    val done = remember(seasonInfos) { seasonInfos.count { it.isCompleted } }
    LaunchedEffect(done) { onDoneCountChange(done) }

    Column(modifier = modifier) {
        if (showSeasonHeader) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = season.name,
                    style = MaterialTheme.typography.header,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                season.language?.let { lang ->
                    LangPill(text = lang, vf = lang.equals("VF", true))
                }
                if (done > 0) {
                    Pill(
                        text = "$done/${episodes?.size ?: done}",
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }

        toast?.let { message ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = NovayIcons.ErrorSeal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { toast = null },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("episodes_toast_dismiss"),
                    ) {
                        Icon(
                            imageVector = NovayIcons.Close,
                            contentDescription = stringResource(R.string.action_dismiss),
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
        if (resolvingBatch) {
            Text(
                text = "Analyse des lecteurs disponibles…",
                style = MaterialTheme.typography.labelMedium,
                color = overlays.crimsonOnContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        enqueueLabel?.let {
            Text(
                text = "Mise en file $it",
                style = MaterialTheme.typography.labelMedium,
                color = overlays.crimsonOnContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        val episodeList = episodes
        val viewKey = when {
            error != null -> "error"
            episodeList == null -> "loading"
            episodeList.isEmpty() -> "empty"
            else -> "list"
        }

        AnimatedContent(
            targetState = viewKey,
            transitionSpec = { motion.stateCrossfade() },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            label = "episodes_body_state",
        ) { stateKey ->
            when (stateKey) {
                "error" -> if (!isDeviceOnline(context)) {
                    OfflineStateView(
                        onRetry = { retryKey++ },
                        modifier = Modifier.padding(bottom = bottomChrome),
                    )
                } else {
                    ErrorView(
                        message = error ?: "Erreur inconnue",
                        onRetry = { retryKey++ },
                        title = stringResource(R.string.error_episodes_title),
                        icon = NovayIcons.ErrorSeal,
                        modifier = Modifier.padding(bottom = bottomChrome),
                    )
                }
                "loading" -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    EpisodesListSkeleton()
                }
                "empty" -> EmptyScreen(
                    icon = NovayIcons.ClapperFilm,
                    lottieRes = R.raw.lottie_film,
                    headingText = "Aucun épisode",
                    subtitleText = "Aucun épisode n'est encore disponible pour cette saison.",
                    modifier = Modifier.padding(bottom = bottomChrome),
                )
                else -> {
                    val loadedEpisodes = episodes.orEmpty()
                    val watchedCount = remember(loadedEpisodes, positions, durations) {
                        loadedEpisodes.count { ep ->
                            val r = positions[ep.number] ?: 0L
                            val d = durations[ep.number] ?: 0L
                            d > 0L && r.toFloat() / d.toFloat() >= 0.85f
                        }
                    }
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 840.dp),
                            contentPadding = PaddingValues(
                                top = 8.dp,
                                bottom = 28.dp + bottomChrome,
                            ),
                        ) {
                            if (loadedEpisodes.isNotEmpty()) {
                                item(key = "timeline_summary", contentType = "timeline_summary") {
                                    EpisodeTimelineSummary(
                                        totalCount = loadedEpisodes.size,
                                        watchedCount = watchedCount,
                                        downloadedCount = done,
                                        activeEpisodeNumber = activeResumeEpisode,
                                    )
                                }
                            }
                            itemsIndexed(
                                items = loadedEpisodes,
                                key = { _, ep -> ep.number },
                                contentType = { _, _ -> "episode_row" },
                            ) { index, episode ->
                                val info = infoFor(episode)
                                val state = when {
                                    resolving == episode.number -> EpDl.RESOLVING
                                    info == null -> EpDl.NONE
                                    info.isCompleted -> EpDl.DONE
                                    info.isFailed -> EpDl.FAILED
                                    info.isActive -> EpDl.DOWNLOADING
                                    else -> EpDl.QUEUED
                                }
                                EpisodeRow(
                                    modifier = if (motion.reduceMotion) Modifier else Modifier.animateItem(),
                                    episode = episode,
                                    dlState = state,
                                    dlPercent = info?.percent ?: 0f,
                                    resumeMs = positions[episode.number] ?: 0L,
                                    durationMs = durations[episode.number] ?: 0L,
                                    isActiveResume = activeResumeEpisode == episode.number,
                                    isFirstInTimeline = index == 0,
                                    isLastInTimeline = index == loadedEpisodes.lastIndex,
                                    selectionMode = selectionMode,
                                    isSelected = selected.contains(episode.number),
                                    onDownload = { download(episode) },
                                    onClick = {
                                        if (selectionMode) {
                                            if (selected.contains(episode.number)) {
                                                selected.remove(episode.number)
                                            } else {
                                                selected.add(episode.number)
                                            }
                                        } else {
                                            val offline = info?.takeIf { it.isCompleted }?.meta?.toStream()
                                            onOpenPlayer(episode, offline)
                                        }
                                    },
                                    onLongClick = {
                                        if (!selectionMode) {
                                            selectionMode = true
                                            selected.clear()
                                        }
                                        if (!selected.contains(episode.number)) {
                                            selected.add(episode.number)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = selectionMode,
            enter = if (motion.reduceMotion) {
                EnterTransition.None
            } else {
                slideInVertically(tween(220, easing = SlateMotion.EaseOutExpo)) { it } +
                    fadeIn(tween(180, easing = SlateMotion.EaseOutExpo))
            },
            exit = if (motion.reduceMotion) {
                ExitTransition.None
            } else {
                slideOutVertically(tween(140, easing = SlateMotion.FastOutExit)) { it } +
                    fadeOut(tween(120, easing = SlateMotion.FastOutExit))
            },
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = bottomChrome),
            ) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (bottomChrome == 0.dp) Modifier.navigationBarsPadding() else Modifier)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {
                                selected.clear()
                                episodes?.forEach { selected.add(it.number) }
                            },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) { Text("Tout") }
                        Spacer(Modifier.width(6.dp))
                        Button(
                            enabled = selected.isNotEmpty(),
                            onClick = { downloadSelected() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = overlays.ctaContainer,
                                contentColor = overlays.onCtaContainer,
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("episodes_batch_download_button"),
                        ) {
                            Text("Télécharger (${selected.size})")
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = {
                                selectionMode = false
                                selected.clear()
                            },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) { Text("Fermer") }
                    }
                }
            }
        }
    }

    dialog?.let { state ->
        AlertDialog(
            onDismissRequest = { dialog = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = {
                Text(
                    text = "Choisir la résolution",
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = if (state.episodes.size == 1) {
                            "Sélectionne la qualité à télécharger pour cet épisode :"
                        } else {
                            "${state.episodes.size} épisodes — sélectionne la qualité du lot :"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    val options = state.options.ifEmpty {
                        listOf(QualityOption(height = 0, label = "Meilleure disponible", playerIndex = 0))
                    }
                    options.forEach { option ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .bounceClick(onClick = { confirm(option) })
                                .minimumInteractiveComponentSize(),
                        ) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            },
            confirmButton = { },
            dismissButton = {
                TextButton(
                    onClick = { dialog = null },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) { Text("Annuler") }
            }
        )
    }
}

@Composable
private fun EpisodeTimelineSummary(
    totalCount: Int,
    watchedCount: Int,
    downloadedCount: Int,
    activeEpisodeNumber: Int?,
) {
    val overlays = NovayTheme.overlays
    val progress = if (totalCount > 0) (watchedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f) else 0f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .novayCinemaGlass(
                shape = MaterialTheme.shapes.medium,
                tint = MaterialTheme.colorScheme.surfaceContainerLow,
                borderColor = overlays.glassBorder,
                showBorder = true,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("episodes_timeline_summary"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "$watchedCount / $totalCount épisode${if (totalCount > 1) "s" else ""} vus",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (activeEpisodeNumber != null) {
                Pill(
                    text = "Ep. $activeEpisodeNumber",
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = overlays.crimsonOnContainer,
                )
            }
            if (downloadedCount > 0) {
                Pill(
                    text = "$downloadedCount DL",
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeRow(
    episode: AnimeEpisode,
    dlState: EpDl,
    dlPercent: Float,
    resumeMs: Long,
    durationMs: Long,
    isActiveResume: Boolean,
    isFirstInTimeline: Boolean,
    isLastInTimeline: Boolean,
    selectionMode: Boolean,
    isSelected: Boolean,
    onDownload: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.medium
    val motion = LocalSlateMotion.current
    val overlays = NovayTheme.overlays
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val containerColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            isActiveResume -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.56f)
            hovered -> MaterialTheme.colorScheme.surfaceContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = motion.colorSpec(180),
        label = "episode_row_bg",
    )
    val rowBorder = when {
        isSelected -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        isActiveResume -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
        else -> null
    }
    val resumeProgress = if (resumeMs > 0L && durationMs > 0L) {
        (resumeMs.toFloat() / durationMs.toFloat()).coerceIn(0.02f, 1f)
    } else {
        null
    }
    val isWatched = resumeProgress != null && resumeProgress >= 0.85f
    val lineColor = when {
        isActiveResume -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        isWatched -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.50f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    }
    val nodeColor = when {
        isActiveResume -> MaterialTheme.colorScheme.primary
        isWatched -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(start = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Rail chronologique discret (Vu / En cours / À voir)
        Column(
            modifier = Modifier
                .width(18.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .weight(1f)
                    .background(if (isFirstInTimeline) androidx.compose.ui.graphics.Color.Transparent else lineColor),
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(if (isActiveResume) 14.dp else 10.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActiveResume) {
                            nodeColor.copy(alpha = 0.24f)
                        } else if (isWatched) {
                            nodeColor
                        } else {
                            MaterialTheme.colorScheme.background
                        },
                    )
                    .then(
                        if (!isWatched) {
                            Modifier.border(
                                width = if (isActiveResume) 2.dp else 1.5.dp,
                                color = nodeColor,
                                shape = CircleShape,
                            )
                        } else {
                            Modifier
                        },
                    ),
            ) {
                if (isActiveResume) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(nodeColor),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .weight(1f)
                    .background(if (isLastInTimeline) androidx.compose.ui.graphics.Color.Transparent else lineColor),
            )
        }

        Spacer(Modifier.width(8.dp))

        Surface(
            shape = shape,
            color = containerColor,
            border = rowBorder,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp)
                .clip(shape)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClickLabel = if (selectionMode) "Sélectionner l'épisode ${episode.number}" else "Lire ${episode.label}",
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .minimumInteractiveComponentSize(),
        ) {
            Column {
                Row(
                    modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 6.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selectionMode) {
                        NovayLottieIcon(
                            res = R.raw.lottie_checkbox,
                            contentDescription = if (isSelected) "Sélectionné" else "Non sélectionné",
                            tint = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            size = 22.dp,
                            restartKey = isSelected,
                            modifier = Modifier.padding(end = 10.dp),
                        )
                    }
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = if (isActiveResume) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    ) {
                        Text(
                            text = "EP ${episode.number.toString().padStart(2, '0')}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isActiveResume) {
                                overlays.crimsonOnContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = episode.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (isActiveResume) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                                    modifier = Modifier.testTag("episode_active_resume_badge"),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(
                                            imageVector = NovayIcons.Play,
                                            contentDescription = null,
                                            tint = overlays.crimsonOnContainer,
                                            modifier = Modifier.size(10.dp),
                                        )
                                        Text(
                                            text = stringResource(R.string.episode_active_badge),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = overlays.crimsonOnContainer,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            } else if (isWatched) {
                                Pill(
                                    text = stringResource(R.string.episode_watched_badge),
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                        }
                        if (resumeMs > 0L) {
                            Text(
                                text = "Reprise à ${formatPosition(resumeMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = overlays.crimsonOnContainer,
                                maxLines = 1,
                            )
                        }
                    }
                    EpisodeDownloadIndicator(
                        state = dlState,
                        percent = dlPercent,
                        onDownload = onDownload,
                    )
                }
                if (resumeProgress != null) {
                    LinearProgressIndicator(
                        progress = { resumeProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = if (isWatched) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeDownloadIndicator(
    state: EpDl,
    percent: Float,
    onDownload: () -> Unit,
) {
    when (state) {
        EpDl.RESOLVING -> Box(
            modifier = Modifier.minimumInteractiveComponentSize(),
            contentAlignment = Alignment.Center,
        ) {
            SwiftSlateSpinner(size = 20.dp)
        }
        EpDl.QUEUED -> IconButton(
            onClick = onDownload,
            modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
            NovayLottieIcon(
                res = R.raw.lottie_clock,
                contentDescription = "En file d'attente",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                size = 22.dp,
            )
        }
        EpDl.DOWNLOADING -> Box(
            modifier = Modifier.minimumInteractiveComponentSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    progress = { (percent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "${percent.toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        EpDl.DONE -> Box(
            modifier = Modifier.minimumInteractiveComponentSize(),
            contentAlignment = Alignment.Center,
        ) {
            NovayLottieIcon(
                res = R.raw.lottie_checkcircle,
                contentDescription = "Téléchargé — lecture hors-ligne",
                tint = MaterialTheme.colorScheme.tertiary,
                size = 22.dp,
            )
        }
        EpDl.FAILED -> IconButton(
            onClick = onDownload,
            modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
            NovayLottieIcon(
                res = R.raw.lottie_alertcircle,
                contentDescription = "Échec — réessayer le téléchargement",
                tint = MaterialTheme.colorScheme.error,
                size = 22.dp,
            )
        }
        EpDl.NONE -> IconButton(
            onClick = onDownload,
            modifier = Modifier
                .clip(CircleShape)
                .minimumInteractiveComponentSize()
                .testTag("episode_download_button"),
        ) {
            NovayLottieIcon(
                res = R.raw.lottie_download,
                contentDescription = "Télécharger l'épisode",
                tint = MaterialTheme.colorScheme.primary,
                size = 22.dp,
            )
        }
    }
}

internal fun formatPosition(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(java.util.Locale.FRENCH, minutes, seconds)
}
