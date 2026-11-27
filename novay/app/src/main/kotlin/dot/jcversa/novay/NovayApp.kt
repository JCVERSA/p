package dot.jcversa.novay

import android.app.Application
import android.content.ComponentCallbacks2
import coil.ImageLoader
import coil.ImageLoaderFactory
import dot.jcversa.novay.performance.NovayPerformanceState
import dot.jcversa.novay.ui.theme.PosterPaletteExtractor
import okhttp3.OkHttpClient

/**
 * Application NoVaY : fournit l'ImageLoader Coil calibré selon les capacités
 * matérielles et libère proprement les caches mémoire sous pression système.
 */
class NovayApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        NovayPerformanceState.init(this)
    }

    override fun newImageLoader(): ImageLoader {
        NovayPerformanceState.ensureInitialized(this)
        return NovayImagePipeline.buildImageLoader(
            context = this,
            httpClient = IMAGE_HTTP_CLIENT,
            profile = NovayPerformanceState.currentProfile,
        )
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        NovayPerformanceState.onTrimMemory(level)
        NovayImagePipeline.trimMemoryCache(this, level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            PosterPaletteExtractor.trimToSize(0)
        } else if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            PosterPaletteExtractor.trimToSize(NovayPerformanceState.currentProfile.paletteCacheEntries / 2)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
    }

    companion object {
        internal val IMAGE_HTTP_CLIENT: OkHttpClient by lazy {
            NovayImagePipeline.createHttpClient()
        }
    }
}
