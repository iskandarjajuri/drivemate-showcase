package com.drivemate.showcase.data.repository

import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.model.SafetyDeclaration
import com.drivemate.showcase.core.util.AlreadySubmittedException
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.data.FirestoreConstants
import com.drivemate.showcase.data.firestore.FirestoreService
import com.drivemate.showcase.domain.repository.SafetyRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.TimeZone

class FirebaseSafetyRepository(
    private val fs: FirestoreService
) : SafetyRepository {

    private companion object { const val TAG = "DM/Safety" }

    override suspend fun submitSafetyDeclaration(
        payload: SafetyDeclaration
    ): AppResult<Unit> {
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
                fs.getDocumentMap("safety_declarations", docId)
            }
            if (existing != null) {
                DMLog.w(
                    TAG,
                    "submitSafetyDeclaration skipped (already exists) uid=$uid day=$dateEpochDay hasEmail=$hasEmail"
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

            val data = mutableMapOf<String, Any>(
                "driverUid" to uid,
                "driverEmail" to driverEmail,
                "dateEpochDay" to dateEpochDay,
                "submittedAt" to now,
                "fitToWork" to payload.fitToWork,
                "noAlcoholOrDrugs" to payload.noAlcoholOrDrugs,
                "noSevereStress" to payload.noSevereStress,
                "willFollowSpeedLimit" to payload.willFollowSpeedLimit,
                "willFollowRestBreaks" to payload.willFollowRestBreaks,
                "willNotUsePhoneWhileDriving" to payload.willNotUsePhoneWhileDriving,
                "willWearSeatbelt" to payload.willWearSeatbelt,
                "confirmed" to payload.confirmed,
                "createdAt" to now,
                "updatedAt" to now
            )

            payload.sleepHours?.let { data["sleepHours"] = it }
            payload.hazardAwareness?.takeIf { it.isNotBlank() }?.let {
                data["hazardAwareness"] = it
            }
            payload.weatherCondition?.takeIf { it.isNotBlank() }?.let {
                data["weatherCondition"] = it
            }
            payload.routeRiskNote?.takeIf { it.isNotBlank() }?.let {
                data["routeRiskNote"] = it
            }
            payload.incidentReport?.takeIf { it.isNotBlank() }?.let {
                data["incidentReport"] = it
            }
            driverName?.let { data["driverName"] = it }

            check(data["driverEmail"] is String) { "driverEmail missing from safety payload" }
            withTimeout(FirestoreConstants.WRITE_TIMEOUT_MS) {
                fs.setDocument("safety_declarations/$docId", data, merge = true)
            }
            DMLog.i(TAG, "submitSafetyDeclaration ok uid=$uid day=$dateEpochDay hasEmail=$hasEmail")
            AppResult.success(Unit)
        } catch (t: TimeoutCancellationException) {
            DMLog.e(TAG, "submitSafetyDeclaration timed out: ${t.message} hasEmail=$hasEmail", t)
            AppResult.failure(t)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            DMLog.e(TAG, "submitSafetyDeclaration failed: ${t.message} hasEmail=$hasEmail", t)
            AppResult.failure(t)
        }
    }

    override suspend fun hasSafetyDeclaration(
        driverUid: String,
        dateEpochDay: Int
    ): AppResult<Boolean> = try {
        val docId = "${driverUid}_${dateEpochDay}"
        val doc = withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
            fs.getDocumentMap("safety_declarations", docId)
        }
        DMLog.d(TAG, "hasSafetyDeclaration uid=$driverUid day=$dateEpochDay -> ${doc != null}")
        AppResult.success(doc != null)
    } catch (t: TimeoutCancellationException) {
        DMLog.e(TAG, "hasSafetyDeclaration timed out: ${t.message}", t)
        AppResult.failure(t)
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        DMLog.e(TAG, "hasSafetyDeclaration failed: ${t.message}", t)
        AppResult.failure(t)
    }
}
