package com.drivemate.showcase

import com.drivemate.showcase.core.model.Checklist
import com.drivemate.showcase.core.util.AlreadySubmittedException
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.ChecklistRepository
import com.drivemate.showcase.domain.usecase.safetychecklist.GetChecklistStatusUseCase
import com.drivemate.showcase.domain.usecase.safetychecklist.SubmitChecklistUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class FakeChecklistRepository(
    var submitResult: AppResult<Unit> = AppResult.success(Unit),
    var hasChecklistResult: AppResult<Boolean> = AppResult.success(false)
) : ChecklistRepository {
    var lastSubmittedChecklist: Checklist? = null
    var lastHasChecklistDriverUid: String? = null
    var lastHasChecklistEpochDay: Int? = null

    override suspend fun submitChecklist(checklist: Checklist): AppResult<Unit> {
        lastSubmittedChecklist = checklist
        return submitResult
    }

    override suspend fun hasChecklist(driverUid: String, dateEpochDay: Int): AppResult<Boolean> {
        lastHasChecklistDriverUid = driverUid
        lastHasChecklistEpochDay = dateEpochDay
        return hasChecklistResult
    }
}

private fun sampleChecklist(hasIssue: Boolean = false) = Checklist(
    vehicleId = "B 1234 XYZ",
    odometerKm = 12_345,
    fuelLevel = "FULL",
    tiresFrontLeftOk = true,
    tiresFrontRightOk = true,
    tiresRearLeftOk = true,
    tiresRearRightOk = true,
    brakesOk = true,
    lightsOk = true,
    turnSignalBrakeLightsOk = true,
    hornOk = true,
    wipersOk = true,
    mirrorsOk = true,
    seatbeltOk = true,
    engineOilOk = true,
    coolantOk = true,
    batteryOk = true,
    washerFluidOk = true,
    cleanlinessOk = true,
    documentsOk = true,
    fireExtinguisherOk = true,
    firstAidOk = true,
    warningTriangleOk = true,
    notes = null,
    hasIssue = hasIssue
)

class SubmitChecklistUseCaseTest {

    @Test
    fun submitSucceedsAndForwardsChecklistToRepository() {
        val repo = FakeChecklistRepository(submitResult = AppResult.success(Unit))
        val useCase = SubmitChecklistUseCase(repo)
        val checklist = sampleChecklist()

        val result = kotlinx.coroutines.runBlocking { useCase(checklist) }

        assertTrue(result is AppResult.Success)
        assertEquals(checklist, repo.lastSubmittedChecklist)
    }

    @Test
    fun submitPropagatesAlreadySubmittedException() {
        val expected = AlreadySubmittedException("Sudah dikirim hari ini")
        val repo = FakeChecklistRepository(submitResult = AppResult.failure(expected))
        val useCase = SubmitChecklistUseCase(repo)

        val result = kotlinx.coroutines.runBlocking { useCase(sampleChecklist()) }

        val error = (result as? AppResult.Failure)?.throwable
        assertIs<AlreadySubmittedException>(error)
        assertEquals(expected.message, error.message)
    }

    @Test
    fun submitPropagatesGenericFailureUnchanged() {
        val expected = IllegalStateException("network unavailable")
        val repo = FakeChecklistRepository(submitResult = AppResult.failure(expected))
        val useCase = SubmitChecklistUseCase(repo)

        val result = kotlinx.coroutines.runBlocking { useCase(sampleChecklist()) }

        val error = (result as? AppResult.Failure)?.throwable
        assertEquals(expected, error)
    }
}

class GetChecklistStatusUseCaseTest {

    @Test
    fun hasChecklistReturnsTrueWhenAlreadySubmittedToday() {
        val repo = FakeChecklistRepository(hasChecklistResult = AppResult.success(true))
        val useCase = GetChecklistStatusUseCase(repo)

        val result = kotlinx.coroutines.runBlocking { useCase("driver-1", 20_000) }

        assertEquals(AppResult.success(true), result)
        assertEquals("driver-1", repo.lastHasChecklistDriverUid)
        assertEquals(20_000, repo.lastHasChecklistEpochDay)
    }

    @Test
    fun hasChecklistReturnsFalseWhenNotSubmittedYet() {
        val repo = FakeChecklistRepository(hasChecklistResult = AppResult.success(false))
        val useCase = GetChecklistStatusUseCase(repo)

        val result = kotlinx.coroutines.runBlocking { useCase("driver-1", 20_000) }

        assertEquals(AppResult.success(false), result)
    }

    @Test
    fun hasChecklistPropagatesFailure() {
        val expected = IllegalStateException("PERMISSION_DENIED")
        val repo = FakeChecklistRepository(hasChecklistResult = AppResult.failure(expected))
        val useCase = GetChecklistStatusUseCase(repo)

        val result = kotlinx.coroutines.runBlocking { useCase("driver-1", 20_000) }

        val error = (result as? AppResult.Failure)?.throwable
        assertNotNull(error)
        assertEquals(expected, error)
    }
}
