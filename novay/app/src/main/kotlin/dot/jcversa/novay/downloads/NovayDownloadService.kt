package dot.jcversa.novay.downloads

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Service foreground des téléchargements (V2.5) : héberge la
 * notification pilotable pendant que le moteur fichier travaille.
 * Les actions du tiroir de notifications y arrivent (Pause / Reprendre /
 * Annuler), c'est tout — le moteur vit dans FileDownloadEngine.
 */
class NovayDownloadService : Service() {

    companion object {
        const val ACTION_PAUSE_ALL = "dot.jcversa.novay.downloads.PAUSE_ALL"
        const val ACTION_RESUME_ALL = "dot.jcversa.novay.downloads.RESUME_ALL"
        const val ACTION_CANCEL_ALL = "dot.jcversa.novay.downloads.CANCEL_ALL"
        private const val ACTION_REFRESH = "dot.jcversa.novay.downloads.REFRESH"
        private const val NOTIFICATION_ID = 1

        /** Le moteur demande un rafraîchissement de la notification. */
        fun notifyState(context: Context) {
            val intent = Intent(context, NovayDownloadService::class.java)
                .setAction(ACTION_REFRESH)
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }.onFailure {
                runCatching { context.startService(intent) }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE_ALL -> Downloads.pauseAll(this)
            ACTION_RESUME_ALL -> Downloads.resumeAll(this)
            ACTION_CANCEL_ALL -> {
                Downloads.snapshot(this).forEach { Downloads.remove(this, it.id) }
                stopForegroundAndSelf()
                return START_NOT_STICKY
            }
        }
        // Moteur silencieux (tout fini/échoué) : on s'arrête — la pause,
        // elle, garde la notification pour pouvoir reprendre du tiroir.
        val working = Downloads.snapshot(this).any { !it.isCompleted && !it.isFailed }
        if (!working) {
            stopForegroundAndSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, Downloads.progressNotification(this))
        return START_STICKY
    }

    private fun stopForegroundAndSelf() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}
