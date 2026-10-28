package com.drivemate.showcase.domain.error

sealed class AttendanceError : Throwable() {
    data object AlreadyClockedIn : AttendanceError()
    data object NotClockedIn : AttendanceError()
    data object AlreadyClockedOut : AttendanceError()
    data object ChecklistIncomplete : AttendanceError()
}
