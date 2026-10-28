package com.drivemate.showcase.data.repository

import com.drivemate.showcase.bridge.IOSAdminAttendancePayload
import com.drivemate.showcase.bridge.IOSAdminChecklistDetailPayload
import com.drivemate.showcase.bridge.IOSAdminChecklistSummaryPayload
import com.drivemate.showcase.bridge.IOSAdminLocationPayload
import com.drivemate.showcase.bridge.IOSAdminSafetyDetailPayload
import com.drivemate.showcase.bridge.IOSAdminSafetySummaryPayload
import com.drivemate.showcase.bridge.IOSAdminWorkdayPayload
import com.drivemate.showcase.bridge.IOSPlatformBridgeRegistry
import com.drivemate.showcase.core.model.AttendanceDailyState
import com.drivemate.showcase.core.model.AttendanceEntry
import com.drivemate.showcase.core.model.Checklist
import com.drivemate.showcase.core.model.ChecklistDetail
import com.drivemate.showcase.domain.checklist.ChecklistFollowUp
import com.drivemate.showcase.domain.checklist.ChecklistFollowUpStatus
import com.drivemate.showcase.domain.checklist.buildChecklistFollowUpPayload
import com.drivemate.showcase.domain.workday.WorkdayDecisionStep
import com.drivemate.showcase.domain.workday.WorkdayDecisionTrailFields
import com.drivemate.showcase.domain.workday.buildWorkdayDecisionStepPayload
import com.drivemate.showcase.domain.workday.mapWorkdayDecisionTrail
import com.drivemate.showcase.core.model.ChecklistSummary
import com.drivemate.showcase.core.model.Location
import com.drivemate.showcase.core.model.PlanSource
import com.drivemate.showcase.core.model.SafetyDeclaration
import com.drivemate.showcase.core.model.SafetyDetail
import com.drivemate.showcase.core.model.SafetySummary
import com.drivemate.showcase.core.model.WorkDay
import com.drivemate.showcase.core.model.normalizeApprovalStatus
import com.drivemate.showcase.core.model.parseDeviationFlags
import com.drivemate.showcase.core.model.resolveAttendanceDailyState
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.AdminDataRepository
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class IOSAdminDataRepository : AdminDataRepository {
    private val bridge
        get() = IOSPlatformBridgeRegistry.adminDataBridge
            ?: error("IOSAdminDataPlatformBridge is not installed")

    override suspend fun getAttendancesByRange(
        startEpochDay: Int,
        endEpochDay: Int,
        driverUid: String?,
        limit: Int
    ): AppResult<List<AttendanceEntry>> =
        suspendCancellableCoroutine { cont ->
            bridge.getAdminAttendancesByRange(startEpochDay, endEpochDay, driverUid, limit) { payloads, errorMessage ->
                if (!cont.isCompleted) {
                    cont.resume(
                        when {
                            errorMessage != null -> AppResult.failure(IllegalStateException(errorMessage))
                            else -> AppResult.success(payloads.orEmpty().map { it.toEntry() })
                        }
                    )
                }
            }
        }

    override suspend fun getChecklistsByRange(
        startEpochDay: Int,
        endEpochDay: Int,
        driverUid: String?,
        limit: Int
    ): AppResult<List<ChecklistSummary>> =
        suspendCancellableCoroutine { cont ->
            bridge.getAdminChecklistsByRange(startEpochDay, endEpochDay, driverUid, limit) { payloads, errorMessage ->
                if (!cont.isCompleted) {
                    cont.resume(
                        when {
                            errorMessage != null -> AppResult.failure(IllegalStateException(errorMessage))
                            else -> AppResult.success(payloads.orEmpty().map { it.toSummary() })
                        }
                    )
                }
            }
        }

    override suspend fun getSafetyByRange(
        startEpochDay: Int,
        endEpochDay: Int,
        driverUid: String?,
        limit: Int
    ): AppResult<List<SafetySummary>> =
        suspendCancellableCoroutine { cont ->
            bridge.getAdminSafetyByRange(startEpochDay, endEpochDay, driverUid, limit) { payloads, errorMessage ->
                if (!cont.isCompleted) {
                    cont.resume(
                        when {
                            errorMessage != null -> AppResult.failure(IllegalStateException(errorMessage))
                            else -> AppResult.success(payloads.orEmpty().map { it.toSummary() })
                        }
                    )
                }
            }
        }

    override suspend fun getWorkdaysByRange(
        startEpochDay: Int,
        endEpochDay: Int,
        driverUid: String?,
        limit: Int
    ): AppResult<List<WorkDay>> =
        suspendCancellableCoroutine { cont ->
            bridge.getAdminWorkdaysByRange(startEpochDay, endEpochDay, driverUid, limit) { payloads, errorMessage ->
                if (!cont.isCompleted) {
                    cont.resume(
                        when {
                            errorMessage != null -> AppResult.failure(IllegalStateException(errorMessage))
                            else -> AppResult.success(payloads.orEmpty().map { it.toWorkDay() })
                        }
                    )
                }
            }
        }

    override suspend fun getLocations(limit: Int): AppResult<List<Location>> =
        suspendCancellableCoroutine { cont ->
            bridge.getAdminLocations(limit) { payloads, errorMessage ->
                if (!cont.isCompleted) {
                    cont.resume(
                        when {
                            errorMessage != null -> AppResult.failure(IllegalStateException(errorMessage))
                            else -> AppResult.success(payloads.orEmpty().map { it.toLocation() })
                        }
                    )
                }
            }
        }

    override suspend fun getAttendanceById(id: String): AppResult<AttendanceEntry?> =
        suspendCancellableCoroutine { cont ->
            bridge.getAdminAttendanceById(id) { payload, errorMessage ->
                if (!cont.isCompleted) {
                    cont.resume(
                        when {
                            errorMessage != null -> AppResult.failure(IllegalStateException(errorMessage))
                            else -> AppResult.success(payload?.toEntry())
                        }
                    )
                }
            }
        }

    override suspend fun getChecklistDetail(id: String): AppResult<ChecklistDetail?> =
        suspendCancellableCoroutine { cont ->
            bridge.getAdminChecklistDetail(id) { payload, errorMessage ->
                if (!cont.isCompleted) {
                    cont.resume(
                        when {
                            errorMessage != null -> AppResult.failure(IllegalStateException(errorMessage))
                            else -> AppResult.success(payload?.toDetail())
                        }
                    )
                }
            }
        }

    override suspend fun getSafetyDetail(id: String): AppResult<SafetyDetail?> =
        suspendCancellableCoroutine { cont ->
            bridge.getAdminSafetyDetail(id) { payload, errorMessage ->
                if (!cont.isCompleted) {
                    cont.resume(
                        when {
                            errorMessage != null -> AppResult.failure(IllegalStateException(errorMessage))
                            else -> AppResult.success(payload?.toDetail())
                        }
                    )
                }
            }
        }

    override suspend fun getWorkdayById(id: String): AppResult<WorkDay?> =
        suspendCancellableCoroutine { cont ->
            bridge.getAdminWorkdayById(id) { payload, errorMessage ->
                if (!cont.isCompleted) {
                    cont.resume(
                        when {
                            errorMessage != null -> AppResult.failure(IllegalStateException(errorMessage))
                            else -> AppResult.success(payload?.toWorkDay())
                        }
                    )
                }
            }
        }

    override suspend fun getLocationByUid(uid: String): AppResult<Location?> =
        suspendCancellableCoroutine { cont ->
            bridge.getAdminLocationByUid(uid) { payload, errorMessage ->
                if (!cont.isCompleted) {
                    cont.resume(
                        when {
                            errorMessage != null -> AppResult.failure(IllegalStateException(errorMessage))
                            else -> AppResult.success(payload?.toLocation())
                        }
                    )
                }
            }
        }

    /**
     * BELUM TERPASANG di iOS — tindak lanjut temuan checklist Android-only sampai Blok 6.
     *
     * Override eksplisit yang beralasan, bukan default diam-diam di interface: pembaca
     * berkas ini harus MELIHAT bahwa jalur ini belum ada. Mengikuti preseden
     * `IOSSpeedViolationRepository.listRecentViolations` dan `IOSLocationRepository`.
     *
     * Saat bridge Swift-nya dibuat nanti: jangan menyusun payload di Swift dan jangan
     * menegakkan mesin statusnya di sana — `evaluateChecklistFollowUp` dan
     * `buildChecklistFollowUpPayload` di commonMain sudah memegang keduanya, dan dua
     * tempat yang bisa berbeda pendapat soal transisi mana yang sah adalah bentuk drift
     * paling mahal yang bisa dibuat di sini.
     */
    override suspend fun updateChecklistFollowUp(
        id: String,
        followUp: ChecklistFollowUp,
    ): AppResult<Unit> = suspendCancellableCoroutine { cont ->
        // Payload dirakit DI SINI, bukan di Swift: nama field beku di
        // `ChecklistFollowUpFields`, dan mengejanya ulang di Swift berarti konstanta kedua
        // yang bisa mendrift — preseden `recordAdminWorkdayDecision` (Blok 4.2).
        val payload = buildChecklistFollowUpPayload(followUp) +
            mapOf("updatedAt" to (followUp.updatedAtMillis ?: 0L))
        val bridge = IOSPlatformBridgeRegistry.adminDataBridge
        if (bridge == null) {
            cont.resume(
                AppResult.failure(IllegalStateException("IOSAdminDataPlatformBridge is not installed"))
            )
            return@suspendCancellableCoroutine
        }
        bridge.applyChecklistFollowUp(id, payload) { errorMessage ->
            if (!cont.isCompleted) {
                cont.resume(
                    if (errorMessage == null) AppResult.success(Unit)
                    else AppResult.failure(IllegalStateException(errorMessage))
                )
            }
        }
    }

    /**
     * Langkah 2 jejak keputusan (Blok 4.2) — cermin write Android
     * `FirebaseAdminDataRepository.recordAdminWorkdayDecision`: map BERSARANG + merge,
     * supaya `decisionTrail.supervisor` (langkah 1, beku menurut `firestore.rules`) lestari.
     * Payload dirakit di sini, bukan di Swift — nama field beku di
     * `WorkdayDecisionTrailFields`.
     */
    override suspend fun recordAdminWorkdayDecision(
        workdayId: String,
        step: WorkdayDecisionStep,
    ): AppResult<Unit> = suspendCancellableCoroutine { cont ->
        // Payload dirakit di SINI, bukan di Swift: nama field beku di
        // `WorkdayDecisionTrailFields`, dan mengejanya ulang di Swift berarti konstanta
        // kedua yang bisa mendrift. Swift hanya menulis map ini dengan merge=true.
        val payload = mapOf(
            WorkdayDecisionTrailFields.TRAIL to mapOf(
                WorkdayDecisionTrailFields.ADMIN to buildWorkdayDecisionStepPayload(step)
            ),
            "updatedAt" to (step.atMillis ?: 0L),
        )
        val bridge = IOSPlatformBridgeRegistry.workdayBridge
        if (bridge == null) {
            cont.resume(
                AppResult.failure(IllegalStateException("IOSWorkdayPlatformBridge is not installed"))
            )
            return@suspendCancellableCoroutine
        }
        bridge.recordAdminWorkdayDecision(workdayId, payload) { errorMessage ->
            if (!cont.isCompleted) {
                cont.resume(
                    if (errorMessage == null) AppResult.success(Unit)
                    else AppResult.failure(IllegalStateException(errorMessage))
                )
            }
        }
    }

}

private fun IOSAdminAttendancePayload.toEntry(): AttendanceEntry = AttendanceEntry(
    id = id,
    driverUid = driverUid,
    timestampMillis = timestampMillis,
    type = type,
    latitude = latitude,
    longitude = longitude,
    locationName = locationName
)

private fun IOSAdminChecklistSummaryPayload.toSummary(): ChecklistSummary = ChecklistSummary(
    id = id,
    driverUid = driverUid,
    driverName = driverName,
    dateEpochDay = dateEpochDay,
    submittedAt = submittedAt,
    hasIssue = hasIssue,
    followUpStatus = ChecklistFollowUpStatus.fromCode(followUpStatusRaw)
)

private fun IOSAdminSafetySummaryPayload.toSummary(): SafetySummary = SafetySummary(
    id = id,
    driverUid = driverUid,
    driverName = driverName,
    dateEpochDay = dateEpochDay,
    submittedAt = submittedAt,
    fitToWork = fitToWork,
    confirmed = confirmed
)

private fun IOSAdminChecklistDetailPayload.toDetail(): ChecklistDetail = ChecklistDetail(
    id = id,
    driverUid = driverUid,
    driverName = driverName,
    driverEmail = driverEmail,
    dateEpochDay = dateEpochDay,
    submittedAt = submittedAt,
    checklist = Checklist(
        vehicleId = vehicleId,
        odometerKm = odometerKm,
        fuelLevel = fuelLevel,
        tiresFrontLeftOk = tiresFrontLeftOk,
        tiresFrontRightOk = tiresFrontRightOk,
        tiresRearLeftOk = tiresRearLeftOk,
        tiresRearRightOk = tiresRearRightOk,
        tirePressureNote = tirePressureNote,
        brakesOk = brakesOk,
        lightsOk = lightsOk,
        hornOk = hornOk,
        wipersOk = wipersOk,
        mirrorsOk = mirrorsOk,
        seatbeltOk = seatbeltOk,
        engineOilOk = engineOilOk,
        coolantOk = coolantOk,
        batteryOk = batteryOk,
        cleanlinessOk = cleanlinessOk,
        documentsOk = documentsOk,
        fireExtinguisherOk = fireExtinguisherOk,
        firstAidOk = firstAidOk,
        warningTriangleOk = warningTriangleOk,
        turnSignalBrakeLightsOk = turnSignalBrakeLightsOk,
        washerFluidOk = washerFluidOk,
        notes = notes,
        hasIssue = hasIssue
    ),
    followUp = ChecklistFollowUp(
        status = ChecklistFollowUpStatus.fromCode(followUpStatusRaw),
        ownerUid = followUpOwnerUid,
        note = followUpNote,
        updatedAtMillis = followUpUpdatedAtMillis,
    )
)

private fun IOSAdminSafetyDetailPayload.toDetail(): SafetyDetail = SafetyDetail(
    id = id,
    driverUid = driverUid,
    driverName = driverName,
    driverEmail = driverEmail,
    dateEpochDay = dateEpochDay,
    submittedAt = submittedAt,
    declaration = SafetyDeclaration(
        fitToWork = fitToWork,
        sleepHours = sleepHours,
        noAlcoholOrDrugs = noAlcoholOrDrugs,
        noSevereStress = noSevereStress,
        willFollowSpeedLimit = willFollowSpeedLimit,
        willFollowRestBreaks = willFollowRestBreaks,
        willNotUsePhoneWhileDriving = willNotUsePhoneWhileDriving,
        willWearSeatbelt = willWearSeatbelt,
        hazardAwareness = hazardAwareness,
        weatherCondition = weatherCondition,
        routeRiskNote = routeRiskNote,
        incidentReport = incidentReport,
        confirmed = confirmed
    )
)

private fun IOSAdminLocationPayload.toLocation(): Location = Location(
    driverUid = driverUid,
    latitude = latitude,
    longitude = longitude,
    speedMps = speedMps,
    speedKph = speedKph,
    updatedAtMillis = updatedAtMillis,
    locationName = locationName
)

private fun IOSAdminWorkdayPayload.toWorkDay(): WorkDay {
    val normalized = normalizeApprovalStatus(
        statusRaw = status,
        approvedByExpat = approvedByExpat,
        approvedByUid = approvedByUid,
        rejectedByUid = rejectedByUid,
        rejectedAtMillis = rejectedAtMillis,
        rejectReason = rejectReason,
        rejectionNote = rejectionNote
    )
    val (total, overtime) = resolveComputedMinutes(
        totalMinutes = totalMinutes,
        overtimeMinutes = overtimeMinutes,
        clockInMillis = clockInMillis,
        clockOutMillis = clockOutMillis
    )
    return WorkDay(
        id = id,
        driverUid = driverUid,
        dateEpochDay = dateEpochDay,
        clockInMillis = clockInMillis,
        clockOutMillis = clockOutMillis,
        totalMinutes = total,
        overtimeMinutes = overtime,
        driverName = driverName,
        approvedByExpat = normalized.approvedByExpat,
        rejectionNote = rejectionNote,
        adjustedBy = adjustedBy,
        adjustedAt = adjustedAt,
        adjustedReason = adjustedReason,
        hasChecklist = hasChecklist,
        checklistSubmittedAt = checklistSubmittedAt,
        hasSafetyDeclaration = hasSafetyDeclaration,
        safetySubmittedAt = safetySubmittedAt,
        approverUid = approverUid,
        approverName = approverName,
        status = normalized.status,
        approvedByUid = approvedByUid,
        approvedAtMillis = approvedAtMillis,
        approvalNote = approvalNote,
        rejectedByUid = rejectedByUid,
        rejectedAtMillis = rejectedAtMillis,
        rejectReason = rejectReason,
        plannedStartMillis = plannedStartMillis,
        plannedEndMillis = plannedEndMillis,
        planSource = PlanSource.fromRaw(planSourceRaw),
        deviationFlags = parseDeviationFlags(deviationFlagsRaw),
        reviewHold = reviewHold ?: false,
        decisionTrail = mapWorkdayDecisionTrail(decisionTrail)
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
