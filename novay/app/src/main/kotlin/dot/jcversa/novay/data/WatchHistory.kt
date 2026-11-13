package dot.jcversa.novay.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Entrée d'historique « Reprendre la lecture » — tout ce qu'il faut pour
 * rouvrir LE bon épisode sans repasser par la recherche : la saison, le
 * numéro, l'affiche, ET les identifiants de lecteur (lienIds + jeton).
 *
 * Le jeton peut être expiré au moment de la reprise : SamaSource.stream()
 * le rafraîchit en rechargeant la page de saison (episode.url).
 */
data class WatchHistoryEntry(
    val animeTitle: String,
    val animeUrl: String,
    val posterUrl: String?,
    val seasonName: String,
    val seasonUrl: String,
    val episodeNumber: Int,
    val episodeLabel: String,
    val lienIds: List<String>,
    val jeton: String,
    val watchedAt: Long
)

/**
 * Historique de lecture : PURES pour l'encode/décode/la fusion (testées
 * JVM), persistance SharedPreferences pour le stockage.
 *
 * Une entrée par épisode (revoir un épisode le remonte en tête, pas de
 * doublon), bornée à [MAX] entrées, ordre « récent d'abord ».
 */
object WatchHistory {

    private const val PREFS = "novay_watch_history"
    private const val KEY = "entries"
    private const val MAX = 20

    /** Clé d'unicité d'un épisode (même convention que les téléchargements). */
    fun entryKey(seasonUrl: String, episodeNumber: Int): String =
        "${seasonUrl.trimEnd('/')}#ep$episodeNumber"

    /**
     * Fusionne [entry] dans [entries] : l'épisode (re)vu passe en tête,
     * l'ancienne occurrence est remplacée, la liste reste bornée.
     */
    fun upsert(entries: List<WatchHistoryEntry>, entry: WatchHistoryEntry): List<WatchHistoryEntry> {
        val key = entryKey(entry.seasonUrl, entry.episodeNumber)
        val kept = entries.filter { entryKey(it.seasonUrl, it.episodeNumber) != key }
        return (listOf(entry) + kept).take(MAX)
    }

    fun encode(entries: List<WatchHistoryEntry>): String {
        val arr = JSONArray()
        for (e in entries) {
            arr.put(
                JSONObject()
                    .put("animeTitle", e.animeTitle)
                    .put("animeUrl", e.animeUrl)
                    .put("posterUrl", e.posterUrl ?: "")
                    .put("seasonName", e.seasonName)
                    .put("seasonUrl", e.seasonUrl)
                    .put("episodeNumber", e.episodeNumber)
                    .put("episodeLabel", e.episodeLabel)
                    .put("lienIds", JSONArray(e.lienIds))
                    .put("jeton", e.jeton)
                    .put("watchedAt", e.watchedAt)
            )
        }
        return arr.toString()
    }

    fun decode(raw: String?): List<WatchHistoryEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val liens = o.optJSONArray("lienIds")
                WatchHistoryEntry(
                    animeTitle = o.optString("animeTitle"),
                    animeUrl = o.optString("animeUrl"),
                    posterUrl = o.optString("posterUrl").ifEmpty { null },
                    seasonName = o.optString("seasonName"),
                    seasonUrl = o.optString("seasonUrl"),
                    episodeNumber = o.optInt("episodeNumber"),
                    episodeLabel = o.optString("episodeLabel"),
                    lienIds = if (liens == null) emptyList() else (0 until liens.length()).mapNotNull { liens.opt(it) as? String },
                    jeton = o.optString("jeton"),
                    watchedAt = o.optLong("watchedAt")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ── Persistance (SharedPreferences) ───────────────────────────────────

    fun record(context: Context, entry: WatchHistoryEntry) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY, encode(upsert(decode(prefs.getString(KEY, null)), entry)))
            .apply()
    }

    /** Entrées récentes (la plus fraîche en tête). */
    fun recent(context: Context): List<WatchHistoryEntry> =
        decode(
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, null)
        )

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY).apply()
    }
}
