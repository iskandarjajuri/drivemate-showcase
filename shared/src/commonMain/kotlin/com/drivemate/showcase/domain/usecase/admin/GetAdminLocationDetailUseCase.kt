package com.drivemate.showcase.domain.usecase.admin

import com.drivemate.showcase.core.model.Location
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.AdminDataRepository

class GetAdminLocationDetailUseCase(
    private val repo: AdminDataRepository
) {
    suspend operator fun invoke(uid: String): AppResult<Location?> =
        repo.getLocationByUid(uid)
}
