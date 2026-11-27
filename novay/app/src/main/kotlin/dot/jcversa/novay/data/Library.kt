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
    val lastCounts: Map<String, Int> = emptyMap(),
    val genres: List<String> = emptyList()
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
        posterUrl = posterUrl,
        genres = genres
    )
}

/**
 * Collection personnalisée d'animés dans la bibliothèque.
 */
data class LibraryCollection(
    val id: String,
    val name: String,
    val animeUrls: List<String> = emptyList(),
    val createdAt: Long = 0L,
)

/**
 * Bibliothèque NoVaY : PURES pour l'encode/décode/la fusion (testées
 * JVM), persistance SharedPreferences — même convention que
 * [WatchHistory].
 */
object Library {

    private const val PREFS = "novay_library"
    private const val KEY = "entries"
    private const val KEY_COLLECTIONS = "collections"

    /** Fusionne [entry] : l'anime passe en tête, l'ancien état (compteurs)
     *  est conservé, la liste reste bornée. */
    fun upsert(entries: List<LibraryEntry>, entry: LibraryEntry): List<LibraryEntry> {
        val kept = entries.filter { it.url.trimEnd('/') != entry.url.trimEnd('/') }
        val normalizedGenres = GenreCatalog.normalizeList(entry.genres)
        val cleanIncomingPoster = ImageUrlSanitizer.sanitizePosterUrl(entry.posterUrl)
        val merged = entries.firstOrNull { it.url.trimEnd('/') == entry.url.trimEnd('/') }
            ?.let { existing ->
                val resolvedPoster = cleanIncomingPoster
                    ?: ImageUrlSanitizer.sanitizePosterUrl(existing.posterUrl)
                entry.copy(
                    posterUrl = resolvedPoster,
                    baseline = existing.baseline,
                    lastCounts = existing.lastCounts,
                    genres = if (normalizedGenres.isNotEmpty()) normalizedGenres else existing.genres,
                )
            }
            ?: entry.copy(
                posterUrl = cleanIncomingPoster,
                genres = normalizedGenres,
            )
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
                    .put("genres", JSONArray(e.genres))
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
                val genresArr = o.optJSONArray("genres")
                val rawPoster = o.optString("posterUrl").ifEmpty { null }
                LibraryEntry(
                    title = o.optString("title"),
                    url = o.optString("url"),
                    slug = o.optString("slug"),
                    posterUrl = ImageUrlSanitizer.sanitizePosterUrl(rawPoster) ?: rawPoster,
                    languages = if (langs == null) {
                        emptyList()
                    } else {
                        (0 until langs.length()).mapNotNull { langs.opt(it) as? String }
                    },
                    addedAt = o.optLong("addedAt"),
                    baseline = countsFromJson(o.optJSONObject("baseline")),
                    lastCounts = countsFromJson(o.optJSONObject("lastCounts")),
                    genres = if (genresArr == null) {
                        emptyList()
                    } else {
                        GenreCatalog.normalizeList(
                            (0 until genresArr.length()).mapNotNull { genresArr.opt(it) as? String }
                        )
                    },
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ── Collections pures ─────────────────────────────────────────────────

    fun defaultCollections(): List<LibraryCollection> = listOf(
        LibraryCollection(id = "coups_de_coeur", name = "Coups de cœur"),
        LibraryCollection(id = "a_voir", name = "À regarder"),
    )

    fun upsertCollection(
        collections: List<LibraryCollection>,
        collection: LibraryCollection,
    ): List<LibraryCollection> {
        val cleanName = collection.name.trim()
        if (cleanName.isEmpty()) return collections
        val normalizedUrls = collection.animeUrls.map { it.trimEnd('/') }.distinct()
        val updated = collection.copy(name = cleanName, animeUrls = normalizedUrls)
        val index = collections.indexOfFirst { it.id == updated.id }
        return if (index >= 0) {
            collections.toMutableList().apply { set(index, updated) }
        } else {
            collections + updated
        }
    }

    fun deleteCollection(
        collections: List<LibraryCollection>,
        collectionId: String,
    ): List<LibraryCollection> = collections.filterNot { it.id == collectionId }

    fun toggleAnimeInCollection(
        collections: List<LibraryCollection>,
        collectionId: String,
        animeUrl: String,
    ): List<LibraryCollection> {
        val normalized = animeUrl.trimEnd('/')
        if (normalized.isEmpty()) return collections
        return collections.map { col ->
            if (col.id != collectionId) {
                col
            } else {
                val existing = col.animeUrls.map { it.trimEnd('/') }
                val nextUrls = if (normalized in existing) {
                    existing.filterNot { it == normalized }
                } else {
                    (existing + normalized).distinct()
                }
                col.copy(animeUrls = nextUrls)
            }
        }
    }

    fun addAnimesToCollection(
        collections: List<LibraryCollection>,
        collectionId: String,
        animeUrls: List<String>,
    ): List<LibraryCollection> {
        val normalizedTargets = animeUrls.map { it.trimEnd('/') }.filter { it.isNotEmpty() }
        if (normalizedTargets.isEmpty()) return collections
        return collections.map { col ->
            if (col.id != collectionId) {
                col
            } else {
                val merged = (col.animeUrls.map { it.trimEnd('/') } + normalizedTargets).distinct()
                col.copy(animeUrls = merged)
            }
        }
    }

    fun encodeCollections(collections: List<LibraryCollection>): String {
        val arr = JSONArray()
        for (c in collections) {
            arr.put(
                JSONObject()
                    .put("id", c.id)
                    .put("name", c.name)
                    .put("animeUrls", JSONArray(c.animeUrls))
                    .put("createdAt", c.createdAt)
            )
        }
        return arr.toString()
    }

    fun decodeCollections(raw: String?): List<LibraryCollection> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id").trim()
                val name = o.optString("name").trim()
                if (id.isEmpty() || name.isEmpty()) return@mapNotNull null
                val urlsArr = o.optJSONArray("animeUrls")
                val urls = if (urlsArr == null) {
                    emptyList()
                } else {
                    (0 until urlsArr.length()).mapNotNull { idx ->
                        (urlsArr.opt(idx) as? String)?.trimEnd('/')?.takeIf { it.isNotEmpty() }
                    }.distinct()
                }
                LibraryCollection(
                    id = id,
                    name = name,
                    animeUrls = urls,
                    createdAt = o.optLong("createdAt"),
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

    // ── Persistance (SharedPreferences + cache mémoire) ───────────────────

    @Volatile private var cachedEntries: List<LibraryEntry>? = null
    @Volatile private var cachedCollections: List<LibraryCollection>? = null

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(context: Context): List<LibraryEntry> {
        cachedEntries?.let { return it }
        return synchronized(this) {
            cachedEntries ?: decode(prefs(context).getString(KEY, null)).also {
                cachedEntries = it
            }
        }
    }

    fun save(context: Context, entries: List<LibraryEntry>) {
        synchronized(this) {
            if (cachedEntries == entries) return
            cachedEntries = entries
            prefs(context).edit().putString(KEY, encode(entries)).apply()
        }
    }

    fun collections(context: Context): List<LibraryCollection> {
        cachedCollections?.let { return it }
        return synchronized(this) {
            cachedCollections ?: run {
                val p = prefs(context)
                val loaded = if (!p.contains(KEY_COLLECTIONS)) {
                    defaultCollections().also {
                        p.edit().putString(KEY_COLLECTIONS, encodeCollections(it)).apply()
                    }
                } else {
                    decodeCollections(p.getString(KEY_COLLECTIONS, null))
                }
                cachedCollections = loaded
                loaded
            }
        }
    }

    fun saveCollections(context: Context, collections: List<LibraryCollection>) {
        synchronized(this) {
            cachedCollections = collections
            prefs(context).edit().putString(KEY_COLLECTIONS, encodeCollections(collections)).apply()
        }
    }

    fun createCollection(context: Context, name: String): LibraryCollection? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        return synchronized(this) {
            val current = collections(context)
            val baseSlug = clean.lowercase()
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
                .ifEmpty { "collection" }
            val uniqueId = if (current.none { it.id == baseSlug }) {
                baseSlug
            } else {
                "${baseSlug}_${System.currentTimeMillis()}"
            }
            val created = LibraryCollection(
                id = uniqueId,
                name = clean,
                createdAt = System.currentTimeMillis(),
            )
            saveCollections(context, upsertCollection(current, created))
            created
        }
    }

    fun removeCollection(context: Context, collectionId: String) {
        synchronized(this) {
            val next = deleteCollection(collections(context), collectionId)
            saveCollections(context, next)
        }
    }

    fun toggleInCollection(context: Context, collectionId: String, animeUrl: String): Boolean {
        val normalized = animeUrl.trimEnd('/')
        return synchronized(this) {
            val next = toggleAnimeInCollection(collections(context), collectionId, normalized)
            saveCollections(context, next)
            next.firstOrNull { it.id == collectionId }?.animeUrls?.contains(normalized) == true
        }
    }

    fun addManyToCollection(context: Context, collectionId: String, animeUrls: List<String>) {
        synchronized(this) {
            val next = addAnimesToCollection(collections(context), collectionId, animeUrls)
            saveCollections(context, next)
        }
    }

    /** Ajoute/enlève un anime ; retourne true s'il est DANS la bibliothèque. */
    fun toggle(
        context: Context,
        entry: LibraryEntry
    ): Boolean = synchronized(this) {
        val current = all(context)
        val normalized = entry.url.trimEnd('/')
        val inLibrary = current.any { it.url.trimEnd('/') == normalized }
        val next = if (inLibrary) remove(current, entry.url) else upsert(current, entry)
        save(context, next)
        !inLibrary
    }

    fun isInLibrary(context: Context, url: String): Boolean {
        val normalized = url.trimEnd('/')
        return all(context).any { it.url.trimEnd('/') == normalized }
    }

    /** Enregistre un comptage réseau pour un anime suivi. */
    fun recordCounts(context: Context, url: String, counts: Map<String, Int>) {
        synchronized(this) {
            val current = all(context)
            val next = withCounts(current, url, counts)
            if (next != current) save(context, next)
        }
    }

    /** La saison a été ouverte : ses nouveaux épisodes sont consommés. */
    fun markSeen(context: Context, seasonUrl: String, count: Int) {
        synchronized(this) {
            val current = all(context)
            val next = markSeasonSeen(current, seasonUrl, count)
            if (next != current) save(context, next)
        }
    }

    /** Met à jour les genres d'un animé déjà suivi sans réinitialiser ses compteurs. */
    fun updateGenres(context: Context, url: String, genres: List<String>) {
        val normalizedGenres = GenreCatalog.normalizeList(genres)
        if (normalizedGenres.isEmpty()) return
        synchronized(this) {
            val normalizedUrl = url.trimEnd('/')
            val current = all(context)
            var changed = false
            val next = current.map { entry ->
                if (entry.url.trimEnd('/') == normalizedUrl && entry.genres != normalizedGenres) {
                    changed = true
                    entry.copy(genres = normalizedGenres)
                } else {
                    entry
                }
            }
            if (changed) save(context, next)
        }
    }

    /** Répare ou met à jour l'URL d'affiche d'un animé suivi sans toucher à ses compteurs ni son ordre. */
    fun updatePoster(context: Context, url: String, posterUrl: String): Boolean {
        val normalizedPoster = ImageUrlSanitizer.sanitizePosterUrl(posterUrl) ?: return false
        val targetKey = catalogIdentityKey(url)
        synchronized(this) {
            val current = all(context)
            var changed = false
            val next = current.map { entry ->
                if (catalogIdentityKey(entry.url) == targetKey && entry.posterUrl != normalizedPoster) {
                    changed = true
                    entry.copy(posterUrl = normalizedPoster)
                } else {
                    entry
                }
            }
            if (changed) save(context, next)
            return changed
        }
    }

    private fun catalogIdentityKey(url: String): String {
        val clean = url.trim().trimEnd('/')
        val idx = clean.indexOf("/catalogue/", ignoreCase = true)
        return if (idx >= 0) clean.substring(idx).lowercase() else clean
    }
}
