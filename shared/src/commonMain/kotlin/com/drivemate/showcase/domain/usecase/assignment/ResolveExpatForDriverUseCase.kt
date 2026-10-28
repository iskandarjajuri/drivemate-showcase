package com.drivemate.showcase.domain.usecase.assignment

import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.DriverAssignmentRepository

/**
 * Cek override hari ini dulu, fallback ke managerUid permanen, null kalau
 * keduanya kosong (state driver-belum-ditugaskan yang sah, bukan error).
 */
class ResolveExpatForDriverUseCase(
    private val repo: DriverAssignmentRepository
) {
    suspend operator fun invoke(driverUid: String, epochDay: Int): AppResult<String?> {
        when (val overrideResult = repo.getDailyOverride(driverUid, epochDay)) {
            is AppResult.Failure -> return AppResult.failure(overrideResult.throwable)
            is AppResult.Success -> {
                val override = overrideResult.data
                if (override != null) return AppResult.success(override.expatUid)
            }
        }
        return repo.getPermanentManager(driverUid)
    }
}
