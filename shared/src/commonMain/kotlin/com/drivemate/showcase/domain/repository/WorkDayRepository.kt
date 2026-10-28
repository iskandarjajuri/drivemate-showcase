package com.drivemate.showcase.domain.repository

import com.drivemate.showcase.core.model.BulkActionResult
import com.drivemate.showcase.core.model.ExplicitWorkdayPlanWindow
import com.drivemate.showcase.core.model.WorkDay
import com.drivemate.showcase.core.model.WorkdaySummary
import com.drivemate.showcase.core.util.AppResult

interface WorkDayRepository {
    suspend fun getWorkDaysByRange(driverUid: String, startEpochDay: Int, endEpochDay: Int): List<WorkDay>
    /** Reads `workday_plans/{driverUid}_{epochDay}` directly — used when no `workdays` doc exists yet. */
    suspend fun getExplicitPlan(driverUid: String, epochDay: Int): AppResult<ExplicitWorkdayPlanWindow?>
    suspend fun getWorkdaySummary(driverUid: String, startEpochDay: Int, endEpochDay: Int): WorkdaySummary
    suspend fun getWorkDayById(workDayId: String): AppResult<WorkDay?>
    suspend fun approve(workDayId: String, note: String?): AppResult<Unit>
    suspend fun bulkApprove(workDayIds: List<String>): BulkActionResult
    suspend fun reject(workDayId: String, note: String): AppResult<Unit>
    suspend fun bulkReject(workDayIds: List<String>, note: String): BulkActionResult
    suspend fun adminOverrideDecision(
        workDayId: String,
        targetStatus: String,
        rejectReason: String?,
        overrideReason: String
    ): AppResult<Unit>
    suspend fun generateWorkdayFromAttendances(driverUid: String, dateEpochDay: Int): AppResult<Unit>
    suspend fun upsertWorkdayPlan(
        driverUid: String,
        epochDay: Int,
        plannedStartMillis: Long,
        plannedEndMillis: Long,
        source: String
    ): AppResult<Unit>
}
