package dot.jcversa.novay.downloads

import android.app.Notification
import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

/**
 * Moteur de téléchargement FICHIER (V2.5 « novabox », design du bot) :
 * les épisodes finissent en VRAIS MP4 dans Download/NoVaY/ du téléphone —
 * visibles dans Fichiers, la galerie, VLC — et se lisent dans l'app
 * depuis ces fichiers.
 *
 * Chaîne (comme le bot) :
 *  1. playlist de la variante choisie (résolution du user) ;
 *  2. segments téléchargés un par un (reprise à l'octet près via
 *     segmentsDone persisté + fichiers temporaires) ;
 *  3. assemblage + remux MP4 natif (MediaExtractor/MediaMuxer) — repli
 *     honnête .ts si le flux résiste (TS se lit partout aussi) ;
 *  4. publication : MediaStore.Downloads (Android 10+, sans permission)
 *     ou Download/NoVaY/ classique (Android 7-9, permission stockage).
 *
 * File SÉQUENTIELLE (une seule à la fois), pause/reprise globale, Wi-Fi
 * uniquement avec reprise automatique au retour du réseau non facturé.
 */
object FileDownloadEngine {

    /** États d'une tâche (moteur — les écrans voient DownloadInfo). */
    enum class State { QUEUED, WAITING_WIFI, RUNNING, PAUSED, DONE, FAILED }

    /** Une tâche de téléchargement persistée. */
    data class Task(
        val id: String,
        var meta: EpisodeDownloadMeta,
        var state: State = State.QUEUED,
        var bytesDone: Long = 0L,
        var bytesTotal: Long = 0L,
        var segmentsDone: Int = 0,
        var segmentCount: Int = 0,
        var error: String? = null
    ) {
        val isCompleted: Boolean get() = state == State.DONE
        val isFailed: Boolean get() = state == State.FAILED
        val isActive: Boolean
            get() = state == State.RUNNING || state == State.QUEUED ||
                state == State.WAITING_WIFI
    }

    private const val PREFS = "novay_file_downloads"
    private const val KEY = "tasks"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pumpJob: Job? = null

    @Volatile private var appContext: Context? = null
    @Volatile private var tasksInternal = mutableListOf<Task>()
    @Volatile private var paused = false
    @Volatile private var wifiOnly = false

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Listener de notification (le service s'y branche). */
    @Volatile var onNotification: (() -> Unit)? = null

    // ── Cycle de vie ─────────────────────────────────────────────────────

    @Synchronized
    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        wifiOnly = Downloads.isWifiOnly(context)
        tasksInternal = loadIndex(context).toMutableList()
        // Une tâche interrompue par un kill reprend là où elle en était.
        tasksInternal.forEach {
            if (it.state == State.RUNNING || it.state == State.WAITING_WIFI) {
                it.state = State.QUEUED
            }
        }
        persist()
        watchNetwork()
        pump()
    }

    fun tasks(): List<Task> = tasksInternal.toList()

    fun find(id: String): Task? = tasksInternal.firstOrNull { it.id == id }

    fun setWifiOnly(value: Boolean) {
        wifiOnly = value
        pump()
    }

    fun downloadsPaused(): Boolean = paused

    fun pauseAll() {
        paused = true
        tasksInternal.forEach {
            if (it.state == State.QUEUED || it.state == State.WAITING_WIFI) {
                it.state = State.PAUSED
            }
        }
        persist()
        notifyUpdate()
    }

    fun resumeAll() {
        paused = false
        tasksInternal.forEach { if (it.state == State.PAUSED) it.state = State.QUEUED }
        persist()
        pump()
    }

    /** Annule + SUPPRIME le fichier publié et l'entrée d'index. */
    fun cancel(context: Context, id: String) {
        val task = find(id) ?: return
        tasksInternal.remove(task)
        persist()
        task.meta.localUri?.let { uri -> runCatching { deletePublished(context, uri) } }
        tempDir(context, id).deleteRecursively()
        pump()
    }

    /** Ajoute une tâche (dédupliquée par id) et lance la file. */
    fun enqueue(context: Context, task: Task) {
        init(context)
        if (tasksInternal.any { it.id == task.id }) return
        tasksInternal.add(task)
        persist()
        pump()
    }

    // ── La boucle ────────────────────────────────────────────────────────

    private fun pump() {
        if (pumpJob?.isActive == true) return
        val context = appContext ?: return
        pumpJob = scope.launch {
            while (true) {
                if (paused) break
                if (wifiOnly && isNetworkMetered(context)) {
                    tasksInternal.forEach {
                        if (it.state == State.QUEUED) it.state = State.WAITING_WIFI
                    }
                    persist()
                    notifyUpdate()
                    break
                }
                val task = tasksInternal.firstOrNull {
                    it.state == State.QUEUED || it.state == State.WAITING_WIFI
                } ?: break
                runTask(context, task)
            }
            // File épuisée ou suspendue : notification d'état, puis silence.
            notifyUpdate()
        }
    }

    private fun runTask(context: Context, task: Task) {
        // Android 7-9 : l'ecriture dans Download/NoVaY/ (API File publique)
        // exige WRITE_EXTERNAL_STORAGE. Si elle manque, on echoue proprement
        // et immediatement avec un message clair plutot qu'en cours de route.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            task.state = State.FAILED
            task.error = "Permission de stockage requise - autorise le stockage dans les parametres puis relance"
            persist()
            notifyUpdate()
            return
        }
        task.state = State.RUNNING
        notifyUpdate()
        try {
            downloadSegments(context, task)
            publish(context, task)
            task.state = State.DONE
            tempDir(context, task.id).deleteRecursively()
        } catch (e: Exception) {
            if (e is InterruptedException || e.message == CANCELLED) {
                task.state = State.PAUSED
            } else {
                task.state = State.FAILED
                task.error = e.message ?: e.javaClass.simpleName
            }
        }
        persist()
        notifyUpdate()
    }

    private const val CANCELLED = "novay-cancelled"

    /** Étape 1-2 : playlist + segments (reprise via segmentsDone). */
    private fun downloadSegments(context: Context, task: Task) {
        val meta = task.meta
        var playlistUrl = meta.streamUrl
        var body = fetchText(playlistUrl, meta.headers)
        // Master reçu ? On choisit la variante de la résolution demandée.
        if (meta.isHls && HlsPlaylist.isMasterPlaylist(body)) {
            val variants = dot.jcversa.novay.data.voiranime.VoiranimeParsers
                .parseHlsMasterPlaylist(body, playlistUrl)
            val wanted = meta.qualityLabel.filter { it.isDigit() }.toIntOrNull()
            val variant = variants.firstOrNull { v -> v.height == wanted }
                ?: variants.firstOrNull()
            if (variant == null) throw IOException("Aucune variante lisible dans la playlist")
            playlistUrl = variant.url
            body = fetchText(playlistUrl, meta.headers)
        }
        val playlist = HlsPlaylist.parseMediaPlaylist(body, playlistUrl)
            ?: throw IOException("Playlist illisible (ni master ni média)")

        val dir = tempDir(context, task.id)
        if (!dir.exists()) dir.mkdirs()
        task.segmentCount = playlist.segmentUrls.size
        task.bytesTotal = if (task.bytesTotal > 0) task.bytesTotal else 0L

        // Segment d'initialisation fMP4 : toujours (re)téléchargé en 1er.
        if (playlist.initSegmentUrl != null) {
            val initFile = File(dir, "init.bin")
            if (!initFile.exists() || task.segmentsDone == 0) {
                downloadTo(playlist.initSegmentUrl, meta.headers, initFile)
            }
        }

        val total = playlist.segmentUrls.size
        for (i in task.segmentsDone until total) {
            if (paused) throw InterruptedException()
            val segFile = File(dir, "seg_%05d.bin".format(i))
            downloadTo(playlist.segmentUrls[i], meta.headers, segFile)
            task.segmentsDone = i + 1
            task.bytesDone += segFile.length()
            // Estimation honnête du total (recalibrée au fil des segments).
            if (task.bytesTotal < task.bytesDone) {
                task.bytesTotal = (task.bytesDone.toLong() * (i + 1) / (i + 1)).coerceAtLeast(task.bytesDone)
            }
            val expectedTotal = estimateTotal(task, total)
            if (expectedTotal > 0) task.bytesTotal = expectedTotal
            if (i % 10 == 0) persist()
            notifyUpdate()
        }
        task.bytesTotal = task.bytesDone
        persist()
    }

    /** Total estimé : moyenne des segments reçus × nombre total. */
    private fun estimateTotal(task: Task, totalSegments: Int): Long {
        val done = task.segmentsDone
        if (done <= 0 || totalSegments <= 0) return 0L
        val avg = task.bytesDone / done
        val remaining = (totalSegments - done).toLong()
        return task.bytesDone + avg * remaining
    }

    /** Étape 3-4 : assemblage, remux MP4, publication. */
    private fun publish(context: Context, task: Task) {
        val meta = task.meta
        val dir = tempDir(context, task.id)
        val language = dot.jcversa.novay.data.sama.SamaParsers
            .samaSubPathLanguage(meta.seasonUrl)

        // Assemblage : un seul flux (init éventuel + segments dans l'ordre).
        val combined = File(dir, "combined.bin")
        FileOutputStream(combined).use { out ->
            File(dir, "init.bin").takeIf { it.exists() }?.let { copy(it, out) }
            for (i in 0 until task.segmentsDone) {
                copy(File(dir, "seg_%05d.bin".format(i)), out)
            }
        }

        // Remux MP4 natif (TS → MP4). fMP4 déjà lisible : gardé tel quel.
        val isFragmented = File(dir, "init.bin").exists()
        var extension = "ts"
        var toPublish = combined
        if (!isFragmented) {
            val mp4 = File(dir, "remuxed.mp4")
            if (remuxToMp4(combined, mp4)) {
                extension = "mp4"
                toPublish = mp4
            }
        } else {
            extension = "mp4"
        }

        val baseName = HlsPlaylist.buildEpisodeFileName(
            animeTitle = meta.animeTitle,
            seasonUrl = meta.seasonUrl,
            episodeNumber = meta.episodeNumber,
            language = language,
            qualityLabel = meta.qualityLabel,
            extension = extension
        )

        val uri = writePublishedFile(context, toPublish, baseName)
        task.meta = meta.copy(
            localUri = uri.toString(),
            fileName = displayNameOf(context, uri, baseName)
        )
    }

    // ── Réseau ───────────────────────────────────────────────────────────

    private fun fetchText(url: String, headers: Map<String, String>): String {
        val request = Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> header(k, v) }
        }.build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} pour $url")
            return resp.body?.string() ?: ""
        }
    }

    private fun downloadTo(url: String, headers: Map<String, String>, target: File) {
        val request = Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> header(k, v) }
        }.build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} pour un segment")
            val body = resp.body ?: throw IOException("Segment vide")
            FileOutputStream(target).use { out -> body.byteStream().copyTo(out) }
        }
    }

    private fun isNetworkMetered(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        return cm.isActiveNetworkMetered
    }

    /** Reprise auto au retour en Wi-Fi (requirements façon media3). */
    private fun watchNetwork() {
        val context = appContext ?: return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (wifiOnly && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) {
                    return
                }
                if (!paused && tasksInternal.any { it.state == State.WAITING_WIFI || it.state == State.PAUSED }) {
                    tasksInternal.forEach {
                        if (it.state == State.WAITING_WIFI) it.state = State.QUEUED
                    }
                    pump()
                }
            }
        })
    }

    // ── Remux TS → MP4 (MediaExtractor + MediaMuxer) ──────────────────────

    /**
     * Remuxe un flux TS concaténé en MP4 (pistes vidéo + audio copiées,
     * sans réencodage). Retourne false si le conteneur résiste — l'appelant
     * garde alors le .ts honnête (lisible partout).
     */
    private fun remuxToMp4(input: File, output: File): Boolean {
        val extractor = MediaExtractor()
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        return try {
            extractor.setDataSource(input.absolutePath)
            val trackMap = mutableMapOf<Int, Int>()
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    trackMap[i] = muxer.addTrack(format)
                }
            }
            if (trackMap.isEmpty()) return false
            muxer.start()
            val buffer = ByteBuffer.allocateDirect(1 shl 20)
            val info = MediaCodec.BufferInfo()
            for ((srcTrack, dstTrack) in trackMap) {
                extractor.selectTrack(srcTrack)
                while (true) {
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    info.offset = 0
                    info.size = size
                    info.presentationTimeUs = extractor.sampleTime
                    info.flags = extractor.sampleFlags and MediaCodec.BUFFER_FLAG_KEY_FRAME
                    if (info.presentationTimeUs >= 0) {
                        muxer.writeSampleData(dstTrack, buffer, info)
                    }
                    extractor.advance()
                }
                extractor.unselectTrack(srcTrack)
            }
            muxer.stop()
            true
        } catch (_: Exception) {
            false
        } finally {
            runCatching { muxer.release() }
            runCatching { extractor.release() }
            if (!output.exists() || output.length() == 0L) {
                output.delete()
                return false
            }
        }
    }

    // ── Publication dans Download/NoVaY/ ─────────────────────────────────

    private fun writePublishedFile(context: Context, source: File, baseName: String): android.net.Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, baseName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeTypeOf(baseName))
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/NoVaY")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("MediaStore a refusé l'écriture")
            resolver.openOutputStream(uri)?.use { out -> FileInputStream(source).use { it.copyTo(out) } }
                ?: throw IOException("Flux de sortie indisponible")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } else {
            @Suppress("DEPRECATION")
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "NoVaY"
            )
            if (!dir.exists() && !dir.mkdirs()) {
                throw IOException("Dossier Download/NoVaY incréable (permission stockage ?)")
            }
            val name = HlsPlaylist.availableFileName(baseName) { File(dir, it).exists() }
            val target = File(dir, name)
            FileInputStream(source).use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
            android.net.Uri.fromFile(target)
        }
    }

    private fun displayNameOf(context: Context, uri: android.net.Uri, fallback: String): String {
        if (uri.scheme == "file") return File(uri.path ?: "").name
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else fallback
            } ?: fallback
        }.getOrDefault(fallback)
    }

    private fun deletePublished(context: Context, uriString: String) {
        val uri = android.net.Uri.parse(uriString)
        if (uri.scheme == "file") {
            File(uri.path ?: "").delete()
        } else {
            context.contentResolver.delete(uri, null, null)
        }
    }

    private fun mimeTypeOf(fileName: String): String =
        if (fileName.endsWith(".mp4", true)) "video/mp4" else "video/mp2t"

    private fun tempDir(context: Context, id: String): File =
        File(context.cacheDir, "novay_dl/" + id.replace(Regex("[^a-zA-Z0-9_-]"), "_"))

    private fun copy(from: File, to: java.io.OutputStream) {
        FileInputStream(from).use { it.copyTo(to) }
    }

    // ── Persistance de l'index ────────────────────────────────────────────

    @Synchronized
    private fun persist() {
        val context = appContext ?: return
        val arr = org.json.JSONArray()
        for (t in tasksInternal) {
            arr.put(
                org.json.JSONObject()
                    .put("meta", t.meta.toJson())
                    .put("state", t.state.name)
                    .put("bytesDone", t.bytesDone)
                    .put("bytesTotal", t.bytesTotal)
                    .put("segmentsDone", t.segmentsDone)
                    .put("segmentCount", t.segmentCount)
                    .put("error", t.error ?: "")
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    private fun loadIndex(context: Context): List<Task> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val meta = EpisodeDownloadMeta.fromJson(
                    o.optString("meta").toByteArray(Charsets.UTF_8)
                ) ?: return@mapNotNull null
                Task(
                    id = Downloads.downloadId(meta.seasonUrl, meta.episodeNumber),
                    meta = meta,
                    state = runCatching { State.valueOf(o.optString("state")) }
                        .getOrDefault(State.QUEUED),
                    bytesDone = o.optLong("bytesDone"),
                    bytesTotal = o.optLong("bytesTotal"),
                    segmentsDone = o.optInt("segmentsDone"),
                    segmentCount = o.optInt("segmentCount"),
                    error = o.optString("error").ifEmpty { null }
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun notifyUpdate() {
        onNotification?.invoke()
    }
}
