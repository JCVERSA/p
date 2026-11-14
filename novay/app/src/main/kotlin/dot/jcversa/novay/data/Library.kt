package dot.jcversa.novay.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Un anime suivi en bibliothèque (V2.4, design Mihon) : tout ce qu'il
 * faut pour l'afficher en grille, re-trouver sa fiche, et détecter les
 * nouveaux épisodes sans tout retélécharger.
 *
 * Compteurs par saison :
 *  - [baseline]     = nombre d'épisodes quand la saison a été « vue »
 *                     (ajout en bibliothèque ou ouverture de la saison) ;
 *  - [lastCounts]   = dernier comptage réseau connu ;
 *  - nouveaux épisodes = Σ max(0, lastCounts - baseline).
 */
data class LibraryEntry(
    val title: String,
    val url: String,
    val slug: String = "",
    val posterUrl: String? = null,
    /** Langues disponibles (« VF », « VOSTFR »…), pour les filtres. */
    val languages: List<String> = emptyList(),
    val addedAt: Long = 0L,
    val baseline: Map<String, Int> = emptyMap(),
    val lastCounts: Map<String, Int> = emptyMap()
) {
    /** Nombre d'épisodes apparus depuis la dernière visite. */
    val newEpisodes: Int
        get() = lastCounts.entries.sumOf { (url, count) ->
            (count - (baseline[url] ?: count)).coerceAtLeast(0)
        }

    /** Re-construit l'objet recherche (entrée de fiche). */
    fun toSearchResult() = AnimeSearchResult(
        title = title,
        url = url,
        slug = slug,
        posterUrl = posterUrl
    )
}

/**
 * Bibliothèque NoVaY : PURES pour l'encode/décode/la fusion (testées
 * JVM), persistance SharedPreferences — même convention que
 * [WatchHistory].
 */
object Library {

    private const val PREFS = "novay_library"
    private const val KEY = "entries"

    /** Fusionne [entry] : l'anime passe en tête, l'ancien état (compteurs)
     *  est conservé, la liste reste bornée. */
    fun upsert(entries: List<LibraryEntry>, entry: LibraryEntry): List<LibraryEntry> {
        val kept = entries.filter { it.url.trimEnd('/') != entry.url.trimEnd('/') }
        val merged = entries.firstOrNull { it.url.trimEnd('/') == entry.url.trimEnd('/') }
            ?.let { entry.copy(baseline = it.baseline, lastCounts = it.lastCounts) }
            ?: entry
        return (listOf(merged) + kept).take(MAX)
    }

    fun remove(entries: List<LibraryEntry>, url: String): List<LibraryEntry> =
        entries.filter { it.url.trimEnd('/') != url.trimEnd('/') }

    /** Enregistre un comptage réseau : les nouveaux épisodes se calculent
     *  contre la baseline, jamais contre le comptage précédent. */
    fun withCounts(
        entries: List<LibraryEntry>,
        url: String,
        counts: Map<String, Int>
    ): List<LibraryEntry> = entries.map {
        if (it.url.trimEnd('/') == url.trimEnd('/') && counts.isNotEmpty()) {
            it.copy(lastCounts = counts)
        } else {
            it
        }
    }

    /** La saison vient d'être ouverte : ses nouveaux épisodes sont « vus ». */
    fun markSeasonSeen(
        entries: List<LibraryEntry>,
        seasonUrl: String,
        count: Int
    ): List<LibraryEntry> = entries.map {
        if (it.lastCounts.containsKey(seasonUrl.trimEnd('/')) ||
            it.baseline.containsKey(seasonUrl.trimEnd('/')
        )) {
            it.copy(baseline = it.baseline + (seasonUrl.trimEnd('/') to count))
        } else {
            it
        }
    }

    fun encode(entries: List<LibraryEntry>): String {
        val arr = JSONArray()
        for (e in entries) {
            arr.put(
                JSONObject()
                    .put("title", e.title)
                    .put("url", e.url)
                    .put("slug", e.slug)
                    .put("posterUrl", e.posterUrl ?: "")
                    .put("languages", JSONArray(e.languages))
                    .put("addedAt", e.addedAt)
                    .put("baseline", countsJson(e.baseline))
                    .put("lastCounts", countsJson(e.lastCounts))
            )
        }
        return arr.toString()
    }

    fun decode(raw: String?): List<LibraryEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val langs = o.optJSONArray("languages")
                LibraryEntry(
                    title = o.optString("title"),
                    url = o.optString("url"),
                    slug = o.optString("slug"),
                    posterUrl = o.optString("posterUrl").ifEmpty { null },
                    languages = if (langs == null) {
                        emptyList()
                    } else {
                        (0 until langs.length()).mapNotNull { langs.opt(it) as? String }
                    },
                    addedAt = o.optLong("addedAt"),
                    baseline = countsFromJson(o.optJSONObject("baseline")),
                    lastCounts = countsFromJson(o.optJSONObject("lastCounts"))
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private const val MAX = 100

    private fun countsJson(counts: Map<String, Int>): JSONObject {
        val o = JSONObject()
        for ((k, v) in counts) o.put(k, v)
        return o
    }

    private fun countsFromJson(o: JSONObject?): Map<String, Int> {
        if (o == null) return emptyMap()
        val map = mutableMapOf<String, Int>()
        for (k in o.keys()) map[k] = o.optInt(k)
        return map
    }

    // ── Persistance (SharedPreferences) ───────────────────────────────────

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(context: Context): List<LibraryEntry> = decode(prefs(context).getString(KEY, null))

    fun save(context: Context, entries: List<LibraryEntry>) {
        prefs(context).edit().putString(KEY, encode(entries)).apply()
    }

    /** Ajoute/enlève un anime ; retourne true s'il est DANS la bibliothèque. */
    fun toggle(
        context: Context,
        entry: LibraryEntry
    ): Boolean {
        val current = all(context)
        val inLibrary = current.any { it.url.trimEnd('/') == entry.url.trimEnd('/') }
        val next = if (inLibrary) remove(current, entry.url) else upsert(current, entry)
        save(context, next)
        return !inLibrary
    }

    fun isInLibrary(context: Context, url: String): Boolean =
        all(context).any { it.url.trimEnd('/') == url.trimEnd('/') }

    /** Enregistre un comptage réseau pour un anime suivi. */
    fun recordCounts(context: Context, url: String, counts: Map<String, Int>) {
        save(context, withCounts(all(context), url, counts))
    }

    /** La saison a été ouverte : ses nouveaux épisodes sont consommés. */
    fun markSeen(context: Context, seasonUrl: String, count: Int) {
        save(context, markSeasonSeen(all(context), seasonUrl, count))
    }
}
