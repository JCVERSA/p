package dot.jcversa.novay.downloads

import android.app.Notification
import android.content.Intent
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler

/**
 * Service de téléchargement media3 : progression en notification
 * PILOTABLE (boutons Pause / Reprendre / Annuler, V2.3), reprise
 * automatique après redémarrage, un téléchargement à la fois (les
 * en-têtes par hôte ne courent aucun risque de course).
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

        /** Actions des boutons de notification (V2.3). */
        const val ACTION_PAUSE_ALL = "dot.jcversa.novay.downloads.PAUSE_ALL"
        const val ACTION_RESUME_ALL = "dot.jcversa.novay.downloads.RESUME_ALL"
        const val ACTION_CANCEL_ALL = "dot.jcversa.novay.downloads.CANCEL_ALL"
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

    /**
     * Boutons de la notification : on intercepte NOS actions avant de
     * rendre la main à media3 (qui gère les siennes). Chaque commande
     * repart par les helpers officiels — le service se met à jour et
     * rafraîchit sa notification tout seul.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE_ALL -> DownloadService.sendPauseDownloads(
                this, NovayDownloadService::class.java, /* foreground= */ false
            )
            ACTION_RESUME_ALL -> DownloadService.sendResumeDownloads(
                this, NovayDownloadService::class.java, /* foreground= */ false
            )
            ACTION_CANCEL_ALL -> DownloadService.sendRemoveAllDownloads(
                this, NovayDownloadService::class.java, /* foreground= */ false
            )
            else -> return super.onStartCommand(intent, flags, startId)
        }
        return START_STICKY
    }
}
