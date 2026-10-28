package com.drivemate.showcase

import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.calendar.WorkCalendarYear
import com.drivemate.showcase.domain.error.CalendarError
import com.drivemate.showcase.domain.repository.WorkCalendarRepository
import com.drivemate.showcase.domain.usecase.calendar.GetWorkCalendarYearUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

private class FakeWorkCalendarRepository(
    private val getYearResult: AppResult<WorkCalendarYear>
) : WorkCalendarRepository {
    override suspend fun getYear(year: Int): AppResult<WorkCalendarYear> = getYearResult

    override suspend fun upsertYear(calendar: WorkCalendarYear): AppResult<Unit> =
        AppResult.success(Unit)

    override suspend fun getActiveYear(nowEpochDay: Int, zoneId: String): AppResult<Int> =
        AppResult.success(2026)
}

class GetWorkCalendarYearUseCaseTest {

    @Test
    fun returnsTypedNotFoundErrorFromRepository() = runBlocking {
        val repo = FakeWorkCalendarRepository(
            getYearResult = AppResult.failure(CalendarError.NotFound)
        )
        val useCase = GetWorkCalendarYearUseCase(repo)

        val result = useCase(2026)

        val failure = result as? AppResult.Failure
        assertTrue(failure != null)
        assertTrue(failure.throwable == CalendarError.NotFound)
    }

    @Test
    fun returnsCalendarWhenRepositorySucceeds() = runBlocking {
        val calendar = WorkCalendarYear(year = 2026)
        val repo = FakeWorkCalendarRepository(
            getYearResult = AppResult.success(calendar)
        )
        val useCase = GetWorkCalendarYearUseCase(repo)

        val result = useCase(2026)

        val success = result as? AppResult.Success
        assertTrue(success != null)
        assertEquals(calendar, success.data)
    }
}
