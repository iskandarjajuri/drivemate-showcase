package com.drivemate.showcase.data.repository

import com.drivemate.showcase.bridge.IOSDriverAssignmentPayload
import com.drivemate.showcase.bridge.IOSPlatformBridgeRegistry
import com.drivemate.showcase.core.model.AssignmentReason
import com.drivemate.showcase.core.model.DriverAssignmentOverride
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.domain.repository.DriverAssignmentRepository
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Penugasan expat untuk driver di iOS. Dulu kelima method-nya
 * `UnsupportedOperationException` dengan catatan "admin UI ini Android-only untuk sesi ini".
 *
 * Dua mekanisme, sengaja terpisah:
 *  - **permanent manager** = `users/{driverUid}.managerUid` (admin-only per rules)
 *  - **daily override** = `driver_assignments/{driverUid}_{epochDay}`, satu hari penuh,
 *    MENANG atas permanent manager untuk hari itu
 *
 * Doc-id dirakit di sini, bukan di Swift — lihat [driverAssignmentDocId].
 */
class IOSDriverAssignmentRepository : DriverAssignmentRepository {

    private val bridge
        get() = IOSPlatformBridgeRegistry.driverAssignmentBridge
            ?: error("IOSDriverAssignmentPlatformBridge is not installed")

    override suspend fun setPermanentManager(driverUid: String, expatUid: String): AppResult<Unit> {
        if (driverUid.isBlank() || expatUid.isBlank()) {
            return AppResult.failure(IllegalArgumentException("setPermanentManager needs both uids"))
        }
        if (driverUid == expatUid) {
            // Dicek di sini juga, bukan hanya di UI: rules menolak driverUid == expatUid
            // untuk override, dan pasangan permanen ke diri sendiri sama-sama tidak masuk akal.
            return AppResult.failure(IllegalArgumentException("driver and expat cannot be the same person"))
        }
        return suspendCancellableCoroutine { cont ->
            bridge.setPermanentManager(driverUid, expatUid) { error ->
                if (!cont.isCompleted) cont.resume(
                    if (error != null) AppResult.failure(IllegalStateException(error))
                    else AppResult.success(Unit)
                )
            }
        }
    }

    override suspend fun getPermanentManager(driverUid: String): AppResult<String?> {
        if (driverUid.isBlank()) {
            return AppResult.failure(IllegalArgumentException("getPermanentManager needs a driverUid"))
        }
        return suspendCancellableCoroutine { cont ->
            bridge.getPermanentManager(driverUid) { managerUid, error ->
                if (!cont.isCompleted) cont.resume(
                    if (error != null) AppResult.failure(IllegalStateException(error))
                    // Blank dinormalkan jadi null: dokumen lama bisa punya managerUid = ""
                    // yang bukan "punya manajer", dan UI tidak boleh menampilkannya sebagai
                    // pasangan yang valid.
                    else AppResult.success(managerUid?.trim()?.takeIf { it.isNotEmpty() })
                )
            }
        }
    }

    override suspend fun upsertDailyOverride(
        driverUid: String,
        expatUid: String,
        assignedBy: String,
        reason: AssignmentReason,
        epochDay: Int
    ): AppResult<Unit> {
        if (driverUid.isBlank() || expatUid.isBlank() || assignedBy.isBlank()) {
            return AppResult.failure(IllegalArgumentException("upsertDailyOverride needs driverUid, expatUid, assignedBy"))
        }
        if (driverUid == expatUid) {
            return AppResult.failure(IllegalArgumentException("driver and expat cannot be the same person"))
        }
        return suspendCancellableCoroutine { cont ->
            bridge.upsertDailyOverride(
                docId = driverAssignmentDocId(driverUid, epochDay),
                driverUid = driverUid,
                expatUid = expatUid,
                assignedBy = assignedBy,
                reasonRaw = reason.toRaw(),
                epochDay = epochDay,
                // Rules menuntut `createdAt is int`, jadi ini epoch millis biasa —
                // BUKAN FieldValue.serverTimestamp(), yang akan gagal validasi tipe.
                //
                // Nilainya dihitung di sini supaya sumber waktunya satu, tapi sisi Swift
                // hanya MENYERTAKANNYA kalau dokumennya belum ada — persis seperti Android
                // (`FirebaseDriverAssignmentRepository`: `get(Source.SERVER)` dulu, lalu
                // `payload["createdAt"] = ...` hanya bila `!existing.exists()`). Tanpa itu,
                // menimpa override yang sama akan menggeser waktu pembuatannya dan jejak
                // audit "sejak kapan penugasan ini ada" hilang.
                createdAtMillis = TimeUtil.nowEpochMillis()
            ) { error ->
                if (!cont.isCompleted) cont.resume(
                    if (error != null) AppResult.failure(IllegalStateException(error))
                    else AppResult.success(Unit)
                )
            }
        }
    }

    override suspend fun getDailyOverride(
        driverUid: String,
        epochDay: Int
    ): AppResult<DriverAssignmentOverride?> {
        if (driverUid.isBlank()) {
            return AppResult.failure(IllegalArgumentException("getDailyOverride needs a driverUid"))
        }
        return suspendCancellableCoroutine { cont ->
            bridge.getDailyOverride(driverAssignmentDocId(driverUid, epochDay)) { payload, error ->
                if (!cont.isCompleted) cont.resume(
                    when {
                        error != null -> AppResult.failure(IllegalStateException(error))
                        payload == null -> AppResult.success(null)
                        else -> AppResult.success(payload.toDomainOrNull())
                    }
                )
            }
        }
    }

    override suspend fun getOverridesForDate(epochDay: Int): AppResult<List<DriverAssignmentOverride>> =
        suspendCancellableCoroutine { cont ->
            bridge.getOverridesForDate(epochDay) { payloads, error ->
                if (!cont.isCompleted) cont.resume(
                    when {
                        error != null -> AppResult.failure(IllegalStateException(error))
                        // mapNotNull, bukan map: satu dokumen dengan `reason` tak dikenal
                        // tidak boleh menjatuhkan seluruh daftar hari itu.
                        else -> AppResult.success(payloads?.mapNotNull { it.toDomainOrNull() } ?: emptyList())
                    }
                )
            }
        }
}

/**
 * Doc-id kanonik `driver_assignments`. Dirakit di Kotlin, bukan di Swift, karena
 * `firestore.rules` MENUNTUT bentuk ini (`isCanonicalAssignmentDocId`): kalau Swift merakit
 * sendiri dan sedikit berbeda, tulisannya ditolak `PERMISSION_DENIED` — yang terlihat
 * seperti masalah izin, bukan masalah penamaan, dan itu jam-jam yang terbuang.
 *
 * Bentuknya sengaja sama dengan kontrak `attendance_daily` (`{uid}_{epochDay}`).
 */
internal fun driverAssignmentDocId(driverUid: String, epochDay: Int): String =
    "${driverUid}_$epochDay"

private fun IOSDriverAssignmentPayload.toDomainOrNull(): DriverAssignmentOverride? {
    val parsedReason = AssignmentReason.fromRaw(reasonRaw) ?: return null
    if (driverUid.isBlank() || expatUid.isBlank()) return null
    return DriverAssignmentOverride(
        driverUid = driverUid,
        expatUid = expatUid,
        assignedBy = assignedBy,
        reason = parsedReason,
        epochDay = epochDay,
        createdAt = createdAtMillis
    )
}
