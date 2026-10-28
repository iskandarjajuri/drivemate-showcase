package com.drivemate.showcase.core.model

data class Attendance(
    val driverUid: String,
    val timestampMillis: Long,
    val latitude: Double?,
    val longitude: Double?,
    val note: String? = null,
    val locationName: String? = null
)
