package com.drivemate.showcase.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.drivemate.showcase.core.model.AssignmentReason
import com.drivemate.showcase.core.model.DriverAssignmentOverride
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.data.FirestoreConstants
import com.drivemate.showcase.domain.repository.DriverAssignmentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

class FirebaseDriverAssignmentRepository(
    private val db: FirebaseFirestore
) : DriverAssignmentRepository {

    private val usersCol get() = db.collection("users")
    private val assignmentsCol get() = db.collection("driver_assignments")

    override suspend fun setPermanentManager(driverUid: String, expatUid: String): AppResult<Unit> {
        return try {
            withTimeout(FirestoreConstants.WRITE_TIMEOUT_MS) {
                usersCol.document(driverUid)
                    .set(mapOf("managerUid" to expatUid), SetOptions.merge())
                    .await()
            }
            AppResult.success(Unit)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            AppResult.failure(t)
        }
    }

    override suspend fun getPermanentManager(driverUid: String): AppResult<String?> {
        return try {
            val snap = withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
                usersCol.document(driverUid).get(Source.SERVER).await()
            }
            AppResult.success(snap.getString("managerUid"))
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            AppResult.failure(t)
        }
    }

    override suspend fun upsertDailyOverride(
        driverUid: String,
        expatUid: String,
        assignedBy: String,
        reason: AssignmentReason,
        epochDay: Int
    ): AppResult<Unit> {
        return try {
            val docRef = assignmentsCol.document("${driverUid}_${epochDay}")
            val payload = mutableMapOf<String, Any>(
                "driverUid" to driverUid,
                "expatUid" to expatUid,
                "assignedBy" to assignedBy,
                "reason" to reason.toRaw(),
                "epochDay" to epochDay
            )
            val existing = withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
                docRef.get(Source.SERVER).await()
            }
            if (!existing.exists()) {
                payload["createdAt"] = TimeUtil.nowEpochMillis()
            }
            withTimeout(FirestoreConstants.WRITE_TIMEOUT_MS) {
                docRef.set(payload, SetOptions.merge()).await()
            }
            AppResult.success(Unit)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            AppResult.failure(t)
        }
    }

    override suspend fun getDailyOverride(driverUid: String, epochDay: Int): AppResult<DriverAssignmentOverride?> {
        return try {
            val snap = withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
                assignmentsCol.document("${driverUid}_${epochDay}").get(Source.SERVER).await()
            }
            if (!snap.exists()) return AppResult.success(null)
            val expatUid = snap.getString("expatUid")
            val assignedBy = snap.getString("assignedBy")
            val reason = AssignmentReason.fromRaw(snap.getString("reason"))
            if (expatUid == null || assignedBy == null || reason == null) {
                return AppResult.success(null)
            }
            AppResult.success(
                DriverAssignmentOverride(
                    driverUid = driverUid,
                    expatUid = expatUid,
                    assignedBy = assignedBy,
                    reason = reason,
                    epochDay = epochDay,
                    createdAt = snap.getLong("createdAt")
                )
            )
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            AppResult.failure(t)
        }
    }

    override suspend fun getOverridesForDate(epochDay: Int): AppResult<List<DriverAssignmentOverride>> {
        return try {
            val snap = withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
                assignmentsCol.whereEqualTo("epochDay", epochDay).get(Source.SERVER).await()
            }
            val overrides = snap.documents.mapNotNull { doc ->
                val driverUid = doc.getString("driverUid") ?: return@mapNotNull null
                val expatUid = doc.getString("expatUid") ?: return@mapNotNull null
                val assignedBy = doc.getString("assignedBy") ?: return@mapNotNull null
                val reason = AssignmentReason.fromRaw(doc.getString("reason")) ?: return@mapNotNull null
                DriverAssignmentOverride(
                    driverUid = driverUid,
                    expatUid = expatUid,
                    assignedBy = assignedBy,
                    reason = reason,
                    epochDay = epochDay,
                    createdAt = doc.getLong("createdAt")
                )
            }
            AppResult.success(overrides)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            AppResult.failure(t)
        }
    }
}
