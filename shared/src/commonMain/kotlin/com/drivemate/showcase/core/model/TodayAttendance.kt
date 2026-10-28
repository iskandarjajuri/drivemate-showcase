package com.drivemate.showcase.core.model

enum class AttendanceDailyState {
    NOT_STARTED,
    OPEN,
    CLOSED
}

fun resolveAttendanceDailyState(
    clockInMillis: Long?,
    clockOutMillis: Long?
): AttendanceDailyState {
    val inMillis = clockInMillis?.takeIf { it > 0L }
    val outMillis = clockOutMillis?.takeIf { it > 0L }
    return when {
        inMillis == null -> AttendanceDailyState.NOT_STARTED
        outMillis == null -> AttendanceDailyState.OPEN
        else -> AttendanceDailyState.CLOSED
    }
}

data class TodayAttendance(
    val driverUid: String,
    val epochDay: Long,
    val clockInMillis: Long?,
    val clockOutMillis: Long?,
    val updatedAtMillis: Long
) {
    val state: AttendanceDailyState
        get() = resolveAttendanceDailyState(clockInMillis, clockOutMillis)
}
