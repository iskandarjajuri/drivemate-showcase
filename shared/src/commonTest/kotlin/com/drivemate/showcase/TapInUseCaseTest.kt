package com.drivemate.showcase

import com.drivemate.showcase.core.model.Attendance
import com.drivemate.showcase.core.model.TodayAttendance
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.domain.repository.AttendanceRepository
import com.drivemate.showcase.domain.usecase.attendance.TapInUseCase
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class FakeTapInAttendanceRepository(
    var todayAttendance: TodayAttendance? = null,
    var getTodayAttendanceError: Throwable? = null,
    var checkInResult: AppResult<Unit> = AppResult.success(Unit),
    var upsertTodayClockInResult: AppResult<TodayAttendance> = AppResult.success(
        TodayAttendance(
            driverUid = "driver-1",
            epochDay = 0L,
            clockInMillis = 0L,
            clockOutMillis = null,
            updatedAtMillis = 0L
        )
    )
) : AttendanceRepository {
    var lastGetTodayAttendanceEpochDay: Long? = null
    var lastUpsertTodayClockInEpochDay: Long? = null

    override suspend fun checkIn(att: Attendance): AppResult<Unit> = checkInResult

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
        lastUpsertTodayClockInEpochDay = epochDay
        return upsertTodayClockInResult
    }

    override suspend fun upsertTodayClockOut(
        driverUid: String,
        epochDay: Long,
        nowMillis: Long
    ): AppResult<TodayAttendance> = AppResult.failure(IllegalStateException("NotImplemented"))

    override suspend fun getTodayAttendance(
        driverUid: String,
        epochDay: Long
    ): AppResult<TodayAttendance?> {
        lastGetTodayAttendanceEpochDay = epochDay
        getTodayAttendanceError?.let { return AppResult.failure(it) }
        return AppResult.success(todayAttendance)
    }

    override suspend fun getToday(driverUid: String): AppResult<Attendance?> =
        AppResult.failure(IllegalStateException("NotImplemented"))

    override suspend fun getAttendancesByEpochDayRange(
        driverUid: String,
        startEpochDay: Long,
        endEpochDay: Long
    ): AppResult<List<Attendance>> = AppResult.failure(IllegalStateException("NotImplemented"))
}

class TapInUseCaseTest {

    @Test
    fun tapInSucceedsWhenAttendancePersistedButDailyUpsertFails() {
        val timestampMillis = 1_700_000_000_000L
        val repo = FakeTapInAttendanceRepository(
            checkInResult = AppResult.success(Unit),
            upsertTodayClockInResult = AppResult.failure(IllegalStateException("upsert failed"))
        )
        val useCase = TapInUseCase(repo)

        val result = kotlinx.coroutines.runBlocking {
            useCase(
                Attendance(
                    driverUid = "driver-1",
                    timestampMillis = timestampMillis,
                    latitude = null,
                    longitude = null,
                    note = "IN"
                )
            )
        }

        val data = (result as? AppResult.Success)?.data
        assertNotNull(data)
        assertEquals("driver-1", data.driverUid)
        assertEquals(timestampMillis, data.clockInMillis)
        assertEquals(null, data.clockOutMillis)
    }

    @Test
    fun tapInDerivesEpochDayFromAttendanceTimestamp() {
        val zone = TimeZone.of("Asia/Jakarta")
        val timestampMillis = TimeUtil.parseHm("23:59", epochDay = 20_000L, zone = zone)
        val expectedEpochDay = TimeUtil.epochDayFromMillis(timestampMillis, zone)
        val repo = FakeTapInAttendanceRepository(
            upsertTodayClockInResult = AppResult.success(
                TodayAttendance(
                    driverUid = "driver-1",
                    epochDay = expectedEpochDay,
                    clockInMillis = timestampMillis,
                    clockOutMillis = null,
                    updatedAtMillis = timestampMillis
                )
            )
        )
        val useCase = TapInUseCase(repo)

        val result = kotlinx.coroutines.runBlocking {
            useCase(
                Attendance(
                    driverUid = "driver-1",
                    timestampMillis = timestampMillis,
                    latitude = null,
                    longitude = null,
                    note = "IN"
                )
            )
        }

        assertTrue(result is AppResult.Success)
        assertEquals(expectedEpochDay, repo.lastGetTodayAttendanceEpochDay)
        assertEquals(expectedEpochDay, repo.lastUpsertTodayClockInEpochDay)
    }

    @Test
    fun tapInFailureRemainsFailureWhenCheckInFails() {
        val expected = IllegalStateException("checkIn failed")
        val repo = FakeTapInAttendanceRepository(
            checkInResult = AppResult.failure(expected)
        )
        val useCase = TapInUseCase(repo)

        val result = kotlinx.coroutines.runBlocking {
            useCase(
                Attendance(
                    driverUid = "driver-1",
                    timestampMillis = 1_700_000_000_000L,
                    latitude = null,
                    longitude = null,
                    note = "IN"
                )
            )
        }

        val error = (result as? AppResult.Failure)?.throwable
        assertNotNull(error)
        assertEquals(expected, error)
    }
}
