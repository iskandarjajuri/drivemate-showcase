package com.drivemate.showcase.domain.usecase.attendance

import com.drivemate.showcase.core.model.AttendanceEntry
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.AdminDataRepository

class GetAttendanceByRangeUseCase(
    private val repo: AdminDataRepository
) {
    suspend operator fun invoke(
        startEpochDay: Int,
        endEpochDay: Int,
        driverUid: String? = null,
        limit: Int = 200
    ): AppResult<List<AttendanceEntry>> =
        repo.getAttendancesByRange(startEpochDay, endEpochDay, driverUid, limit)
}
