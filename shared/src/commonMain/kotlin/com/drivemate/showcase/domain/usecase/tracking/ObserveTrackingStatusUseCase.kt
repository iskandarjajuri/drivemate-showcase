package com.drivemate.showcase.domain.usecase.tracking

import com.drivemate.showcase.core.model.TrackingStatus
import com.drivemate.showcase.domain.repository.TrackingStatusRepository
import kotlinx.coroutines.flow.StateFlow

class ObserveTrackingStatusUseCase(
    private val repo: TrackingStatusRepository
) {
    operator fun invoke(): StateFlow<TrackingStatus> = repo.status
}
