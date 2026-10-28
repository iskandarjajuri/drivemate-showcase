package com.drivemate.showcase.data.repository

import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.model.Checklist
import com.drivemate.showcase.core.util.AlreadySubmittedException
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.data.FirestoreConstants
import com.drivemate.showcase.data.firestore.FirestoreService
import com.drivemate.showcase.domain.repository.ChecklistRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.TimeZone

class FirebaseChecklistRepository(
    private val fs: FirestoreService
) : ChecklistRepository {

    private companion object { const val TAG = "DM/Checklist" }

    override suspend fun submitChecklist(checklist: Checklist): AppResult<Unit> {
        var hasEmail = false
        return try {
            val uid = fs.currentUserUidOrThrow()
            val now = TimeUtil.nowEpochMillis()
            val zone = TimeZone.of("Asia/Jakarta")
            val dateEpochDay = TimeUtil.todayEpochDay(zone).toInt()
            val authEmail = runCatching { fs.currentUserEmailOrThrow() }.getOrNull()
            hasEmail = !authEmail.isNullOrBlank()

            val docId = "${uid}_${dateEpochDay}"
            val existing = withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
                fs.getDocumentMap("vehicle_checklists", docId)
            }
            if (existing != null) {
                DMLog.w(
                    TAG,
                    "submitChecklist skipped (already exists) uid=$uid day=$dateEpochDay hasEmail=$hasEmail"
                )
                return AppResult.failure(AlreadySubmittedException("Sudah dikirim hari ini"))
            }

            val userDoc = withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
                fs.getDocumentMap("users", uid)
            }
            val rawName = userDoc?.get("name") as? String
            val userEmail = userDoc?.get("email") as? String
            val driverEmail = authEmail?.takeIf { it.isNotBlank() }
                ?: userEmail?.takeIf { it.isNotBlank() }
                ?: ""
            val driverName = rawName?.takeIf { it.isNotBlank() }
                ?: driverEmail.substringBefore("@").takeIf { it.isNotBlank() }
            hasEmail = driverEmail.isNotBlank()

            val payload = mutableMapOf<String, Any>(
                "driverUid" to uid,
                "driverEmail" to driverEmail,
                "dateEpochDay" to dateEpochDay,
                "submittedAt" to now,
                "createdAt" to now,
                "updatedAt" to now,
                "hasIssue" to checklist.hasIssue
            )

            checklist.vehicleId?.takeIf { it.isNotBlank() }?.let { payload["vehicleId"] = it }
            checklist.odometerKm?.let { payload["odometerKm"] = it }
            checklist.fuelLevel?.takeIf { it.isNotBlank() }?.let { payload["fuelLevel"] = it }
            checklist.tiresFrontLeftOk?.let { payload["tiresFrontLeftOk"] = it }
            checklist.tiresFrontRightOk?.let { payload["tiresFrontRightOk"] = it }
            checklist.tiresRearLeftOk?.let { payload["tiresRearLeftOk"] = it }
            checklist.tiresRearRightOk?.let { payload["tiresRearRightOk"] = it }
            checklist.tirePressureNote?.takeIf { it.isNotBlank() }?.let {
                payload["tirePressureNote"] = it
            }
            checklist.brakesOk?.let { payload["brakesOk"] = it }
            checklist.lightsOk?.let { payload["lightsOk"] = it }
            checklist.turnSignalBrakeLightsOk?.let { payload["turnSignalBrakeLightsOk"] = it }
            checklist.hornOk?.let { payload["hornOk"] = it }
            checklist.wipersOk?.let { payload["wipersOk"] = it }
            checklist.mirrorsOk?.let { payload["mirrorsOk"] = it }
            checklist.seatbeltOk?.let { payload["seatbeltOk"] = it }
            checklist.engineOilOk?.let { payload["engineOilOk"] = it }
            checklist.coolantOk?.let { payload["coolantOk"] = it }
            checklist.batteryOk?.let { payload["batteryOk"] = it }
            checklist.washerFluidOk?.let { payload["washerFluidOk"] = it }
            checklist.cleanlinessOk?.let { payload["cleanlinessOk"] = it }
            checklist.documentsOk?.let { payload["documentsOk"] = it }
            checklist.fireExtinguisherOk?.let { payload["fireExtinguisherOk"] = it }
            checklist.firstAidOk?.let { payload["firstAidOk"] = it }
            checklist.warningTriangleOk?.let { payload["warningTriangleOk"] = it }
            checklist.notes?.takeIf { it.isNotBlank() }?.let { payload["notes"] = it }
            driverName?.let { payload["driverName"] = it }

            check(payload["driverEmail"] is String) { "driverEmail missing from checklist payload" }
            withTimeout(FirestoreConstants.WRITE_TIMEOUT_MS) {
                fs.setDocument("vehicle_checklists/$docId", payload, merge = true)
            }
            DMLog.i(TAG, "submitChecklist ok uid=$uid day=$dateEpochDay hasEmail=$hasEmail")
            AppResult.success(Unit)
        } catch (t: TimeoutCancellationException) {
            DMLog.e(TAG, "submitChecklist timed out: ${t.message} hasEmail=$hasEmail", t)
            AppResult.failure(t)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            DMLog.e(TAG, "submitChecklist failed: ${t.message} hasEmail=$hasEmail", t)
            AppResult.failure(t)
        }
    }

    override suspend fun hasChecklist(
        driverUid: String,
        dateEpochDay: Int
    ): AppResult<Boolean> = try {
        val docId = "${driverUid}_${dateEpochDay}"
        val doc = withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
            fs.getDocumentMap("vehicle_checklists", docId)
        }
        DMLog.d(TAG, "hasChecklist uid=$driverUid day=$dateEpochDay -> ${doc != null}")
        AppResult.success(doc != null)
    } catch (t: TimeoutCancellationException) {
        DMLog.e(TAG, "hasChecklist timed out: ${t.message}", t)
        AppResult.failure(t)
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        DMLog.e(TAG, "hasChecklist failed: ${t.message}", t)
        AppResult.failure(t)
    }
}
