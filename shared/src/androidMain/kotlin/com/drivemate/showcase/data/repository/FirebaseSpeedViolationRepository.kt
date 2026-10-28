package com.drivemate.showcase.data.repository

import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.model.SpeedViolation
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.data.FirestoreConstants
import com.drivemate.showcase.data.firestore.FirestoreService
import com.drivemate.showcase.domain.repository.SpeedViolationRepository
import com.drivemate.showcase.domain.safety.SpeedViolationPage
import com.drivemate.showcase.domain.safety.mapSpeedViolationRecordOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout

/**
 * Tulis dan baca event pelanggaran di `speed_violations` (append-only, `addDocument`),
 * mengikuti pola `attendances` — BUKAN overwrite seperti `locations/{uid}`.
 */
class FirebaseSpeedViolationRepository(
    private val fs: FirestoreService
) : SpeedViolationRepository {

    private companion object {
        const val TAG = "DM/SpeedViolation"
        const val COLLECTION = "speed_violations"
    }

    override suspend fun reportViolation(violation: SpeedViolation): AppResult<Unit> = try {
        val uid = fs.currentUserUidOrThrow()
        require(violation.driverUid == uid) { "driverUid mismatch with authenticated user" }

        val payload = mutableMapOf<String, Any>(
            "driverUid" to uid,
            "speedKph" to violation.speedKph,
            "speedLimitKph" to violation.speedLimitKph,
            "occurredAtMillis" to violation.occurredAtMillis
        )
        violation.latitude?.let { payload["latitude"] = it }
        violation.longitude?.let { payload["longitude"] = it }

        // Tidak melog kecepatan/koordinat/uid — aturan "tidak ada data sensitif di log".
        DMLog.i(TAG, "write speed_violations payload keys=${payload.keys}")
        withTimeout(FirestoreConstants.WRITE_TIMEOUT_MS) {
            fs.addDocument(COLLECTION, payload)
        }
        DMLog.d(TAG, "write speed_violations success")
        AppResult.success(Unit)
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        DMLog.w(TAG, "write speed_violations failed: ${t.message}")
        AppResult.failure(t)
    }

    override suspend fun listRecentViolations(
        sinceMillis: Long,
        limit: Int,
    ): AppResult<SpeedViolationPage> = try {
        // Satu penyaring field, dan field-nya SAMA dengan yang di-orderBy — jadi tidak
        // butuh indeks komposit. Lihat KDoc interface soal kenapa tidak ada `driverUid`.
        // Konvensi `query()`: sufiks operator pada nama field, prefix "-" = menurun.
        val docs = withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
            fs.query(
                collection = COLLECTION,
                where = listOf("occurredAtMillis>=" to sinceMillis),
                orderBy = "-occurredAtMillis",
                limit = limit,
            )
        }
        // `mapNotNull`: dokumen cacat digugurkan, bukan dibaca dengan angka default.
        val records = docs.mapNotNull { mapSpeedViolationRecordOrNull(it.id, it.data) }
        // Tidak melog kecepatan/koordinat/uid — aturan "tidak ada data sensitif di log".
        DMLog.i(TAG, "read speed_violations docs=${docs.size} mapped=${records.size}")
        // Jumlah MENTAH ikut keluar: itulah angka yang menentukan "terpotong". Menghitungnya
        // dari `records` membuat layar mengaku lengkap justru saat jendelanya penuh DAN
        // datanya berantakan.
        AppResult.success(SpeedViolationPage(records, docs.size))
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        DMLog.w(TAG, "read speed_violations failed: ${t.message}")
        AppResult.failure(t)
    }
}
