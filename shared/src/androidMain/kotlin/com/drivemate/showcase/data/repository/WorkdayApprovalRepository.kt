package com.drivemate.showcase.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.data.FirestoreConstants
import com.drivemate.showcase.domain.repository.AuthRepository
import com.drivemate.showcase.domain.workday.WorkdayDecision
import com.drivemate.showcase.domain.workday.WorkdayDecisionRole
import com.drivemate.showcase.domain.workday.WorkdayDecisionStep
import com.drivemate.showcase.domain.workday.buildWorkdayDecisionStepPayload
import com.drivemate.showcase.domain.workday.workdayDecisionStepPath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * Path dan payload langkah 1 jejak keputusan — dipakai jalur approve/reject tunggal maupun
 * massal, supaya keduanya tidak bisa mencatat bentuk yang berbeda.
 *
 * Langkah 1 **sekali tulis lalu beku** menurut `firestore.rules`. Konsekuensi yang perlu
 * diketahui: kalau seorang expat pernah menyetujui lalu keputusannya direset ke `pending`
 * oleh regenerasi workday, peninjauan BERIKUTNYA tidak bisa menimpa langkah 1 — write-nya
 * ditolak. Itu disengaja: yang tercatat adalah peninjauan pertama, dan koreksi setelahnya
 * adalah wilayah override admin yang membawa alasannya sendiri.
 */
internal val supervisorStepPath: String = workdayDecisionStepPath(WorkdayDecisionRole.SUPERVISOR)

internal fun supervisorStepPayload(
    decision: WorkdayDecision,
    actorUid: String,
    nowMillis: Long
): Map<String, Any> = buildWorkdayDecisionStepPayload(
    WorkdayDecisionStep(
        role = WorkdayDecisionRole.SUPERVISOR,
        decision = decision,
        actorUid = actorUid,
        atMillis = nowMillis
    )
)

internal class WorkdayApprovalRepository(
    private val db: FirebaseFirestore,
    private val authRepo: AuthRepository
) {
    private val workdaysCol get() = db.collection("workdays")

    private companion object { const val TAG = "DM/WorkdayRepo" }

    // ─── Single approve ───────────────────────────────────────────────────────

    internal suspend fun approve(
        workDayId: String,
        note: String?
    ): AppResult<Unit> {
        return try {
            val actorUid = currentActorUidOrThrow(authRepo, "approve")
            val now = TimeUtil.nowEpochMillis()
            withTimeout(FirestoreConstants.WRITE_TIMEOUT_MS) {
                workdaysCol.document(workDayId)
                    .update(
                        mapOf(
                            "approvedByExpat" to true,
                            "status" to STATUS_APPROVED,
                            "rejectionNote" to FieldValue.delete(),
                            "approverUid" to actorUid,
                            "approverName" to null,
                            "approvedByUid" to actorUid,
                            "approvedAtMillis" to now,
                            "approvalNote" to note,
                            "rejectedByUid" to null,
                            "rejectedAtMillis" to null,
                            "rejectReason" to null,
                            "adjustedBy" to actorUid,
                            "updatedAt" to now,
                            // Langkah 1 jejak keputusan (Blok 4.2). Path bertitik: `update`
                            // pada map bersarang MENGGANTI map itu, jadi menulis
                            // `decisionTrail` utuh akan menghapus langkah 2 kalau sudah ada.
                            supervisorStepPath to supervisorStepPayload(
                                decision = WorkdayDecision.APPROVED,
                                actorUid = actorUid,
                                nowMillis = now
                            )
                        )
                    )
                    .await()
            }
            AppResult.Success(Unit)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            AppResult.Failure(t)
        }
    }

    // ─── Single reject ────────────────────────────────────────────────────────

    internal suspend fun reject(
        workDayId: String,
        note: String
    ): AppResult<Unit> {
        return try {
            val actorUid = currentActorUidOrThrow(authRepo, "reject")
            val now = TimeUtil.nowEpochMillis()
            val ref = workdaysCol.document(workDayId)
            val snap = withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
                ref.get(Source.SERVER).await()
            }
            if (!snap.exists()) {
                DMLog.w(
                    TAG,
                    "[EXPAT_REVIEW] reject_precheck docId=$workDayId ok=false missing=[doc] wrongType=[] extra=[] " +
                        "driverUidFromId=- epochFromId=-"
                )
                return AppResult.Failure(IllegalStateException("Workday tidak ditemukan."))
            }
            val precheck = precheckWorkday(workDayId, snap.data ?: emptyMap())
            DMLog.d(
                TAG,
                "[EXPAT_REVIEW] reject_precheck docId=$workDayId ok=${precheck.ok} " +
                    "missing=${precheck.missing} wrongType=${precheck.wrongType} " +
                    "extra=${precheck.extraKeys} driverUidFromId=${precheck.driverUidFromId ?: "-"} " +
                    "epochFromId=${precheck.epochFromId ?: "-"}"
            )
            if (!precheck.ok) {
                return AppResult.Failure(
                    IllegalStateException("Workday tidak valid: ${precheck.invalidReason(workDayId)}")
                )
            }
            val payload = mutableMapOf<String, Any?>(
                "approvedByExpat" to false,
                "status" to STATUS_REJECTED,
                "rejectionNote" to FieldValue.delete(),
                "approverUid" to actorUid,
                "approverName" to null,
                "rejectedByUid" to actorUid,
                "rejectedAtMillis" to now,
                "rejectReason" to note,
                "approvedByUid" to null,
                "approvedAtMillis" to null,
                "approvalNote" to null,
                "adjustedBy" to actorUid,
                "updatedAt" to now,
                supervisorStepPath to supervisorStepPayload(
                    decision = WorkdayDecision.REJECTED,
                    actorUid = actorUid,
                    nowMillis = now
                )
            )
            precheck.backfill.forEach { (key, value) -> payload[key] = value }
            withTimeout(FirestoreConstants.WRITE_TIMEOUT_MS) {
                ref.update(payload).await()
            }
            AppResult.Success(Unit)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            AppResult.Failure(t)
        }
    }

    // ─── Admin override ───────────────────────────────────────────────────────

    internal suspend fun adminOverrideDecision(
        workDayId: String,
        targetStatus: String,
        rejectReason: String?,
        overrideReason: String
    ): AppResult<Unit> {
        return try {
            val actorUid = currentActorUidOrThrow(authRepo, "adminOverrideDecision")
            val role = currentRoleOrThrow(authRepo, "adminOverrideDecision")
            if (role != "admin") {
                return AppResult.Failure(IllegalStateException("Admin only"))
            }
            val normalizedTargetStatus = targetStatus.trim().lowercase()
            if (normalizedTargetStatus != STATUS_APPROVED && normalizedTargetStatus != STATUS_REJECTED) {
                return AppResult.Failure(IllegalArgumentException("Invalid target status"))
            }
            val auditReason = overrideReason.trim()
            if (auditReason.isBlank()) {
                return AppResult.Failure(IllegalArgumentException("Override reason is required"))
            }
            val normalizedRejectReason = rejectReason?.trim()?.takeIf { it.isNotEmpty() }
            if (normalizedTargetStatus == STATUS_REJECTED && normalizedRejectReason == null) {
                return AppResult.Failure(IllegalArgumentException("Reject reason is required for rejected status"))
            }
            val ref = workdaysCol.document(workDayId)
            val snap = withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
                ref.get(Source.SERVER).await()
            }
            if (!snap.exists()) {
                return AppResult.Failure(IllegalStateException("Workday not found"))
            }
            val now = TimeUtil.nowEpochMillis()
            val previousStatus = decisionStatusFromSnapshot(snap)
            val previousFinalized = isFinalizedByStatusOrActor(snap)
            val currentRejectReason = snap.getString("rejectReason")?.trim()?.takeIf { it.isNotEmpty() }
            val rejectReasonChanged = normalizedTargetStatus == STATUS_REJECTED
                && currentRejectReason != normalizedRejectReason
            val statusChanged = previousStatus != normalizedTargetStatus

            val payload = mutableMapOf<String, Any?>(
                "status" to normalizedTargetStatus,
                "approvedByExpat" to (normalizedTargetStatus == STATUS_APPROVED),
                "approverUid" to actorUid,
                "approverName" to null,
                "adjustedBy" to actorUid,
                "updatedAt" to now,
                "rejectionNote" to FieldValue.delete()
            )

            if (normalizedTargetStatus == STATUS_APPROVED) {
                payload["approvedByUid"] = actorUid
                payload["approvedAtMillis"] = now
                payload["approvalNote"] = null
                payload["rejectedByUid"] = null
                payload["rejectedAtMillis"] = null
                payload["rejectReason"] = null
            } else {
                if (statusChanged) {
                    payload["rejectedByUid"] = actorUid
                    payload["rejectedAtMillis"] = now
                    payload["approvedByUid"] = null
                    payload["approvedAtMillis"] = null
                    payload["approvalNote"] = null
                }
                payload["rejectReason"] = normalizedRejectReason
            }

            if (previousFinalized && (statusChanged || rejectReasonChanged)) {
                payload["overrideByUid"] = actorUid
                payload["overrideAtMillis"] = now
                payload["overrideReason"] = auditReason
                payload["overrideFromStatus"] = previousStatus
            }

            withTimeout(FirestoreConstants.WRITE_TIMEOUT_MS) {
                ref.update(payload).await()
            }
            AppResult.Success(Unit)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            AppResult.Failure(t)
        }
    }
}
