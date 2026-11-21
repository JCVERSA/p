package dot.jcversa.novay.downloads

import android.content.Context
import android.content.SharedPreferences

/**
 * Registre des en-têtes HTTP exigés par chaque hôte de flux
 * (Referer/Origin du lecteur d'origine) — utilisé par les téléchargements
 * ET la lecture hors-ligne, qui passent par des DataSource différents de
 * la chaîne OkHttp de SamaSource.
 *
 * Chaque flux résolu enregistre ses en-têtes pour son hôte ; le registre
 * persiste (SharedPreferences) pour survivre aux redémarrages — un
 * téléchargement repris après redémarrage doit pouvoir re-signer ses
 * requêtes segment par segment.
 */
object StreamHeadersRegistry {

    private const val PREFS = "novay_stream_headers"

    @Volatile
    private var prefs: SharedPreferences? = null
    private val memory = HashMap<String, Map<String, String>>()

    /** À appeler une fois au démarrage (MainActivity). */
    fun init(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs?.all?.forEach { (host, value) ->
                val headers = (value as? String)?.let { deserialize(it) }
                if (headers != null) memory[host] = headers
            }
        }
    }

    /** Enregistre les en-têtes d'un hôte (flux résolu). */
    fun register(host: String, headers: Map<String, String>) {
        if (host.isEmpty() || headers.isEmpty()) return
        synchronized(this) {
            memory[host] = headers
            prefs?.edit()?.putString(host, serialize(headers))?.apply()
        }
    }

    /** En-têtes d'un hôte (vide si inconnu). */
    fun headersFor(host: String): Map<String, String> =
        synchronized(this) { memory[host] } ?: emptyMap()

    private fun serialize(headers: Map<String, String>): String =
        headers.entries.joinToString("\n") { "${it.key}=${it.value}" }

    private fun deserialize(raw: String): Map<String, String> =
        raw.split("\n").mapNotNull { line ->
            val idx = line.indexOf('=')
            if (idx <= 0) null else line.substring(0, idx) to line.substring(idx + 1)
        }.toMap()
}
