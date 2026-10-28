package com.drivemate.showcase

import com.drivemate.showcase.core.model.DriverWorkloadFactorCode
import com.drivemate.showcase.core.model.DriverWorkloadFactorSeverity
import com.drivemate.showcase.core.model.DriverWorkloadLevel
import com.drivemate.showcase.core.model.MobilityInsight
import com.drivemate.showcase.core.model.MobilityInsightSeverity
import com.drivemate.showcase.core.model.MobilityInsightType
import com.drivemate.showcase.domain.usecase.workload.CalculateDriverWorkloadUseCase
import com.drivemate.showcase.domain.usecase.workload.DriverWorkloadInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CalculateDriverWorkloadUseCaseTest {

    private val useCase = CalculateDriverWorkloadUseCase()

    @Test
    fun returnsLowWhenObservedSignalsAreHealthy() {
        val result = useCase(
            DriverWorkloadInput(
                dutyMinutes = 8 * 60,
                drivingMinutes = 5 * 60,
                tripCount = 4,
                stopCount = 6,
                hasChecklistIssue = false,
                hasMissingSafetyDeclaration = false,
                staleLocationMinutes = 10
            )
        )

        assertEquals(DriverWorkloadLevel.LOW, result.level)
        assertEquals(0, result.score)
        assertTrue(result.factors.isEmpty())
        assertEquals("driver_workload.action.none", result.recommendedActionKey)
    }

    @Test
    fun returnsNormalWhenSignalsAreUnknown() {
        val result = useCase(DriverWorkloadInput())

        assertEquals(DriverWorkloadLevel.NORMAL, result.level)
        assertEquals(0, result.score)
        assertTrue(result.factors.isEmpty())
        assertEquals("driver_workload.action.monitor", result.recommendedActionKey)
    }

    @Test
    fun longDutyAtTenHoursReturnsElevated() {
        val result = useCase(
            DriverWorkloadInput(
                dutyMinutes = 10 * 60
            )
        )

        assertEquals(DriverWorkloadLevel.ELEVATED, result.level)
        assertEquals(3, result.score)
        val factor = result.factors.firstOrNull()
        assertNotNull(factor)
        assertEquals(DriverWorkloadFactorCode.LONG_DUTY, factor.code)
        assertEquals(DriverWorkloadFactorSeverity.ELEVATED, factor.severity)
        assertEquals(3, factor.weight)
    }

    @Test
    fun veryLongDutyAtTwelveHoursReturnsHigh() {
        val result = useCase(
            DriverWorkloadInput(
                dutyMinutes = 12 * 60
            )
        )

        assertEquals(DriverWorkloadLevel.HIGH, result.level)
        assertEquals(5, result.score)
        val factor = result.factors.firstOrNull()
        assertNotNull(factor)
        assertEquals(DriverWorkloadFactorCode.LONG_DUTY, factor.code)
        assertEquals(DriverWorkloadFactorSeverity.HIGH, factor.severity)
        assertEquals("driver_workload.action.review_immediately", result.recommendedActionKey)
    }

    @Test
    fun checklistIssueIncreasesScore() {
        val result = useCase(
            DriverWorkloadInput(
                hasChecklistIssue = true
            )
        )

        assertEquals(DriverWorkloadLevel.NORMAL, result.level)
        assertEquals(2, result.score)
        assertTrue(result.factors.any { it.code == DriverWorkloadFactorCode.CHECKLIST_ISSUE })
    }

    @Test
    fun missingSafetyIncreasesScore() {
        val result = useCase(
            DriverWorkloadInput(
                hasMissingSafetyDeclaration = true
            )
        )

        assertEquals(DriverWorkloadLevel.ELEVATED, result.level)
        assertEquals(3, result.score)
        assertTrue(result.factors.any { it.code == DriverWorkloadFactorCode.MISSING_SAFETY_DECLARATION })
    }

    @Test
    fun staleLocationIncreasesScore() {
        val result = useCase(
            DriverWorkloadInput(
                staleLocationMinutes = 90
            )
        )

        assertEquals(DriverWorkloadLevel.NORMAL, result.level)
        assertEquals(1, result.score)
        val factor = result.factors.firstOrNull { it.code == DriverWorkloadFactorCode.STALE_LOCATION }
        assertNotNull(factor)
        assertEquals(DriverWorkloadFactorSeverity.LOW, factor.severity)
    }

    @Test
    fun multipleFactorsCombineToHigh() {
        val result = useCase(
            DriverWorkloadInput(
                dutyMinutes = 10 * 60,
                hasChecklistIssue = true,
                hasMissingSafetyDeclaration = true,
                staleLocationMinutes = 180
            )
        )

        assertEquals(DriverWorkloadLevel.HIGH, result.level)
        assertEquals(10, result.score)
        assertEquals(4, result.factors.size)
    }

    @Test
    fun multipleHighSeverityMobilityInsightsIncreaseRisk() {
        val result = useCase(
            DriverWorkloadInput(
                mobilityInsights = listOf(
                    sampleMobilityInsight("high-1", MobilityInsightSeverity.HIGH),
                    sampleMobilityInsight("high-2", MobilityInsightSeverity.HIGH)
                )
            )
        )

        assertEquals(DriverWorkloadLevel.HIGH, result.level)
        assertEquals(5, result.score)
        val factor = result.factors.firstOrNull { it.code == DriverWorkloadFactorCode.MOBILITY_INSIGHT_CLUSTER }
        assertNotNull(factor)
        assertEquals(DriverWorkloadFactorSeverity.HIGH, factor.severity)
    }

    @Test
    fun fatigueSelfReportAddsStrongRiskWhenProvided() {
        val result = useCase(
            DriverWorkloadInput(
                fatigueSelfReport = DriverWorkloadFactorSeverity.HIGH
            )
        )

        assertEquals(DriverWorkloadLevel.HIGH, result.level)
        assertEquals(5, result.score)
        assertTrue(result.factors.any { it.code == DriverWorkloadFactorCode.FATIGUE_SELF_REPORT })
    }
}

private fun sampleMobilityInsight(
    key: String,
    severity: MobilityInsightSeverity
): MobilityInsight {
    return MobilityInsight(
        key = key,
        type = MobilityInsightType.LONG_DUTY_DURATION,
        severity = severity,
        titleKey = "mobility_insight.long_duty_duration.title",
        messageKey = "mobility_insight.long_duty_duration.message",
        recommendedActionKey = "mobility_insight.long_duty_duration.action"
    )
}
