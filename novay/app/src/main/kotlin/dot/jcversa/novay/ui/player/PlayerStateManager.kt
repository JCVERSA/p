package dot.jcversa.novay.ui.player

import android.content.Context
import android.media.MediaCodec
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import dot.jcversa.novay.performance.ExoPlayerBufferProfile
import dot.jcversa.novay.performance.NovayPerformanceState
import dot.jcversa.novay.R
import dot.jcversa.novay.data.AnimeEpisode
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.AnimeSeason
import dot.jcversa.novay.data.AnimeSource
import dot.jcversa.novay.data.ResolvedStream
import dot.jcversa.novay.data.WatchHistory
import dot.jcversa.novay.data.WatchHistoryEntry
import dot.jcversa.novay.data.voiranime.VoiranimeParsers
import dot.jcversa.novay.downloads.Downloads
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class PlayerTrackOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val language: String?,
    val selected: Boolean,
    internal val trackGroup: TrackGroup,
)

@Immutable
data class PlayerSession(
    val anime: AnimeSearchResult?,
    val animeTitle: String,
    val posterUrl: String?,
    val season: AnimeSeason,
    val episode: AnimeEpisode,
    val offline: Boolean,
    val initialStream: ResolvedStream?,
) {
    val key: String
        get() = "${season.url.trimEnd('/')}#${episode.number}"
}

/**
 * Gestionnaire central et unique du lecteur Media3 (`ExoPlayer`) pour toute
 * l'application :
 *  - garantit UN seul moteur `ExoPlayer` partagé entre le lecteur portrait
 *    intégré, le mini-lecteur flottant et le plein écran paysage ;
 *  - sépare l'état de lecture de l'état de présentation ;
 *  - persiste la progression dans [WatchHistory] sans interruption ni fuite.
 */
@Stable
@OptIn(UnstableApi::class)
class PlayerStateManager(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext

    var session by mutableStateOf<PlayerSession?>(null)
        private set

    var stream by mutableStateOf<ResolvedStream?>(null)
        private set

    var isResolvingStream by mutableStateOf(false)
        private set

    var streamError by mutableStateOf<String?>(null)
        private set

    var selectedQualityUrl by mutableStateOf<String?>(null)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    var isBuffering by mutableStateOf(false)
        private set

    var positionMs by mutableLongStateOf(0L)
        private set

    var durationMs by mutableLongStateOf(0L)
        private set

    var playbackSpeed by mutableFloatStateOf(1f)
        private set

    var isFastForwardHold by mutableStateOf(false)
        private set

    var videoAspectRatio by mutableFloatStateOf(MiniPlayerGeometry.DEFAULT_ASPECT_RATIO)
        private set

    @androidx.annotation.VisibleForTesting
    internal fun setVideoAspectRatioForTest(rawRatio: Float) {
        videoAspectRatio = MiniPlayerGeometry.normalizeVideoAspectRatio(rawRatio)
    }

    var audioTracks by mutableStateOf<List<PlayerTrackOption>>(emptyList())
        private set

    var subtitleTracks by mutableStateOf<List<PlayerTrackOption>>(emptyList())
        private set

    var subtitlesDisabled by mutableStateOf(false)
        private set

    var isWatchScreenVisible by mutableStateOf(false)
        private set

    var inlineVisibleFraction by mutableFloatStateOf(1f)
        private set

    var isFullscreenRequested by mutableStateOf(false)
        private set

    var presentationMode by mutableStateOf(PlayerPresentationMode.HIDDEN)
        private set

    var scrollToTopSignal by mutableIntStateOf(0)
        private set

    var userSeeking by mutableStateOf(false)
        private set

    val hasActiveSession: Boolean by derivedStateOf {
        session != null && presentationMode != PlayerPresentationMode.HIDDEN
    }

    private var exoPlayerInstance: ExoPlayer? = null

    // V2.9.6 (essai owner) : UN SEUL moteur — ExoPlayer pour le streaming
    // ET pour les fichiers téléchargés (libVLC retiré : ~-93 Mo d'APK).
    // Seek TS approximatif via constant-bitrate seeking (cf.
    // Downloads.offlineMediaSource). Retour arrière possible en un commit.
    private var engineHost: FrameLayout? = null
    private var exoPlayerViewHost: PlayerView? = null

    private var lastLoadedSignature: String? = null
    private var loadJob: Job? = null
    private var recoveryJob: Job? = null
    private var progressJob: Job? = null
    private var resumeSeekApplied = false
    private var lastSavedAtMs = 0L
    private var codecRecoveryAttempts = 0
    private var backgroundResumePositionMs = 0L
    private var suspendedInBackground = false
    private var activeWatchToken: Any? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
            if (playing) {
                codecRecoveryAttempts = 0
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            isBuffering = playbackState == Player.STATE_BUFFERING
            val player = exoPlayerInstance ?: return
            if (playbackState == Player.STATE_READY) {
                durationMs = player.duration.coerceAtLeast(0L)
                codecRecoveryAttempts = 0
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            isBuffering = false
            val activeSession = session
            val activeStream = stream
            if (activeSession != null &&
                activeStream != null &&
                codecRecoveryAttempts < MAX_CODEC_RECOVERY_ATTEMPTS &&
                isCodecReclaimOrDecoderError(error)
            ) {
                attemptCodecRecovery(activeSession, activeStream)
                return
            }
            streamError = error.localizedMessage ?: error.message ?: "Erreur de lecture vidéo"
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                val rawRatio = (videoSize.width * videoSize.pixelWidthHeightRatio) / videoSize.height.toFloat()
                videoAspectRatio = MiniPlayerGeometry.normalizeVideoAspectRatio(rawRatio)
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            val audio = ArrayList<PlayerTrackOption>()
            val subs = ArrayList<PlayerTrackOption>()
            tracks.groups.forEachIndexed { groupIdx, group ->
                val type = group.type
                if (type != C.TRACK_TYPE_AUDIO && type != C.TRACK_TYPE_TEXT) return@forEachIndexed
                for (trackIdx in 0 until group.length) {
                    if (!group.isTrackSupported(trackIdx)) continue
                    val fmt = group.getTrackFormat(trackIdx)
                    val lang = fmt.language?.takeIf { it.isNotBlank() && it != "und" }
                    val readableLang = lang?.let {
                        Locale.forLanguageTag(it).getDisplayLanguage(Locale.FRENCH)
                            .replaceFirstChar { ch -> ch.uppercase() }
                    }
                    val label = fmt.label?.takeIf { it.isNotBlank() }
                        ?: readableLang
                        ?: if (type == C.TRACK_TYPE_AUDIO) "Audio ${audio.size + 1}" else "Sous-titres ${subs.size + 1}"
                    val option = PlayerTrackOption(
                        groupIndex = groupIdx,
                        trackIndex = trackIdx,
                        label = label,
                        language = lang,
                        selected = group.isTrackSelected(trackIdx),
                        trackGroup = group.mediaTrackGroup,
                    )
                    if (type == C.TRACK_TYPE_AUDIO) audio.add(option) else subs.add(option)
                }
            }
            audioTracks = audio
            subtitleTracks = subs
            val player = exoPlayerInstance
            subtitlesDisabled = player?.trackSelectionParameters?.disabledTrackTypes
                ?.contains(C.TRACK_TYPE_TEXT) == true ||
                (subs.isNotEmpty() && subs.none { it.selected })
        }
    }

    fun getOrCreatePlayer(): ExoPlayer {
        exoPlayerInstance?.let { return it }
        NovayPerformanceState.ensureInitialized(appContext)
        val bufferProfile = NovayPerformanceState.currentProfile.exoBufferProfile
        val renderersFactory = DefaultRenderersFactory(appContext)
            .setEnableDecoderFallback(true)
        return ExoPlayer.Builder(appContext, renderersFactory)
            .setLoadControl(buildLoadControl(bufferProfile))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also { player ->
                player.addListener(playerListener)
                exoPlayerInstance = player
                // La PlayerView hebergee reference peut-etre une instance
                // precedemment liberee (re-ouverture apres fermeture).
                exoPlayerViewHost?.player = player
                ensureProgressLoopStarted()
            }
    }

    /**
     * Vue vidéo du lecteur : un conteneur neutre qui héberge le PlayerView
     * ExoPlayer — streaming comme fichiers téléchargés (moteur unique
     * depuis V2.9.6).
     */
    fun createSinglePlayerView(context: Context): View {
        val container = FrameLayout(context)
        container.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        refreshEngineViewChild(container)
        engineHost = container
        return container
    }

    private fun refreshEngineViewChild(container: FrameLayout) {
        // Le PlayerView retiré ne doit pas garder une référence à un
        // ExoPlayer libéré.
        exoPlayerViewHost?.player = null
        container.removeAllViews()
        exoPlayerViewHost = null
        val playerView = createExoPlayerView(container.context)
        exoPlayerViewHost = playerView
        container.addView(playerView)
    }

    /**
     * L'hote Compose de la vue unique quitte la composition : on oublie
     * les references de vues (le prochain factory recree le conteneur).
     */
    fun onSingleViewHostReleased() {
        engineHost = null
        exoPlayerViewHost = null
    }

    private fun createExoPlayerView(context: Context): PlayerView {
        val inflated = LayoutInflater.from(context)
            .inflate(R.layout.novay_player_view, FrameLayout(context), false) as PlayerView
        inflated.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        inflated.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        inflated.setEnableComposeSurfaceSyncWorkaround(true)
        inflated.player = getOrCreatePlayer()
        inflated.useController = false
        return inflated
    }

    fun openEpisode(
        source: AnimeSource,
        season: AnimeSeason,
        episode: AnimeEpisode,
        animeTitle: String,
        posterUrl: String?,
        anime: AnimeSearchResult? = null,
        initialStream: ResolvedStream? = null,
    ) {
        val nextKey = "${season.url.trimEnd('/')}#${episode.number}"
        val currentSession = session
        if (currentSession != null &&
            currentSession.key == nextKey &&
            streamError == null &&
            (stream != null || isResolvingStream)
        ) {
            if (anime != null && currentSession.anime == null) {
                session = currentSession.copy(anime = anime)
            }
            isWatchScreenVisible = true
            inlineVisibleFraction = 1f
            recomputePresentationMode(forceInlineIfWatchVisible = true)
            return
        }

        flushProgressNow()
        loadJob?.cancel()
        recoveryJob?.cancel()
        codecRecoveryAttempts = 0
        suspendedInBackground = false

        val nextSession = PlayerSession(
            anime = anime,
            animeTitle = animeTitle,
            posterUrl = posterUrl,
            season = season,
            episode = episode,
            offline = initialStream != null,
            initialStream = initialStream,
        )
        session = nextSession
        stream = initialStream
        streamError = null
        selectedQualityUrl = null
        audioTracks = emptyList()
        subtitleTracks = emptyList()
        subtitlesDisabled = false
        videoAspectRatio = MiniPlayerGeometry.DEFAULT_ASPECT_RATIO
        positionMs = 0L
        durationMs = 0L
        resumeSeekApplied = false
        lastLoadedSignature = null
        isWatchScreenVisible = true
        inlineVisibleFraction = 1f
        presentationMode = if (isFullscreenRequested) {
            PlayerPresentationMode.FULLSCREEN_LANDSCAPE
        } else {
            PlayerPresentationMode.EMBEDDED_PORTRAIT
        }

        loadJob = scope.launch {
            val isOffline = if (initialStream != null) {
                true
            } else {
                withContext(Dispatchers.IO) {
                    runCatching {
                        Downloads.isDownloaded(appContext, season.url, episode.number)
                    }.getOrDefault(false)
                }
            }
            val resumePos = withContext(Dispatchers.IO) {
                WatchHistory.positionOf(appContext, season.url, episode.number)
            }
            if (session?.key != nextKey) return@launch
            session = nextSession.copy(offline = isOffline)

            val resolved: ResolvedStream = if (initialStream != null) {
                isResolvingStream = false
                initialStream
            } else {
                isResolvingStream = true
                try {
                    // V2.6 : épisode téléchargé -> LE FICHIER publié dans
                    // Download/NoVaY/ d'abord (aucun réseau) ; la résolution
                    // en ligne n'est qu'un repli (fichier supprimé, etc.).
                    if (isOffline) {
                        withContext(Dispatchers.IO) {
                            Downloads.downloadedStream(appContext, season.url, episode.number)
                        } ?: source.stream(episode)
                    } else {
                        withContext(Dispatchers.IO) { source.stream(episode) }
                    }
                } catch (e: Exception) {
                    if (session?.key == nextKey) {
                        streamError = e.message ?: "Erreur inconnue"
                        isResolvingStream = false
                    }
                    return@launch
                }
            }

            if (session?.key != nextKey) return@launch
            stream = resolved
            isResolvingStream = false

            withContext(Dispatchers.IO) {
                WatchHistory.record(
                    appContext,
                    WatchHistoryEntry(
                        animeTitle = animeTitle.ifEmpty { season.name },
                        animeUrl = anime?.url ?: episode.url,
                        posterUrl = posterUrl,
                        seasonName = season.name,
                        seasonUrl = season.url,
                        episodeNumber = episode.number,
                        episodeLabel = episode.label,
                        lienIds = episode.lienIds,
                        jeton = episode.jeton,
                        watchedAt = System.currentTimeMillis(),
                    ),
                )
            }

            preparePlayerWithStream(
                resolvedStream = resolved,
                offline = isOffline,
                qualityUrl = null,
                initialSeekMs = resumePos,
            )
        }
    }

    fun retryResolve(source: AnimeSource) {
        val current = session ?: return
        lastLoadedSignature = null
        streamError = null
        codecRecoveryAttempts = 0
        suspendedInBackground = false
        recoveryJob?.cancel()
        loadJob?.cancel()
        if (exoPlayerInstance?.playerError != null) {
            releasePlayerOnly()
        }
        val key = current.key
        loadJob = scope.launch {
            isResolvingStream = true
            val resolved = try {
                withContext(Dispatchers.IO) { source.stream(current.episode) }
            } catch (e: Exception) {
                if (session?.key == key) {
                    streamError = e.message ?: "Erreur inconnue"
                    isResolvingStream = false
                }
                return@launch
            }
            if (session?.key != key) return@launch
            stream = resolved
            isResolvingStream = false
            val resumePos = withContext(Dispatchers.IO) {
                WatchHistory.positionOf(appContext, current.season.url, current.episode.number)
            }
            preparePlayerWithStream(
                resolvedStream = resolved,
                offline = current.offline,
                qualityUrl = selectedQualityUrl,
                initialSeekMs = resumePos,
            )
        }
    }

    fun selectQuality(qualityUrl: String?) {
        val currentSession = session ?: return
        val currentStream = stream ?: return
        if (selectedQualityUrl == qualityUrl) return
        selectedQualityUrl = qualityUrl
        val keepPositionMs = currentPlaybackPositionMs()
        preparePlayerWithStream(
            resolvedStream = currentStream,
            offline = currentSession.offline,
            qualityUrl = qualityUrl,
            initialSeekMs = keepPositionMs,
        )
    }

    private fun preparePlayerWithStream(
        resolvedStream: ResolvedStream,
        offline: Boolean,
        qualityUrl: String?,
        initialSeekMs: Long,
        autoPlay: Boolean = true,
    ) {
        val effectiveUrl = qualityUrl ?: resolvedStream.url
        // V2.9.6 (essai owner) : UN SEUL moteur — les fichiers téléchargés
        // passent AUSSI par ExoPlayer (libVLC retiré : ~-93 Mo d'APK). Le
        // .ts local bénéficie du constant-bitrate seeking : seek approximatif
        // mais rapide (même comportement que le streaming HLS).
        val player = getOrCreatePlayer()
        val signature = "${session?.key}|$offline|$effectiveUrl"
        if (signature == lastLoadedSignature) return
        lastLoadedSignature = signature

        val mediaSource = if (offline) {
            Downloads.offlineMediaSource(appContext, resolvedStream)
        } else {
            val httpFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(resolvedStream.headers["User-Agent"] ?: VoiranimeParsers.UA)
                .setDefaultRequestProperties(resolvedStream.headers - "User-Agent")
                .setAllowCrossProtocolRedirects(true)
            val dataSourceFactory = DefaultDataSource.Factory(appContext, httpFactory)
            if (resolvedStream.isHls) {
                HlsMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(effectiveUrl))
            } else {
                ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(effectiveUrl))
            }
        }
        player.setMediaSource(mediaSource)
        player.setPlaybackSpeed(if (isFastForwardHold) 2f else playbackSpeed)
        if (initialSeekMs > 0L) {
            player.seekTo(initialSeekMs)
            resumeSeekApplied = true
        }
        player.prepare()
        player.playWhenReady = autoPlay
    }

    private fun attemptCodecRecovery(
        activeSession: PlayerSession,
        activeStream: ResolvedStream,
    ) {
        codecRecoveryAttempts++
        val resumeMs = currentPlaybackPositionMs().coerceAtLeast(0L)
        val shouldPlay = exoPlayerInstance?.playWhenReady ?: true
        val targetKey = activeSession.key
        streamError = null
        isBuffering = true
        releasePlayerOnly()
        recoveryJob?.cancel()
        recoveryJob = scope.launch {
            delay(CODEC_RECOVERY_DELAY_MS)
            if (session?.key != targetKey || stream == null) return@launch
            lastLoadedSignature = null
            preparePlayerWithStream(
                resolvedStream = activeStream,
                offline = activeSession.offline,
                qualityUrl = selectedQualityUrl,
                initialSeekMs = resumeMs,
                autoPlay = shouldPlay,
            )
        }
    }

    private fun releasePlayerOnly() {
        exoPlayerInstance?.let { player ->
            player.removeListener(playerListener)
            player.release()
        }
        exoPlayerInstance = null
        lastLoadedSignature = null
    }

    fun togglePlayPause() {
        val player = exoPlayerInstance ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun play() {
        exoPlayerInstance?.play()
    }

    fun pause() {
        exoPlayerInstance?.pause()
    }

    fun currentPositionMs(): Long = currentPlaybackPositionMs()

    /** Position de lecture (moteur unique : ExoPlayer). */
    private fun currentPlaybackPositionMs(): Long =
        exoPlayerInstance?.currentPosition?.coerceAtLeast(0L) ?: positionMs

    fun seekTo(targetMs: Long) {
        val player = exoPlayerInstance ?: return
        val maxDur = player.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
        val clamped = targetMs.coerceIn(0L, maxDur)
        player.seekTo(clamped)
        positionMs = clamped
    }

    fun setScrubbing(enabled: Boolean) {
        // Optimisation de rendu ExoPlayer uniquement.
        exoPlayerInstance?.isScrubbingModeEnabled = enabled
    }

    fun previewSeekPosition(ms: Long) {
        userSeeking = true
        positionMs = ms.coerceAtLeast(0L)
    }

    fun commitSeekPosition(ms: Long) {
        seekTo(ms)
        userSeeking = false
    }

    fun selectPlaybackSpeed(speed: Float) {
        playbackSpeed = speed
        if (!isFastForwardHold) {
            exoPlayerInstance?.setPlaybackSpeed(speed)
        }
    }

    fun setTemporaryFastSpeed(enabled: Boolean) {
        if (isFastForwardHold == enabled) return
        isFastForwardHold = enabled
        val rate = if (enabled) 2f else playbackSpeed
        exoPlayerInstance?.setPlaybackSpeed(rate)
    }

    fun selectAudioTrack(option: PlayerTrackOption) {
        val player = exoPlayerInstance ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(option.trackGroup, option.trackIndex))
            .build()
    }

    fun selectSubtitleTrack(option: PlayerTrackOption?) {
        val player = exoPlayerInstance ?: return
        val builder = player.trackSelectionParameters.buildUpon()
        if (option == null) {
            builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            subtitlesDisabled = true
        } else {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            builder.setOverrideForType(TrackSelectionOverride(option.trackGroup, option.trackIndex))
            subtitlesDisabled = false
        }
        player.trackSelectionParameters = builder.build()
    }

    fun enterFullscreen() {
        if (session == null) return
        isFullscreenRequested = true
        presentationMode = PlayerPresentationMode.FULLSCREEN_LANDSCAPE
    }

    fun exitFullscreen() {
        isFullscreenRequested = false
        recomputePresentationMode(forceInlineIfWatchVisible = true)
    }

    fun minimizeToFloating() {
        if (session == null) return
        isFullscreenRequested = false
        presentationMode = PlayerPresentationMode.FLOATING_MINI
    }

    fun requestRestoreToEmbedded() {
        if (session == null) return
        isFullscreenRequested = false
        inlineVisibleFraction = 1f
        presentationMode = PlayerPresentationMode.EMBEDDED_PORTRAIT
        scrollToTopSignal++
    }

    fun onWatchScreenEnter(token: Any) {
        activeWatchToken = token
        updateWatchScreenVisibility(true)
    }

    fun onWatchScreenExit(token: Any) {
        if (activeWatchToken === token) {
            activeWatchToken = null
            updateWatchScreenVisibility(false)
        }
    }

    fun markWatchRouteExited() {
        activeWatchToken = null
        updateWatchScreenVisibility(false)
    }

    fun updateWatchScreenVisibility(visible: Boolean) {
        if (isWatchScreenVisible == visible) return
        isWatchScreenVisible = visible
        recomputePresentationMode(forceInlineIfWatchVisible = visible)
    }

    fun updateInlineSlotVisibility(visibleFraction: Float) {
        inlineVisibleFraction = visibleFraction.coerceIn(0f, 1f)
        if (!isWatchScreenVisible || isFullscreenRequested || session == null) return
        presentationMode = MiniPlayerGeometry.resolveInlineModeWithHysteresis(
            currentMode = presentationMode,
            visibleFraction = inlineVisibleFraction,
        )
    }

    private fun recomputePresentationMode(forceInlineIfWatchVisible: Boolean = false) {
        if (session == null) {
            presentationMode = PlayerPresentationMode.HIDDEN
            return
        }
        presentationMode = when {
            isFullscreenRequested -> PlayerPresentationMode.FULLSCREEN_LANDSCAPE
            !isWatchScreenVisible -> PlayerPresentationMode.FLOATING_MINI
            forceInlineIfWatchVisible -> PlayerPresentationMode.EMBEDDED_PORTRAIT
            else -> MiniPlayerGeometry.resolveInlineModeWithHysteresis(
                currentMode = presentationMode,
                visibleFraction = inlineVisibleFraction,
            )
        }
    }

    fun onAppBackgrounded() {
        flushProgressNow()
        val activeSession = session
        val activeStream = stream
        if (activeSession != null && activeStream != null && exoPlayerInstance != null) {
            backgroundResumePositionMs = currentPlaybackPositionMs()
            suspendedInBackground = true
        }
        isPlaying = false
        isBuffering = false
        // Libère immédiatement MediaCodec en arrière-plan pour éviter la révocation
        // forcée par ResourceManagerService ("Released by resource manager").
        releasePlayerOnly()
    }

    fun onAppForegrounded() {
        if (!suspendedInBackground) return
        suspendedInBackground = false
        val activeSession = session ?: return
        val activeStream = stream ?: return
        lastLoadedSignature = null
        preparePlayerWithStream(
            resolvedStream = activeStream,
            offline = activeSession.offline,
            qualityUrl = selectedQualityUrl,
            initialSeekMs = backgroundResumePositionMs,
            autoPlay = false,
        )
    }

    fun closeAndStop() {
        flushProgressNow()
        loadJob?.cancel()
        recoveryJob?.cancel()
        // Audit S-02 : la boucle de progression doit mourir avec le lecteur
        // (elle redemarre a la prochaine lecture, a la creation ExoPlayer).
        progressJob?.cancel()
        suspendedInBackground = false
        codecRecoveryAttempts = 0
        activeWatchToken = null
        isFullscreenRequested = false
        isWatchScreenVisible = false
        isResolvingStream = false
        streamError = null
        stream = null
        session = null
        presentationMode = PlayerPresentationMode.HIDDEN
        engineHost = null
        releasePlayerOnly()
        isPlaying = false
        isBuffering = false
        positionMs = 0L
        durationMs = 0L
    }

    fun release() {
        flushProgressNow()
        loadJob?.cancel()
        recoveryJob?.cancel()
        progressJob?.cancel()
        engineHost = null
        releasePlayerOnly()
        session = null
        stream = null
        presentationMode = PlayerPresentationMode.HIDDEN
    }

    private fun ensureProgressLoopStarted() {
        if (progressJob?.isActive == true) return
        progressJob = scope.launch {
            while (isActive) {
                // Source de vérité : ExoPlayer (moteur unique depuis
                // V2.9.6).
                val player = exoPlayerInstance
                val playingNow: Boolean
                val dur: Long
                val pos: Long
                if (player != null) {
                    playingNow = player.isPlaying
                    dur = player.duration.coerceAtLeast(0L)
                    pos = player.currentPosition.coerceAtLeast(0L)
                } else {
                    delay(500L)
                    continue
                }
                isPlaying = playingNow
                durationMs = dur
                if (!userSeeking) positionMs = pos

                val now = System.currentTimeMillis()
                if (playingNow && now - lastSavedAtMs >= 5000L && dur > 0L && pos > 3000L) {
                    lastSavedAtMs = now
                    val activeSession = session
                    if (activeSession != null) {
                        val effective = if (pos > dur * 95L / 100L) 0L else pos
                        withContext(Dispatchers.IO) {
                            WatchHistory.recordPosition(
                                appContext,
                                activeSession.season.url,
                                activeSession.episode.number,
                                effective,
                                dur,
                            )
                        }
                    }
                }
                delay(if (playingNow) 500L else 1000L)
            }
        }
    }

    private fun flushProgressNow() {
        val activeSession = session ?: return
        val player = exoPlayerInstance ?: return
        val pos = player.currentPosition
        val dur = player.duration
        if (dur > 0L && pos > 3000L) {
            val effective = if (pos > dur * 95L / 100L) 0L else pos
            scope.launch(Dispatchers.IO) {
                WatchHistory.recordPosition(
                    appContext,
                    activeSession.season.url,
                    activeSession.episode.number,
                    effective,
                    dur,
                )
            }
        }
    }

    companion object {
        private const val MAX_CODEC_RECOVERY_ATTEMPTS = 2
        private const val CODEC_RECOVERY_DELAY_MS = 180L

        internal fun buildLoadControl(profile: ExoPlayerBufferProfile): DefaultLoadControl =
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    profile.minBufferMs,
                    profile.maxBufferMs,
                    profile.bufferForPlaybackMs,
                    profile.bufferForPlaybackAfterRebufferMs,
                )
                .setBackBuffer(profile.backBufferDurationMs, /* retainBackBufferFromKeyframe = */ false)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

        internal fun isCodecReclaimOrDecoderError(error: PlaybackException): Boolean =
            isCodecReclaimOrDecoderError(error.errorCode, error.cause)

        internal fun isCodecReclaimOrDecoderError(errorCode: Int, cause: Throwable?): Boolean {
            if (errorCode == PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED ||
                errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
                errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                errorCode == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED
            ) {
                return true
            }
            var current: Throwable? = cause
            var depth = 0
            while (current != null && depth < 6) {
                if (current is MediaCodec.CodecException || current is MediaCodecRenderer.DecoderInitializationException) {
                    return true
                }
                current = current.cause
                depth++
            }
            return false
        }
    }
}
