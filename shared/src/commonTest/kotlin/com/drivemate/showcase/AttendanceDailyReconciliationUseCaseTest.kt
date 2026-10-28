package com.drivemate.showcase

import com.drivemate.showcase.core.model.Attendance
import com.drivemate.showcase.core.model.TodayAttendance
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.domain.calendar.WorkCalendarYear
import com.drivemate.showcase.domain.calendar.WorkdayOverride
import com.drivemate.showcase.domain.calendar.WorkdayRule
import com.drivemate.showcase.domain.repository.AttendanceRepository
import com.drivemate.showcase.domain.repository.WorkCalendarRepository
import com.drivemate.showcase.domain.usecase.attendance.ReconcileAttendanceDailyUseCase
import com.drivemate.showcase.domain.usecase.attendance.buildDailyAttendanceSnapshot
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeReconcileAttendanceRepository(
    var attendances: List<Attendance> = emptyList(),
    initialDaily: Map<Long, TodayAttendance?> = emptyMap(),
) : AttendanceRepository {
    val dailyDocs = initialDaily.toMutableMap()
    var upsertClockInCalls: Int = 0
    var upsertClockOutCalls: Int = 0

    override suspend fun checkIn(att: Attendance): AppResult<Unit> =
        AppResult.failure(IllegalStateException("NotImplemented"))

    override suspend fun checkOut(
        timestampMillis: Long,
        locationName: String?,
        latitude: Double?,
        longitude: Double?
    ): AppResult<Unit> = AppResult.failure(IllegalStateException("NotImplemented"))

    override suspend fun upsertTodayClockIn(
        driverUid: String,
        epochDay: Long,
        nowMillis: Long
    ): AppResult<TodayAttendance> {
        val existing = dailyDocs[epochDay]
        if (existing?.clockInMillis != null) {
            return AppResult.failure(IllegalStateException("AlreadyClockedIn"))
        }
        val updated = TodayAttendance(
            driverUid = driverUid,
            epochDay = epochDay,
            clockInMillis = nowMillis,
            clockOutMillis = existing?.clockOutMillis,
            updatedAtMillis = nowMillis
        )
        dailyDocs[epochDay] = updated
        upsertClockInCalls += 1
        return AppResult.success(updated)
    }

    override suspend fun upsertTodayClockOut(
        driverUid: String,
        epochDay: Long,
        nowMillis: Long
    ): AppResult<TodayAttendance> {
        val existing = dailyDocs[epochDay] ?: return AppResult.failure(
            IllegalStateException("NotClockedIn")
        )
        if (existing.clockInMillis == null) {
            return AppResult.failure(IllegalStateException("NotClockedIn"))
        }
        if (existing.clockOutMillis != null) {
            return AppResult.failure(IllegalStateException("AlreadyClockedOut"))
        }
        val updated = existing.copy(
            clockOutMillis = nowMillis,
            updatedAtMillis = nowMillis
        )
        dailyDocs[epochDay] = updated
        upsertClockOutCalls += 1
        return AppResult.success(updated)
    }

    override suspend fun getTodayAttendance(
        driverUid: String,
        epochDay: Long
    ): AppResult<TodayAttendance?> = AppResult.success(dailyDocs[epochDay])

    override suspend fun getAttendancesByEpochDayRange(
        driverUid: String,
        startEpochDay: Long,
        endEpochDay: Long
    ): AppResult<List<Attendance>> = AppResult.success(attendances)

    override suspend fun getToday(driverUid: String): AppResult<Attendance?> =
        AppResult.failure(IllegalStateException("NotImplemented"))
}

private class FakeReconcileWorkCalendarRepository(
    private val calendarByYear: Map<Int, WorkCalendarYear>
) : WorkCalendarRepository {
    override suspend fun getYear(year: Int): AppResult<WorkCalendarYear> {
        return AppResult.success(calendarByYear[year] ?: WorkCalendarYear(year = year))
    }

    override suspend fun upsertYear(calendar: WorkCalendarYear): AppResult<Unit> =
        AppResult.success(Unit)

    override suspend fun getActiveYear(nowEpochDay: Int, zoneId: String): AppResult<Int> =
        AppResult.success(LocalDate.fromEpochDays(nowEpochDay).year)
}

class AttendanceDailyReconciliationUseCaseTest {

    @Test
    fun reconcileRepairsMissingDailyDocWhenAttendancesExist() {
        val zone = TimeZone.of("Asia/Jakarta")
        val day = 20_000L
        val inMillis = TimeUtil.parseHm("07:30", day, zone)
        val outMillis = TimeUtil.parseHm("16:30", day, zone)
        val repo = FakeReconcileAttendanceRepository(
            attendances = listOf(
                Attendance("driver-1", inMillis, null, null, note = "IN"),
                Attendance("driver-1", outMillis, null, null, note = "OUT")
            )
        )
        val calendarRepo = FakeReconcileWorkCalendarRepository(
            mapOf(LocalDate.fromEpochDays(day.toInt()).year to WorkCalendarYear(LocalDate.fromEpochDays(day.toInt()).year))
        )
        val useCase = ReconcileAttendanceDailyUseCase(repo, calendarRepo)

        val result = kotlinx.coroutines.runBlocking {
            useCase(
                driverUid = "driver-1",
                startEpochDay = day,
                endEpochDay = day,
                zone = zone
            )
        }

        val stats = (result as? AppResult.Success)?.data
        assertNotNull(stats)
        assertEquals(1, stats.scannedDays)
        assertEquals(1, stats.repairedDays)
        assertEquals(0, stats.noOpDays)
        assertEquals(0, stats.failedDays)
        assertEquals(1, repo.upsertClockInCalls)
        assertEquals(1, repo.upsertClockOutCalls)
        val daily = repo.dailyDocs[day]
        assertNotNull(daily)
        assertEquals(inMillis, daily.clockInMillis)
        assertEquals(outMillis, daily.clockOutMillis)
    }

    @Test
    fun reconcileNoOpsWhenDailyDocAlreadyCorrect() {
        val zone = TimeZone.of("Asia/Jakarta")
        val day = 20_010L
        val inMillis = TimeUtil.parseHm("07:30", day, zone)
        val outMillis = TimeUtil.parseHm("16:50", day, zone)
        val repo = FakeReconcileAttendanceRepository(
            attendances = listOf(
                Attendance("driver-1", inMillis, null, null, note = "IN"),
                Attendance("driver-1", outMillis, null, null, note = "OUT")
            ),
            initialDaily = mapOf(
                day to TodayAttendance(
                    driverUid = "driver-1",
                    epochDay = day,
                    clockInMillis = inMillis,
                    clockOutMillis = outMillis,
                    updatedAtMillis = outMillis
                )
            )
        )
        val calendarRepo = FakeReconcileWorkCalendarRepository(
            mapOf(LocalDate.fromEpochDays(day.toInt()).year to WorkCalendarYear(LocalDate.fromEpochDays(day.toInt()).year))
        )
        val useCase = ReconcileAttendanceDailyUseCase(repo, calendarRepo)

        val result = kotlinx.coroutines.runBlocking {
            useCase(
                driverUid = "driver-1",
                startEpochDay = day,
                endEpochDay = day,
                zone = zone
            )
        }

        val stats = (result as? AppResult.Success)?.data
        assertNotNull(stats)
        assertEquals(1, stats.scannedDays)
        assertEquals(0, stats.repairedDays)
        assertEquals(1, stats.noOpDays)
        assertEquals(0, stats.failedDays)
        assertEquals(0, repo.upsertClockInCalls)
        assertEquals(0, repo.upsertClockOutCalls)
    }

    @Test
    fun dailySnapshotHandlesHolidayOverrideAndKeepsWorkSpecNull() {
        val zone = TimeZone.of("Asia/Jakarta")
        val day = 20_020L
        val date = LocalDate.fromEpochDays(day.toInt())
        val inMillis = TimeUtil.parseHm("23:55", day, zone)
        val outMillis = TimeUtil.parseHm("23:59", day, zone)
        val calendar = WorkCalendarYear(
            year = date.year,
            overrides = listOf(
                WorkdayOverride(
                    date = date,
                    isWorkday = false,
                    ruleOverride = null
                )
            )
        )

        val snapshot = buildDailyAttendanceSnapshot(
            epochDay = day,
            events = listOf(
                Attendance("driver-1", inMillis, null, null, note = "IN"),
                Attendance("driver-1", outMillis, null, null, note = "OUT")
            ),
            calendar = calendar
        )

        assertNotNull(snapshot)
        assertNull(snapshot.workSpec)
        assertTrue(!snapshot.summary.isWorkday)
        assertEquals(inMillis, snapshot.clockInMillis)
        assertEquals(outMillis, snapshot.clockOutMillis)
    }

    @Test
    fun dailySnapshotTreatsWeekendSwapAsWorkdayUsingDefaultRule() {
        val zone = TimeZone.of("Asia/Jakarta")
        val day = LocalDate(2024, 1, 14).toEpochDays() // Sunday
        val date = LocalDate.fromEpochDays(day.toInt())
        val inMillis = TimeUtil.parseHm("07:30", day, zone)
        val outMillis = TimeUtil.parseHm("16:30", day, zone)
        val calendar = WorkCalendarYear(
            year = date.year,
            overrides = listOf(
                WorkdayOverride(
                    date = date,
                    isWorkday = true,
                    ruleOverride = null
                )
            )
        )

        val snapshot = buildDailyAttendanceSnapshot(
            epochDay = day,
            events = listOf(
                Attendance("driver-1", inMillis, null, null, note = "IN"),
                Attendance("driver-1", outMillis, null, null, note = "OUT")
            ),
            calendar = calendar
        )

        assertNotNull(snapshot)
        assertTrue(snapshot.summary.isWorkday)
        assertEquals(480, snapshot.summary.scheduledNetMinutes)
        assertEquals(0, snapshot.summary.overtimeMinutes)
    }

    @Test
    fun dailySnapshotAppliesCustomOverrideForScheduledAndOvertime() {
        val zone = TimeZone.of("Asia/Jakarta")
        val day = 20_021L
        val date = LocalDate.fromEpochDays(day.toInt())
        val inMillis = TimeUtil.parseHm("09:00", day, zone)
        val outMillis = TimeUtil.parseHm("17:00", day, zone)
        val customRule = WorkdayRule(
            startLocalTime = LocalTime(9, 0),
            endLocalTime = LocalTime(16, 0),
            breakMinutes = 30
        )
        val calendar = WorkCalendarYear(
            year = date.year,
            overrides = listOf(
                WorkdayOverride(
                    date = date,
                    isWorkday = true,
                    ruleOverride = customRule
                )
            )
        )

        val snapshot = buildDailyAttendanceSnapshot(
            epochDay = day,
            events = listOf(
                Attendance("driver-1", inMillis, null, null, note = "IN"),
                Attendance("driver-1", outMillis, null, null, note = "OUT")
            ),
            calendar = calendar
        )

        assertNotNull(snapshot)
        assertTrue(snapshot.summary.isWorkday)
        assertEquals(390, snapshot.summary.scheduledNetMinutes)
        assertEquals(60, snapshot.summary.overtimeMinutes)
    }

    @Test
    fun reconcileSeparatesEventsAcrossMidnightByTimestampDay() {
        val zone = TimeZone.of("Asia/Jakarta")
        val day = 20_030L
        val nextDay = day + 1L
        val inMillis = TimeUtil.parseHm("23:59", day, zone)
        val outMillisNextDay = TimeUtil.parseHm("00:10", nextDay, zone)
        val repo = FakeReconcileAttendanceRepository(
            attendances = listOf(
                Attendance("driver-1", inMillis, null, null, note = "IN"),
                Attendance("driver-1", outMillisNextDay, null, null, note = "OUT")
            )
        )
        val year = LocalDate.fromEpochDays(day.toInt()).year
        val calendarRepo = FakeReconcileWorkCalendarRepository(mapOf(year to WorkCalendarYear(year)))
        val useCase = ReconcileAttendanceDailyUseCase(repo, calendarRepo)

        val result = kotlinx.coroutines.runBlocking {
            useCase(
                driverUid = "driver-1",
                startEpochDay = day,
                endEpochDay = nextDay,
                zone = zone
            )
        }

        val stats = (result as? AppResult.Success)?.data
        assertNotNull(stats)
        assertEquals(2, stats.scannedDays)
        assertEquals(1, stats.repairedDays)
        assertEquals(1, stats.noOpDays)
        assertEquals(0, stats.failedDays)
        val dayDoc = repo.dailyDocs[day]
        assertNotNull(dayDoc)
        assertEquals(inMillis, dayDoc.clockInMillis)
        assertNull(dayDoc.clockOutMillis)
        assertEquals(1, repo.upsertClockInCalls)
        assertEquals(0, repo.upsertClockOutCalls)
    }
}
