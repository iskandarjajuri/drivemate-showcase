package com.drivemate.showcase.data.repository

import com.drivemate.showcase.bridge.IOSPlatformBridgeRegistry
import com.drivemate.showcase.core.model.SpeedLimitConfig
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.SafetyConfigRepository
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Baca `app_config/safety` di iOS — cermin `FirebaseSafetyConfigRepository` Android.
 *
 * Dulu stub. Sekarang nyata, karena sumber kecepatan iOS sudah ada (publikasi lokasi live).
 *
 * **`Success(null)`, bukan `Failure`, saat dokumennya belum ada.** "Belum dikonfigurasi"
 * adalah keadaan sah — dan pemanggil (`GetSpeedLimitConfigUseCase`) yang memutuskan
 * fail-closed, yaitu deteksi kecepatan MATI. Mengembalikannya sebagai error akan membuat
 * layar menampilkan kegagalan untuk keadaan yang sepenuhnya normal.
 */
class IOSSafetyConfigRepository : SafetyConfigRepository {

    private val bridge
        get() = IOSPlatformBridgeRegistry.safetyConfigBridge
            ?: error("IOSSafetyPlatformConfigBridge is not installed")

    override suspend fun getSpeedLimitConfig(): AppResult<SpeedLimitConfig?> =
        suspendCancellableCoroutine { cont ->
            bridge.getSpeedLimitConfig { limitKph, enabled, exists, error ->
                if (cont.isCompleted) return@getSpeedLimitConfig
                cont.resume(
                    when {
                        error != null -> AppResult.failure(IllegalStateException(error))
                        !exists -> AppResult.success(null)
                        // Batas yang tidak masuk akal diperlakukan seperti "belum
                        // dikonfigurasi", bukan dipakai apa adanya: batas 0 km/jam akan
                        // menandai SETIAP driver yang bergerak sebagai pelanggar.
                        limitKph == null || limitKph <= 0.0 -> AppResult.success(null)
                        else -> AppResult.success(
                            SpeedLimitConfig(speedLimitKph = limitKph, enabled = enabled)
                        )
                    }
                )
            }
        }

    /**
     * BELUM TERPASANG di iOS — layar setelan keselamatan Android-only sampai Blok 6.
     *
     * Mengikuti preseden `IOSSpeedViolationRepository.listRecentViolations` dan
     * `IOSLocationRepository`: override eksplisit yang beralasan, bukan default diam-diam
     * di interface, supaya pembaca berkas ini melihat bahwa jalur ini belum ada. Bentuk
     * dokumen yang sah sudah tersedia lintas-platform (`buildSafetyConfigPayload`), jadi
     * saat bridge Swift-nya dibuat, ia tidak boleh merakit payload-nya sendiri.
     */
    override suspend fun upsertSpeedLimitConfig(
        speedLimitKph: Double,
        enabled: Boolean,
    ): AppResult<Unit> = AppResult.failure(
        UnsupportedOperationException(
            "Setelan keselamatan belum terpasang di iOS (Android-primary, Blok 6)"
        )
    )
}
