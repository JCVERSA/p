package dot.jcversa.novay

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient

/**
 * Application NoVaY : fournit l'ImageLoader Coil centralisé des affiches.
 */
class NovayApp : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader =
        NovayImagePipeline.buildImageLoader(this, IMAGE_HTTP_CLIENT)

    companion object {
        internal val IMAGE_HTTP_CLIENT: OkHttpClient by lazy {
            NovayImagePipeline.createHttpClient()
        }
    }
}
