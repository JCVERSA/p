package dot.jcversa.novay

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import dot.jcversa.novay.data.sama.SamaParsers
import okhttp3.OkHttpClient

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
            .crossfade(true)
            .build()

    companion object {
        private val IMAGE_HTTP_CLIENT = OkHttpClient.Builder()
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
