package dot.jcversa.novay

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dot.jcversa.novay.data.sama.SamaParsers
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Stratégie de calcul de l'en-tête `Referer` par hôte CDN.
 */
enum class RefererStrategy {
    /** En-tête Referer = origine du CDN lui-même (`https://cdn.example/`). */
    TARGET_ORIGIN,

    /** En-tête Referer = origine active de la source catalogue (`https://anime-sama.to/`). */
    SITE_ORIGIN,

    /** Aucun en-tête Referer envoyé (certains CDN bloquent tout Referer tiers). */
    NONE,
}

/**
 * Pipeline HTTP & Coil central pour toutes les affiches / miniatures NoVaY :
 *  - en-têtes navigateur (`User-Agent`, `Accept` images, `Accept-Language`) ;
 *  - négociation adaptative du `Referer` en cas de HTTP 401/403 (mémorisée par hôte) ;
 *  - réessais exponentiels bornés sur 429 / 5xx / erreurs I/O transitoires (jamais sur 404/410) ;
 *  - rejet des réponses HTML déguisées en HTTP 200 (soft-404 / anti-bot) sans mise en cache ;
 *  - décodage ARGB_8888 fiable (`allowRgb565(false)`) pour préserver transparence et profils WebP/PNG.
 */
object NovayImagePipeline {

    private const val ACCEPT_IMAGE = "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"
    private const val ACCEPT_LANGUAGE = "fr-FR,fr;q=0.9,en;q=0.8"

    @Volatile
    var activeSiteOrigin: String = SamaParsers.FALLBACK_ORIGIN

    private val hostStrategyCache = ConcurrentHashMap<String, RefererStrategy>()

    fun clearHostStrategies() {
        hostStrategyCache.clear()
    }

    internal fun cachedStrategyFor(host: String): RefererStrategy? =
        hostStrategyCache[host.lowercase()]

    fun createHttpClient(
        siteOriginProvider: () -> String = { activeSiteOrigin },
        sleepMs: (Long) -> Unit = { duration ->
            if (duration > 0L) Thread.sleep(duration)
        },
        maxTransientRetries: Int = 2,
        extraInterceptor: Interceptor? = null,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .addInterceptor(
            ImageResilienceInterceptor(
                siteOriginProvider = siteOriginProvider,
                sleepMs = sleepMs,
                maxTransientRetries = maxTransientRetries,
            ),
        )
        .apply {
            if (extraInterceptor != null) addInterceptor(extraInterceptor)
        }
        .build()

    fun buildImageLoader(
        context: Context,
        httpClient: OkHttpClient = createHttpClient(),
    ): ImageLoader = ImageLoader.Builder(context.applicationContext)
        .okHttpClient(httpClient)
        .memoryCache {
            MemoryCache.Builder(context.applicationContext)
                .maxSizePercent(0.22)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.applicationContext.cacheDir.resolve("image_cache"))
                .maxSizeBytes(64L * 1024 * 1024)
                .build()
        }
        .allowRgb565(false)
        .respectCacheHeaders(false)
        .crossfade(160)
        .build()

    internal class ImageResilienceInterceptor(
        private val siteOriginProvider: () -> String,
        private val sleepMs: (Long) -> Unit,
        private val maxTransientRetries: Int,
    ) : Interceptor {

        override fun intercept(chain: Interceptor.Chain): Response {
            val original = chain.request()
            val host = original.url.host.lowercase()
            val siteOrigin = siteOriginProvider().trimEnd('/')
            val siteHost = runCatching { java.net.URI(siteOrigin).host?.lowercase() }.getOrNull()

            val initialStrategy = hostStrategyCache[host] ?: if (host == siteHost) {
                RefererStrategy.SITE_ORIGIN
            } else {
                RefererStrategy.TARGET_ORIGIN
            }

            var transientAttempt = 0
            while (true) {
                if (chain.call().isCanceled()) throw IOException("Canceled")
                val response = try {
                    executeWithRefererNegotiation(chain, original, host, siteOrigin, initialStrategy)
                } catch (ioe: IOException) {
                    if (transientAttempt < maxTransientRetries && !chain.call().isCanceled()) {
                        val waitMs = 250L * (1 shl transientAttempt)
                        transientAttempt++
                        sleepSafely(waitMs)
                        continue
                    }
                    throw ioe
                }

                // Garde anti soft-404 / page HTML Cloudflare renvoyée en HTTP 200
                if (response.isSuccessful && isHtmlContentType(response.header("Content-Type"))) {
                    response.close()
                    return syntheticNonCacheableError(original, 422, "HTML returned instead of image")
                }

                val code = response.code
                val retryableHttp = (code == 429 || code in 500..599)
                if (retryableHttp && transientAttempt < maxTransientRetries && !chain.call().isCanceled()) {
                    val retryAfterMs = parseRetryAfterMs(response.header("Retry-After"))
                    val waitMs = retryAfterMs ?: (250L * (1 shl transientAttempt))
                    response.close()
                    transientAttempt++
                    sleepSafely(waitMs)
                    continue
                }

                return response
            }
        }

        private fun executeWithRefererNegotiation(
            chain: Interceptor.Chain,
            original: Request,
            host: String,
            siteOrigin: String,
            initialStrategy: RefererStrategy,
        ): Response {
            val order = listOf(
                initialStrategy,
                RefererStrategy.SITE_ORIGIN,
                RefererStrategy.NONE,
                RefererStrategy.TARGET_ORIGIN,
            ).distinct()

            var lastResponse: Response? = null
            for ((idx, strategy) in order.withIndex()) {
                val req = buildAttemptRequest(original, siteOrigin, strategy)
                val resp = chain.proceed(req)
                if (resp.isSuccessful) {
                    if (idx > 0 || !hostStrategyCache.containsKey(host)) {
                        hostStrategyCache[host] = strategy
                    }
                    return resp
                }
                val isAuthHotlinkBlock = (resp.code == 403 || resp.code == 401)
                if (!isAuthHotlinkBlock || idx == order.lastIndex || chain.call().isCanceled()) {
                    return resp
                }
                lastResponse = resp
                resp.close()
            }
            return lastResponse ?: chain.proceed(buildAttemptRequest(original, siteOrigin, initialStrategy))
        }

        private fun buildAttemptRequest(
            original: Request,
            siteOrigin: String,
            strategy: RefererStrategy,
        ): Request {
            val builder = original.newBuilder()
            if (original.header("User-Agent") == null) {
                builder.header("User-Agent", SamaParsers.UA)
            }
            if (original.header("Accept") == null) {
                builder.header("Accept", ACCEPT_IMAGE)
            }
            if (original.header("Accept-Language") == null) {
                builder.header("Accept-Language", ACCEPT_LANGUAGE)
            }
            when (strategy) {
                RefererStrategy.TARGET_ORIGIN -> {
                    val u = original.url
                    val portPart = if (u.port != 80 && u.port != 443) ":${u.port}" else ""
                    builder.header("Referer", "${u.scheme}://${u.host}$portPart/")
                }
                RefererStrategy.SITE_ORIGIN -> {
                    builder.header("Referer", "$siteOrigin/")
                }
                RefererStrategy.NONE -> {
                    builder.removeHeader("Referer")
                }
            }
            return builder.build()
        }

        private fun parseRetryAfterMs(header: String?): Long? {
            val seconds = header?.trim()?.toLongOrNull() ?: return null
            if (seconds <= 0L) return 150L
            return (seconds * 1000L).coerceAtMost(2_000L)
        }

        private fun sleepSafely(ms: Long) {
            try {
                sleepMs(ms)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }

        private fun isHtmlContentType(contentType: String?): Boolean {
            val ct = contentType?.lowercase() ?: return false
            return ct.startsWith("text/html") || ct.startsWith("application/xhtml+xml")
        }

        private fun syntheticNonCacheableError(request: Request, code: Int, message: String): Response =
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(message)
                .header("Cache-Control", "no-store")
                .body(ByteArray(0).toResponseBody(null))
                .build()
    }
}
