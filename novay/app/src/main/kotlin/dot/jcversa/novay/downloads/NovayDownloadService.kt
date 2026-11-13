package dot.jcversa.novay.downloads

import android.app.Notification
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler

/**
 * Service de téléchargement media3 : progression en notification,
 * reprise automatique après redémarrage, un téléchargement à la fois
 * (les en-têtes par hôte ne courent aucun risque de course).
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class NovayDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    NOTIFICATION_UPDATE_INTERVAL_MS,
    Downloads.CHANNEL_ID,
    dot.jcversa.novay.R.string.app_name,
    0
) {
    companion object {
        const val FOREGROUND_NOTIFICATION_ID = 42
        const val NOTIFICATION_UPDATE_INTERVAL_MS = 1000L
    }

    override fun getDownloadManager(): androidx.media3.exoplayer.offline.DownloadManager =
        Downloads.manager(applicationContext)

    /** Pas de scheduler platform (JobScheduler) : la reprise passe par
     *  la réouverture de l'appli (DownloadManager recharge l'index). */
    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification = Downloads.progressNotification(applicationContext, downloads, notMetRequirements)
}
