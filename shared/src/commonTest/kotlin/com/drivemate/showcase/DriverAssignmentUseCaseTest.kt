package com.drivemate.showcase

import com.drivemate.showcase.core.model.AssignmentReason
import com.drivemate.showcase.core.model.DriverAssignmentOverride
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.DriverAssignmentRepository
import com.drivemate.showcase.domain.usecase.assignment.GetDailyOverrideUseCase
import com.drivemate.showcase.domain.usecase.assignment.ResolveExpatForDriverUseCase
import com.drivemate.showcase.domain.usecase.assignment.SetPermanentManagerUseCase
import com.drivemate.showcase.domain.usecase.assignment.UpsertDailyOverrideUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class DriverAssignmentFakeRepository(
    var dailyOverride: DriverAssignmentOverride? = null,
    var dailyOverrideError: Throwable? = null,
    var permanentManager: String? = null,
    var permanentManagerError: Throwable? = null
) : DriverAssignmentRepository {
    var lastSetPermanentManagerDriverUid: String? = null
    var lastSetPermanentManagerExpatUid: String? = null
    var lastUpsertOverrideCall: DriverAssignmentOverride? = null
    var upsertCalled: Boolean = false

    override suspend fun setPermanentManager(driverUid: String, expatUid: String): AppResult<Unit> {
        lastSetPermanentManagerDriverUid = driverUid
        lastSetPermanentManagerExpatUid = expatUid
        return AppResult.success(Unit)
    }

    override suspend fun getPermanentManager(driverUid: String): AppResult<String?> {
        permanentManagerError?.let { return AppResult.failure(it) }
        return AppResult.success(permanentManager)
    }

    override suspend fun upsertDailyOverride(
        driverUid: String,
        expatUid: String,
        assignedBy: String,
        reason: AssignmentReason,
        epochDay: Int
    ): AppResult<Unit> {
        upsertCalled = true
        lastUpsertOverrideCall = DriverAssignmentOverride(
            driverUid = driverUid,
            expatUid = expatUid,
            assignedBy = assignedBy,
            reason = reason,
            epochDay = epochDay,
            createdAt = null
        )
        return AppResult.success(Unit)
    }

    override suspend fun getDailyOverride(driverUid: String, epochDay: Int): AppResult<DriverAssignmentOverride?> {
        dailyOverrideError?.let { return AppResult.failure(it) }
        return AppResult.success(dailyOverride)
    }

    override suspend fun getOverridesForDate(epochDay: Int): AppResult<List<DriverAssignmentOverride>> {
        return AppResult.success(listOfNotNull(dailyOverride))
    }
}

class DriverAssignmentUseCaseTest {

    @Test
    fun resolveExpatForDriver_returns_override_expat_when_override_exists() {
        val repo = DriverAssignmentFakeRepository(
            dailyOverride = DriverAssignmentOverride(
                driverUid = "driver1",
                expatUid = "expatSubstitute",
                assignedBy = "admin1",
                reason = AssignmentReason.SICK,
                epochDay = 20320,
                createdAt = 1L
            ),
            permanentManager = "expatPermanent"
        )
        val useCase = ResolveExpatForDriverUseCase(repo)

        val result = kotlinx.coroutines.runBlocking { useCase("driver1", 20320) }

        assertTrue(result is AppResult.Success)
        assertEquals("expatSubstitute", result.data)
    }

    @Test
    fun resolveExpatForDriver_falls_back_to_permanent_manager_when_no_override() {
        val repo = DriverAssignmentFakeRepository(
            dailyOverride = null,
            permanentManager = "expatPermanent"
        )
        val useCase = ResolveExpatForDriverUseCase(repo)

        val result = kotlinx.coroutines.runBlocking { useCase("driver1", 20320) }

        assertTrue(result is AppResult.Success)
        assertEquals("expatPermanent", result.data)
    }

    @Test
    fun resolveExpatForDriver_returns_null_when_neither_exists() {
        val repo = DriverAssignmentFakeRepository(
            dailyOverride = null,
            permanentManager = null
        )
        val useCase = ResolveExpatForDriverUseCase(repo)

        val result = kotlinx.coroutines.runBlocking { useCase("driver1", 20320) }

        assertTrue(result is AppResult.Success)
        assertNull(result.data)
    }

    @Test
    fun resolveExpatForDriver_propagates_override_lookup_failure() {
        val repo = DriverAssignmentFakeRepository(
            dailyOverrideError = IllegalStateException("network error")
        )
        val useCase = ResolveExpatForDriverUseCase(repo)

        val result = kotlinx.coroutines.runBlocking { useCase("driver1", 20320) }

        assertTrue(result is AppResult.Failure)
    }

    @Test
    fun upsertDailyOverride_rejects_driver_equal_to_expat() {
        val repo = DriverAssignmentFakeRepository()
        val useCase = UpsertDailyOverrideUseCase(repo)

        val result = kotlinx.coroutines.runBlocking {
            useCase("sameUid", "sameUid", "admin1", AssignmentReason.OTHER, 20320)
        }

        assertTrue(result is AppResult.Failure)
        assertFalse(repo.upsertCalled)
    }

    @Test
    fun upsertDailyOverride_delegates_to_repository_on_valid_input() {
        val repo = DriverAssignmentFakeRepository()
        val useCase = UpsertDailyOverrideUseCase(repo)

        val result = kotlinx.coroutines.runBlocking {
            useCase("driver1", "expat1", "admin1", AssignmentReason.LEAVE, 20320)
        }

        assertTrue(result is AppResult.Success)
        assertTrue(repo.upsertCalled)
        assertEquals("driver1", repo.lastUpsertOverrideCall?.driverUid)
        assertEquals("expat1", repo.lastUpsertOverrideCall?.expatUid)
        assertEquals(AssignmentReason.LEAVE, repo.lastUpsertOverrideCall?.reason)
        assertEquals(20320, repo.lastUpsertOverrideCall?.epochDay)
    }

    @Test
    fun setPermanentManager_delegates_to_repository() {
        val repo = DriverAssignmentFakeRepository()
        val useCase = SetPermanentManagerUseCase(repo)

        val result = kotlinx.coroutines.runBlocking { useCase("driver1", "expat1") }

        assertTrue(result is AppResult.Success)
        assertEquals("driver1", repo.lastSetPermanentManagerDriverUid)
        assertEquals("expat1", repo.lastSetPermanentManagerExpatUid)
    }

    @Test
    fun getDailyOverride_delegates_to_repository() {
        val override = DriverAssignmentOverride(
            driverUid = "driver1",
            expatUid = "expat1",
            assignedBy = "admin1",
            reason = AssignmentReason.OVERTIME_REST,
            epochDay = 20320,
            createdAt = 1L
        )
        val repo = DriverAssignmentFakeRepository(dailyOverride = override)
        val useCase = GetDailyOverrideUseCase(repo)

        val result = kotlinx.coroutines.runBlocking { useCase("driver1", 20320) }

        assertTrue(result is AppResult.Success)
        assertEquals(override, result.data)
    }
}
