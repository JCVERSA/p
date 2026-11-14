package dot.jcversa.novay.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
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
import androidx.media3.exoplayer.offline.DownloadCursor
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
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

    @Volatile private var appContext: Context? = null
    @Volatile private var cacheInternal: androidx.media3.datasource.cache.SimpleCache? = null
    @Volatile private var managerInternal: DownloadManager? = null
    @Volatile private var notificationHelper: DownloadNotificationHelper? = null

    /** État d'un téléchargement, exposé à l'UI. */
    data class DownloadInfo(
        val id: String,
        val state: Int,
        val percent: Float,
        val meta: EpisodeDownloadMeta?
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
                        meta = EpisodeDownloadMeta.fromJson(d.request.data)
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

    fun progressNotification(
        context: Context,
        downloads: List<Download>,
        notMetRequirements: Int
    ): Notification {
        val helper = notificationHelper ?: synchronized(this) {
            notificationHelper ?: DownloadNotificationHelper(context, CHANNEL_ID)
                .also { notificationHelper = it }
        }
        return helper.buildProgressNotification(
            context,
            android.R.drawable.stat_sys_download,
            /* contentIntent= */ null,
            "Téléchargements NoVaY",
            downloads,
            notMetRequirements
        )
    }

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
