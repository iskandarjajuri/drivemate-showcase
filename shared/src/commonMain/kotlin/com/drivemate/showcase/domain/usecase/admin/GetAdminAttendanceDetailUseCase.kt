package com.drivemate.showcase.domain.usecase.admin

import com.drivemate.showcase.core.model.AttendanceEntry
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.AdminDataRepository

class GetAdminAttendanceDetailUseCase(
    private val repo: AdminDataRepository
) {
    suspend operator fun invoke(id: String): AppResult<AttendanceEntry?> =
        repo.getAttendanceById(id)
}
