package com.drivemate.showcase.domain.usecase.tracking

import com.drivemate.showcase.core.model.TrackingStatus
import com.drivemate.showcase.domain.repository.TrackingStatusRepository

class UpdateTrackingStatusUseCase(
    private val repo: TrackingStatusRepository
) {
    operator fun invoke(status: TrackingStatus) {
        repo.update(status)
    }
}
