package com.drivemate.showcase.data.repository

import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.model.SpeedLimitConfig
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.data.FirestoreConstants
import com.drivemate.showcase.data.firestore.FirestoreService
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.domain.repository.SafetyConfigRepository
import com.drivemate.showcase.domain.safety.buildSafetyConfigPayload
import com.drivemate.showcase.domain.safety.mapSpeedLimitConfigOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout

/**
 * Baca dan tulis `app_config/safety`.
 *
 * Mengembalikan `Success(null)` — bukan `Failure` — kalau dokumennya belum ada.
 * "Belum dikonfigurasi" adalah keadaan sah, bukan error; pemanggil yang
 * memutuskan fail-closed.
 *
 * Pemetaan dokumen→model dipindah ke `mapSpeedLimitConfigOrNull` di commonMain (Blok
 * 3.4): aturan fail-closed-nya terlalu penting untuk hidup di dalam kelas platform yang
 * tidak bisa diuji di mesin ini, dan kedua platform harus memutuskan hal yang sama untuk
 * dokumen yang sama.
 */
class FirebaseSafetyConfigRepository(
    private val fs: FirestoreService
) : SafetyConfigRepository {

    private companion object {
        const val TAG = "DM/SafetyConfig"
        const val COLLECTION = "app_config"
        const val DOC_ID = "safety"
    }

    override suspend fun getSpeedLimitConfig(): AppResult<SpeedLimitConfig?> = try {
        val map = withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
            fs.getDocumentMap(COLLECTION, DOC_ID)
        }
        if (map == null) {
            DMLog.i(TAG, "app_config/safety belum ada — deteksi kecepatan fail-closed (mati)")
            AppResult.success(null)
        } else {
            val config = mapSpeedLimitConfigOrNull(map)
            if (config == null) {
                DMLog.w(TAG, "app_config/safety ada tapi speedLimitKph tidak valid — fail-closed")
            }
            AppResult.success(config)
        }
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        DMLog.w(TAG, "gagal baca app_config/safety: ${t.message}")
        AppResult.failure(t)
    }

    override suspend fun upsertSpeedLimitConfig(
        speedLimitKph: Double,
        enabled: Boolean,
    ): AppResult<Unit> = try {
        // `merge = false`: penulisan PENUH. Merge bisa meninggalkan dokumen setengah
        // terisi, dan `enabled = true` tanpa batas yang sah berarti deteksi mati
        // sementara layar mengaku hidup.
        val payload = buildSafetyConfigPayload(
            speedLimitKph = speedLimitKph,
            enabled = enabled,
            updatedAtMillis = TimeUtil.nowEpochMillis(),
        )
        DMLog.i(TAG, "write app_config/safety keys=${payload.keys} enabled=$enabled")
        withTimeout(FirestoreConstants.WRITE_TIMEOUT_MS) {
            fs.setDocument("$COLLECTION/$DOC_ID", payload, merge = false)
        }
        AppResult.success(Unit)
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        DMLog.w(TAG, "gagal tulis app_config/safety: ${t.message}")
        AppResult.failure(t)
    }
}
