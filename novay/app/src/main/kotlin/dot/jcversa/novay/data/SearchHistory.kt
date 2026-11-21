package dot.jcversa.novay.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

/**
 * Entrée de recherche récente persistée localement sur l'appareil.
 */
data class SearchHistoryEntry(
    val query: String,
    val searchedAt: Long,
)

/**
 * Historique des recherches Catalogue :
 *  - fonctions PURES pour la normalisation, la déduplication, l'insertion
 *    bornée à [MAX] et l'encode/décode JSON (testées JVM) ;
 *  - persistance SharedPreferences + cache mémoire @Volatile (même convention
 *    que [Library] et [WatchHistory]).
 */
object SearchHistory {

    private const val PREFS = "novay_search_history"
    private const val KEY = "entries"
    const val MAX = 12

    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val MULTI_SPACE = Regex("\\s+")

    /** Normalise une requête (casse + accents + espaces) pour dédupliquer proprement. */
    fun normalizeKey(query: String): String {
        val trimmed = query.trim().replace(MULTI_SPACE, " ")
        if (trimmed.isEmpty()) return ""
        val decomposed = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
        return COMBINING_MARKS.replace(decomposed, "").lowercase()
    }

    /** Insère [query] en tête, déduplique par clé normalisée et borne à [MAX]. */
    fun upsert(
        entries: List<SearchHistoryEntry>,
        query: String,
        searchedAt: Long = System.currentTimeMillis(),
    ): List<SearchHistoryEntry> {
        val clean = query.trim().replace(MULTI_SPACE, " ")
        if (clean.isEmpty()) return entries
        val key = normalizeKey(clean)
        val kept = entries.filter { normalizeKey(it.query) != key }
        return (listOf(SearchHistoryEntry(query = clean, searchedAt = searchedAt)) + kept).take(MAX)
    }

    /** Retire toute entrée correspondant à [query] (sans sensibilité à la casse/accents). */
    fun remove(
        entries: List<SearchHistoryEntry>,
        query: String,
    ): List<SearchHistoryEntry> {
        val key = normalizeKey(query)
        if (key.isEmpty()) return entries
        return entries.filter { normalizeKey(it.query) != key }
    }

    fun encode(entries: List<SearchHistoryEntry>): String {
        val arr = JSONArray()
        for (e in entries) {
            arr.put(
                JSONObject()
                    .put("query", e.query)
                    .put("searchedAt", e.searchedAt),
            )
        }
        return arr.toString()
    }

    fun decode(raw: String?): List<SearchHistoryEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val query = o.optString("query").trim()
                if (query.isEmpty()) return@mapNotNull null
                SearchHistoryEntry(
                    query = query,
                    searchedAt = o.optLong("searchedAt"),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ── Persistance (SharedPreferences + cache mémoire) ───────────────────

    @Volatile
    private var cachedEntries: List<SearchHistoryEntry>? = null

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun recent(context: Context): List<SearchHistoryEntry> {
        cachedEntries?.let { return it }
        return synchronized(this) {
            cachedEntries ?: decode(prefs(context).getString(KEY, null)).also {
                cachedEntries = it
            }
        }
    }

    fun record(
        context: Context,
        query: String,
        searchedAt: Long = System.currentTimeMillis(),
    ): List<SearchHistoryEntry> = synchronized(this) {
        val next = upsert(recent(context), query, searchedAt)
        cachedEntries = next
        prefs(context).edit().putString(KEY, encode(next)).apply()
        next
    }

    fun remove(context: Context, query: String): List<SearchHistoryEntry> = synchronized(this) {
        val next = remove(recent(context), query)
        cachedEntries = next
        prefs(context).edit().putString(KEY, encode(next)).apply()
        next
    }

    fun clear(context: Context): List<SearchHistoryEntry> = synchronized(this) {
        cachedEntries = emptyList()
        prefs(context).edit().remove(KEY).apply()
        emptyList()
    }
}
