package com.drivemate.showcase.domain.repository

import com.drivemate.showcase.core.model.AssignmentReason
import com.drivemate.showcase.core.model.DriverAssignmentOverride
import com.drivemate.showcase.core.util.AppResult

interface DriverAssignmentRepository {
    suspend fun setPermanentManager(driverUid: String, expatUid: String): AppResult<Unit>
    suspend fun getPermanentManager(driverUid: String): AppResult<String?>
    suspend fun upsertDailyOverride(
        driverUid: String,
        expatUid: String,
        assignedBy: String,
        reason: AssignmentReason,
        epochDay: Int
    ): AppResult<Unit>
    suspend fun getDailyOverride(driverUid: String, epochDay: Int): AppResult<DriverAssignmentOverride?>

    /** Semua override untuk satu hari lintas driver — dipakai layar Admin, bukan resolusi per-driver. */
    suspend fun getOverridesForDate(epochDay: Int): AppResult<List<DriverAssignmentOverride>>
}
