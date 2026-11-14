package dot.jcversa.novay.downloads

import android.app.Notification
import android.app.PendingIntent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import androidx.core.app.NotificationCompat
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import dot.jcversa.novay.data.ResolvedStream
import dot.jcversa.novay.data.sama.SamaParsers
import java.io.File
import java.io.IOException
import java.net.URI

/**
 * Coordonnateur des téléchargements NoVaY (singleton, initialisé par
 * MainActivity) : cache offline (media3 SimpleCache), DownloadManager,
 * notifications de progression et MediaSources de lecture hors-ligne.
 *
 * Stockage : stockage externe PRIVÉ de l'appli
 * (Android/data/dot.jcversa.novay/files/offline) — aucune permission de
 * stockage requise ; le contenu est lisible par NoVaY uniquement.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object Downloads {

    const val CHANNEL_ID = "novay_downloads"

    private const val PREFS = "novay_download_prefs"
    private const val KEY_WIFI_ONLY = "wifi_only"

    @Volatile private var appContext: Context? = null
    @Volatile private var cacheInternal: androidx.media3.datasource.cache.SimpleCache? = null
    @Volatile private var managerInternal: DownloadManager? = null

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
        val isCompleted: Boolean get() = state == Download.STATE_COMPLETED
        val isFailed: Boolean get() = state == Download.STATE_FAILED
        val isActive: Boolean
            get() = state == Download.STATE_DOWNLOADING || state == Download.STATE_QUEUED ||
                state == Download.STATE_RESTARTING
    }

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        StreamHeadersRegistry.init(context)
        createChannel(context.applicationContext)
        // Instancie le manager (charge l'index persisté + reprend les
        // téléchargements interrompus).
        manager(context.applicationContext)
        // Contrainte réseau persistée (V2.3) : Wi-Fi uniquement ?
        if (isWifiOnly(context)) {
            runCatching {
                manager(context.applicationContext)
                    .setRequirements(Requirements(Requirements.NETWORK_UNMETERED))
            }
        }
    }

    /** True si l'utilisateur a demandé « Wi-Fi uniquement ». */
    fun isWifiOnly(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_WIFI_ONLY, false)

    /**
     * Active/interrompt « Wi-Fi uniquement » : les téléchargements se
     * mettent en attente en 4G et reprennent tout seuls en Wi-Fi
     * (Requirements media3 — inspiration Downpour/Ketch).
     */
    fun setWifiOnly(context: Context, wifiOnly: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_WIFI_ONLY, wifiOnly).apply()
        runCatching {
            manager(context).setRequirements(
                if (wifiOnly) Requirements(Requirements.NETWORK_UNMETERED) else Requirements(0)
            )
        }
    }

    fun manager(context: Context): DownloadManager {
        managerInternal?.let { return it }
        synchronized(this) {
            managerInternal?.let { return it }
            val ctx = context.applicationContext
            val dbProvider = StandaloneDatabaseProvider(ctx)
            val dir = File(
                ctx.getExternalFilesDir(null) ?: ctx.filesDir,
                "offline"
            )
            val cache = androidx.media3.datasource.cache.SimpleCache(
                dir,
                androidx.media3.datasource.cache.NoOpCacheEvictor(),
                dbProvider
            )
            cacheInternal = cache
            DownloadManager(
                ctx,
                dbProvider,
                cache,
                RegistryDataSourceFactory()
            ) { it.run() }.also {
                it.maxParallelDownloads = 1
                managerInternal = it
            }
        }
        return managerInternal!!
    }

    fun cache(context: Context): androidx.media3.datasource.cache.SimpleCache =
        cacheInternal ?: synchronized(this) {
            manager(context) // construit aussi le cache
            cacheInternal!!
        }

    /**
     * Lance le téléchargement d'un épisode.
     *
     * @param preferredHeight hauteur choisie par l'utilisateur (V2.3) ;
     *   null = meilleure qualité de ce lecteur. Si ce lecteur ne propose
     *   pas la hauteur, repli honnête sur sa meilleure.
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
        val choice = dot.jcversa.novay.data.DownloadChoice.pick(stream, preferredHeight)
        val meta = EpisodeDownloadMeta(
            animeTitle = animeTitle,
            seasonName = seasonName,
            seasonUrl = seasonUrl,
            episodeNumber = episodeNumber,
            episodeLabel = episodeLabel,
            streamUrl = choice.url,
            isHls = choice.isHls,
            headers = stream.headers
        )
        // Les en-têtes exigés par l'hôte du flux, pour les DataSource de
        // téléchargement et de lecture hors-ligne.
        hostOf(choice.url)?.let { StreamHeadersRegistry.register(it, stream.headers) }
        hostOf(stream.url)?.let { StreamHeadersRegistry.register(it, stream.headers) }

        val mediaItem = MediaItem.Builder()
            .setUri(choice.url)
            .setMimeType(if (choice.isHls) MimeTypes.APPLICATION_M3U8 else MimeTypes.VIDEO_MP4)
            .build()

        val helper = DownloadHelper.Factory()
            .setDataSourceFactory(RegistryDataSourceFactory())
            .create(mediaItem)
        helper.prepare(object : DownloadHelper.Callback {
            override fun onPrepared(h: DownloadHelper, tracksInfoAvailable: Boolean) {
                val request = h.getDownloadRequest(
                    downloadId(seasonUrl, episodeNumber),
                    meta.toJson().toByteArray(Charsets.UTF_8)
                )
                h.release()
                DownloadService.sendAddDownload(
                    context, NovayDownloadService::class.java, request, /* foreground= */ true
                )
                onStart()
            }

            override fun onPrepareError(h: DownloadHelper, e: IOException) {
                h.release()
                onError(e.message ?: "Préparation du téléchargement impossible")
            }
        })
    }

    fun pauseAll(context: Context) =
        DownloadService.sendPauseDownloads(context, NovayDownloadService::class.java, /* foreground= */ false)

    fun resumeAll(context: Context) =
        DownloadService.sendResumeDownloads(context, NovayDownloadService::class.java, /* foreground= */ false)

    fun remove(context: Context, id: String) =
        DownloadService.sendRemoveDownload(context, NovayDownloadService::class.java, id, /* foreground= */ false)

    fun downloadId(seasonUrl: String, episodeNumber: Int): String =
        "${seasonUrl.trimEnd('/')}#ep$episodeNumber"

    /** L'épisode de cette saison est-il téléchargé (terminé) ? */
    fun isDownloaded(context: Context, seasonUrl: String, episodeNumber: Int): Boolean =
        manager(context).downloadIndex.getDownload(downloadId(seasonUrl, episodeNumber))
            ?.state == Download.STATE_COMPLETED

    /** Snapshot des téléchargements pour l'UI (index persisté). */
    fun snapshot(context: Context): List<DownloadInfo> {
        val out = mutableListOf<DownloadInfo>()
        manager(context).downloadIndex.getDownloads().use { cursor ->
            while (cursor.moveToNext()) {
                val d = cursor.download
                out.add(
                    DownloadInfo(
                        id = d.request.id,
                        state = d.state,
                        percent = d.percentDownloaded,
                        meta = EpisodeDownloadMeta.fromJson(d.request.data),
                        bytesDownloaded = d.bytesDownloaded,
                        totalBytes = d.contentLength
                    )
                )
            }
        }
        return out
    }

    /** MediaSource de lecture : cache offline d'abord, réseau en repli. */
    fun offlineMediaSource(
        context: Context,
        stream: ResolvedStream
    ): MediaSource {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(stream.headers["User-Agent"] ?: SamaParsers.UA)
            .setDefaultRequestProperties(stream.headers - "User-Agent")
            .setAllowCrossProtocolRedirects(true)
        val cacheFactory = CacheDataSource.Factory()
            .setCache(cache(context))
            .setUpstreamDataSourceFactory(httpFactory)
            .setCacheWriteDataSinkFactory(null) // lecture : pas de ré-écriture
        return if (stream.isHls) {
            HlsMediaSource.Factory(cacheFactory).createMediaSource(MediaItem.fromUri(stream.url))
        } else {
            ProgressiveMediaSource.Factory(cacheFactory)
                .createMediaSource(MediaItem.fromUri(stream.url))
        }
    }

    /**
     * Notification de progression PILOTABLE (V2.3, inspiration Ketch) :
     * boutons Pause / Reprendre / Annuler directement dans le tiroir de
     * notifications — inutile de rouvrir l'appli pour mettre en pause.
     */
    fun progressNotification(
        context: Context,
        downloads: List<Download>,
        notMetRequirements: Int
    ): Notification {
        val active = downloads.filter {
            it.state == Download.STATE_DOWNLOADING || it.state == Download.STATE_RESTARTING
        }
        val queued = downloads.filter { it.state == Download.STATE_QUEUED }
        val pausedGlobally = runCatching {
            manager(context).downloadsPaused
        }.getOrDefault(false)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Téléchargements NoVaY")
            .setOnlyAlertOnce(true)
            .setOngoing(true)

        when {
            active.isNotEmpty() -> {
                val percents = active.map { it.percentDownloaded }.filter { it >= 0f }
                val percent = if (percents.isEmpty()) -1 else percents.sum().toInt() / percents.size
                val suffix = if (queued.isNotEmpty()) " · ${queued.size} en file" else ""
                builder
                    .setContentText(
                        "${active.size} en cours · ${percent.coerceAtLeast(0)} %$suffix"
                    )
                    .setProgress(100, percent.coerceAtLeast(0), percent < 0)
                    .addAction(
                        0, "Pause",
                        actionIntent(context, NovayDownloadService.ACTION_PAUSE_ALL, 1)
                    )
            }
            pausedGlobally || queued.isNotEmpty() -> {
                val label = when {
                    notMetRequirements != 0 -> "En attente du réseau"
                    pausedGlobally -> "En pause"
                    else -> "En file"
                }
                builder
                    .setContentText(label)
                    .addAction(
                        0, "Reprendre",
                        actionIntent(context, NovayDownloadService.ACTION_RESUME_ALL, 2)
                    )
            }
            else -> builder.setContentText("Terminé")
        }

        // Annuler reste possible tant qu'il y a du travail.
        if (active.isNotEmpty() || queued.isNotEmpty()) {
            builder.addAction(
                0, "Annuler",
                actionIntent(context, NovayDownloadService.ACTION_CANCEL_ALL, 3)
            )
        }
        return builder.build()
    }

    private fun actionIntent(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            context.applicationContext,
            requestCode,
            Intent(context.applicationContext, NovayDownloadService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE
        )

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Téléchargements", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun hostOf(url: String): String? = try {
        URI(url).host
    } catch (_: Exception) {
        null
    }
}

/**
 * DataSource HTTP qui signe chaque requête avec les en-têtes enregistrés
 * pour l'hôte demandé (Referer/Origin du lecteur d'origine).
 *
 * La Factory de DefaultHttpDataSource COPIE la map d'en-têtes
 * (clearAndSet) : un registre « vivant » ne peut pas s'y brancher. On
 * reconstruit donc le délégué à chaque open(), avec les en-têtes de
 * L'URL ouverte — un DataSource par instance = aucune course possible
 * entre téléchargements parallèles.
 */
@UnstableApi
class RegistryDataSource : DataSource {

    private val listeners = mutableListOf<TransferListener>()
    private var delegate: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        listeners.add(transferListener)
        delegate?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val host = dataSpec.uri.host ?: ""
        val headers = StreamHeadersRegistry.headersFor(host)
        val ds = DefaultHttpDataSource.Factory()
            .setUserAgent(headers["User-Agent"] ?: SamaParsers.UA)
            .setDefaultRequestProperties(headers - "User-Agent")
            .setAllowCrossProtocolRedirects(true)
            .createDataSource()
        listeners.forEach { ds.addTransferListener(it) }
        delegate = ds
        return ds.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        delegate?.read(buffer, offset, length)
            ?: throw IllegalStateException("DataSource non ouvert")

    override fun getUri() = delegate?.uri

    override fun close() {
        delegate?.close()
    }
}

@UnstableApi
class RegistryDataSourceFactory : DataSource.Factory {
    override fun createDataSource(): DataSource = RegistryDataSource()
}
