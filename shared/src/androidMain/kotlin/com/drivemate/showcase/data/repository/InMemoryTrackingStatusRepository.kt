package com.drivemate.showcase.data.repository

import com.drivemate.showcase.core.model.TrackingStatus
import com.drivemate.showcase.domain.repository.TrackingStatusRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class InMemoryTrackingStatusRepository : TrackingStatusRepository {
    private val _status = MutableStateFlow(TrackingStatus())
    override val status: StateFlow<TrackingStatus> = _status

    override fun update(status: TrackingStatus) {
        _status.value = status
    }
}
