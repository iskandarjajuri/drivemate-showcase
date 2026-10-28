package com.drivemate.showcase

import com.drivemate.showcase.core.model.Checklist
import com.drivemate.showcase.core.model.DeviationFlag
import com.drivemate.showcase.core.model.Location
import com.drivemate.showcase.core.model.MobilityInsightReasonCode
import com.drivemate.showcase.core.model.MobilityInsightSeverity
import com.drivemate.showcase.core.model.MobilityInsightType
import com.drivemate.showcase.core.model.SafetyDeclaration
import com.drivemate.showcase.core.model.TodayAttendance
import com.drivemate.showcase.core.model.WorkDay
import com.drivemate.showcase.domain.usecase.mobility.GenerateMobilityInsightsUseCase
import com.drivemate.showcase.domain.usecase.mobility.MobilityInsightInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GenerateMobilityInsightsUseCaseTest {

    private val useCase = GenerateMobilityInsightsUseCase()

    @Test
    fun returnsEmptyWhenSignalsAreHealthy() {
        val workDay = sampleClosedWorkDay()

        val insights = useCase(
            MobilityInsightInput(
                workDay = workDay,
                checklist = Checklist(hasIssue = false),
                safetyDeclaration = sampleSafetyDeclaration(),
                latestLocation = Location(
                    driverUid = workDay.driverUid,
                    latitude = -6.2,
                    longitude = 106.8,
                    updatedAtMillis = workDay.clockOutMillis
                ),
                nowMillis = workDay.clockOutMillis
            )
        )

        assertTrue(insights.isEmpty())
    }

    @Test
    fun doesNotInferMissingSafetyWhenSignalIsUnknown() {
        val insights = useCase(
            MobilityInsightInput(
                todayAttendance = TodayAttendance(
                    driverUid = "driver-1",
                    epochDay = 20_300L,
                    clockInMillis = 1_700_000_000_000L,
                    clockOutMillis = null,
                    updatedAtMillis = 1_700_000_000_000L
                ),
                nowMillis = 1_700_001_800_000L
            )
        )

        assertTrue(insights.none { it.type == MobilityInsightType.SAFETY_DECLARATION_MISSING })
    }

    @Test
    fun returnsAttendanceIncompleteInsightForOpenDuty() {
        val insights = useCase(
            MobilityInsightInput(
                todayAttendance = TodayAttendance(
                    driverUid = "driver-1",
                    epochDay = 20_300L,
                    clockInMillis = 1_700_000_000_000L,
                    clockOutMillis = null,
                    updatedAtMillis = 1_700_000_000_000L
                ),
                nowMillis = 1_700_001_800_000L
            )
        )

        assertEquals(1, insights.size)
        val insight = insights.first()
        assertEquals(MobilityInsightType.ATTENDANCE_INCOMPLETE, insight.type)
        assertEquals(MobilityInsightSeverity.HIGH, insight.severity)
        assertEquals("mobility_insight.attendance_incomplete.title", insight.titleKey)
        assertTrue(
            insight.reasons.any {
                it.code == MobilityInsightReasonCode.ATTENDANCE_STATE && it.value == "open"
            }
        )
    }

    @Test
    fun returnsHighInsightWhenSafetyDeclarationIsMissing() {
        val workDay = sampleClosedWorkDay(hasSafetyDeclaration = false)

        val insights = useCase(
            MobilityInsightInput(
                workDay = workDay,
                checklist = Checklist(hasIssue = false),
                nowMillis = workDay.clockOutMillis
            )
        )

        assertEquals(1, insights.size)
        val insight = insights.first()
        assertEquals(MobilityInsightType.SAFETY_DECLARATION_MISSING, insight.type)
        assertEquals(MobilityInsightSeverity.HIGH, insight.severity)
        assertEquals(workDay.id, insight.workdayId)
        assertTrue(
            insight.reasons.any {
                it.code == MobilityInsightReasonCode.SAFETY_DECLARATION_STATUS && it.value == "missing"
            }
        )
    }

    @Test
    fun returnsChecklistIssueInsightWhenChecklistHasIssue() {
        val workDay = sampleClosedWorkDay()

        val insights = useCase(
            MobilityInsightInput(
                workDay = workDay,
                checklist = Checklist(hasIssue = true),
                safetyDeclaration = sampleSafetyDeclaration(),
                nowMillis = workDay.clockOutMillis
            )
        )

        assertEquals(1, insights.size)
        val insight = insights.first()
        assertEquals(MobilityInsightType.CHECKLIST_ISSUE_DETECTED, insight.type)
        assertEquals(MobilityInsightSeverity.ELEVATED, insight.severity)
        assertTrue(
            insight.reasons.any {
                it.code == MobilityInsightReasonCode.CHECKLIST_STATUS && it.value == "issue_detected"
            }
        )
    }

    @Test
    fun returnsElevatedLongDutyInsightWhenThresholdIsExceeded() {
        val workDay = sampleClosedWorkDay(totalMinutes = 10 * 60)

        val insights = useCase(
            MobilityInsightInput(
                workDay = workDay,
                checklist = Checklist(hasIssue = false),
                safetyDeclaration = sampleSafetyDeclaration(),
                nowMillis = workDay.clockOutMillis
            )
        )

        assertEquals(1, insights.size)
        val insight = insights.first()
        assertEquals(MobilityInsightType.LONG_DUTY_DURATION, insight.type)
        assertEquals(MobilityInsightSeverity.ELEVATED, insight.severity)
        assertReasonValue(insight, MobilityInsightReasonCode.DUTY_MINUTES, "600")
        assertReasonValue(insight, MobilityInsightReasonCode.DUTY_THRESHOLD_MINUTES, "600")
    }

    @Test
    fun returnsHighLongDutyInsightAtOrAboveTwelveHours() {
        val workDay = sampleClosedWorkDay(totalMinutes = 12 * 60)

        val insights = useCase(
            MobilityInsightInput(
                workDay = workDay,
                checklist = Checklist(hasIssue = false),
                safetyDeclaration = sampleSafetyDeclaration(),
                nowMillis = workDay.clockOutMillis
            )
        )

        assertEquals(1, insights.size)
        val insight = insights.first()
        assertEquals(MobilityInsightType.LONG_DUTY_DURATION, insight.type)
        assertEquals(MobilityInsightSeverity.HIGH, insight.severity)
        assertReasonValue(insight, MobilityInsightReasonCode.DUTY_THRESHOLD_MINUTES, "720")
    }

    @Test
    fun returnsLowLocationStaleInsightAtOrAboveNinetyMinutes() {
        val insights = useCase(
            MobilityInsightInput(
                todayAttendance = TodayAttendance(
                    driverUid = "driver-1",
                    epochDay = 20_300L,
                    clockInMillis = 1_700_000_000_000L,
                    clockOutMillis = null,
                    updatedAtMillis = 1_700_000_000_000L
                ),
                latestLocation = Location(
                    driverUid = "driver-1",
                    latitude = -6.2,
                    longitude = 106.8,
                    updatedAtMillis = 1_700_000_000_000L
                ),
                nowMillis = 1_700_005_400_000L
            )
        )

        assertEquals(2, insights.size)
        val locationInsight = insights.firstOrNull { it.type == MobilityInsightType.LOCATION_STALE }
        assertNotNull(locationInsight)
        assertEquals(MobilityInsightSeverity.LOW, locationInsight.severity)
        assertReasonValue(locationInsight, MobilityInsightReasonCode.LOCATION_STALE_MINUTES, "90")
    }

    @Test
    fun returnsAdminReviewNeededWhenReviewHoldOrDeviationExists() {
        val workDay = sampleClosedWorkDay(
            reviewHold = true,
            deviationFlags = listOf(DeviationFlag.EXCESSIVE_OT)
        )

        val insights = useCase(
            MobilityInsightInput(
                workDay = workDay,
                checklist = Checklist(hasIssue = false),
                safetyDeclaration = sampleSafetyDeclaration(),
                nowMillis = workDay.clockOutMillis
            )
        )

        assertEquals(1, insights.size)
        val insight = insights.first()
        assertEquals(MobilityInsightType.ADMIN_REVIEW_NEEDED, insight.type)
        assertEquals(MobilityInsightSeverity.ELEVATED, insight.severity)
        assertReasonValue(insight, MobilityInsightReasonCode.REVIEW_HOLD_STATUS, "active")
        assertReasonValue(insight, MobilityInsightReasonCode.DEVIATION_FLAG, "excessive_ot")
    }
}

private fun sampleClosedWorkDay(
    totalMinutes: Int = 8 * 60,
    hasSafetyDeclaration: Boolean? = true,
    reviewHold: Boolean = false,
    deviationFlags: List<DeviationFlag> = emptyList()
): WorkDay {
    return WorkDay(
        id = "workday-driver-1-20300",
        driverUid = "driver-1",
        dateEpochDay = 20_300,
        clockInMillis = 1_700_000_000_000L,
        clockOutMillis = 1_700_028_800_000L,
        totalMinutes = totalMinutes,
        overtimeMinutes = 0,
        hasChecklist = true,
        hasSafetyDeclaration = hasSafetyDeclaration,
        reviewHold = reviewHold,
        deviationFlags = deviationFlags
    )
}

private fun sampleSafetyDeclaration(): SafetyDeclaration {
    return SafetyDeclaration(
        fitToWork = true,
        sleepHours = 7,
        noAlcoholOrDrugs = true,
        noSevereStress = true,
        willFollowSpeedLimit = true,
        willFollowRestBreaks = true,
        willNotUsePhoneWhileDriving = true,
        willWearSeatbelt = true,
        confirmed = true
    )
}

private fun assertReasonValue(
    insight: com.drivemate.showcase.core.model.MobilityInsight,
    code: MobilityInsightReasonCode,
    expectedValue: String
) {
    assertTrue(
        insight.reasons.any { it.code == code && it.value == expectedValue },
        "Expected reason $code=$expectedValue but got ${insight.reasons}"
    )
}
