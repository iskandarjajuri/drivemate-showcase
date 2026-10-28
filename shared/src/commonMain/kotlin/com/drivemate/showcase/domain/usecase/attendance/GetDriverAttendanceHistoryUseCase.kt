package com.drivemate.showcase.domain.usecase.attendance

import com.drivemate.showcase.domain.repository.AttendanceRepository

class GetDriverAttendanceHistoryUseCase(private val repo: AttendanceRepository) {
    suspend operator fun invoke(
        driverUid: String,
        startEpochDay: Long,
        endEpochDay: Long
    ) = repo.getAttendancesByEpochDayRange(driverUid, startEpochDay, endEpochDay)
}
