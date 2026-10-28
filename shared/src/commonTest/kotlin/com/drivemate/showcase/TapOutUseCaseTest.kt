package com.drivemate.showcase

import com.drivemate.showcase.core.model.Attendance
import com.drivemate.showcase.core.model.Checklist
import com.drivemate.showcase.core.model.SafetyDeclaration
import com.drivemate.showcase.core.model.TodayAttendance
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.domain.error.AttendanceError
import com.drivemate.showcase.domain.repository.AttendanceRepository
import com.drivemate.showcase.domain.repository.ChecklistRepository
import com.drivemate.showcase.domain.repository.SafetyRepository
import com.drivemate.showcase.domain.usecase.attendance.TapOutUseCase
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class FakeAttendanceRepository(
    var todayAttendance: TodayAttendance? = null,
    var todayAttendanceError: Throwable? = null,
    var latestAttendance: Attendance? = null,
    var latestAttendanceError: Throwable? = null,
    var checkOutResult: AppResult<Unit> = AppResult.success(Unit),
    var upsertTodayClockOutResult: AppResult<TodayAttendance> = AppResult.failure(
        IllegalStateException("NotClockedIn")
    )
) : AttendanceRepository {
    var lastCheckOutTimestampMillis: Long? = null
    var lastUpsertTodayClockOutEpochDay: Long? = null
    var lastUpsertTodayClockOutNowMillis: Long? = null
    var lastGetTodayAttendanceEpochDay: Long? = null

    override suspend fun checkIn(att: Attendance): AppResult<Unit> {
        return AppResult.failure(IllegalStateException("NotImplemented"))
    }

    override suspend fun checkOut(
        timestampMillis: Long,
        locationName: String?,
        latitude: Double?,
        longitude: Double?
    ): AppResult<Unit> {
        lastCheckOutTimestampMillis = timestampMillis
        return checkOutResult
    }

    override suspend fun upsertTodayClockIn(
        driverUid: String,
        epochDay: Long,
        nowMillis: Long
    ): AppResult<TodayAttendance> {
        return AppResult.failure(IllegalStateException("NotImplemented"))
    }

    override suspend fun upsertTodayClockOut(
        driverUid: String,
        epochDay: Long,
        nowMillis: Long
    ): AppResult<TodayAttendance> {
        lastUpsertTodayClockOutEpochDay = epochDay
        lastUpsertTodayClockOutNowMillis = nowMillis
        return upsertTodayClockOutResult
    }

    override suspend fun getTodayAttendance(
        driverUid: String,
        epochDay: Long
    ): AppResult<TodayAttendance?> {
        lastGetTodayAttendanceEpochDay = epochDay
        todayAttendanceError?.let { return AppResult.failure(it) }
        return AppResult.success(todayAttendance)
    }

    override suspend fun getToday(driverUid: String): AppResult<Attendance?> {
        latestAttendanceError?.let { return AppResult.failure(it) }
        return AppResult.success(latestAttendance)
    }

    override suspend fun getAttendancesByEpochDayRange(
        driverUid: String,
        startEpochDay: Long,
        endEpochDay: Long
    ): AppResult<List<Attendance>> = AppResult.failure(IllegalStateException("NotImplemented"))
}

/** Defaults to "complete" so tests unrelated to the checklist gate don't need to opt in. */
private class TapOutFakeChecklistRepository(
    var hasChecklistResult: AppResult<Boolean> = AppResult.success(true)
) : ChecklistRepository {
    var lastHasChecklistEpochDay: Int? = null

    override suspend fun submitChecklist(checklist: Checklist): AppResult<Unit> =
        AppResult.failure(IllegalStateException("NotImplemented"))

    override suspend fun hasChecklist(driverUid: String, dateEpochDay: Int): AppResult<Boolean> {
        lastHasChecklistEpochDay = dateEpochDay
        return hasChecklistResult
    }
}

/** Defaults to "complete" so tests unrelated to the checklist gate don't need to opt in. */
private class TapOutFakeSafetyRepository(
    var hasSafetyDeclarationResult: AppResult<Boolean> = AppResult.success(true)
) : SafetyRepository {
    var lastHasSafetyDeclarationEpochDay: Int? = null

    override suspend fun submitSafetyDeclaration(payload: SafetyDeclaration): AppResult<Unit> =
        AppResult.failure(IllegalStateException("NotImplemented"))

    override suspend fun hasSafetyDeclaration(driverUid: String, dateEpochDay: Int): AppResult<Boolean> {
        lastHasSafetyDeclarationEpochDay = dateEpochDay
        return hasSafetyDeclarationResult
    }
}

class TapOutUseCaseTest {
    @Test
    fun tapOutUsesSameMillisForOutWriteAndDailyUpsertBeforeMidnight() {
        val zone = TimeZone.of("Asia/Jakarta")
        val outMillis = TimeUtil.parseHm("23:59", epochDay = 20_000L, zone = zone)
        val expectedEpochDay = TimeUtil.epochDayFromMillis(outMillis, zone)
        val repo = FakeAttendanceRepository(
            todayAttendance = TodayAttendance(
                driverUid = "driver-1",
                epochDay = expectedEpochDay,
                clockInMillis = outMillis - 60_000L,
                clockOutMillis = null,
                updatedAtMillis = outMillis - 60_000L
            ),
            checkOutResult = AppResult.success(Unit),
            upsertTodayClockOutResult = AppResult.success(
                TodayAttendance(
                    driverUid = "driver-1",
                    epochDay = expectedEpochDay,
                    clockInMillis = outMillis - 60_000L,
                    clockOutMillis = outMillis,
                    updatedAtMillis = outMillis
                )
            )
        )
        val useCase = TapOutUseCase(repo, TapOutFakeChecklistRepository(), TapOutFakeSafetyRepository()) { outMillis }

        val result = kotlinx.coroutines.runBlocking {
            useCase(driverUid = "driver-1")
        }

        assertTrue(result is AppResult.Success)
        assertEquals(outMillis, repo.lastCheckOutTimestampMillis)
        assertEquals(expectedEpochDay, repo.lastGetTodayAttendanceEpochDay)
        assertEquals(expectedEpochDay, repo.lastUpsertTodayClockOutEpochDay)
        assertEquals(outMillis, repo.lastUpsertTodayClockOutNowMillis)
    }

    @Test
    fun tapOutUsesSameMillisForOutWriteAndDailyUpsertAfterMidnight() {
        val zone = TimeZone.of("Asia/Jakarta")
        val outMillis = TimeUtil.parseHm("00:01", epochDay = 20_001L, zone = zone)
        val expectedEpochDay = TimeUtil.epochDayFromMillis(outMillis, zone)
        val repo = FakeAttendanceRepository(
            todayAttendance = TodayAttendance(
                driverUid = "driver-1",
                epochDay = expectedEpochDay,
                clockInMillis = outMillis - 60_000L,
                clockOutMillis = null,
                updatedAtMillis = outMillis - 60_000L
            ),
            checkOutResult = AppResult.success(Unit),
            upsertTodayClockOutResult = AppResult.success(
                TodayAttendance(
                    driverUid = "driver-1",
                    epochDay = expectedEpochDay,
                    clockInMillis = outMillis - 60_000L,
                    clockOutMillis = outMillis,
                    updatedAtMillis = outMillis
                )
            )
        )
        val useCase = TapOutUseCase(repo, TapOutFakeChecklistRepository(), TapOutFakeSafetyRepository()) { outMillis }

        val result = kotlinx.coroutines.runBlocking {
            useCase(driverUid = "driver-1")
        }

        assertTrue(result is AppResult.Success)
        assertEquals(outMillis, repo.lastCheckOutTimestampMillis)
        assertEquals(expectedEpochDay, repo.lastGetTodayAttendanceEpochDay)
        assertEquals(expectedEpochDay, repo.lastUpsertTodayClockOutEpochDay)
        assertEquals(outMillis, repo.lastUpsertTodayClockOutNowMillis)
    }

    @Test
    fun tapOutSucceedsWhenAttendancePersistedButDailyUpsertFails() {
        val repo = FakeAttendanceRepository(
            todayAttendance = TodayAttendance(
                driverUid = "driver-1",
                epochDay = 20_000L,
                clockInMillis = 123456789L,
                clockOutMillis = null,
                updatedAtMillis = 123456789L
            ),
            checkOutResult = AppResult.success(Unit),
            upsertTodayClockOutResult = AppResult.failure(IllegalStateException("upsert failed"))
        )
        val useCase = TapOutUseCase(repo, TapOutFakeChecklistRepository(), TapOutFakeSafetyRepository())

        val result = kotlinx.coroutines.runBlocking {
            useCase(driverUid = "driver-1")
        }

        val data = (result as? AppResult.Success)?.data
        assertNotNull(data)
        assertEquals(123456789L, data.clockInMillis)
        assertNotNull(data.clockOutMillis)
    }

    @Test
    fun tapOutSucceedsWhenDailyMissingButInExists() {
        val repo = FakeAttendanceRepository(
            todayAttendance = null,
            latestAttendance = Attendance(
                driverUid = "driver-1",
                timestampMillis = 123456789L,
                latitude = null,
                longitude = null,
                note = null
            ),
            checkOutResult = AppResult.success(Unit),
            upsertTodayClockOutResult = AppResult.failure(IllegalStateException("NotClockedIn"))
        )
        val useCase = TapOutUseCase(repo, TapOutFakeChecklistRepository(), TapOutFakeSafetyRepository())

        val result = kotlinx.coroutines.runBlocking {
            useCase(driverUid = "driver-1")
        }

        val data = (result as? AppResult.Success)?.data
        assertNotNull(data)
        assertEquals(123456789L, data.clockInMillis)
        assertNotNull(data.clockOutMillis)
    }

    @Test
    fun tapOutFailsWhenDailyMissingAndNoInExists() {
        val repo = FakeAttendanceRepository(
            todayAttendance = null,
            latestAttendance = null,
            checkOutResult = AppResult.success(Unit)
        )
        val useCase = TapOutUseCase(repo, TapOutFakeChecklistRepository(), TapOutFakeSafetyRepository())

        val result = kotlinx.coroutines.runBlocking {
            useCase(driverUid = "driver-1")
        }

        val error = (result as? AppResult.Failure)?.throwable
        assertNotNull(error)
        assertTrue(error is AttendanceError.NotClockedIn)
    }

    @Test
    fun tapOutBlockedWhenChecklistNotSubmitted() {
        val repo = FakeAttendanceRepository(
            todayAttendance = TodayAttendance(
                driverUid = "driver-1",
                epochDay = 20_000L,
                clockInMillis = 123456789L,
                clockOutMillis = null,
                updatedAtMillis = 123456789L
            )
        )
        val checklistRepo = TapOutFakeChecklistRepository(hasChecklistResult = AppResult.success(false))
        val safetyRepo = TapOutFakeSafetyRepository(hasSafetyDeclarationResult = AppResult.success(true))
        val useCase = TapOutUseCase(repo, checklistRepo, safetyRepo)

        val result = kotlinx.coroutines.runBlocking {
            useCase(driverUid = "driver-1")
        }

        val error = (result as? AppResult.Failure)?.throwable
        assertNotNull(error)
        assertTrue(error is AttendanceError.ChecklistIncomplete)
        // The Firestore write itself must never be attempted once the gate fails.
        assertEquals(null, repo.lastCheckOutTimestampMillis)
    }

    @Test
    fun tapOutBlockedWhenSafetyDeclarationNotSubmitted() {
        val repo = FakeAttendanceRepository(
            todayAttendance = TodayAttendance(
                driverUid = "driver-1",
                epochDay = 20_000L,
                clockInMillis = 123456789L,
                clockOutMillis = null,
                updatedAtMillis = 123456789L
            )
        )
        val checklistRepo = TapOutFakeChecklistRepository(hasChecklistResult = AppResult.success(true))
        val safetyRepo = TapOutFakeSafetyRepository(hasSafetyDeclarationResult = AppResult.success(false))
        val useCase = TapOutUseCase(repo, checklistRepo, safetyRepo)

        val result = kotlinx.coroutines.runBlocking {
            useCase(driverUid = "driver-1")
        }

        val error = (result as? AppResult.Failure)?.throwable
        assertNotNull(error)
        assertTrue(error is AttendanceError.ChecklistIncomplete)
        assertEquals(null, repo.lastCheckOutTimestampMillis)
    }

    @Test
    fun tapOutBlockedOnFallbackPathTooWhenChecklistNotSubmitted() {
        // STEP 1 finding: tapOutFallbackWhenDailyMissing is a second route to a
        // successful checkout, reached when today's daily record is missing but
        // a raw "IN" attendance event exists. The gate sits before the branch
        // that chooses between the two paths, so it must block this route too.
        val repo = FakeAttendanceRepository(
            todayAttendance = null,
            latestAttendance = Attendance(
                driverUid = "driver-1",
                timestampMillis = 123456789L,
                latitude = null,
                longitude = null,
                note = null
            )
        )
        val checklistRepo = TapOutFakeChecklistRepository(hasChecklistResult = AppResult.success(false))
        val safetyRepo = TapOutFakeSafetyRepository(hasSafetyDeclarationResult = AppResult.success(true))
        val useCase = TapOutUseCase(repo, checklistRepo, safetyRepo)

        val result = kotlinx.coroutines.runBlocking {
            useCase(driverUid = "driver-1")
        }

        val error = (result as? AppResult.Failure)?.throwable
        assertNotNull(error)
        assertTrue(error is AttendanceError.ChecklistIncomplete)
        assertEquals(null, repo.lastCheckOutTimestampMillis)
    }

    @Test
    fun tapOutProceedsWhenChecklistAndSafetyBothComplete() {
        val repo = FakeAttendanceRepository(
            todayAttendance = TodayAttendance(
                driverUid = "driver-1",
                epochDay = 20_000L,
                clockInMillis = 123456789L,
                clockOutMillis = null,
                updatedAtMillis = 123456789L
            ),
            checkOutResult = AppResult.success(Unit),
            upsertTodayClockOutResult = AppResult.success(
                TodayAttendance(
                    driverUid = "driver-1",
                    epochDay = 20_000L,
                    clockInMillis = 123456789L,
                    clockOutMillis = 987654321L,
                    updatedAtMillis = 987654321L
                )
            )
        )
        val checklistRepo = TapOutFakeChecklistRepository(hasChecklistResult = AppResult.success(true))
        val safetyRepo = TapOutFakeSafetyRepository(hasSafetyDeclarationResult = AppResult.success(true))
        val useCase = TapOutUseCase(repo, checklistRepo, safetyRepo) { 987654321L }

        val result = kotlinx.coroutines.runBlocking {
            useCase(driverUid = "driver-1")
        }

        assertTrue(result is AppResult.Success)
        assertNotNull(repo.lastCheckOutTimestampMillis)
        // Queried the SAME day the checkout itself is for, not an arbitrary one.
        assertEquals(
            TimeUtil.epochDayFromMillis(987654321L, TimeZone.of("Asia/Jakarta")).toInt(),
            checklistRepo.lastHasChecklistEpochDay
        )
        assertEquals(
            TimeUtil.epochDayFromMillis(987654321L, TimeZone.of("Asia/Jakarta")).toInt(),
            safetyRepo.lastHasSafetyDeclarationEpochDay
        )
    }

    @Test
    fun tapOutPropagatesChecklistRepoFailureRatherThanMislabelingAsIncomplete() {
        // A repo error (e.g. a network hiccup reading checklist status) is not
        // the same fact as "checklist incomplete" and must not be reported as
        // one -- that would tell the driver something false.
        val repo = FakeAttendanceRepository(
            todayAttendance = TodayAttendance(
                driverUid = "driver-1",
                epochDay = 20_000L,
                clockInMillis = 123456789L,
                clockOutMillis = null,
                updatedAtMillis = 123456789L
            )
        )
        val boom = IllegalStateException("network hiccup")
        val checklistRepo = TapOutFakeChecklistRepository(hasChecklistResult = AppResult.failure(boom))
        val safetyRepo = TapOutFakeSafetyRepository(hasSafetyDeclarationResult = AppResult.success(true))
        val useCase = TapOutUseCase(repo, checklistRepo, safetyRepo)

        val result = kotlinx.coroutines.runBlocking {
            useCase(driverUid = "driver-1")
        }

        val error = (result as? AppResult.Failure)?.throwable
        assertEquals(boom, error)
        assertFalse(error is AttendanceError.ChecklistIncomplete)
    }
}
