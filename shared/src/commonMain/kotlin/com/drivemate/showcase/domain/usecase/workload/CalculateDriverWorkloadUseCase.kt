package com.drivemate.showcase.domain.usecase.workload

import com.drivemate.showcase.core.model.DriverWorkloadFact
import com.drivemate.showcase.core.model.DriverWorkloadFactCode
import com.drivemate.showcase.core.model.DriverWorkloadFactor
import com.drivemate.showcase.core.model.DriverWorkloadFactorCode
import com.drivemate.showcase.core.model.DriverWorkloadFactorSeverity
import com.drivemate.showcase.core.model.DriverWorkloadLevel
import com.drivemate.showcase.core.model.DriverWorkloadScore
import com.drivemate.showcase.core.model.MobilityInsight
import com.drivemate.showcase.core.model.MobilityInsightSeverity

private const val DUTY_ELEVATED_MINUTES = 10 * 60
private const val DUTY_HIGH_MINUTES = 12 * 60
private const val DRIVING_ELEVATED_MINUTES = 8 * 60
private const val DRIVING_HIGH_MINUTES = 10 * 60
private const val TRIP_ELEVATED_COUNT = 8
private const val TRIP_HIGH_COUNT = 12
private const val STOP_ELEVATED_COUNT = 12
private const val STOP_HIGH_COUNT = 20
private const val LOCATION_STALE_LOW_MINUTES = 90
private const val LOCATION_STALE_ELEVATED_MINUTES = 180
private const val CHECKLIST_MULTI_ISSUE_COUNT = 2
private const val ELEVATED_LEVEL_SCORE_THRESHOLD = 3
private const val HIGH_LEVEL_SCORE_THRESHOLD = 7
private const val HIGH_FACTOR_OVERRIDE_WEIGHT = 5
private const val MAX_WORKLOAD_SCORE = 29

/**
 * v0 thresholds intentionally stay deterministic and conservative:
 * - long duty elevated at >= 10h and high at >= 12h
 * - driving time, trip count, and stop count only add incremental risk
 * - missing safety, clustered mobility insights, and explicit fatigue self-report are strong signals
 */
class CalculateDriverWorkloadUseCase {

    operator fun invoke(input: DriverWorkloadInput): DriverWorkloadScore {
        val factors = mutableListOf<DriverWorkloadFactor>()

        buildLongDutyFactor(input)?.let(factors::add)
        buildDrivingDurationFactor(input)?.let(factors::add)
        buildTripVolumeFactor(input)?.let(factors::add)
        buildStopVolumeFactor(input)?.let(factors::add)
        buildChecklistIssueFactor(input)?.let(factors::add)
        buildMissingSafetyFactor(input)?.let(factors::add)
        buildStaleLocationFactor(input)?.let(factors::add)
        buildMobilityInsightFactor(input)?.let(factors::add)
        buildFatigueSelfReportFactor(input)?.let(factors::add)

        val sortedFactors = factors.sortedWith(
            compareByDescending<DriverWorkloadFactor> { it.severity.priority }
                .thenByDescending { it.weight }
                .thenBy { it.code.name }
        )
        val score = sortedFactors.fold(0) { acc, factor -> acc + factor.weight }
        val level = resolveLevel(
            score = score,
            factors = sortedFactors,
            hasObservedSignals = input.hasObservedSignals()
        )

        return DriverWorkloadScore(
            level = level,
            score = score,
            maxScore = MAX_WORKLOAD_SCORE,
            factors = sortedFactors,
            recommendedActionKey = level.recommendedActionKey()
        )
    }

    private fun buildLongDutyFactor(input: DriverWorkloadInput): DriverWorkloadFactor? {
        val dutyMinutes = input.dutyMinutes.normalizedOrNull() ?: return null
        return when {
            dutyMinutes >= DUTY_HIGH_MINUTES -> DriverWorkloadFactor(
                code = DriverWorkloadFactorCode.LONG_DUTY,
                severity = DriverWorkloadFactorSeverity.HIGH,
                weight = 5,
                facts = listOf(
                    DriverWorkloadFact(DriverWorkloadFactCode.DUTY_MINUTES, dutyMinutes.toString()),
                    DriverWorkloadFact(DriverWorkloadFactCode.DUTY_THRESHOLD_MINUTES, DUTY_HIGH_MINUTES.toString())
                )
            )

            dutyMinutes >= DUTY_ELEVATED_MINUTES -> DriverWorkloadFactor(
                code = DriverWorkloadFactorCode.LONG_DUTY,
                severity = DriverWorkloadFactorSeverity.ELEVATED,
                weight = 3,
                facts = listOf(
                    DriverWorkloadFact(DriverWorkloadFactCode.DUTY_MINUTES, dutyMinutes.toString()),
                    DriverWorkloadFact(DriverWorkloadFactCode.DUTY_THRESHOLD_MINUTES, DUTY_ELEVATED_MINUTES.toString())
                )
            )

            else -> null
        }
    }

    private fun buildDrivingDurationFactor(input: DriverWorkloadInput): DriverWorkloadFactor? {
        val drivingMinutes = input.drivingMinutes.normalizedOrNull() ?: return null
        return when {
            drivingMinutes >= DRIVING_HIGH_MINUTES -> DriverWorkloadFactor(
                code = DriverWorkloadFactorCode.DRIVING_DURATION,
                severity = DriverWorkloadFactorSeverity.ELEVATED,
                weight = 2,
                facts = listOf(
                    DriverWorkloadFact(DriverWorkloadFactCode.DRIVING_MINUTES, drivingMinutes.toString()),
                    DriverWorkloadFact(DriverWorkloadFactCode.DRIVING_THRESHOLD_MINUTES, DRIVING_HIGH_MINUTES.toString())
                )
            )

            drivingMinutes >= DRIVING_ELEVATED_MINUTES -> DriverWorkloadFactor(
                code = DriverWorkloadFactorCode.DRIVING_DURATION,
                severity = DriverWorkloadFactorSeverity.LOW,
                weight = 1,
                facts = listOf(
                    DriverWorkloadFact(DriverWorkloadFactCode.DRIVING_MINUTES, drivingMinutes.toString()),
                    DriverWorkloadFact(DriverWorkloadFactCode.DRIVING_THRESHOLD_MINUTES, DRIVING_ELEVATED_MINUTES.toString())
                )
            )

            else -> null
        }
    }

    private fun buildTripVolumeFactor(input: DriverWorkloadInput): DriverWorkloadFactor? {
        val tripCount = input.tripCount.normalizedOrNull() ?: return null
        return when {
            tripCount >= TRIP_HIGH_COUNT -> DriverWorkloadFactor(
                code = DriverWorkloadFactorCode.TRIP_VOLUME,
                severity = DriverWorkloadFactorSeverity.ELEVATED,
                weight = 2,
                facts = listOf(
                    DriverWorkloadFact(DriverWorkloadFactCode.TRIP_COUNT, tripCount.toString()),
                    DriverWorkloadFact(DriverWorkloadFactCode.TRIP_THRESHOLD_COUNT, TRIP_HIGH_COUNT.toString())
                )
            )

            tripCount >= TRIP_ELEVATED_COUNT -> DriverWorkloadFactor(
                code = DriverWorkloadFactorCode.TRIP_VOLUME,
                severity = DriverWorkloadFactorSeverity.LOW,
                weight = 1,
                facts = listOf(
                    DriverWorkloadFact(DriverWorkloadFactCode.TRIP_COUNT, tripCount.toString()),
                    DriverWorkloadFact(DriverWorkloadFactCode.TRIP_THRESHOLD_COUNT, TRIP_ELEVATED_COUNT.toString())
                )
            )

            else -> null
        }
    }

    private fun buildStopVolumeFactor(input: DriverWorkloadInput): DriverWorkloadFactor? {
        val stopCount = input.stopCount.normalizedOrNull() ?: return null
        return when {
            stopCount >= STOP_HIGH_COUNT -> DriverWorkloadFactor(
                code = DriverWorkloadFactorCode.STOP_VOLUME,
                severity = DriverWorkloadFactorSeverity.ELEVATED,
                weight = 2,
                facts = listOf(
                    DriverWorkloadFact(DriverWorkloadFactCode.STOP_COUNT, stopCount.toString()),
                    DriverWorkloadFact(DriverWorkloadFactCode.STOP_THRESHOLD_COUNT, STOP_HIGH_COUNT.toString())
                )
            )

            stopCount >= STOP_ELEVATED_COUNT -> DriverWorkloadFactor(
                code = DriverWorkloadFactorCode.STOP_VOLUME,
                severity = DriverWorkloadFactorSeverity.LOW,
                weight = 1,
                facts = listOf(
                    DriverWorkloadFact(DriverWorkloadFactCode.STOP_COUNT, stopCount.toString()),
                    DriverWorkloadFact(DriverWorkloadFactCode.STOP_THRESHOLD_COUNT, STOP_ELEVATED_COUNT.toString())
                )
            )

            else -> null
        }
    }

    private fun buildChecklistIssueFactor(input: DriverWorkloadInput): DriverWorkloadFactor? {
        val checklistIssueCount = input.resolveChecklistIssueCount() ?: return null
        if (checklistIssueCount <= 0) return null

        val weight = if (checklistIssueCount >= CHECKLIST_MULTI_ISSUE_COUNT) 3 else 2
        return DriverWorkloadFactor(
            code = DriverWorkloadFactorCode.CHECKLIST_ISSUE,
            severity = DriverWorkloadFactorSeverity.ELEVATED,
            weight = weight,
            facts = listOf(
                DriverWorkloadFact(DriverWorkloadFactCode.CHECKLIST_ISSUE_COUNT, checklistIssueCount.toString())
            )
        )
    }

    private fun buildMissingSafetyFactor(input: DriverWorkloadInput): DriverWorkloadFactor? {
        if (input.hasMissingSafetyDeclaration != true) return null
        return DriverWorkloadFactor(
            code = DriverWorkloadFactorCode.MISSING_SAFETY_DECLARATION,
            severity = DriverWorkloadFactorSeverity.HIGH,
            weight = 3,
            facts = listOf(
                DriverWorkloadFact(DriverWorkloadFactCode.SAFETY_DECLARATION_STATUS, "missing")
            )
        )
    }

    private fun buildStaleLocationFactor(input: DriverWorkloadInput): DriverWorkloadFactor? {
        val staleLocationMinutes = input.staleLocationMinutes.normalizedOrNull() ?: return null
        return when {
            staleLocationMinutes >= LOCATION_STALE_ELEVATED_MINUTES -> DriverWorkloadFactor(
                code = DriverWorkloadFactorCode.STALE_LOCATION,
                severity = DriverWorkloadFactorSeverity.ELEVATED,
                weight = 2,
                facts = listOf(
                    DriverWorkloadFact(DriverWorkloadFactCode.STALE_LOCATION_MINUTES, staleLocationMinutes.toString()),
                    DriverWorkloadFact(
                        DriverWorkloadFactCode.STALE_LOCATION_THRESHOLD_MINUTES,
                        LOCATION_STALE_ELEVATED_MINUTES.toString()
                    )
                )
            )

            staleLocationMinutes >= LOCATION_STALE_LOW_MINUTES -> DriverWorkloadFactor(
                code = DriverWorkloadFactorCode.STALE_LOCATION,
                severity = DriverWorkloadFactorSeverity.LOW,
                weight = 1,
                facts = listOf(
                    DriverWorkloadFact(DriverWorkloadFactCode.STALE_LOCATION_MINUTES, staleLocationMinutes.toString()),
                    DriverWorkloadFact(
                        DriverWorkloadFactCode.STALE_LOCATION_THRESHOLD_MINUTES,
                        LOCATION_STALE_LOW_MINUTES.toString()
                    )
                )
            )

            else -> null
        }
    }

    private fun buildMobilityInsightFactor(input: DriverWorkloadInput): DriverWorkloadFactor? {
        val highCount = input.mobilityInsights.count { it.severity == MobilityInsightSeverity.HIGH }
        val elevatedCount = input.mobilityInsights.count { it.severity == MobilityInsightSeverity.ELEVATED }
        val severity: DriverWorkloadFactorSeverity
        val weight: Int

        when {
            highCount >= 2 -> {
                severity = DriverWorkloadFactorSeverity.HIGH
                weight = 5
            }

            highCount == 1 && elevatedCount >= 1 -> {
                severity = DriverWorkloadFactorSeverity.ELEVATED
                weight = 4
            }

            highCount == 1 -> {
                severity = DriverWorkloadFactorSeverity.ELEVATED
                weight = 3
            }

            elevatedCount >= 2 -> {
                severity = DriverWorkloadFactorSeverity.ELEVATED
                weight = 2
            }

            else -> return null
        }

        return DriverWorkloadFactor(
            code = DriverWorkloadFactorCode.MOBILITY_INSIGHT_CLUSTER,
            severity = severity,
            weight = weight,
            facts = listOf(
                DriverWorkloadFact(DriverWorkloadFactCode.HIGH_SEVERITY_INSIGHT_COUNT, highCount.toString()),
                DriverWorkloadFact(DriverWorkloadFactCode.ELEVATED_SEVERITY_INSIGHT_COUNT, elevatedCount.toString())
            )
        )
    }

    private fun buildFatigueSelfReportFactor(input: DriverWorkloadInput): DriverWorkloadFactor? {
        val fatigueSeverity = input.fatigueSelfReport ?: return null
        val weight = when (fatigueSeverity) {
            DriverWorkloadFactorSeverity.HIGH -> 5
            DriverWorkloadFactorSeverity.ELEVATED -> 3
            DriverWorkloadFactorSeverity.LOW -> 1
        }
        return DriverWorkloadFactor(
            code = DriverWorkloadFactorCode.FATIGUE_SELF_REPORT,
            severity = fatigueSeverity,
            weight = weight,
            facts = listOf(
                DriverWorkloadFact(
                    DriverWorkloadFactCode.FATIGUE_SELF_REPORT_SEVERITY,
                    fatigueSeverity.name.lowercase()
                )
            )
        )
    }
}

data class DriverWorkloadInput(
    val dutyMinutes: Int? = null,
    val drivingMinutes: Int? = null,
    val tripCount: Int? = null,
    val stopCount: Int? = null,
    val checklistIssueCount: Int? = null,
    val hasChecklistIssue: Boolean? = null,
    val hasMissingSafetyDeclaration: Boolean? = null,
    val staleLocationMinutes: Int? = null,
    val mobilityInsights: List<MobilityInsight> = emptyList(),
    val fatigueSelfReport: DriverWorkloadFactorSeverity? = null
)

private fun DriverWorkloadInput.resolveChecklistIssueCount(): Int? {
    val count = checklistIssueCount.normalizedOrNull()
    return when {
        count != null -> count
        hasChecklistIssue == true -> 1
        hasChecklistIssue == false -> 0
        else -> null
    }
}

private fun DriverWorkloadInput.hasObservedSignals(): Boolean {
    return dutyMinutes != null ||
        drivingMinutes != null ||
        tripCount != null ||
        stopCount != null ||
        checklistIssueCount != null ||
        hasChecklistIssue != null ||
        hasMissingSafetyDeclaration != null ||
        staleLocationMinutes != null ||
        mobilityInsights.isNotEmpty() ||
        fatigueSelfReport != null
}

private fun resolveLevel(
    score: Int,
    factors: List<DriverWorkloadFactor>,
    hasObservedSignals: Boolean
): DriverWorkloadLevel {
    return when {
        score == 0 && hasObservedSignals -> DriverWorkloadLevel.LOW
        score == 0 -> DriverWorkloadLevel.NORMAL
        factors.any {
            it.severity == DriverWorkloadFactorSeverity.HIGH && it.weight >= HIGH_FACTOR_OVERRIDE_WEIGHT
        } -> DriverWorkloadLevel.HIGH

        score >= HIGH_LEVEL_SCORE_THRESHOLD -> DriverWorkloadLevel.HIGH
        score >= ELEVATED_LEVEL_SCORE_THRESHOLD -> DriverWorkloadLevel.ELEVATED
        else -> DriverWorkloadLevel.NORMAL
    }
}

private fun DriverWorkloadLevel.recommendedActionKey(): String {
    return when (this) {
        DriverWorkloadLevel.LOW -> "driver_workload.action.none"
        DriverWorkloadLevel.NORMAL -> "driver_workload.action.monitor"
        DriverWorkloadLevel.ELEVATED -> "driver_workload.action.review_shift"
        DriverWorkloadLevel.HIGH -> "driver_workload.action.review_immediately"
    }
}

private fun Int?.normalizedOrNull(): Int? = this?.takeIf { it >= 0 }
