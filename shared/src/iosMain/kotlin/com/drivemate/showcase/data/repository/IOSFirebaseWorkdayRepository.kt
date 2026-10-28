package com.drivemate.showcase.data.repository

import com.drivemate.showcase.bridge.IOSAttendanceEventRecord
import com.drivemate.showcase.bridge.IOSExistingWorkdayFields
import com.drivemate.showcase.bridge.IOSPlatformBridgeRegistry
import com.drivemate.showcase.bridge.IOSWorkdayDecisionFields
import com.drivemate.showcase.bridge.IOSWorkdayPlanFields
import com.drivemate.showcase.bridge.IOSWorkdayPayload
import com.drivemate.showcase.bridge.IOSWorkdayPlatformBridge
import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.model.BulkActionItemResult
import com.drivemate.showcase.core.model.BulkActionOutcome
import com.drivemate.showcase.core.model.BulkActionResult
import com.drivemate.showcase.core.model.BulkActionType
import com.drivemate.showcase.core.model.ExplicitWorkdayPlanWindow
import com.drivemate.showcase.core.model.ReasonCode
import com.drivemate.showcase.core.model.WorkDay
import com.drivemate.showcase.core.model.WorkdaySummary
import com.drivemate.showcase.core.model.AttendanceDailyState
import com.drivemate.showcase.core.model.approvalStatus
import com.drivemate.showcase.core.model.PlanSource
import com.drivemate.showcase.core.model.computeDeviationFlags
import com.drivemate.showcase.core.model.normalizeApprovalStatus
import com.drivemate.showcase.core.model.resolveApprovalStatus
import com.drivemate.showcase.core.model.resolveAttendanceDailyState
import com.drivemate.showcase.core.model.shouldHoldReview
import com.drivemate.showcase.core.model.toFirestore
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.data.FirestoreConstants
import com.drivemate.showcase.domain.calendar.DEFAULT_WORK_CALENDAR_TIMEZONE_ID
import com.drivemate.showcase.domain.calendar.WorkCalendarYear
import com.drivemate.showcase.domain.calendar.WorkSession
import com.drivemate.showcase.domain.calendar.computeCalendarDrivenWorkdayComputation
import com.drivemate.showcase.domain.repository.AuthRepository
import com.drivemate.showcase.domain.repository.WorkCalendarRepository
import com.drivemate.showcase.domain.repository.WorkDayRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlin.coroutines.resume

private const val BULK_TAG = "DM/WorkdayRepo"
private const val GEN_TAG = "DM/WorkdayGen"

class IOSFirebaseWorkdayRepository(
    private val authRepo: AuthRepository,
    // Generation resolves planned hours / overtime from the calendar SSOT, exactly as
    // the Android facade does by passing WorkCalendarRepository into its generation
    // sub-repository. iOS already has a real read path (IOSWorkCalendarRepository).
    private val workCalendarRepository: WorkCalendarRepository
) : WorkDayRepository {
    private val workdayBridge: IOSWorkdayPlatformBridge
        get() = IOSPlatformBridgeRegistry.workdayBridge
            ?: error("IOSWorkdayPlatformBridge is not installed")

    override suspend fun getWorkDaysByRange(
        driverUid: String,
        startEpochDay: Int,
        endEpochDay: Int
    ): List<WorkDay> = suspendCancellableCoroutine { continuation ->
        workdayBridge.fetchDriverWorkdays(driverUid, startEpochDay, endEpochDay) { payloads, errorMessage ->
            if (errorMessage != null) {
                continuation.resumeWith(Result.failure(IllegalStateException(errorMessage)))
            } else {
                continuation.resume((payloads ?: emptyList()).map { it.toWorkDay() })
            }
        }
    }

    override suspend fun getWorkdaySummary(
        driverUid: String,
        startEpochDay: Int,
        endEpochDay: Int
    ): WorkdaySummary {
        val workdays = getWorkDaysByRange(driverUid, startEpochDay, endEpochDay)
        return WorkdaySummary(
            driverUid = driverUid,
            totalDays = workdays.size,
            approvedCount = workdays.count { it.approvalStatus().name == "APPROVED" },
            rejectedCount = workdays.count { it.approvalStatus().name == "REJECTED" },
            pendingCount = workdays.count { it.approvalStatus().name == "PENDING" },
            lastEpochDay = workdays.maxOfOrNull { it.dateEpochDay }
        )
    }

    // Reuses the same fetchExplicitPlan bridge helper generation already uses — the
    // bridge only returns the two planned-window fields (IOSWorkdayPlanFields), so
    // this returns ExplicitWorkdayPlanWindow rather than the fuller WorkdayPlan model
    // Android's getExplicitPlan builds (source/createdAt/createdByUid/note aren't
    // fetched here; no caller of this method needs them).
    override suspend fun getExplicitPlan(
        driverUid: String,
        epochDay: Int
    ): AppResult<ExplicitWorkdayPlanWindow?> {
        return try {
            val uid = resolveDriverUidOrThrow(driverUid, "getExplicitPlan")
            val plan = withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
                fetchExplicitPlan(uid, epochDay)
            }
            AppResult.success(
                plan?.let {
                    val start = it.plannedStartMillis
                    val end = it.plannedEndMillis
                    if (start != null && end != null) ExplicitWorkdayPlanWindow(start, end) else null
                }
            )
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            AppResult.Failure(t)
        }
    }

    override suspend fun getWorkDayById(workDayId: String): AppResult<WorkDay?> =
        suspendCancellableCoroutine { continuation ->
            workdayBridge.fetchWorkdayById(workDayId) { payload, errorMessage ->
                if (!continuation.isCompleted) {
                    continuation.resume(
                        when {
                            errorMessage != null -> AppResult.failure(IllegalStateException(errorMessage))
                            payload == null -> AppResult.success(null)
                            else -> AppResult.success(payload.toWorkDayDetail())
                        }
                    )
                }
            }
        }

    override suspend fun approve(workDayId: String, note: String?): AppResult<Unit> =
        suspendCancellableCoroutine { cont ->
            workdayBridge.approveWorkday(workDayId, note) { errorMessage ->
                if (!cont.isCompleted) {
                    cont.resume(
                        if (errorMessage != null) AppResult.failure(IllegalStateException(errorMessage))
                        else AppResult.success(Unit)
                    )
                }
            }
        }

    override suspend fun bulkApprove(workDayIds: List<String>): BulkActionResult =
        runBulkDecision(workDayIds, BulkActionType.APPROVE, note = null)

    override suspend fun reject(workDayId: String, note: String): AppResult<Unit> =
        suspendCancellableCoroutine { cont ->
            workdayBridge.rejectWorkday(workDayId, note) { errorMessage ->
                if (!cont.isCompleted) {
                    cont.resume(
                        if (errorMessage != null) AppResult.failure(IllegalStateException(errorMessage))
                        else AppResult.success(Unit)
                    )
                }
            }
        }

    override suspend fun bulkReject(workDayIds: List<String>, note: String): BulkActionResult =
        runBulkDecision(workDayIds, BulkActionType.REJECT, note = note)

    /**
     * Mirrors `WorkdayBulkApprovalRepository.bulkApprove` / `.bulkReject` (androidMain).
     * Both Android paths are identical apart from the action label, the audit payload,
     * and the reject note, so they are folded into one function here.
     *
     * Behaviour deliberately kept identical to Android, including the quirks:
     *  - a failure to resolve the actor UID fails *every* id rather than throwing;
     *  - each document is read server-side first, then precheck-gated, then written;
     *  - a read/write timeout surfaces as `TimeoutCancellationException`, which is a
     *    `CancellationException` and is therefore rethrown out of the whole bulk run
     *    instead of being recorded per item. `BulkApproveWorkDaysUseCase` catches it
     *    and returns an all-failed result — same as Android.
     */
    private suspend fun runBulkDecision(
        workDayIds: List<String>,
        action: BulkActionType,
        note: String?
    ): BulkActionResult {
        val isApprove = action == BulkActionType.APPROVE
        val origin = if (isApprove) "bulkApprove" else "bulkReject"
        val logVerb = if (isApprove) "approve" else "reject"

        val actorUid = try {
            currentActorUidOrThrow(origin)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            return BulkActionResult(
                action = action,
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
        DMLog.i(BULK_TAG, "[EXPAT_REVIEW] ${logVerb}_start authUid=$actorUid count=${workDayIds.size}")

        workDayIds.forEach { id ->
            val fields = try {
                withTimeout(FirestoreConstants.READ_TIMEOUT_MS) { fetchDecisionFields(id) }
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
                DMLog.w(BULK_TAG, "[EXPAT_REVIEW] ${logVerb}_result docId=$id outcome=FAILED reasonCode=EXCEPTION")
                return@forEach
            }

            if (!fields.exists) {
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
                    BULK_TAG,
                    "[EXPAT_REVIEW] ${logVerb}_precheck docId=$id ok=false missing=[doc] wrongType=[] extra=[] " +
                        "willBackfill=false reasonCode=LEGACY_MISSING_FIELDS driverUidFromId=- epochFromId=-"
                )
                DMLog.w(
                    BULK_TAG,
                    "[EXPAT_REVIEW] ${logVerb}_result docId=$id outcome=SKIPPED reasonCode=LEGACY_MISSING_FIELDS"
                )
                return@forEach
            }

            val precheck = precheckWorkdayFields(id, fields)
            DMLog.d(
                BULK_TAG,
                "[EXPAT_REVIEW] ${logVerb}_precheck docId=$id ok=${precheck.ok} " +
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
                    BULK_TAG,
                    "[EXPAT_REVIEW] ${logVerb}_result docId=$id outcome=SKIPPED reasonCode=${precheck.reasonCode}"
                )
                return@forEach
            }

            try {
                val failure = withTimeout(FirestoreConstants.WRITE_TIMEOUT_MS) {
                    applyDecision(
                        workdayId = id,
                        approve = isApprove,
                        rejectReason = note,
                        backfillDateEpochDay = precheck.backfillDateEpochDay,
                        backfillTotalMinutes = precheck.backfillTotalMinutes
                    )
                }
                if (failure == null) {
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
                    DMLog.i(BULK_TAG, "[EXPAT_REVIEW] ${logVerb}_result docId=$id outcome=SUCCESS reasonCode=NONE")
                } else {
                    val reasonCode = if (failure.permissionDenied) {
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
                            reasonMessage = failure.message
                        )
                    )
                    DMLog.w(
                        BULK_TAG,
                        "[EXPAT_REVIEW] ${logVerb}_result docId=$id outcome=FAILED reasonCode=$reasonCode"
                    )
                }
            } catch (c: CancellationException) {
                throw c
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
                DMLog.w(BULK_TAG, "[EXPAT_REVIEW] ${logVerb}_result docId=$id outcome=FAILED reasonCode=EXCEPTION")
            }
        }

        val result = BulkActionResult(
            action = action,
            attemptedCount = workDayIds.size,
            successCount = successCount,
            skippedCount = skippedCount,
            failedCount = failedCount,
            items = results
        )
        DMLog.i(
            BULK_TAG,
            "[EXPAT_REVIEW] ${logVerb}_bulk_done success=$successCount skipped=$skippedCount failed=$failedCount"
        )
        return result
    }

    // Mirrors currentActorUidOrThrow(authRepo, origin) in WorkdayRepositoryHelpers.kt (androidMain).
    private suspend fun currentActorUidOrThrow(origin: String): String {
        val uid = when (val res = authRepo.currentUser()) {
            is AppResult.Success -> res.data?.uid
            is AppResult.Failure -> null
        }
        return uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Missing auth in $origin")
    }

    // Mirrors currentRoleOrThrow(authRepo, origin) in WorkdayRepositoryHelpers.kt (androidMain).
    private suspend fun currentRoleOrThrow(origin: String): String {
        val role = when (val res = authRepo.currentUser()) {
            is AppResult.Success -> res.data?.role?.toFirestore()
            is AppResult.Failure -> null
        }
        return role?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Missing auth role in $origin")
    }

    private suspend fun applyOverride(
        workdayId: String,
        targetStatus: String,
        actorUid: String,
        nowMillis: Long,
        rejectReason: String?,
        statusChanged: Boolean,
        overrideReason: String?,
        overrideFromStatus: String?
    ): String? = suspendCancellableCoroutine { cont ->
        workdayBridge.applyAdminOverrideDecision(
            workdayId = workdayId,
            targetStatus = targetStatus,
            actorUid = actorUid,
            nowMillis = nowMillis,
            rejectReason = rejectReason,
            statusChanged = statusChanged,
            overrideReason = overrideReason,
            overrideFromStatus = overrideFromStatus
        ) { errorMessage ->
            if (!cont.isCompleted) {
                cont.resume(errorMessage)
            }
        }
    }

    private suspend fun fetchDecisionFields(workdayId: String): IOSWorkdayDecisionFields =
        suspendCancellableCoroutine { cont ->
            workdayBridge.fetchWorkdayDecisionFields(workdayId) { fields, errorMessage ->
                if (!cont.isCompleted) {
                    when {
                        errorMessage != null -> cont.resumeWith(
                            Result.failure(IllegalStateException(errorMessage))
                        )
                        fields == null -> cont.resumeWith(
                            Result.failure(IllegalStateException("Workday read returned no data"))
                        )
                        else -> cont.resume(fields)
                    }
                }
            }
        }

    private suspend fun applyDecision(
        workdayId: String,
        approve: Boolean,
        rejectReason: String?,
        backfillDateEpochDay: Long?,
        backfillTotalMinutes: Long?
    ): DecisionFailure? = suspendCancellableCoroutine { cont ->
        workdayBridge.applyBulkWorkdayDecision(
            workdayId = workdayId,
            approve = approve,
            rejectReason = rejectReason,
            backfillDateEpochDay = backfillDateEpochDay,
            backfillTotalMinutes = backfillTotalMinutes
        ) { errorMessage, permissionDenied ->
            if (!cont.isCompleted) {
                cont.resume(
                    if (errorMessage == null) null
                    else DecisionFailure(errorMessage, permissionDenied)
                )
            }
        }
    }

    /**
     * Mirrors `WorkdayApprovalRepository.adminOverrideDecision` (androidMain),
     * including validation order: actor UID → role → target status → override reason
     * → reject reason → document read.
     */
    override suspend fun adminOverrideDecision(
        workDayId: String,
        targetStatus: String,
        rejectReason: String?,
        overrideReason: String
    ): AppResult<Unit> {
        return try {
            val actorUid = currentActorUidOrThrow("adminOverrideDecision")
            val role = currentRoleOrThrow("adminOverrideDecision")
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
                return AppResult.Failure(
                    IllegalArgumentException("Reject reason is required for rejected status")
                )
            }

            val fields = withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
                fetchDecisionFields(workDayId)
            }
            if (!fields.exists) {
                return AppResult.Failure(IllegalStateException("Workday not found"))
            }

            val now = TimeUtil.nowEpochMillis()
            val previousStatus = decisionStatusFromFields(fields)
            val previousFinalized = isFinalizedByStatusOrActor(fields)
            val currentRejectReason = fields.rejectReason?.trim()?.takeIf { it.isNotEmpty() }
            val rejectReasonChanged = normalizedTargetStatus == STATUS_REJECTED &&
                currentRejectReason != normalizedRejectReason
            val statusChanged = previousStatus != normalizedTargetStatus

            // Android only stamps the override audit trail when it is genuinely
            // overriding an already-finalized decision that actually moves.
            val writeOverrideAudit = previousFinalized && (statusChanged || rejectReasonChanged)

            val failure = withTimeout(FirestoreConstants.WRITE_TIMEOUT_MS) {
                applyOverride(
                    workdayId = workDayId,
                    targetStatus = normalizedTargetStatus,
                    actorUid = actorUid,
                    nowMillis = now,
                    rejectReason = normalizedRejectReason,
                    statusChanged = statusChanged,
                    overrideReason = if (writeOverrideAudit) auditReason else null,
                    overrideFromStatus = if (writeOverrideAudit) previousStatus else null
                )
            }
            if (failure != null) {
                return AppResult.Failure(IllegalStateException(failure))
            }
            AppResult.Success(Unit)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            AppResult.Failure(t)
        }
    }

    /**
     * Mirrors `WorkdayGenerationRepository.generateWorkdayFromAttendances` (androidMain).
     *
     * Note the deliberate no-op semantics: a day with no IN/OUT pair, or an event
     * missing its timestamp, returns Success — not Failure. Android treats "nothing
     * to generate" as a successful run, and callers rely on that.
     */
    override suspend fun generateWorkdayFromAttendances(
        driverUid: String,
        dateEpochDay: Int
    ): AppResult<Unit> {
        return try {
            val uid = try {
                resolveDriverUidOrThrow(driverUid, "generateWorkdayFromAttendances")
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                return AppResult.Failure(t)
            }
            DMLog.i(
                GEN_TAG,
                "generateWorkdayFromAttendances raw=$driverUid canonical=$uid day=$dateEpochDay"
            )
            val start = TimeUtil.parseHm("00:00", dateEpochDay.toLong())
            val end = TimeUtil.parseHm("00:00", (dateEpochDay + 1).toLong())

            val attendanceEvents = withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
                fetchAttendanceEvents(uid, start, end)
            }

            val firstIn = attendanceEvents.firstOrNull { it.type == "IN" || it.note == "IN" }
            val lastOut = attendanceEvents.lastOrNull { it.type == "OUT" || it.note == "OUT" }
            if (firstIn == null || lastOut == null) {
                DMLog.d(GEN_TAG, "Skip generate: missing IN/OUT for uid=$uid day=$dateEpochDay")
                return AppResult.Success(Unit)
            }
            val clockIn = firstIn.timestampMillis
            val clockOut = lastOut.timestampMillis

            val calendar = getCalendarForEpochDay(dateEpochDay)
            val computed = computeCalendarDrivenWorkdayComputation(
                calendar = calendar,
                dateEpochDay = dateEpochDay,
                sessions = listOf(
                    WorkSession(clockInMillis = clockIn, clockOutMillis = clockOut)
                )
            )
            val totalMinutes = computed.totalMinutes
            val overtimeMinutes = computed.overtimeMinutes
            val now = TimeUtil.nowEpochMillis()

            val explicitPlan = withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
                fetchExplicitPlan(uid, dateEpochDay)
            }
            val plannedStart = explicitPlan?.plannedStartMillis ?: computed.plannedStartMillis
            val plannedEnd = explicitPlan?.plannedEndMillis ?: computed.plannedEndMillis
            val planSource = when {
                explicitPlan != null -> PlanSource.EXPLICIT
                computed.isWorkday -> PlanSource.DEFAULT
                // PlanSource has no dedicated HOLIDAY type; keep existing off-day bucket.
                else -> PlanSource.WEEKEND
            }
            val isCalendarOffDay = !computed.isWorkday
            val deviationFlags = computeDeviationFlags(
                isWeekend = isCalendarOffDay,
                planStartMillis = plannedStart,
                planEndMillis = plannedEnd,
                clockInMillis = clockIn,
                clockOutMillis = clockOut,
                graceMinutes = 15,
                thresholdMinutes = 30
            )
            val reviewHold = shouldHoldReview(deviationFlags)

            val existing = withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
                fetchExistingWorkdays(uid, dateEpochDay)
            }
            val existingDoc = existing.firstOrNull()
            if (existing.size > 1) {
                DMLog.w(
                    GEN_TAG,
                    "Multiple workdays found for uid=$uid day=$dateEpochDay; updating first"
                )
            }
            val targetDocId = existingDoc?.documentId ?: "${uid}_${dateEpochDay}"

            val prevTotalMinutes = existingDoc?.totalMinutes?.toInt()
            val prevOvertimeMinutes = existingDoc?.overtimeMinutes?.toInt()
            val prevStatusRaw = existingDoc?.status
            val prevDecision = resolveApprovalStatus(
                statusRaw = prevStatusRaw,
                approvedByExpat = existingDoc?.approvedByExpat,
                approvedByUid = existingDoc?.approvedByUid,
                rejectedByUid = existingDoc?.rejectedByUid,
                rejectedAtMillis = existingDoc?.rejectedAtMillis,
                rejectReason = existingDoc?.rejectReason,
                rejectionNote = existingDoc?.rejectionNote
            )
            val prevStatus = when (prevDecision) {
                true -> STATUS_APPROVED
                false -> STATUS_REJECTED
                null -> STATUS_PENDING
            }
            val computedChanged = existingDoc == null ||
                existingDoc.clockInMillis == null ||
                existingDoc.clockOutMillis == null ||
                prevTotalMinutes == null ||
                prevOvertimeMinutes == null ||
                existingDoc.clockInMillis != clockIn ||
                existingDoc.clockOutMillis != clockOut ||
                prevTotalMinutes != totalMinutes ||
                prevOvertimeMinutes != overtimeMinutes
            val shouldResetApproval = computedChanged
            val shouldSetPending = existingDoc == null || shouldResetApproval ||
                (prevStatusRaw.isNullOrBlank() && prevDecision == null)
            val newStatus = if (shouldSetPending) STATUS_PENDING else prevStatus
            DMLog.i(
                GEN_TAG,
                "[WORKDAY_REGEN] docId=$targetDocId computedChanged=$computedChanged " +
                    "prevStatus=$prevStatus newStatus=$newStatus"
            )

            val failure = withTimeout(FirestoreConstants.WRITE_TIMEOUT_MS) {
                upsertGenerated(
                    documentId = targetDocId,
                    driverUid = uid,
                    dateEpochDay = dateEpochDay,
                    clockInMillis = clockIn,
                    clockOutMillis = clockOut,
                    totalMinutes = totalMinutes,
                    overtimeMinutes = overtimeMinutes,
                    plannedStartMillis = plannedStart,
                    plannedEndMillis = plannedEnd,
                    planSource = planSource.toRaw(),
                    deviationFlags = deviationFlags.map { it.toRaw() },
                    reviewHold = reviewHold,
                    updatedAtMillis = now,
                    createdAtMillis = if (existingDoc == null) now else null,
                    setStatusPending = shouldSetPending,
                    clearApprovalFields = existingDoc != null && shouldResetApproval,
                    writeSystemClockCorrection = existingDoc != null && computedChanged
                )
            }
            if (failure != null) {
                DMLog.e(GEN_TAG, "Generate failed: $failure")
                return AppResult.Failure(IllegalStateException(failure))
            }
            DMLog.i(GEN_TAG, "Upsert workday uid=$uid day=$dateEpochDay docId=$targetDocId")
            AppResult.Success(Unit)
        } catch (c: CancellationException) {
            DMLog.d(GEN_TAG, "Generate cancelled (lifecycle)")
            throw c
        } catch (t: Throwable) {
            DMLog.e(GEN_TAG, "Generate failed: ${t.message}")
            AppResult.Failure(t)
        }
    }

    // Mirrors WorkdayGenerationRepository.getCalendarForEpochDay: a calendar read
    // failure degrades to a default-rules year rather than failing generation.
    private suspend fun getCalendarForEpochDay(dateEpochDay: Int): WorkCalendarYear {
        val year = LocalDate.fromEpochDays(dateEpochDay).year
        return when (val result = workCalendarRepository.getYear(year)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> {
                DMLog.w(
                    BULK_TAG,
                    "getCalendarForEpochDay fallback year=$year reason=${result.throwable.message}"
                )
                WorkCalendarYear(year = year, timezoneId = DEFAULT_WORK_CALENDAR_TIMEZONE_ID)
            }
        }
    }

    private suspend fun fetchAttendanceEvents(
        driverUid: String,
        startMillis: Long,
        endMillis: Long
    ): List<IOSAttendanceEventRecord> = suspendCancellableCoroutine { cont ->
        workdayBridge.fetchAttendanceEventsInRange(driverUid, startMillis, endMillis) { records, errorMessage ->
            if (!cont.isCompleted) {
                if (errorMessage != null) {
                    cont.resumeWith(Result.failure(IllegalStateException(errorMessage)))
                } else {
                    cont.resume(records ?: emptyList())
                }
            }
        }
    }

    // Returns null when the plan doc is absent or lacks either planned bound —
    // same two null paths as Android's getExplicitPlan.
    private suspend fun fetchExplicitPlan(
        driverUid: String,
        dateEpochDay: Int
    ): IOSWorkdayPlanFields? = suspendCancellableCoroutine { cont ->
        workdayBridge.fetchWorkdayPlan(driverUid, dateEpochDay) { plan, errorMessage ->
            if (!cont.isCompleted) {
                when {
                    errorMessage != null ->
                        cont.resumeWith(Result.failure(IllegalStateException(errorMessage)))
                    plan == null || !plan.exists -> cont.resume(null)
                    plan.plannedStartMillis == null || plan.plannedEndMillis == null ->
                        cont.resume(null)
                    else -> cont.resume(plan)
                }
            }
        }
    }

    private suspend fun fetchExistingWorkdays(
        driverUid: String,
        dateEpochDay: Int
    ): List<IOSExistingWorkdayFields> = suspendCancellableCoroutine { cont ->
        workdayBridge.fetchExistingWorkdaysForDay(driverUid, dateEpochDay) { docs, errorMessage ->
            if (!cont.isCompleted) {
                if (errorMessage != null) {
                    cont.resumeWith(Result.failure(IllegalStateException(errorMessage)))
                } else {
                    cont.resume(docs ?: emptyList())
                }
            }
        }
    }

    private suspend fun upsertGenerated(
        documentId: String,
        driverUid: String,
        dateEpochDay: Int,
        clockInMillis: Long,
        clockOutMillis: Long,
        totalMinutes: Int,
        overtimeMinutes: Int,
        plannedStartMillis: Long?,
        plannedEndMillis: Long?,
        planSource: String,
        deviationFlags: List<String>,
        reviewHold: Boolean,
        updatedAtMillis: Long,
        createdAtMillis: Long?,
        setStatusPending: Boolean,
        clearApprovalFields: Boolean,
        writeSystemClockCorrection: Boolean
    ): String? = suspendCancellableCoroutine { cont ->
        workdayBridge.upsertGeneratedWorkday(
            documentId = documentId,
            driverUid = driverUid,
            dateEpochDay = dateEpochDay,
            clockInMillis = clockInMillis,
            clockOutMillis = clockOutMillis,
            totalMinutes = totalMinutes,
            overtimeMinutes = overtimeMinutes,
            plannedStartMillis = plannedStartMillis,
            plannedEndMillis = plannedEndMillis,
            planSource = planSource,
            deviationFlags = deviationFlags,
            reviewHold = reviewHold,
            updatedAtMillis = updatedAtMillis,
            createdAtMillis = createdAtMillis,
            setStatusPending = setStatusPending,
            clearApprovalFields = clearApprovalFields,
            writeSystemClockCorrection = writeSystemClockCorrection
        ) { errorMessage ->
            if (!cont.isCompleted) {
                cont.resume(errorMessage)
            }
        }
    }

    /**
     * Mirrors `WorkdayGenerationRepository.upsertWorkdayPlan` (androidMain).
     *
     * Deliberately does NOT reuse [currentActorUidOrThrow]: Android resolves the actor
     * inline here and returns `Failure(IllegalStateException("Missing auth"))`, whereas
     * that helper throws `"Missing auth in <origin>"`. Same check, different surfaced
     * message — kept as-is so the error text stays identical across platforms.
     *
     * Unlike generation (Fase 1c) this path never consults the work calendar: the
     * planned window arrives fully specified in the parameters.
     */
    override suspend fun upsertWorkdayPlan(
        driverUid: String,
        epochDay: Int,
        plannedStartMillis: Long,
        plannedEndMillis: Long,
        source: String
    ): AppResult<Unit> {
        return try {
            val uid = try {
                resolveDriverUidOrThrow(driverUid, "upsertWorkdayPlan")
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                return AppResult.Failure(t)
            }
            if (plannedStartMillis >= plannedEndMillis) {
                return AppResult.Failure(IllegalArgumentException("Invalid plan range"))
            }
            val normalizedSource = source.trim().lowercase()
            if (normalizedSource != "admin" && normalizedSource != "expat") {
                return AppResult.Failure(IllegalArgumentException("Invalid plan source"))
            }
            val authUid = when (val res = authRepo.currentUser()) {
                is AppResult.Success -> res.data?.uid
                is AppResult.Failure -> null
            }
            if (authUid.isNullOrBlank()) {
                return AppResult.Failure(IllegalStateException("Missing auth"))
            }
            val now = TimeUtil.nowEpochMillis()
            val documentId = "${uid}_${epochDay}"

            // Raw existence only — a plan doc missing its planned bounds still counts as
            // existing for Android's `existing.exists()`, so the null-collapsing wrapper
            // used by generation (fetchExplicitPlan) would be wrong here.
            val existing = withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
                fetchWorkdayPlanFields(uid, epochDay)
            }
            val isNew = existing?.exists != true

            val failure = withTimeout(FirestoreConstants.WRITE_TIMEOUT_MS) {
                upsertPlanDocument(
                    documentId = documentId,
                    driverUid = uid,
                    dateEpochDay = epochDay,
                    plannedStartMillis = plannedStartMillis,
                    plannedEndMillis = plannedEndMillis,
                    source = normalizedSource,
                    createdAtMillis = if (isNew) now else null,
                    createdByUid = if (isNew) authUid else null
                )
            }
            if (failure != null) {
                return AppResult.Failure(IllegalStateException(failure))
            }
            AppResult.Success(Unit)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            AppResult.Failure(t)
        }
    }

    private suspend fun fetchWorkdayPlanFields(
        driverUid: String,
        dateEpochDay: Int
    ): IOSWorkdayPlanFields? = suspendCancellableCoroutine { cont ->
        workdayBridge.fetchWorkdayPlan(driverUid, dateEpochDay) { plan, errorMessage ->
            if (!cont.isCompleted) {
                if (errorMessage != null) {
                    cont.resumeWith(Result.failure(IllegalStateException(errorMessage)))
                } else {
                    cont.resume(plan)
                }
            }
        }
    }

    private suspend fun upsertPlanDocument(
        documentId: String,
        driverUid: String,
        dateEpochDay: Int,
        plannedStartMillis: Long,
        plannedEndMillis: Long,
        source: String,
        createdAtMillis: Long?,
        createdByUid: String?
    ): String? = suspendCancellableCoroutine { cont ->
        workdayBridge.upsertWorkdayPlanDocument(
            documentId = documentId,
            driverUid = driverUid,
            dateEpochDay = dateEpochDay,
            plannedStartMillis = plannedStartMillis,
            plannedEndMillis = plannedEndMillis,
            source = source,
            createdAtMillis = createdAtMillis,
            createdByUid = createdByUid
        ) { errorMessage ->
            if (!cont.isCompleted) {
                cont.resume(errorMessage)
            }
        }
    }

    private fun IOSWorkdayPayload.toWorkDay(): WorkDay =
        WorkDay(
            id = id,
            driverUid = driverUid,
            dateEpochDay = dateEpochDay,
            clockInMillis = clockInMillis,
            clockOutMillis = clockOutMillis,
            totalMinutes = totalMinutes,
            overtimeMinutes = overtimeMinutes,
            driverName = driverName,
            approvedByExpat = approvedByExpat,
            rejectionNote = rejectionNote,
            hasChecklist = hasChecklist,
            hasSafetyDeclaration = hasSafetyDeclaration,
            status = status,
            approvedByUid = approvedByUid,
            rejectedByUid = rejectedByUid,
            rejectedAtMillis = rejectedAtMillis,
            rejectReason = rejectReason,
            plannedStartMillis = plannedStartMillis,
            plannedEndMillis = plannedEndMillis
        )

    // Detail fetch only: normalizes status/approvedByExpat for legacy docs and zeroes
    // out totals for a still-open workday, same as the already-shipped admin detail
    // path (IOSAdminDataRepository.toWorkDay()). Does NOT cross-reference
    // attendance_daily the way WorkdayQueryRepository.getWorkDayById does on
    // Android, so a workday whose clock times were corrected via attendance_daily
    // will show the raw workdays/{id} values here, not the reconciled ones.
    private fun IOSWorkdayPayload.toWorkDayDetail(): WorkDay {
        val normalized = normalizeApprovalStatus(
            statusRaw = status,
            approvedByExpat = approvedByExpat,
            approvedByUid = approvedByUid,
            rejectedByUid = rejectedByUid,
            rejectedAtMillis = rejectedAtMillis,
            rejectReason = rejectReason,
            rejectionNote = rejectionNote
        )
        val (total, overtime) = resolveComputedMinutes(totalMinutes, overtimeMinutes, clockInMillis, clockOutMillis)
        return toWorkDay().copy(
            status = normalized.status,
            approvedByExpat = normalized.approvedByExpat,
            totalMinutes = total,
            overtimeMinutes = overtime
        )
    }
}

private data class DecisionFailure(
    val message: String,
    val permissionDenied: Boolean
)

// ─── Status constants + decision-state helpers (iOS mirror) ──────────────────
//
// Mirrors STATUS_* / decisionStatusFromSnapshot / isFinalizedByStatusOrActor in
// WorkdayRepositoryHelpers.kt (androidMain). Android reads a DocumentSnapshot; the
// iOS versions read the same four fields off IOSWorkdayDecisionFields. Same
// precedence, same blank handling. See the precheck note below on why this is
// duplicated rather than hoisted to commonMain.

private const val STATUS_PENDING = "pending"
private const val STATUS_APPROVED = "approved"
private const val STATUS_REJECTED = "rejected"

// Mirrors isProbablyUid / resolveDriverUidOrThrow in WorkdayRepositoryHelpers.kt
// (androidMain). Guards against a display name or email being passed where a UID is
// required, which would otherwise write a workday under an unqueryable key.
private fun isProbablyUid(key: String): Boolean =
    key.length >= 20 && !key.contains("@")

private fun resolveDriverUidOrThrow(raw: String, origin: String): String {
    val uid = raw.trim()
    if (!isProbablyUid(uid)) {
        DMLog.w(BULK_TAG, "Reject non-UID driver key in $origin: '$raw'")
        throw IllegalStateException("Driver UID required in $origin")
    }
    return uid
}

private fun decisionStatusFromFields(fields: IOSWorkdayDecisionFields): String {
    val status = fields.status?.trim()?.lowercase()
    if (status == STATUS_APPROVED || status == STATUS_REJECTED) return status
    if (!fields.rejectedByUid.isNullOrBlank()) return STATUS_REJECTED
    if (!fields.approvedByUid.isNullOrBlank()) return STATUS_APPROVED
    return STATUS_PENDING
}

private fun isFinalizedByStatusOrActor(fields: IOSWorkdayDecisionFields): Boolean {
    val status = fields.status?.trim()?.lowercase()
    if (status == STATUS_APPROVED || status == STATUS_REJECTED) return true
    if (!fields.approvedByUid.isNullOrBlank()) return true
    if (!fields.rejectedByUid.isNullOrBlank()) return true
    return false
}

// ─── Approval precheck (iOS mirror) ──────────────────────────────────────────
//
// Deliberate duplication of WORKDAY_ALLOWED_KEYS / parseWorkdayDocId /
// precheckWorkday from WorkdayRepositoryHelpers.kt (androidMain). That file is one
// of the six FirebaseWorkdayRepository split files CLAUDE.md freezes, so hoisting
// the shared logic into commonMain is out of scope here — see the session report
// for the recommended follow-up. Android remains the source of truth: any change
// there must be mirrored here, and the two must stay behaviourally identical.

private val WORKDAY_ALLOWED_KEYS = setOf(
    "driverUid",
    "driverName",
    "dateEpochDay",
    "clockInMillis",
    "clockOutMillis",
    "totalMinutes",
    "overtimeMinutes",
    "hasChecklist",
    "checklistSubmittedAt",
    "hasSafetyDeclaration",
    "safetySubmittedAt",
    "approvedByExpat",
    "approverUid",
    "approverName",
    "status",
    "approvedByUid",
    "approvedAtMillis",
    "approvalNote",
    "rejectedByUid",
    "rejectedAtMillis",
    "rejectReason",
    "rejectionNote",
    "adjustedAt",
    "adjustedBy",
    "adjustedReason",
    "overrideByUid",
    "overrideAtMillis",
    "overrideReason",
    "overrideFromStatus",
    "plannedStartMillis",
    "plannedEndMillis",
    "planSource",
    "deviationFlags",
    "reviewHold",
    "originalClockInMillis",
    "originalClockOutMillis",
    "clockCorrectionMeta",
    "expatCorrectionHistory",
    "expatDecision",
    "createdAt",
    "updatedAt"
)

private data class WorkdayPrecheck(
    val ok: Boolean,
    val missing: List<String>,
    val wrongType: List<String>,
    val extraKeys: List<String>,
    val driverUidFromId: String?,
    val epochFromId: Int?,
    val backfillDateEpochDay: Long?,
    val backfillTotalMinutes: Long?,
    val driverUidValue: String?,
    val dateEpochDayValue: Int?,
    val willBackfill: Boolean,
    val reasonCode: ReasonCode,
    val reasonMessage: String?
)

private fun parseWorkdayDocId(docId: String): Pair<String?, Int?> {
    val idx = docId.lastIndexOf('_')
    if (idx <= 0 || idx >= docId.length - 1) return Pair(null, null)
    val driverUid = docId.substring(0, idx).ifBlank { null }
    val epoch = docId.substring(idx + 1).toIntOrNull()
    return Pair(driverUid, epoch)
}

private fun precheckWorkdayFields(docId: String, fields: IOSWorkdayDecisionFields): WorkdayPrecheck {
    val (driverUidFromId, epochFromId) = parseWorkdayDocId(docId)
    val missing = mutableListOf<String>()
    val wrongType = mutableListOf<String>()
    val extraKeys = fields.allKeys.filter { it !in WORKDAY_ALLOWED_KEYS }.sorted()

    // A present-but-blank driverUid counts as wrongType on Android (`is String && isNotBlank`).
    val driverUidOk = fields.driverUid != null && fields.driverUid.isNotBlank()
    if (!driverUidOk) {
        if (!fields.driverUidPresent) missing.add("driverUid") else wrongType.add("driverUid")
    }

    val dateEpochDayValue = fields.dateEpochDay?.toInt()
    val hasDateField = dateEpochDayValue != null
    if (!hasDateField) {
        if (!fields.dateEpochDayPresent) missing.add("dateEpochDay") else wrongType.add("dateEpochDay")
    }

    val totalOk = fields.totalMinutes != null
    if (!totalOk) {
        if (!fields.totalMinutesPresent) missing.add("totalMinutes") else wrongType.add("totalMinutes")
    }

    val hasEpochFromId = epochFromId != null
    // Backfill dateEpochDay from docId only when field is missing and docId is parseable.
    val backfillDateEpochDay = if (!hasDateField && hasEpochFromId) epochFromId.toLong() else null
    val backfillTotalMinutes = if (!totalOk) 0L else null
    val willBackfill = backfillDateEpochDay != null || backfillTotalMinutes != null

    val ok = driverUidOk && extraKeys.isEmpty() && (hasDateField || hasEpochFromId)
    val invalidDocId = !hasDateField && !hasEpochFromId
    val reasonCode = when {
        ok -> ReasonCode.NONE
        !driverUidOk -> ReasonCode.INVALID_SCHEMA
        extraKeys.isNotEmpty() -> ReasonCode.INVALID_SCHEMA
        invalidDocId -> ReasonCode.INVALID_DOC_ID_FORMAT
        else -> ReasonCode.INVALID_SCHEMA
    }
    val reasonMessage = if (ok) null else "missing=$missing wrongType=$wrongType extra=$extraKeys"
    return WorkdayPrecheck(
        ok = ok,
        missing = missing,
        wrongType = wrongType,
        extraKeys = extraKeys,
        driverUidFromId = driverUidFromId,
        epochFromId = epochFromId,
        backfillDateEpochDay = backfillDateEpochDay,
        backfillTotalMinutes = backfillTotalMinutes,
        driverUidValue = fields.driverUid,
        dateEpochDayValue = dateEpochDayValue ?: epochFromId,
        willBackfill = willBackfill,
        reasonCode = reasonCode,
        reasonMessage = reasonMessage
    )
}

private fun resolveComputedMinutes(
    totalMinutes: Int,
    overtimeMinutes: Int,
    clockInMillis: Long,
    clockOutMillis: Long
): Pair<Int, Int> {
    val state = resolveAttendanceDailyState(
        clockInMillis = clockInMillis.takeIf { it > 0L },
        clockOutMillis = clockOutMillis.takeIf { it > 0L }
    )
    return if (state == AttendanceDailyState.OPEN) {
        0 to 0
    } else {
        totalMinutes.coerceAtLeast(0) to overtimeMinutes.coerceAtLeast(0)
    }
}
