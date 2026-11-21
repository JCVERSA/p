package dot.jcversa.novay.performance

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.compose.runtime.Immutable

/**
 * Niveau de performance actif calculé dynamiquement à partir du matériel,
 * de la version d'Android, de la pression mémoire, de la batterie et du mode
 * thermique (avec éventuelle surcharge utilisateur).
 */
enum class PerformanceTier {
    LOW,
    MID,
    HIGH,
}

/**
 * Mode de performance sélectionnable dans Plus -> Performances & fluidité.
 */
enum class PerformanceMode(
    val id: String,
    val label: String,
    val shortLabel: String,
    val description: String,
) {
    AUTO(
        id = "auto",
        label = "Automatique",
        shortLabel = "Auto",
        description = "Adapte flous, décodage d'affiches et buffers selon la RAM, la batterie et la température.",
    ),
    HIGH(
        id = "high",
        label = "Haute qualité",
        shortLabel = "Élevé",
        description = "Active tous les effets de verre dépoli, flous d'ambiance et affiches haute précision.",
    ),
    BALANCED(
        id = "balanced",
        label = "Équilibré",
        shortLabel = "Équilibré",
        description = "Conserve les animations fluides tout en modérant les flous coûteux et les caches.",
    ),
    SAVER(
        id = "saver",
        label = "Économie",
        shortLabel = "Économie",
        description = "Remplace les flous GPU par des voiles translucides et allège la mémoire sur appareils modestes.",
    ),
    ;

    companion object {
        fun fromId(id: String?): PerformanceMode =
            entries.firstOrNull { it.id == id } ?: AUTO
    }
}

/**
 * Profil de tampon Media3 ExoPlayer calibré selon la RAM disponible.
 */
@Immutable
enum class ExoPlayerBufferProfile(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int,
    val backBufferDurationMs: Int,
) {
    LOW_RAM(
        minBufferMs = 8_000,
        maxBufferMs = 18_000,
        bufferForPlaybackMs = 1_500,
        bufferForPlaybackAfterRebufferMs = 3_000,
        backBufferDurationMs = 5_000,
    ),
    BALANCED(
        minBufferMs = 12_000,
        maxBufferMs = 30_000,
        bufferForPlaybackMs = 2_000,
        bufferForPlaybackAfterRebufferMs = 4_000,
        backBufferDurationMs = 15_000,
    ),
    FLAGSHIP(
        minBufferMs = 15_000,
        maxBufferMs = 50_000,
        bufferForPlaybackMs = 2_500,
        bufferForPlaybackAfterRebufferMs = 5_000,
        backBufferDurationMs = 30_000,
    ),
}

/**
 * Instantané matériel et système du téléphone.
 */
@Immutable
data class DeviceCapabilities(
    val totalRamMb: Int = 4096,
    val isLowRamDevice: Boolean = false,
    val cpuCores: Int = 8,
    val sdkInt: Int = Build.VERSION.SDK_INT,
    val isPowerSaveMode: Boolean = false,
    val isThermalThrottled: Boolean = false,
    val memoryPressureLevel: Int = 0,
    val jankDowngradeActive: Boolean = false,
) {
    val hasMemoryPressure: Boolean
        get() = memoryPressureLevel >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
}

/**
 * Contrat immuable consommé par l'UI, Coil, Lottie, Shimmer et ExoPlayer.
 * Ne retire jamais l'identité visuelle ni le système de mouvement : adapte
 * uniquement le coût de rendu GPU/RAM.
 */
@Immutable
data class PerformanceProfile(
    val tier: PerformanceTier,
    val mode: PerformanceMode,
    val effectiveReason: String,
    val allowLiveHazeBlur: Boolean,
    val allowAtmosphericBackdropBlur: Boolean,
    val allowCardTextShadowBlur: Boolean,
    val allowAnimatedShimmer: Boolean,
    val allowLottieAnimations: Boolean,
    val preferRgb565Posters: Boolean,
    val imageMemoryCachePercent: Double,
    val paletteCacheEntries: Int,
    val coilCrossfadeMs: Int,
    val exoBufferProfile: ExoPlayerBufferProfile,
)

object PerformanceClassifier {

    fun defaultProfile(): PerformanceProfile = resolve(
        capabilities = DeviceCapabilities(),
        mode = PerformanceMode.AUTO,
    )

    fun resolve(
        capabilities: DeviceCapabilities,
        mode: PerformanceMode,
    ): PerformanceProfile {
        val supportsRenderEffectBlur = capabilities.sdkInt >= 31
        val hardwareTier = classifyHardwareTier(capabilities)

        val effectiveTier = when (mode) {
            PerformanceMode.HIGH -> if (capabilities.isThermalThrottled || capabilities.hasMemoryPressure) {
                PerformanceTier.MID
            } else {
                PerformanceTier.HIGH
            }
            PerformanceMode.BALANCED -> if (capabilities.isThermalThrottled || capabilities.isPowerSaveMode || capabilities.isLowRamDevice) {
                PerformanceTier.LOW
            } else {
                PerformanceTier.MID
            }
            PerformanceMode.SAVER -> PerformanceTier.LOW
            PerformanceMode.AUTO -> when {
                hardwareTier == PerformanceTier.LOW -> PerformanceTier.LOW
                capabilities.isPowerSaveMode || capabilities.isThermalThrottled || capabilities.hasMemoryPressure ->
                    PerformanceTier.LOW
                capabilities.jankDowngradeActive -> PerformanceTier.LOW
                else -> hardwareTier
            }
        }

        val allowLiveBlur = when {
            !supportsRenderEffectBlur -> false
            effectiveTier == PerformanceTier.HIGH -> true
            effectiveTier == PerformanceTier.MID && mode == PerformanceMode.HIGH -> true
            else -> false
        }

        val allowBackdropBlur = when {
            !supportsRenderEffectBlur -> false
            effectiveTier == PerformanceTier.LOW -> false
            capabilities.isPowerSaveMode || capabilities.isThermalThrottled -> false
            else -> true
        }

        val preferRgb565 = when (mode) {
            PerformanceMode.SAVER -> true
            PerformanceMode.HIGH -> capabilities.isLowRamDevice || capabilities.totalRamMb <= 2560
            PerformanceMode.BALANCED, PerformanceMode.AUTO ->
                effectiveTier == PerformanceTier.LOW || capabilities.isLowRamDevice || capabilities.totalRamMb <= 3200
        }

        val memPercent = when (effectiveTier) {
            PerformanceTier.LOW -> 0.12
            PerformanceTier.MID -> 0.18
            PerformanceTier.HIGH -> 0.24
        }

        val paletteEntries = when (effectiveTier) {
            PerformanceTier.LOW -> 24
            PerformanceTier.MID -> 48
            PerformanceTier.HIGH -> 64
        }

        val crossfadeMs = when (effectiveTier) {
            PerformanceTier.LOW -> 0
            PerformanceTier.MID -> 90
            PerformanceTier.HIGH -> 160
        }

        val exoProfile = when {
            effectiveTier == PerformanceTier.LOW || capabilities.isLowRamDevice || capabilities.totalRamMb <= 3200 ->
                ExoPlayerBufferProfile.LOW_RAM
            effectiveTier == PerformanceTier.MID -> ExoPlayerBufferProfile.BALANCED
            else -> ExoPlayerBufferProfile.FLAGSHIP
        }

        val reason = buildReason(capabilities, mode, effectiveTier)

        return PerformanceProfile(
            tier = effectiveTier,
            mode = mode,
            effectiveReason = reason,
            allowLiveHazeBlur = allowLiveBlur,
            allowAtmosphericBackdropBlur = allowBackdropBlur,
            allowCardTextShadowBlur = effectiveTier != PerformanceTier.LOW,
            allowAnimatedShimmer = effectiveTier != PerformanceTier.LOW,
            allowLottieAnimations = effectiveTier != PerformanceTier.LOW || mode == PerformanceMode.HIGH,
            preferRgb565Posters = preferRgb565,
            imageMemoryCachePercent = memPercent,
            paletteCacheEntries = paletteEntries,
            coilCrossfadeMs = crossfadeMs,
            exoBufferProfile = exoProfile,
        )
    }

    internal fun classifyHardwareTier(capabilities: DeviceCapabilities): PerformanceTier {
        if (capabilities.isLowRamDevice ||
            capabilities.totalRamMb <= 2600 ||
            capabilities.cpuCores <= 4 ||
            capabilities.sdkInt < 26
        ) {
            return PerformanceTier.LOW
        }
        if (capabilities.totalRamMb >= 5600 &&
            capabilities.cpuCores >= 8 &&
            capabilities.sdkInt >= 31
        ) {
            return PerformanceTier.HIGH
        }
        return PerformanceTier.MID
    }

    private fun buildReason(
        capabilities: DeviceCapabilities,
        mode: PerformanceMode,
        tier: PerformanceTier,
    ): String {
        val ramLabel = if (capabilities.totalRamMb >= 1024) {
            val gb = (capabilities.totalRamMb + 511) / 1024
            "$gb Go RAM"
        } else {
            "${capabilities.totalRamMb} Mo RAM"
        }
        return when {
            mode == PerformanceMode.SAVER -> "Mode Économie · flous allégés · $ramLabel"
            capabilities.isThermalThrottled -> "Modération thermique · $ramLabel"
            capabilities.isPowerSaveMode -> "Économiseur d'énergie actif · $ramLabel"
            capabilities.hasMemoryPressure -> "Pression mémoire détectée · $ramLabel"
            capabilities.jankDowngradeActive -> "Lissage adaptatif des frames · $ramLabel"
            tier == PerformanceTier.LOW && capabilities.sdkInt < 31 ->
                "Voile translucide (Android < 12) · $ramLabel"
            tier == PerformanceTier.LOW -> "Optimisé appareil modeste · $ramLabel"
            tier == PerformanceTier.HIGH -> "Haute qualité · $ramLabel"
            else -> "Équilibré · $ramLabel"
        }
    }
}

object DeviceCapabilitiesReader {

    fun read(
        context: Context,
        memoryPressureLevel: Int = 0,
        jankDowngradeActive: Boolean = false,
    ): DeviceCapabilities {
        val appCtx = context.applicationContext
        val am = appCtx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        val totalRamMb = runCatching {
            if (am != null) {
                am.getMemoryInfo(memInfo)
                (memInfo.totalMem / (1024L * 1024L)).toInt().coerceAtLeast(512)
            } else {
                4096
            }
        }.getOrDefault(4096)

        val isLowRam = runCatching { am?.isLowRamDevice == true }.getOrDefault(false)
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

        val pm = appCtx.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isPowerSave = runCatching { pm?.isPowerSaveMode == true }.getOrDefault(false)
        val isThermalThrottled = if (Build.VERSION.SDK_INT >= 29 && pm != null) {
            runCatching {
                pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
            }.getOrDefault(false)
        } else {
            false
        }

        return DeviceCapabilities(
            totalRamMb = totalRamMb,
            isLowRamDevice = isLowRam,
            cpuCores = cores,
            sdkInt = Build.VERSION.SDK_INT,
            isPowerSaveMode = isPowerSave,
            isThermalThrottled = isThermalThrottled,
            memoryPressureLevel = memoryPressureLevel,
            jankDowngradeActive = jankDowngradeActive,
        )
    }
}
