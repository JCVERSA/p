package dot.jcversa.novay

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dot.jcversa.novay.data.sama.SamaParsers
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Application NoVaY : fournit l'ImageLoader Coil des affiches.
 *
 * Les CDN d'images vérifient parfois le Referer (anti-hotlink) : chaque
 * requête d'affiche est signée avec l'origine de l'image elle-même et
 * l'UA du navigateur utilisé pour le scrape.
 */
class NovayApp : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(IMAGE_HTTP_CLIENT)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.22)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }
            .allowRgb565(true)
            .respectCacheHeaders(false)
            .crossfade(160)
            .build()

    companion object {
        private val IMAGE_HTTP_CLIENT = OkHttpClient.Builder()
            .connectionPool(ConnectionPool(6, 5, TimeUnit.MINUTES))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val u = chain.request().url
                val request = chain.request().newBuilder()
                    .header("User-Agent", SamaParsers.UA)
                    .header("Referer", "${u.scheme}://${u.host}/")
                    .build()
                chain.proceed(request)
            }
            .build()
    }
}
