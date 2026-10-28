package com.drivemate.showcase.domain.repository

import com.drivemate.showcase.core.model.TrackingStatus
import kotlinx.coroutines.flow.StateFlow

interface TrackingStatusRepository {
    val status: StateFlow<TrackingStatus>
    fun update(status: TrackingStatus)
}
