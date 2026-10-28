package com.drivemate.showcase

import com.drivemate.showcase.core.model.BulkActionResult
import com.drivemate.showcase.core.model.BulkActionType
import com.drivemate.showcase.core.model.ExplicitWorkdayPlanWindow
import com.drivemate.showcase.core.model.PlanSource
import com.drivemate.showcase.core.model.WorkDay
import com.drivemate.showcase.core.model.WorkdaySummary
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.WorkDayRepository
import com.drivemate.showcase.domain.usecase.workday.GetDriverTodayScheduleUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class ScheduleFakeWorkDayRepository(
    var workDaysForDay: List<WorkDay> = emptyList(),
    var explicitPlan: ExplicitWorkdayPlanWindow? = null,
    var explicitPlanError: Throwable? = null
) : WorkDayRepository {
    var getExplicitPlanCalled: Boolean = false

    override suspend fun getWorkDaysByRange(driverUid: String, startEpochDay: Int, endEpochDay: Int): List<WorkDay> =
        workDaysForDay

    override suspend fun getExplicitPlan(driverUid: String, epochDay: Int): AppResult<ExplicitWorkdayPlanWindow?> {
        getExplicitPlanCalled = true
        explicitPlanError?.let { return AppResult.failure(it) }
        return AppResult.success(explicitPlan)
    }

    override suspend fun getWorkdaySummary(driverUid: String, startEpochDay: Int, endEpochDay: Int): WorkdaySummary =
        WorkdaySummary(driverUid, 0, 0, 0, 0, null)

    override suspend fun getWorkDayById(workDayId: String): AppResult<WorkDay?> = AppResult.success(null)
    override suspend fun approve(workDayId: String, note: String?): AppResult<Unit> = AppResult.success(Unit)
    override suspend fun bulkApprove(workDayIds: List<String>): BulkActionResult =
        BulkActionResult(BulkActionType.APPROVE, 0, 0, 0, 0, emptyList())

    override suspend fun reject(workDayId: String, note: String): AppResult<Unit> = AppResult.success(Unit)
    override suspend fun bulkReject(workDayIds: List<String>, note: String): BulkActionResult =
        BulkActionResult(BulkActionType.REJECT, 0, 0, 0, 0, emptyList())

    override suspend fun adminOverrideDecision(
        workDayId: String,
        targetStatus: String,
        rejectReason: String?,
        overrideReason: String
    ): AppResult<Unit> = AppResult.success(Unit)

    override suspend fun generateWorkdayFromAttendances(driverUid: String, dateEpochDay: Int): AppResult<Unit> =
        AppResult.success(Unit)

    override suspend fun upsertWorkdayPlan(
        driverUid: String,
        epochDay: Int,
        plannedStartMillis: Long,
        plannedEndMillis: Long,
        source: String
    ): AppResult<Unit> = AppResult.success(Unit)
}

class GetDriverTodayScheduleUseCaseTest {

    @Test
    fun returns_existing_workday_schedule_unchanged_when_workdays_doc_exists() {
        val existing = WorkDay(
            id = "driver1_20320",
            driverUid = "driver1",
            dateEpochDay = 20320,
            clockInMillis = 1_000L,
            clockOutMillis = 2_000L,
            totalMinutes = 480,
            plannedStartMillis = 111L,
            plannedEndMillis = 222L,
            planSource = PlanSource.DEFAULT
        )
        val repo = ScheduleFakeWorkDayRepository(workDaysForDay = listOf(existing))
        val useCase = GetDriverTodayScheduleUseCase(repo)

        val result = kotlinx.coroutines.runBlocking { useCase("driver1", 20320) }

        assertTrue(result is AppResult.Success)
        assertEquals(111L, result.data.plannedStartMillis)
        assertEquals(222L, result.data.plannedEndMillis)
        assertEquals(PlanSource.DEFAULT, result.data.planSource)
        // Existing-document behaviour must not consult workday_plans at all.
        kotlin.test.assertFalse(repo.getExplicitPlanCalled)
    }

    @Test
    fun falls_back_to_explicit_plan_when_no_workdays_doc_exists() {
        val repo = ScheduleFakeWorkDayRepository(
            workDaysForDay = emptyList(),
            explicitPlan = ExplicitWorkdayPlanWindow(plannedStartMillis = 333L, plannedEndMillis = 444L)
        )
        val useCase = GetDriverTodayScheduleUseCase(repo)

        val result = kotlinx.coroutines.runBlocking { useCase("driver1", 20320) }

        assertTrue(result is AppResult.Success)
        assertEquals(333L, result.data.plannedStartMillis)
        assertEquals(444L, result.data.plannedEndMillis)
        assertEquals(PlanSource.EXPLICIT, result.data.planSource)
        assertTrue(repo.getExplicitPlanCalled)
    }

    @Test
    fun falls_back_to_empty_when_neither_workdays_doc_nor_explicit_plan_exists() {
        val repo = ScheduleFakeWorkDayRepository(workDaysForDay = emptyList(), explicitPlan = null)
        val useCase = GetDriverTodayScheduleUseCase(repo)

        val result = kotlinx.coroutines.runBlocking { useCase("driver1", 20320) }

        assertTrue(result is AppResult.Success)
        assertNull(result.data.plannedStartMillis)
        assertNull(result.data.plannedEndMillis)
        assertNull(result.data.planSource)
    }

    @Test
    fun degrades_to_empty_when_explicit_plan_lookup_fails() {
        val repo = ScheduleFakeWorkDayRepository(
            workDaysForDay = emptyList(),
            explicitPlanError = IllegalStateException("network error")
        )
        val useCase = GetDriverTodayScheduleUseCase(repo)

        val result = kotlinx.coroutines.runBlocking { useCase("driver1", 20320) }

        assertTrue(result is AppResult.Success)
        assertNull(result.data.plannedStartMillis)
        assertNull(result.data.planSource)
    }
}
