package dot.jcversa.novay.performance

import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.FrameMetrics
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

val LocalPerformanceProfile = staticCompositionLocalOf {
    PerformanceClassifier.defaultProfile()
}

/**
 * État global et réactif de performance NoVaY :
 *  - persiste le choix utilisateur (`Auto`, `Élevé`, `Équilibré`, `Économie`) ;
 *  - écoute batterie, mode thermique (API 29+), pression mémoire et jank temps réel ;
 *  - recalcule un [PerformanceProfile] immuable sans recomposer inutilement.
 */
object NovayPerformanceState {
    private const val PREFS = "novay_performance_prefs"
    private const val KEY_MODE = "performance_mode"

    private const val FRAME_WINDOW_SIZE = 60
    private const val SLOW_FRAME_THRESHOLD_NS = 24_000_000L // > 24ms (~< 42fps)
    private const val JANK_FRACTION_TRIGGER = 0.30f
    private const val JANK_FRACTION_RECOVER = 0.08f
    private const val TRIGGER_WINDOWS = 2
    private const val RECOVER_WINDOWS = 4

    var mode by mutableStateOf(PerformanceMode.AUTO)
        private set

    var capabilities by mutableStateOf(DeviceCapabilities())
        private set

    var currentProfile by mutableStateOf(PerformanceClassifier.defaultProfile())
        private set

    private var memoryPressureLevel by mutableIntStateOf(0)
    private var jankDowngradeActive by mutableStateOf(false)
    private var badWindowsCount = 0
    private var goodWindowsCount = 0

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        val appCtx = context.applicationContext
        val raw = appCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, PerformanceMode.AUTO.id)
        mode = PerformanceMode.fromId(raw)
        capabilities = DeviceCapabilitiesReader.read(
            context = appCtx,
            memoryPressureLevel = memoryPressureLevel,
            jankDowngradeActive = jankDowngradeActive,
        )
        recompute()
        initialized = true
    }

    fun ensureInitialized(context: Context) {
        if (!initialized) {
            init(context)
        }
    }

    fun setMode(context: Context, value: PerformanceMode) {
        val appCtx = context.applicationContext
        mode = value
        appCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, value.id)
            .apply()
        if (value != PerformanceMode.AUTO) {
            jankDowngradeActive = false
            badWindowsCount = 0
            goodWindowsCount = 0
        }
        refreshCapabilities(appCtx)
    }

    fun refreshCapabilities(context: Context) {
        capabilities = DeviceCapabilitiesReader.read(
            context = context.applicationContext,
            memoryPressureLevel = memoryPressureLevel,
            jankDowngradeActive = jankDowngradeActive,
        )
        recompute()
    }

    fun onTrimMemory(level: Int) {
        memoryPressureLevel = if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            level
        } else {
            0
        }
        capabilities = capabilities.copy(memoryPressureLevel = memoryPressureLevel)
        recompute()
    }

    fun recordFrameWindowResult(jankyFraction: Float, totalFrames: Int) {
        if (totalFrames < 20 || mode != PerformanceMode.AUTO) return
        val changed: Boolean
        if (jankyFraction >= JANK_FRACTION_TRIGGER) {
            goodWindowsCount = 0
            badWindowsCount++
            if (!jankDowngradeActive && badWindowsCount >= TRIGGER_WINDOWS) {
                jankDowngradeActive = true
                changed = true
            } else {
                changed = false
            }
        } else if (jankyFraction <= JANK_FRACTION_RECOVER) {
            badWindowsCount = 0
            goodWindowsCount++
            if (jankDowngradeActive && goodWindowsCount >= RECOVER_WINDOWS) {
                jankDowngradeActive = false
                changed = true
            } else {
                changed = false
            }
        } else {
            changed = false
        }
        if (changed) {
            capabilities = capabilities.copy(jankDowngradeActive = jankDowngradeActive)
            recompute()
        }
    }

    @androidx.annotation.VisibleForTesting
    internal fun resetForTest(
        deviceCapabilities: DeviceCapabilities = DeviceCapabilities(),
        performanceMode: PerformanceMode = PerformanceMode.AUTO,
    ) {
        mode = performanceMode
        memoryPressureLevel = deviceCapabilities.memoryPressureLevel
        jankDowngradeActive = deviceCapabilities.jankDowngradeActive
        badWindowsCount = 0
        goodWindowsCount = 0
        capabilities = deviceCapabilities
        initialized = true
        recompute()
    }

    private fun recompute() {
        val next = PerformanceClassifier.resolve(capabilities, mode)
        if (next != currentProfile) {
            currentProfile = next
        }
    }

    internal const val WINDOW_FRAME_COUNT = FRAME_WINDOW_SIZE
    internal const val SLOW_FRAME_NS = SLOW_FRAME_THRESHOLD_NS
}

/**
 * Observeur léger de métriques d'images (API 24+, zéro allocation par frame)
 * pour rétrograder en douceur les effets coûteux si un appareil perd trop
 * d'images consécutivement.
 */
internal class FrameJankWindowTracker(
    private val onWindowEvaluated: (jankyFraction: Float, totalFrames: Int) -> Unit,
) {
    private var frameCount = 0
    private var jankyCount = 0

    fun recordFrameDurationNs(totalDurationNs: Long) {
        frameCount++
        if (totalDurationNs > NovayPerformanceState.SLOW_FRAME_NS) {
            jankyCount++
        }
        if (frameCount >= NovayPerformanceState.WINDOW_FRAME_COUNT) {
            val fraction = jankyCount.toFloat() / frameCount.toFloat()
            val total = frameCount
            frameCount = 0
            jankyCount = 0
            onWindowEvaluated(fraction, total)
        }
    }
}

@Composable
fun PerformanceProfileProvider(content: @Composable () -> Unit) {
    val context = LocalContext.current
    NovayPerformanceState.ensureInitialized(context)

    DisposableEffect(context) {
        val appCtx = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                NovayPerformanceState.refreshCapabilities(appCtx)
            }
        }
        val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                appCtx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                appCtx.registerReceiver(receiver, filter)
            }
        }

        val powerManager = appCtx.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val thermalListener = if (Build.VERSION.SDK_INT >= 29 && powerManager != null) {
            PowerManager.OnThermalStatusChangedListener {
                NovayPerformanceState.refreshCapabilities(appCtx)
            }.also { listener ->
                runCatching { powerManager.addThermalStatusListener(listener) }
            }
        } else {
            null
        }

        val activity = context as? ComponentActivity
        val window = activity?.window
        val tracker = FrameJankWindowTracker { jankFraction, totalFrames ->
            NovayPerformanceState.recordFrameWindowResult(jankFraction, totalFrames)
        }
        val frameListener = if (window != null) {
            Window.OnFrameMetricsAvailableListener { _, frameMetrics, _ ->
                val duration = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION)
                if (duration > 0L) {
                    tracker.recordFrameDurationNs(duration)
                }
            }.also { listener ->
                runCatching {
                    window.addOnFrameMetricsAvailableListener(
                        listener,
                        Handler(Looper.getMainLooper()),
                    )
                }
            }
        } else {
            null
        }

        onDispose {
            runCatching { appCtx.unregisterReceiver(receiver) }
            if (Build.VERSION.SDK_INT >= 29 && powerManager != null && thermalListener != null) {
                runCatching { powerManager.removeThermalStatusListener(thermalListener) }
            }
            if (window != null && frameListener != null) {
                runCatching { window.removeOnFrameMetricsAvailableListener(frameListener) }
            }
        }
    }

    CompositionLocalProvider(
        LocalPerformanceProfile provides NovayPerformanceState.currentProfile,
        content = content,
    )
}
