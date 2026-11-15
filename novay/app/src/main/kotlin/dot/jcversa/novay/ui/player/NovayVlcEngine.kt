package dot.jcversa.novay.ui.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import kotlin.concurrent.thread

/**
 * Moteur libVLC (V2.8) — la moitié « fichiers téléchargés » du lecteur
 * hybride NoVaY. Le streaming en ligne reste sur ExoPlayer (en-têtes
 * Referer/UA + choix de qualité) ; les vrais fichiers de Download/NoVaY/
 * passent par libVLC, le moteur de VLC : conteneurs et codecs presque
 * illimités, MP4 imparfait ou .ts de repli lus sans broncher, repli
 * logiciel automatique quand le décodeur matériel manque (précieux en
 * 32 bits).
 *
 * Threads : les appels libVLC (open/stop/seek/pause) sont sérialisés sur
 * une file dédiée — setMedia/stop rejoignent le thread d'input et peuvent
 * bloquer sur un flux capricieux. TOUT ce qui touche aux vues
 * (attachViews/detachViews) s'exécute sur le THREAD PRINCIPAL : c'est le
 * contrat @MainThread de l'AWindow libVLC (vérifié dans les sources
 * officielles vlc-android) — un attachement lancé depuis un thread de
 * fond ne connecte jamais la SurfaceTexture (son sans image, écran noir ;
 * le bug constaté sur l'appareil du owner depuis la V2.8). Les
 * événements arrivent sur le thread principal.
 *
 * L'instance LibVLC (lourde, native) vit pour toute la durée de l'app :
 * un seul chargement de la bibliothèque, réouverture instantanée.
 */
class NovayVlcEngine(
    private val context: Context,
    private val callbacks: Callbacks,
) {

    interface Callbacks {
        fun onVlcPlaying(playing: Boolean)
        fun onVlcBuffering(buffering: Boolean)
        fun onVlcTime(timeMs: Long)
        fun onVlcLength(lengthMs: Long)
        fun onVlcEnded()
        fun onVlcError(message: String)
        fun onVlcVideoSize(rawRatio: Float)
    }

    companion object {
        @Volatile private var libVlcInstance: LibVLC? = null

        /**
         * Délai du repli de rendu : lecture démarrée mais toujours aucune
         * sortie vidéo après ce délai → bascule TextureView → SurfaceView.
         */
        private const val VOUT_WATCHDOG_MS = 3_000L

        /**
         * PUR (testé JVM) : faut-il basculer le rendu en SurfaceView ?
         * Lecture active, aucune sortie vidéo reçue (Event.Vout), et une
         * surface actuellement attachée à remplacer.
         */
        fun voutFallbackNeeded(active: Boolean, hadVout: Boolean, viewsAttached: Boolean): Boolean =
            active && !hadVout && viewsAttached

        @Synchronized
        private fun libVlc(appContext: Context): LibVLC =
            libVlcInstance ?: LibVLC(
                appContext,
                arrayListOf(
                    // Vitesse de lecture sans « voix de canard ».
                    "--audio-time-stretch",
                ),
            ).also { libVlcInstance = it }
    }

    /** File sérialisée des appels au moteur natif. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val main = Handler(Looper.getMainLooper())

    private var player: MediaPlayer? = null
    private var videoLayout: VLCVideoLayout? = null

    @Volatile var active = false
        private set
    @Volatile private var playing = false
    @Volatile private var timeMs = 0L
    @Volatile private var lengthMs = 0L
    @Volatile private var pendingSeekMs = 0L
    @Volatile private var viewsAttached = false
    @Volatile private var currentUri: Uri? = null

    /** Une sortie vidéo a été créée (Event.Vout > 0) depuis open(). */
    @Volatile private var hadVout = false
    /** TextureView (V2.8, verre CinemaGlass) tant qu'aucun repli rendu. */
    @Volatile private var preferTextureView = true
    /** Le chien de garde de rendu n'est armé qu'une fois par lecture. */
    @Volatile private var voutWatchdogArmed = false

    /**
     * Descripteur du fichier content:// en cours de lecture : doit rester
     * ouvert tant que libVLC lit (l'URI fd://N pointe dessus).
     */
    @Volatile private var heldFileDescriptor: android.os.ParcelFileDescriptor? = null

    /** Focus audio + casque débranché : même comportement qu'ExoPlayer. */
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var noisyReceiverRegistered = false

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pause()
        }
    }
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            -> pause()
        }
    }

    /** Position courante (cache des événements — jamais d'appel bloquant). */
    val currentPositionMs: Long get() = timeMs
    val durationMs: Long get() = lengthMs
    val isPlayingNow: Boolean get() = playing

    // ── Vue vidéo ───────────────────────────────────────────────────────

    /**
     * Vue vidéo du moteur (créée/remplie sur le thread principal par le
     * composable hôte). L'attachement réel au player se fait sur le THREAD
     * PRINCIPAL (contrat @MainThread de l'AWindow libVLC, cf.
     * attachViewsIfNeeded).
     */
    fun ensureVideoLayout(context: Context): View {
        var layout = videoLayout
        if (layout == null) {
            layout = VLCVideoLayout(context)
            layout.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            videoLayout = layout
        }
        // Ré-hébergement : la vue ne peut avoir deux parents.
        (layout.parent as? ViewGroup)?.removeView(layout)
        scope.launch { attachViewsIfNeeded() }
        return layout
    }

    // ── Ouverture / lecture ─────────────────────────────────────────────

    fun open(uri: Uri, initialSeekMs: Long, autoPlay: Boolean, rate: Float) {
        active = true
        playing = false
        hadVout = false
        voutWatchdogArmed = false
        pendingSeekMs = initialSeekMs.coerceAtLeast(0L)
        // Position immédiatement honnête (reprise en pause) + durée
        // conservée si on ré-ouvre LE MÊME fichier (retour d'arrière-plan).
        timeMs = pendingSeekMs
        if (uri != currentUri) {
            lengthMs = 0L
            currentUri = uri
        }
        main.post { callbacks.onVlcBuffering(true) }
        scope.launch {
            // V2.9.2 : le MediaPlayer est TOUJOURS créé sur le thread
            // principal — le handler d'événements de VLCObject se lie au
            // looper du thread créateur (pattern des lecteurs VLC Android
            // réels). Créé sur un thread IO sans looper, la distribution
            // des événements Playing/Paused/TimeChanged n'est pas garantie
            // : c'est l'icône play/pause figée constatée par l'owner.
            val mp = withContext(Dispatchers.Main) { ensurePlayer() }
            // V2.9.4 : la surface est branchée AVANT la lecture, sur le
            // thread principal (le bug écran noir depuis la V2.8 : la vue
            // était attachée depuis la file IO, après play()). Si la vue
            // n'existe pas encore, ensureVideoLayout relancera l'appel.
            attachViewsIfNeeded()
            runCatching {
                // Un média précédent peut encore tourner : arrêt propre.
                runCatching { mp.stop() }
                closeHeldFileDescriptor()
                val media = mediaFromUri(libVlc(context.applicationContext), uri)
                mp.media = media
                media.release()
                mp.setRate(rate)
                // V2.9.3 : retour à la config V2.8 VALIDÉE sur l'appareil
                // du owner — setTime AVANT play() (reprise en pause
                // immédiatement honnête), en acquittant tout de suite pour
                // que applyPendingSeekOnPlay ne cherche pas deux fois.
                if (pendingSeekMs > 0L) {
                    runCatching { mp.setTime(pendingSeekMs) }
                    pendingSeekMs = 0L
                }
                if (autoPlay) {
                    requestFocus()
                    mp.play()
                }
            }.onFailure { e ->
                main.post { callbacks.onVlcError(e.message ?: "Erreur d'ouverture libVLC") }
            }
        }
    }

    fun play() {
        scope.launch {
            val mp = player ?: return@launch
            if (!active) return@launch
            requestFocus()
            runCatching { mp.play() }
        }
    }

    fun pause() {
        scope.launch {
            val mp = player ?: return@launch
            abandonFocus()
            runCatching { mp.pause() }
        }
    }

    fun togglePlayPause() {
        if (playing) pause() else play()
    }

    fun seekTo(targetMs: Long) {
        val clamped = if (lengthMs > 0L) targetMs.coerceIn(0L, lengthMs) else targetMs.coerceAtLeast(0L)
        timeMs = clamped
        scope.launch {
            val mp = player ?: return@launch
            runCatching { mp.setTime(clamped) }
        }
    }

    fun setRate(rate: Float) {
        scope.launch {
            runCatching { player?.setRate(rate) }
        }
    }

    /** Arrêt + détachement des vues — le moteur repasse inactif. */
    fun stopAndDetach() {
        active = false
        playing = false
        timeMs = 0L
        lengthMs = 0L
        pendingSeekMs = 0L
        currentUri = null
        hadVout = false
        voutWatchdogArmed = false
        abandonFocus()
        scope.launch {
            val mp = player ?: return@launch
            runCatching {
                if (viewsAttached) {
                    // detachViews : aussi un contrat @MainThread (AWindow).
                    withContext(Dispatchers.Main) { runCatching { mp.detachViews() } }
                    viewsAttached = false
                }
                mp.stop()
            }
            // Le descripteur de fichier (content://) ne survit pas à l'arrêt.
            closeHeldFileDescriptor()
        }
    }

    fun release() {
        stopAndDetach()
        val mp = player
        player = null
        if (mp != null) {
            // Teardown sur son propre thread : scope.cancel() annule les
            // coroutines encore en file d'attente, et mp.release() rejoint
            // le thread d'input VLC (potentiellement bloquant).
            thread(name = "novay-vlc-release") {
                runCatching { mp.setEventListener(null); mp.release() }
                closeHeldFileDescriptor()
            }
        }
        scope.cancel()
    }

    // ── Internes ────────────────────────────────────────────────────────

    /**
     * Construit le média libVLC selon le schéma — exactement la stratégie
     * de VLC for Android 3.x : le natif ne comprend PAS `content://`, on
     * résout via le ContentResolver en descripteur de fichier ouvert en
     * lecture (`fd://N`, gardé ouvert pendant toute la lecture).
     */
    private fun mediaFromUri(libvlc: LibVLC, uri: Uri): Media {
        val media = when {
            uri.scheme.equals("content", ignoreCase = true) -> {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    ?: throw IllegalStateException("Fichier introuvable sur le stockage")
                heldFileDescriptor?.runCatching { close() }
                heldFileDescriptor = pfd
                Media(libvlc, Uri.parse("fd://${pfd.fd}"))
            }
            uri.scheme == null -> {
                // Chemin brut (/storage/...) -> URI file:// propre.
                Media(libvlc, Uri.fromFile(java.io.File(uri.path ?: uri.toString())))
            }
            else -> Media(libvlc, uri)
        }
        // V2.9.3 : AUCUNE option media — retour EXACT à la config V2.8
        // validée sur l'appareil du owner. Les options :no-mediacodec-dr /
        // :no-omxil-dr / :input-fast-seek ajoutées en V2.9 coupaient le
        // rendu vidéo (écran noir, son seul) sur son Android 7 32 bits :
        // le rendu direct MediaCodec y est le chemin qui MARCHE.
        return media
    }

    private fun closeHeldFileDescriptor() {
        synchronized(this) {
            heldFileDescriptor?.runCatching { close() }
            heldFileDescriptor = null
        }
    }

    private fun ensurePlayer(): MediaPlayer {
        player?.let { return it }
        val mp = MediaPlayer(libVlc(context.applicationContext))
        mp.setEventListener(eventListener)
        player = mp
        return mp
    }

    private suspend fun attachViewsIfNeeded() {
        val mp = player ?: return
        val layout = videoLayout ?: return
        if (viewsAttached) return
        // V2.9.4 : attachViews DOIT s'exécuter sur le THREAD PRINCIPAL —
        // contrat @MainThread de l'AWindow libVLC (sources officielles
        // vlc-android). Lancé depuis la file IO (V2.8 → V2.9.3),
        // attachTextureView() ne connectait jamais la SurfaceTexture :
        // son sans image, écran noir. C'est LE bug de l'écran noir.
        withContext(Dispatchers.Main) {
            if (viewsAttached || !active) return@withContext
            val attach: () -> Unit = {
                if (!viewsAttached && active && player === mp && videoLayout === layout) {
                    runCatching { mp.attachViews(layout, null, false, preferTextureView) }
                        .onSuccess { viewsAttached = true }
                        .onFailure { e ->
                            // Plus jamais d'échec silencieux : si la surface
                            // ne s'attache pas, l'écran le dit.
                            callbacks.onVlcError(
                                "Surface vidéo impossible : ${e.message ?: e.javaClass.simpleName}",
                            )
                        }
                }
            }
            // Vue pas encore rattachée à la fenêtre (ajoutée au frame en
            // cours) : on attend le frame suivant — post() s'exécute une
            // fois la vue attachée et mesurée, le pattern du player
            // officiel VLC qui attend la disponibilité de ses surfaces.
            if (layout.isAttachedToWindow) attach() else layout.post(attach)
        }
    }

    /**
     * Chien de garde du rendu (V2.9.4) : armé au premier événement
     * Playing. Si aucune sortie vidéo n'est apparue après [VOUT_WATCHDOG_MS],
     * on détache et ré-attache la surface en SURFACEVIEW — le rendu du
     * player officiel VLC, le plus fiable sur les GPU anciens. La sortie
     * vidéo libVLC est recréée à chaud, sans coupure du son.
     */
    private fun armVoutWatchdog() {
        if (voutWatchdogArmed) return
        voutWatchdogArmed = true
        scope.launch {
            delay(VOUT_WATCHDOG_MS)
            if (voutFallbackNeeded(active, hadVout, viewsAttached)) {
                reattachWithSurfaceView()
            }
        }
    }

    private suspend fun reattachWithSurfaceView() {
        val mp = player ?: return
        val layout = videoLayout ?: return
        withContext(Dispatchers.Main) {
            if (!voutFallbackNeeded(active, hadVout, viewsAttached)) return@withContext
            runCatching {
                mp.detachViews()
                viewsAttached = false
                preferTextureView = false
                mp.attachViews(layout, null, false, false)
                viewsAttached = true
            }
        }
    }

    private val eventListener = MediaPlayer.EventListener { event ->
        when (event.type) {
            MediaPlayer.Event.Playing -> {
                playing = true
                armVoutWatchdog()
                main.post {
                    applyPendingSeekOnPlay()
                    callbacks.onVlcPlaying(true)
                    callbacks.onVlcBuffering(false)
                }
            }
            MediaPlayer.Event.Paused -> {
                playing = false
                main.post { callbacks.onVlcPlaying(false) }
            }
            MediaPlayer.Event.Buffering -> {
                // VLC balaie 0..100 (Float) : 100 = prêt à jouer.
                val done = event.buffering >= 100f
                main.post { callbacks.onVlcBuffering(!done) }
            }
            MediaPlayer.Event.TimeChanged -> {
                timeMs = event.getTimeChanged()
                main.post { callbacks.onVlcTime(timeMs) }
            }
            MediaPlayer.Event.LengthChanged -> {
                lengthMs = event.getLengthChanged()
                main.post { callbacks.onVlcLength(lengthMs) }
            }
            MediaPlayer.Event.EndReached -> {
                playing = false
                abandonFocus()
                main.post {
                    callbacks.onVlcPlaying(false)
                    callbacks.onVlcEnded()
                }
            }
            MediaPlayer.Event.EncounteredError -> {
                playing = false
                abandonFocus()
                main.post { callbacks.onVlcError("Erreur de lecture du fichier (libVLC)") }
            }
            MediaPlayer.Event.Vout -> {
                if (event.getVoutCount() > 0) {
                    // La sortie vidéo EXISTE : le chien de garde de rendu
                    // n'a plus rien à faire.
                    hadVout = true
                    readVideoSize()
                }
            }
        }
    }

    /** La seek d'ouverture n'est fiable qu'une fois la lecture lancée. */
    private fun applyPendingSeekOnPlay() {
        val seek = pendingSeekMs
        if (seek > 0L) {
            pendingSeekMs = 0L
            scope.launch {
                runCatching { player?.setTime(seek) }
            }
        }
    }

    private fun readVideoSize() {
        scope.launch {
            runCatching {
                val track = player?.currentVideoTrack ?: return@launch
                if (track.width > 0 && track.height > 0) {
                    val sar = if (track.sarNum > 0 && track.sarDen > 0) {
                        track.sarNum.toFloat() / track.sarDen.toFloat()
                    } else {
                        1f
                    }
                    val rawRatio = track.width * sar / track.height.toFloat()
                    main.post { callbacks.onVlcVideoSize(rawRatio) }
                }
            }
        }
    }

    // ── Focus audio (parité ExoPlayer) ──────────────────────────────────

    private fun requestFocus() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request = focusRequest ?: AudioFocusRequest.Builder(
                    AudioManager.AUDIOFOCUS_GAIN,
                ).setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build(),
                ).setOnAudioFocusChangeListener(focusListener).build().also { focusRequest = it }
                audioManager.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    focusListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN,
                )
            }
            if (!noisyReceiverRegistered) {
                context.registerReceiver(
                    noisyReceiver,
                    IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                )
                noisyReceiverRegistered = true
            }
        }
    }

    private fun abandonFocus() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(focusListener)
            }
            if (noisyReceiverRegistered) {
                context.unregisterReceiver(noisyReceiver)
                noisyReceiverRegistered = false
            }
        }
    }
}
