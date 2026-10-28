package com.drivemate.showcase.core.model

data class MobilityInsight(
    val key: String,
    val type: MobilityInsightType,
    val severity: MobilityInsightSeverity,
    val titleKey: String,
    val messageKey: String,
    val recommendedActionKey: String? = null,
    val reasons: List<MobilityInsightReason> = emptyList(),
    val driverUid: String? = null,
    val dateEpochDay: Int? = null,
    val workdayId: String? = null
)

data class MobilityInsightReason(
    val code: MobilityInsightReasonCode,
    val value: String? = null
)

enum class MobilityInsightType {
    ATTENDANCE_INCOMPLETE,
    SAFETY_DECLARATION_MISSING,
    CHECKLIST_ISSUE_DETECTED,
    LONG_DUTY_DURATION,
    LOCATION_STALE,
    ADMIN_REVIEW_NEEDED
}

enum class MobilityInsightSeverity(val priority: Int) {
    LOW(priority = 0),
    ELEVATED(priority = 1),
    HIGH(priority = 2)
}

enum class MobilityInsightReasonCode {
    ATTENDANCE_STATE,
    SAFETY_DECLARATION_STATUS,
    CHECKLIST_STATUS,
    DUTY_MINUTES,
    DUTY_THRESHOLD_MINUTES,
    LOCATION_STALE_MINUTES,
    REVIEW_HOLD_STATUS,
    DEVIATION_FLAG
}
