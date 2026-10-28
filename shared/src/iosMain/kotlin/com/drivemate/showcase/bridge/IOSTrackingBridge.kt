package com.drivemate.showcase.bridge

import com.drivemate.showcase.domain.usecase.tracking.ObserveTrackingStatusUseCase
import com.drivemate.showcase.domain.usecase.tracking.UpdateTrackingStatusUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal class IOSTrackingBridge(
    private val scope: CoroutineScope,
    private val observeTrackingStatus: ObserveTrackingStatusUseCase,
    private val updateTrackingStatus: UpdateTrackingStatusUseCase
) {
    private var trackingJob: Job? = null

    fun startObserving(trackingChanged: (IOSTrackingSnapshot) -> Unit) {
        trackingJob?.cancel()
        trackingJob = scope.launch {
            observeTrackingStatus().collect { status ->
                trackingChanged(status.toIosSnapshot())
            }
        }
    }

    fun stopObserving() {
        trackingJob?.cancel()
        trackingJob = null
    }

    fun setHideLocationWhenOffDuty(enabled: Boolean) {
        val current = observeTrackingStatus().value
        if (current.hideLocationWhenOffDuty == enabled) return
        updateTrackingStatus(current.copy(hideLocationWhenOffDuty = enabled))
    }
}
