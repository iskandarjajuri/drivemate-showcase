package com.drivemate.showcase.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.model.BulkActionItemResult
import com.drivemate.showcase.core.model.BulkActionOutcome
import com.drivemate.showcase.core.model.BulkActionResult
import com.drivemate.showcase.core.model.BulkActionType
import com.drivemate.showcase.core.model.ReasonCode
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.data.FirestoreConstants
import com.drivemate.showcase.domain.repository.AuthRepository
import com.drivemate.showcase.domain.workday.WorkdayDecision
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

internal class WorkdayBulkApprovalRepository(
    private val db: FirebaseFirestore,
    private val authRepo: AuthRepository
) {
    private val workdaysCol get() = db.collection("workdays")

    private companion object { const val TAG = "DM/WorkdayRepo" }

    // ─── Bulk approve ─────────────────────────────────────────────────────────

    internal suspend fun bulkApprove(workDayIds: List<String>): BulkActionResult {
        val actorUid = try {
            currentActorUidOrThrow(authRepo, "bulkApprove")
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            return BulkActionResult(
                action = BulkActionType.APPROVE,
                attemptedCount = workDayIds.size,
                successCount = 0,
                skippedCount = 0,
                failedCount = workDayIds.size,
                items = workDayIds.map { id ->
                    BulkActionItemResult(
                        docId = id,
                        driverUid = null,
                        dateEpochDay = null,
                        outcome = BulkActionOutcome.FAILED,
                        reasonCode = ReasonCode.EXCEPTION,
                        reasonMessage = t.message
                    )
                }
            )
        }
        val results = mutableListOf<BulkActionItemResult>()
        var successCount = 0
        var skippedCount = 0
        var failedCount = 0
        DMLog.i(TAG, "[EXPAT_REVIEW] approve_start authUid=$actorUid count=${workDayIds.size}")
        workDayIds.forEach { id ->
            val ref = workdaysCol.document(id)
            val snap = try {
                withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
                    ref.get(Source.SERVER).await()
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                failedCount += 1
                results.add(
                    BulkActionItemResult(
                        docId = id,
                        driverUid = null,
                        dateEpochDay = null,
                        outcome = BulkActionOutcome.FAILED,
                        reasonCode = ReasonCode.EXCEPTION,
                        reasonMessage = t.message
                    )
                )
                DMLog.w(TAG, "[EXPAT_REVIEW] approve_result docId=$id outcome=FAILED reasonCode=EXCEPTION")
                return@forEach
            }
            if (!snap.exists()) {
                skippedCount += 1
                results.add(
                    BulkActionItemResult(
                        docId = id,
                        driverUid = null,
                        dateEpochDay = null,
                        outcome = BulkActionOutcome.SKIPPED,
                        reasonCode = ReasonCode.LEGACY_MISSING_FIELDS,
                        reasonMessage = "doc_missing"
                    )
                )
                DMLog.w(
                    TAG,
                    "[EXPAT_REVIEW] approve_precheck docId=$id ok=false missing=[doc] wrongType=[] extra=[] " +
                        "willBackfill=false reasonCode=LEGACY_MISSING_FIELDS driverUidFromId=- epochFromId=-"
                )
                DMLog.w(TAG, "[EXPAT_REVIEW] approve_result docId=$id outcome=SKIPPED reasonCode=LEGACY_MISSING_FIELDS")
                return@forEach
            }
            val precheck = precheckWorkday(id, snap.data ?: emptyMap())
            DMLog.d(
                TAG,
                "[EXPAT_REVIEW] approve_precheck docId=$id ok=${precheck.ok} " +
                    "missing=${precheck.missing} wrongType=${precheck.wrongType} extra=${precheck.extraKeys} " +
                    "willBackfill=${precheck.willBackfill} reasonCode=${precheck.reasonCode} " +
                    "driverUidFromId=${precheck.driverUidFromId ?: "-"} epochFromId=${precheck.epochFromId ?: "-"}"
            )
            if (!precheck.ok) {
                skippedCount += 1
                results.add(
                    BulkActionItemResult(
                        docId = id,
                        driverUid = precheck.driverUidValue ?: precheck.driverUidFromId,
                        dateEpochDay = precheck.dateEpochDayValue,
                        outcome = BulkActionOutcome.SKIPPED,
                        reasonCode = precheck.reasonCode,
                        reasonMessage = precheck.reasonMessage
                    )
                )
                DMLog.w(
                    TAG,
                    "[EXPAT_REVIEW] approve_result docId=$id outcome=SKIPPED reasonCode=${precheck.reasonCode}"
                )
                return@forEach
            }
            val now = TimeUtil.nowEpochMillis()
            val payload = mutableMapOf<String, Any?>(
                "approvedByExpat" to true,
                "status" to STATUS_APPROVED,
                "rejectionNote" to FieldValue.delete(),
                "approverUid" to actorUid,
                "approverName" to null,
                "approvedByUid" to actorUid,
                "approvedAtMillis" to now,
                "approvalNote" to null,
                "rejectedByUid" to null,
                "rejectedAtMillis" to null,
                "rejectReason" to null,
                "adjustedBy" to actorUid,
                "updatedAt" to now,
                supervisorStepPath to supervisorStepPayload(
                    decision = WorkdayDecision.APPROVED,
                    actorUid = actorUid,
                    nowMillis = now
                )
            )
            val driverUid = precheck.driverUidValue ?: (precheck.driverUidFromId ?: "-")
            DMLog.d(
                TAG,
                "[EXPAT_REVIEW] approve_payload keys=${payload.keys.sorted()} docId=$id driverUid=$driverUid"
            )
            precheck.backfill.forEach { (key, value) -> payload[key] = value }
            try {
                withTimeout(FirestoreConstants.WRITE_TIMEOUT_MS) {
                    ref.update(payload).await()
                }
                successCount += 1
                results.add(
                    BulkActionItemResult(
                        docId = id,
                        driverUid = precheck.driverUidValue ?: precheck.driverUidFromId,
                        dateEpochDay = precheck.dateEpochDayValue,
                        outcome = BulkActionOutcome.SUCCESS,
                        reasonCode = ReasonCode.NONE,
                        reasonMessage = null
                    )
                )
                DMLog.i(TAG, "[EXPAT_REVIEW] approve_result docId=$id outcome=SUCCESS reasonCode=NONE")
            } catch (c: CancellationException) {
                throw c
            } catch (e: FirebaseFirestoreException) {
                val reasonCode = if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    ReasonCode.PERMISSION_DENIED
                } else {
                    ReasonCode.EXCEPTION
                }
                failedCount += 1
                results.add(
                    BulkActionItemResult(
                        docId = id,
                        driverUid = precheck.driverUidValue ?: precheck.driverUidFromId,
                        dateEpochDay = precheck.dateEpochDayValue,
                        outcome = BulkActionOutcome.FAILED,
                        reasonCode = reasonCode,
                        reasonMessage = e.message
                    )
                )
                DMLog.w(TAG, "[EXPAT_REVIEW] approve_result docId=$id outcome=FAILED reasonCode=$reasonCode")
            } catch (t: Throwable) {
                failedCount += 1
                results.add(
                    BulkActionItemResult(
                        docId = id,
                        driverUid = precheck.driverUidValue ?: precheck.driverUidFromId,
                        dateEpochDay = precheck.dateEpochDayValue,
                        outcome = BulkActionOutcome.FAILED,
                        reasonCode = ReasonCode.EXCEPTION,
                        reasonMessage = t.message
                    )
                )
                DMLog.w(TAG, "[EXPAT_REVIEW] approve_result docId=$id outcome=FAILED reasonCode=EXCEPTION")
            }
        }
        val result = BulkActionResult(
            action = BulkActionType.APPROVE,
            attemptedCount = workDayIds.size,
            successCount = successCount,
            skippedCount = skippedCount,
            failedCount = failedCount,
            items = results
        )
        DMLog.i(
            TAG,
            "[EXPAT_REVIEW] approve_bulk_done success=$successCount skipped=$skippedCount failed=$failedCount"
        )
        return result
    }

    // ─── Bulk reject ──────────────────────────────────────────────────────────

    internal suspend fun bulkReject(workDayIds: List<String>, note: String): BulkActionResult {
        val actorUid = try {
            currentActorUidOrThrow(authRepo, "bulkReject")
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            return BulkActionResult(
                action = BulkActionType.REJECT,
                attemptedCount = workDayIds.size,
                successCount = 0,
                skippedCount = 0,
                failedCount = workDayIds.size,
                items = workDayIds.map { id ->
                    BulkActionItemResult(
                        docId = id,
                        driverUid = null,
                        dateEpochDay = null,
                        outcome = BulkActionOutcome.FAILED,
                        reasonCode = ReasonCode.EXCEPTION,
                        reasonMessage = t.message
                    )
                }
            )
        }
        val results = mutableListOf<BulkActionItemResult>()
        var successCount = 0
        var skippedCount = 0
        var failedCount = 0
        DMLog.i(TAG, "[EXPAT_REVIEW] reject_start authUid=$actorUid count=${workDayIds.size}")
        workDayIds.forEach { id ->
            val ref = workdaysCol.document(id)
            val snap = try {
                withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
                    ref.get(Source.SERVER).await()
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                failedCount += 1
                results.add(
                    BulkActionItemResult(
                        docId = id,
                        driverUid = null,
                        dateEpochDay = null,
                        outcome = BulkActionOutcome.FAILED,
                        reasonCode = ReasonCode.EXCEPTION,
                        reasonMessage = t.message
                    )
                )
                DMLog.w(TAG, "[EXPAT_REVIEW] reject_result docId=$id outcome=FAILED reasonCode=EXCEPTION")
                return@forEach
            }
            if (!snap.exists()) {
                skippedCount += 1
                results.add(
                    BulkActionItemResult(
                        docId = id,
                        driverUid = null,
                        dateEpochDay = null,
                        outcome = BulkActionOutcome.SKIPPED,
                        reasonCode = ReasonCode.LEGACY_MISSING_FIELDS,
                        reasonMessage = "doc_missing"
                    )
                )
                DMLog.w(
                    TAG,
                    "[EXPAT_REVIEW] reject_precheck docId=$id ok=false missing=[doc] wrongType=[] extra=[] " +
                        "willBackfill=false reasonCode=LEGACY_MISSING_FIELDS driverUidFromId=- epochFromId=-"
                )
                DMLog.w(TAG, "[EXPAT_REVIEW] reject_result docId=$id outcome=SKIPPED reasonCode=LEGACY_MISSING_FIELDS")
                return@forEach
            }
            val precheck = precheckWorkday(id, snap.data ?: emptyMap())
            DMLog.d(
                TAG,
                "[EXPAT_REVIEW] reject_precheck docId=$id ok=${precheck.ok} " +
                    "missing=${precheck.missing} wrongType=${precheck.wrongType} extra=${precheck.extraKeys} " +
                    "willBackfill=${precheck.willBackfill} reasonCode=${precheck.reasonCode} " +
                    "driverUidFromId=${precheck.driverUidFromId ?: "-"} epochFromId=${precheck.epochFromId ?: "-"}"
            )
            if (!precheck.ok) {
                skippedCount += 1
                results.add(
                    BulkActionItemResult(
                        docId = id,
                        driverUid = precheck.driverUidValue ?: precheck.driverUidFromId,
                        dateEpochDay = precheck.dateEpochDayValue,
                        outcome = BulkActionOutcome.SKIPPED,
                        reasonCode = precheck.reasonCode,
                        reasonMessage = precheck.reasonMessage
                    )
                )
                DMLog.w(
                    TAG,
                    "[EXPAT_REVIEW] reject_result docId=$id outcome=SKIPPED reasonCode=${precheck.reasonCode}"
                )
                return@forEach
            }
            val now = TimeUtil.nowEpochMillis()
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
            try {
                withTimeout(FirestoreConstants.WRITE_TIMEOUT_MS) {
                    ref.update(payload).await()
                }
                successCount += 1
                results.add(
                    BulkActionItemResult(
                        docId = id,
                        driverUid = precheck.driverUidValue ?: precheck.driverUidFromId,
                        dateEpochDay = precheck.dateEpochDayValue,
                        outcome = BulkActionOutcome.SUCCESS,
                        reasonCode = ReasonCode.NONE,
                        reasonMessage = null
                    )
                )
                DMLog.i(TAG, "[EXPAT_REVIEW] reject_result docId=$id outcome=SUCCESS reasonCode=NONE")
            } catch (c: CancellationException) {
                throw c
            } catch (e: FirebaseFirestoreException) {
                val reasonCode = if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    ReasonCode.PERMISSION_DENIED
                } else {
                    ReasonCode.EXCEPTION
                }
                failedCount += 1
                results.add(
                    BulkActionItemResult(
                        docId = id,
                        driverUid = precheck.driverUidValue ?: precheck.driverUidFromId,
                        dateEpochDay = precheck.dateEpochDayValue,
                        outcome = BulkActionOutcome.FAILED,
                        reasonCode = reasonCode,
                        reasonMessage = e.message
                    )
                )
                DMLog.w(TAG, "[EXPAT_REVIEW] reject_result docId=$id outcome=FAILED reasonCode=$reasonCode")
            } catch (t: Throwable) {
                failedCount += 1
                results.add(
                    BulkActionItemResult(
                        docId = id,
                        driverUid = precheck.driverUidValue ?: precheck.driverUidFromId,
                        dateEpochDay = precheck.dateEpochDayValue,
                        outcome = BulkActionOutcome.FAILED,
                        reasonCode = ReasonCode.EXCEPTION,
                        reasonMessage = t.message
                    )
                )
                DMLog.w(TAG, "[EXPAT_REVIEW] reject_result docId=$id outcome=FAILED reasonCode=EXCEPTION")
            }
        }
        val result = BulkActionResult(
            action = BulkActionType.REJECT,
            attemptedCount = workDayIds.size,
            successCount = successCount,
            skippedCount = skippedCount,
            failedCount = failedCount,
            items = results
        )
        DMLog.i(
            TAG,
            "[EXPAT_REVIEW] reject_bulk_done success=$successCount skipped=$skippedCount failed=$failedCount"
        )
        return result
    }
}
