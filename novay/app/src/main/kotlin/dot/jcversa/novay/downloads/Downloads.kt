package dot.jcversa.novay.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import dot.jcversa.novay.data.DownloadChoice
import dot.jcversa.novay.data.ResolvedStream
import dot.jcversa.novay.data.sama.SamaParsers
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource

/**
 * Façade des téléchargements NoVaY (V2.5 « novabox ») : les épisodes
 * deviennent de VRAIS FICHIERS dans Download/NoVaY/ du téléphone —
 * nommés façon lisible (« Solo Leveling - S01E03 VOSTFR 1080p.mp4 »),
 * visibles dans Fichiers/galerie/VLC, lus in-app depuis ces fichiers.
 *
 * L'API publique est celle consommée par les écrans (V2.3/V2.4) : le
 * moteur derrière (FileDownloadEngine) a changé, pas les contrats.
 */
object Downloads {

    const val CHANNEL_ID = "novay_downloads"

    // ── États exposés (moteur fichier, plus de media3) ────────────────────

    private const val STATE_QUEUED = 1
    private const val STATE_RUNNING = 2
    private const val STATE_PAUSED = 3
    private const val STATE_COMPLETED = 4
    private const val STATE_FAILED = 5

    /** État d'un téléchargement, exposé à l'UI. */
    data class DownloadInfo(
        val id: String,
        val state: Int,
        val percent: Float,
        val meta: EpisodeDownloadMeta?,
        /** Octets reçus / taille totale (pour vitesse et temps restant). */
        val bytesDownloaded: Long = 0L,
        val totalBytes: Long = 0L
    ) {
        val isCompleted: Boolean get() = state == STATE_COMPLETED
        val isFailed: Boolean get() = state == STATE_FAILED
        val isActive: Boolean
            get() = state == STATE_RUNNING || state == STATE_QUEUED
    }

    /** Vue « manager » (compatibilité écrans V2.4 : downloadsPaused). */
    class ManagerView(context: Context) {
        val downloadsPaused: Boolean
            get() = FileDownloadEngine.downloadsPaused()
    }

    fun manager(@Suppress("UNUSED_PARAMETER") context: Context): ManagerView = ManagerView(context)

    // ── Préférences ───────────────────────────────────────────────────────

    private const val PREFS = "novay_download_prefs"
    private const val KEY_WIFI_ONLY = "wifi_only"

    fun isWifiOnly(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_WIFI_ONLY, false)

    fun setWifiOnly(context: Context, wifiOnly: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_WIFI_ONLY, wifiOnly).apply()
        FileDownloadEngine.setWifiOnly(wifiOnly)
    }

    // ── Cycle de vie ──────────────────────────────────────────────────────

    fun init(context: Context) {
        createChannel(context)
        FileDownloadEngine.onNotification = {
            NovayDownloadService.notifyState(context.applicationContext)
        }
        FileDownloadEngine.init(context)
    }

    // ── File ──────────────────────────────────────────────────────────────

    fun downloadId(seasonUrl: String, episodeNumber: Int): String =
        "${seasonUrl.trimEnd('/')}#ep$episodeNumber"

    /** L'épisode de cette saison est-il téléchargé (fichier publié) ? */
    fun isDownloaded(context: Context, seasonUrl: String, episodeNumber: Int): Boolean =
        FileDownloadEngine.find(downloadId(seasonUrl, episodeNumber))?.isCompleted == true

    /**
     * Flux de lecture de l'épisode téléchargé : LE FICHIER du téléphone
     * (content:///file://), jamais le réseau. Null si absent/incomplet.
     */
    fun downloadedStream(context: Context, seasonUrl: String, episodeNumber: Int): ResolvedStream? {
        FileDownloadEngine.init(context)
        return FileDownloadEngine.find(downloadId(seasonUrl, episodeNumber))
            ?.takeIf { it.isCompleted }
            ?.meta
            ?.toStream()
    }

    /**
     * Met un épisode en file : la VARIANTE choisie par l'utilisateur est
     * résolue (DownloadChoice), les métadonnées embarquent l'URL de sa
     * playlist + les en-têtes du lecteur.
     */
    fun startEpisodeDownload(
        context: Context,
        animeTitle: String,
        seasonName: String,
        seasonUrl: String,
        episodeNumber: Int,
        episodeLabel: String,
        stream: ResolvedStream,
        preferredHeight: Int? = null,
        onStart: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val choice = DownloadChoice.pick(stream, preferredHeight)
        val meta = EpisodeDownloadMeta(
            animeTitle = animeTitle,
            seasonName = seasonName,
            seasonUrl = seasonUrl,
            episodeNumber = episodeNumber,
            episodeLabel = episodeLabel,
            streamUrl = choice.url,
            isHls = choice.isHls,
            headers = stream.headers,
            qualityLabel = choice.qualityLabel
        )
        FileDownloadEngine.enqueue(
            context,
            FileDownloadEngine.Task(
                id = downloadId(seasonUrl, episodeNumber),
                meta = meta
            )
        )
        onStart()
    }

    /** Snapshot pour l'UI (états, pourcentages, octets). */
    fun snapshot(context: Context): List<DownloadInfo> {
        FileDownloadEngine.init(context)
        return FileDownloadEngine.tasks().map { task ->
            val percent = if (task.isCompleted) {
                100f
            } else if (task.bytesTotal > 0) {
                (task.bytesDone * 100f / task.bytesTotal).coerceIn(0f, 100f)
            } else {
                0f
            }
            DownloadInfo(
                id = task.id,
                state = when (task.state) {
                    FileDownloadEngine.State.QUEUED -> STATE_QUEUED
                    FileDownloadEngine.State.RUNNING -> STATE_RUNNING
                    FileDownloadEngine.State.WAITING_WIFI -> STATE_QUEUED
                    FileDownloadEngine.State.PAUSED -> STATE_PAUSED
                    FileDownloadEngine.State.DONE -> STATE_COMPLETED
                    FileDownloadEngine.State.FAILED -> STATE_FAILED
                },
                percent = percent,
                meta = task.meta,
                bytesDownloaded = task.bytesDone,
                totalBytes = if (task.isCompleted) task.bytesDone else task.bytesTotal
            )
        }
    }

    fun pauseAll(context: Context) {
        FileDownloadEngine.pauseAll()
        NovayDownloadService.notifyState(context)
    }

    fun resumeAll(context: Context) {
        FileDownloadEngine.resumeAll()
        NovayDownloadService.notifyState(context)
    }

    /** Annule un téléchargement + supprime SON fichier publié. */
    fun remove(context: Context, id: String) {
        FileDownloadEngine.cancel(context, id)
        NovayDownloadService.notifyState(context)
    }

    // ── Lecture in-app ────────────────────────────────────────────────────

    /**
     * Source de lecture : l'épisode téléchargé se lit DEPUIS LE FICHIER
     * du téléphone (content:// ou file://) ; en ligne, la source HTTP
     * du flux avec ses en-têtes.
     */
    fun offlineMediaSource(
        context: Context,
        stream: ResolvedStream
    ): ProgressiveMediaSource {
        val url = stream.url
        val isLocal = url.startsWith("content://") || url.startsWith("file://") ||
            url.startsWith("/")
        val factory = if (isLocal) {
            DefaultDataSource.Factory(context)
        } else {
            val httpFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(stream.headers["User-Agent"] ?: SamaParsers.UA)
                .setDefaultRequestProperties(stream.headers - "User-Agent")
                .setAllowCrossProtocolRedirects(true)
            DefaultDataSource.Factory(context, httpFactory)
        }
        return ProgressiveMediaSource.Factory(factory)
            .createMediaSource(MediaItem.fromUri(url))
    }

    // ── Notification pilotable (V2.3, inspiration Ketch) ──────────────────

    /**
     * Notification de progression : boutons Pause / Reprendre / Annuler
     * directement dans le tiroir de notifications.
     */
    fun progressNotification(context: Context): Notification {
        val infos = snapshot(context)
        val active = infos.filter { it.isActive }
        val queued = infos.filter { it.state == STATE_QUEUED }
        val paused = infos.filter { it.state == STATE_PAUSED }
        val done = infos.count { it.isCompleted }
        val failed = infos.filter { it.isFailed }

        val enginePaused = FileDownloadEngine.downloadsPaused()
        val wifiOnly = isWifiOnly(context)

        val (title, text) = when {
            active.isNotEmpty() -> {
                val current = active.firstOrNull { it.state == STATE_RUNNING } ?: active.first()
                "Téléchargements NoVaY" to
                    "${active.size} en cours · ${current.percent.toInt()} % · ${queued.size} en file"
            }
            enginePaused && paused.isNotEmpty() ->
                "Téléchargements NoVaY" to "En pause (${paused.size} en attente)"
            failed.isNotEmpty() ->
                "Téléchargements NoVaY" to
                    "${failed.size} échec(s) — ${failed.first().meta?.episodeLabel ?: ""}"
            done > 0 && infos.all { it.isCompleted } ->
                "Téléchargements NoVaY" to "Terminé — $done épisode(s) dans Download/NoVaY"
            queued.isNotEmpty() && wifiOnly ->
                "Téléchargements NoVaY" to "En attente du Wi-Fi (${queued.size})"
            queued.isNotEmpty() ->
                "Téléchargements NoVaY" to "En file (${queued.size})"
            else -> "Téléchargements NoVaY" to "Aucun téléchargement"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(active.isNotEmpty())
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (infos.isNotEmpty()) {
            if (enginePaused) {
                builder.addAction(
                    0, "Reprendre",
                    actionIntent(context, NovayDownloadService.ACTION_RESUME_ALL, 2)
                )
            } else {
                builder.addAction(
                    0, "Pause",
                    actionIntent(context, NovayDownloadService.ACTION_PAUSE_ALL, 1)
                )
            }
            builder.addAction(
                0, "Annuler",
                actionIntent(context, NovayDownloadService.ACTION_CANCEL_ALL, 3)
            )
        }
        return builder.build()
    }

    private fun actionIntent(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            context,
            requestCode,
            Intent(context, NovayDownloadService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Téléchargements NoVaY",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progression des téléchargements d'épisodes"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
