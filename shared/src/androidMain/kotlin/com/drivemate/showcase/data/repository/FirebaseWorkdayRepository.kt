package com.drivemate.showcase.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.drivemate.showcase.core.model.BulkActionResult
import com.drivemate.showcase.core.model.ExplicitWorkdayPlanWindow
import com.drivemate.showcase.core.model.WorkDay
import com.drivemate.showcase.core.model.WorkdaySummary
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.AuthRepository
import com.drivemate.showcase.domain.repository.WorkCalendarRepository
import com.drivemate.showcase.domain.repository.WorkDayRepository

// Facade: same class name and constructor as before — DI wiring in AndroidSharedModule is unchanged.
// All business logic lives in the four sub-repositories; this class only delegates.
class FirebaseWorkdayRepository(
    db: FirebaseFirestore,
    authRepo: AuthRepository,
    workCalendarRepository: WorkCalendarRepository
) : WorkDayRepository {

    private val query    = WorkdayQueryRepository(db)
    private val approval = WorkdayApprovalRepository(db, authRepo)
    private val bulk     = WorkdayBulkApprovalRepository(db, authRepo)
    private val gen      = WorkdayGenerationRepository(db, authRepo, workCalendarRepository)

    // ─── Read operations ──────────────────────────────────────────────────────

    override suspend fun getWorkDaysByRange(
        driverUid: String,
        startEpochDay: Int,
        endEpochDay: Int
    ): List<WorkDay> = query.getWorkDaysByRange(driverUid, startEpochDay, endEpochDay)

    override suspend fun getWorkdaySummary(
        driverUid: String,
        startEpochDay: Int,
        endEpochDay: Int
    ): WorkdaySummary = query.getWorkdaySummary(driverUid, startEpochDay, endEpochDay)

    override suspend fun getWorkDayById(workDayId: String): AppResult<WorkDay?> =
        query.getWorkDayById(workDayId)

    override suspend fun getExplicitPlan(
        driverUid: String,
        epochDay: Int
    ): AppResult<ExplicitWorkdayPlanWindow?> = gen.getExplicitPlanWindow(driverUid, epochDay)

    // ─── Single-record decisions ──────────────────────────────────────────────

    override suspend fun approve(workDayId: String, note: String?): AppResult<Unit> =
        approval.approve(workDayId, note)

    override suspend fun reject(workDayId: String, note: String): AppResult<Unit> =
        approval.reject(workDayId, note)

    override suspend fun adminOverrideDecision(
        workDayId: String,
        targetStatus: String,
        rejectReason: String?,
        overrideReason: String
    ): AppResult<Unit> = approval.adminOverrideDecision(workDayId, targetStatus, rejectReason, overrideReason)

    // ─── Bulk decisions ───────────────────────────────────────────────────────

    override suspend fun bulkApprove(workDayIds: List<String>): BulkActionResult =
        bulk.bulkApprove(workDayIds)

    override suspend fun bulkReject(workDayIds: List<String>, note: String): BulkActionResult =
        bulk.bulkReject(workDayIds, note)

    // ─── Generation and plan management ──────────────────────────────────────

    override suspend fun generateWorkdayFromAttendances(
        driverUid: String,
        dateEpochDay: Int
    ): AppResult<Unit> = gen.generateWorkdayFromAttendances(driverUid, dateEpochDay)

    override suspend fun upsertWorkdayPlan(
        driverUid: String,
        epochDay: Int,
        plannedStartMillis: Long,
        plannedEndMillis: Long,
        source: String
    ): AppResult<Unit> = gen.upsertWorkdayPlan(driverUid, epochDay, plannedStartMillis, plannedEndMillis, source)

    // ─── Internal range query (was public on original class, preserved for any direct callers) ──

    internal suspend fun latestForDriver(
        driverUid: String,
        daysBack: Int = 90,
        limit: Int = 30
    ): List<WorkdayDTO> = query.latestForDriver(driverUid, daysBack, limit)

    internal suspend fun byDriverAndEpochRange(
        driverUid: String,
        startEpochDayInclusive: Int,
        endEpochDayInclusive: Int,
        limit: Int = 100
    ): List<WorkdayDTO> = query.byDriverAndEpochRange(driverUid, startEpochDayInclusive, endEpochDayInclusive, limit)
}
