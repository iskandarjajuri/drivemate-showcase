package com.drivemate.showcase.data.repository

import com.drivemate.showcase.bridge.IOSPlatformBridgeRegistry
import com.drivemate.showcase.bridge.IOSSpeedViolationDocPayload
import com.drivemate.showcase.core.model.SpeedViolation
import com.drivemate.showcase.core.model.buildSpeedViolationPayload
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.SpeedViolationRepository
import com.drivemate.showcase.domain.safety.SpeedViolationPage
import com.drivemate.showcase.domain.safety.SpeedViolationRecord
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Baca dan tulis event pelanggaran kecepatan di `speed_violations` — cermin
 * `FirebaseSpeedViolationRepository` Android.
 *
 * Dulu stub, dengan catatan bahwa `firestore.rules` hanya mengizinkan `create` bila
 * `driverUid == request.auth.uid`. Syarat itu sekarang dipenuhi dengan mengambil uid dari
 * bridge auth dan menyusun payload lewat fungsi commonMain yang sama dipakai Android —
 * bukan dengan mengarang bentuk dokumennya di sini.
 *
 * Append-only: dokumen ditambahkan (`addDocument`), tidak pernah diperbarui. Riwayat
 * pelanggaran adalah catatan audit; menimpanya akan menghapus bukti.
 */
class IOSSpeedViolationRepository : SpeedViolationRepository {

    private val configBridge
        get() = IOSPlatformBridgeRegistry.safetyConfigBridge
            ?: error("IOSSafetyPlatformConfigBridge is not installed")

    private val locationBridge
        get() = IOSPlatformBridgeRegistry.locationBridge
            ?: error("IOSLocationPlatformBridge is not installed")

    override suspend fun reportViolation(violation: SpeedViolation): AppResult<Unit> {
        val authUid = locationBridge.currentAuthUid()
        if (authUid.isNullOrBlank()) {
            return AppResult.failure(IllegalStateException("Not signed in"))
        }
        val fields = buildSpeedViolationPayload(violation, authUid)
            ?: return AppResult.failure(
                IllegalArgumentException("driverUid mismatch with authenticated user")
            )
        return suspendCancellableCoroutine { cont ->
            configBridge.addSpeedViolation(fields) { error ->
                if (!cont.isCompleted) cont.resume(
                    if (error != null) AppResult.failure(IllegalStateException(error))
                    else AppResult.success(Unit)
                )
            }
        }
    }

    /**
     * Baca riwayat lewat bridge, lalu serahkan bentuknya ke commonMain.
     *
     * `rawDocCount` diambil dari `payloads.size`, BUKAN dari `records.size`: itulah angka
     * yang menentukan "terpotong" dan "digugurkan" (lihat KDoc [SpeedViolationPage]).
     * Karena itu sisi Swift wajib mengembalikan satu payload per dokumen tanpa menyaring —
     * kontraknya ada di KDoc [IOSSpeedViolationDocPayload].
     *
     * `payloads == null` tanpa `error` diperlakukan sebagai kegagalan, bukan sebagai
     * daftar kosong. Pada layar bukti keselamatan, "tidak bisa dibaca" yang tampil sebagai
     * "tidak ada pelanggaran" adalah false-negative paling berbahaya yang bisa dibuat
     * lapisan ini.
     */
    override suspend fun listRecentViolations(
        sinceMillis: Long,
        limit: Int,
    ): AppResult<SpeedViolationPage> = suspendCancellableCoroutine { cont ->
        configBridge.listSpeedViolations(sinceMillis, limit) { payloads, error ->
            if (!cont.isCompleted) cont.resume(
                when {
                    error != null -> AppResult.failure(IllegalStateException(error))
                    payloads == null -> AppResult.failure(
                        IllegalStateException("speed_violations read returned no result")
                    )
                    else -> AppResult.success(
                        SpeedViolationPage(
                            records = payloads.mapNotNull { it.toRecordOrNull() },
                            rawDocCount = payloads.size
                        )
                    )
                }
            )
        }
    }
}

/**
 * Petakan satu payload menjadi [SpeedViolationRecord], atau `null` bila cacat.
 *
 * **MENCERMINKAN `mapSpeedViolationRecordOrNull` di commonMain — kalau salah satu berubah,
 * yang lain harus ikut.** Aturannya identik dan disengaja identik: keempat field wajibnya
 * sama dengan yang dituntut `firestore.rules` pada `create`, `occurredAtMillis` harus
 * positif, dan **tidak ada nilai default untuk kecepatan** — dokumen tanpa `speedKph`
 * digugurkan, bukan dibaca sebagai 0 km/jam, karena baris pelanggaran dengan angka
 * karangan lebih berbahaya daripada baris yang tidak muncul: ia terlihat seperti bukti.
 *
 * Kenapa tidak memanggil `mapSpeedViolationRecordOrNull` langsung: fungsi itu menerima
 * `Map<String, Any?>`, dan merakit map dari payload bertipe hanya akan mengembalikan cast
 * `as? Number` yang justru dihindari dengan membawa tipe konkret melintasi bridge —
 * ditambah satu tempat lagi tempat nama kunci Firestore hidup.
 */
private fun IOSSpeedViolationDocPayload.toRecordOrNull(): SpeedViolationRecord? {
    val uid = driverUid?.takeIf { it.isNotBlank() } ?: return null
    val speed = speedKph ?: return null
    val limit = speedLimitKph ?: return null
    val occurredAt = occurredAtMillis?.takeIf { it > 0L } ?: return null
    return SpeedViolationRecord(
        id = id,
        driverUid = uid,
        speedKph = speed,
        speedLimitKph = limit,
        occurredAtMillis = occurredAt,
        latitude = latitude,
        longitude = longitude
    )
}
