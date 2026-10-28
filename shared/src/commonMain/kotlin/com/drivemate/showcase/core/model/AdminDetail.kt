package com.drivemate.showcase.core.model

import com.drivemate.showcase.domain.checklist.ChecklistFollowUp

data class ChecklistDetail(
    val id: String,
    val driverUid: String,
    val driverName: String?,
    val driverEmail: String?,
    val dateEpochDay: Int,
    val submittedAt: Long,
    val checklist: Checklist,
    /**
     * Tindak lanjut temuan (Blok 4.1). Default `NEW` — itu arti dokumen yang belum punya
     * field-nya sama sekali, yaitu SELURUH dokumen production sebelum blok ini.
     */
    val followUp: ChecklistFollowUp = ChecklistFollowUp()
)

data class SafetyDetail(
    val id: String,
    val driverUid: String,
    val driverName: String?,
    val driverEmail: String?,
    val dateEpochDay: Int,
    val submittedAt: Long,
    val declaration: SafetyDeclaration
)
