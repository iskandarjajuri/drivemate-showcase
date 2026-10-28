package com.drivemate.showcase.feature.driver

import androidx.annotation.StringRes

sealed interface DriverUiState {
    data object Loading : DriverUiState
    object Idle : DriverUiState
    data class Working(
        val clockedIn: Boolean,
        val lastTapIn: String? = null,
        val lastTapOut: String? = null,
        @param:StringRes val messageRes: Int? = null,
        val todayAttendance: TodayAttendanceUi = TodayAttendanceUi()
    ) : DriverUiState
    data class Error(@param:StringRes val messageRes: Int) : DriverUiState
}

data class TodayAttendanceUi(
    val clockInMillis: Long? = null,
    val clockOutMillis: Long? = null
) {
    val isWorking: Boolean
        get() = clockInMillis != null && clockOutMillis == null
    val isCompleted: Boolean
        get() = clockInMillis != null && clockOutMillis != null
}
