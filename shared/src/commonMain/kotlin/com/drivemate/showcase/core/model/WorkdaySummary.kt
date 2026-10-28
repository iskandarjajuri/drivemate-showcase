package com.drivemate.showcase.core.model

data class WorkdaySummary(
    val driverUid: String,
    val totalDays: Int,
    val approvedCount: Int,
    val rejectedCount: Int,
    val pendingCount: Int,
    val lastEpochDay: Int?
)
