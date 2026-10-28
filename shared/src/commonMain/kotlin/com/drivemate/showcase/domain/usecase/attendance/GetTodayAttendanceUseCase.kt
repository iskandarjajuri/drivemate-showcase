package com.drivemate.showcase.domain.usecase.attendance

import com.drivemate.showcase.core.model.TodayAttendance
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.AttendanceRepository

class GetTodayAttendanceUseCase(
    private val repo: AttendanceRepository
) {
    suspend operator fun invoke(
        driverUid: String,
        epochDay: Long
    ): AppResult<TodayAttendance?> = repo.getTodayAttendance(driverUid, epochDay)
}
