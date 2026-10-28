package com.drivemate.showcase.feature.driver

import androidx.annotation.StringRes
import com.drivemate.showcase.core.model.TrackingIssue

data class DriverTrackingUiState(
    val warningType: TrackingIssue? = null,
    @param:StringRes val warningMessageRes: Int? = null,
    val batteryOptimizationEnabled: Boolean = false,
    val hideLocationWhenOffDuty: Boolean = false,
    /**
     * R3 — driver sedang melebihi batas kecepatan SEKARANG.
     *
     * Ini lapisan penegakan real-time, TERPISAH dari `willFollowSpeedLimit` di
     * Safety Declaration (itu janji/pledge yang dicentang sekali di awal hari).
     * Keduanya sengaja tidak digabung.
     */
    val overSpeedLimit: Boolean = false,
    val currentSpeedKph: Double? = null,
    val speedLimitKph: Double? = null
)
