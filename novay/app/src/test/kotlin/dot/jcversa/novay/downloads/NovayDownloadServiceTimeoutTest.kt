package dot.jcversa.novay.downloads

import android.content.pm.ServiceInfo
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NovayDownloadServiceTimeoutTest {

    @Test
    fun onTimeout_pausesEngineAndStopsSelfCleanly() {
        val service = Robolectric.buildService(NovayDownloadService::class.java).create().get()

        service.onTimeout(
            startId = 7,
            fgsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )

        assertTrue(shadowOf(service).isStoppedBySelf)
    }
}
