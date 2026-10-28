package com.drivemate.showcase.data.repository

import com.drivemate.showcase.core.model.TrackingStatus
import com.drivemate.showcase.domain.repository.TrackingStatusRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSUserDefaults

private const val OFF_DUTY_PRIVACY_KEY = "tracking_hide_location_when_off_duty"

class ApplePersistedTrackingStatusRepository(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults
) : TrackingStatusRepository {

    private val _status = MutableStateFlow(
        TrackingStatus(
            hideLocationWhenOffDuty = defaults.boolForKey(OFF_DUTY_PRIVACY_KEY)
        )
    )

    override val status: StateFlow<TrackingStatus> = _status.asStateFlow()

    override fun update(status: TrackingStatus) {
        _status.value = status
        defaults.setBool(status.hideLocationWhenOffDuty, forKey = OFF_DUTY_PRIVACY_KEY)
    }
}
