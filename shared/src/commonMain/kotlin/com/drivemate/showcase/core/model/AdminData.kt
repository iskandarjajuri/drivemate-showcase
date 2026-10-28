package com.drivemate.showcase.core.model

import com.drivemate.showcase.domain.checklist.ChecklistFollowUpStatus

data class AttendanceEntry(
    val id: String,
    val driverUid: String,
    val timestampMillis: Long,
    val type: String,
    val latitude: Double?,
    val longitude: Double?,
    val locationName: String?
)

data class ChecklistSummary(
    val id: String,
    val driverUid: String,
    val driverName: String?,
    val dateEpochDay: Int,
    val submittedAt: Long,
    val hasIssue: Boolean,
    /**
     * Status tindak lanjut (Blok 4.1). Dibawa di ringkasan, bukan cuma di detail, supaya
     * daftar pengecualian bisa berhenti menagih temuan yang sudah ditutup — tanpa ini,
     * "tindak lanjut" hanya tercatat dan tidak mengubah apa pun yang dilihat admin.
     */
    val followUpStatus: ChecklistFollowUpStatus = ChecklistFollowUpStatus.NEW
)

data class SafetySummary(
    val id: String,
    val driverUid: String,
    val driverName: String?,
    val dateEpochDay: Int,
    val submittedAt: Long,
    val fitToWork: Boolean,
    val confirmed: Boolean
)

data class SopStatus(
    val uid: String,
    val name: String?,
    val sopAccepted: Boolean,
    val sopAcceptedAtMillis: Long?
)
