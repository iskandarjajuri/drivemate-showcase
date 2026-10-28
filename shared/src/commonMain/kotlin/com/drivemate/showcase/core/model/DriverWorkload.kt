package com.drivemate.showcase.core.model

data class DriverWorkloadScore(
    val level: DriverWorkloadLevel,
    val score: Int,
    val maxScore: Int,
    val factors: List<DriverWorkloadFactor> = emptyList(),
    val recommendedActionKey: String
) {
    val normalizedScorePercent: Int
        get() = if (maxScore <= 0) 0 else ((score.coerceAtLeast(0) * 100) / maxScore).coerceIn(0, 100)
}

enum class DriverWorkloadLevel {
    LOW,
    NORMAL,
    ELEVATED,
    HIGH
}

data class DriverWorkloadFactor(
    val code: DriverWorkloadFactorCode,
    val severity: DriverWorkloadFactorSeverity,
    val weight: Int,
    val facts: List<DriverWorkloadFact> = emptyList()
)

enum class DriverWorkloadFactorSeverity(val priority: Int) {
    LOW(priority = 0),
    ELEVATED(priority = 1),
    HIGH(priority = 2)
}

enum class DriverWorkloadFactorCode {
    LONG_DUTY,
    DRIVING_DURATION,
    TRIP_VOLUME,
    STOP_VOLUME,
    CHECKLIST_ISSUE,
    MISSING_SAFETY_DECLARATION,
    STALE_LOCATION,
    MOBILITY_INSIGHT_CLUSTER,
    FATIGUE_SELF_REPORT
}

data class DriverWorkloadFact(
    val code: DriverWorkloadFactCode,
    val value: String? = null
)

enum class DriverWorkloadFactCode {
    DUTY_MINUTES,
    DUTY_THRESHOLD_MINUTES,
    DRIVING_MINUTES,
    DRIVING_THRESHOLD_MINUTES,
    TRIP_COUNT,
    TRIP_THRESHOLD_COUNT,
    STOP_COUNT,
    STOP_THRESHOLD_COUNT,
    CHECKLIST_ISSUE_COUNT,
    SAFETY_DECLARATION_STATUS,
    STALE_LOCATION_MINUTES,
    STALE_LOCATION_THRESHOLD_MINUTES,
    HIGH_SEVERITY_INSIGHT_COUNT,
    ELEVATED_SEVERITY_INSIGHT_COUNT,
    FATIGUE_SELF_REPORT_SEVERITY
}
