package com.drivemate.showcase.feature.driver

import com.drivemate.showcase.R
import com.drivemate.showcase.core.model.Checklist
import com.drivemate.showcase.core.model.Role
import com.drivemate.showcase.core.model.User
import com.drivemate.showcase.core.model.UserSession
import com.drivemate.showcase.core.util.AlreadySubmittedException
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.AuthRepository
import com.drivemate.showcase.domain.repository.ChecklistRepository
import com.drivemate.showcase.domain.usecase.safetychecklist.GetChecklistStatusUseCase
import com.drivemate.showcase.domain.usecase.safetychecklist.SubmitChecklistUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeChecklistRepository(
    var submitResult: AppResult<Unit> = AppResult.success(Unit),
    var hasChecklistResult: AppResult<Boolean> = AppResult.success(false)
) : ChecklistRepository {
    var submitCallCount = 0
        private set

    // A real backend flips hasChecklist() to true right after a successful
    // write — mirror that instead of a static canned value, otherwise
    // submit()'s trailing refreshStatus() call clobbers statusToday back to
    // whatever hasChecklistResult was configured to before the submit.
    private var submittedSuccessfully = false

    override suspend fun submitChecklist(checklist: Checklist): AppResult<Unit> {
        submitCallCount++
        if (submitResult is AppResult.Success) submittedSuccessfully = true
        return submitResult
    }

    override suspend fun hasChecklist(driverUid: String, dateEpochDay: Int): AppResult<Boolean> =
        if (submittedSuccessfully) AppResult.success(true) else hasChecklistResult
}

private class FakeAuthRepository(
    var currentUserResult: AppResult<User?> = AppResult.success(
        User(uid = "driver-1", email = "driver1@example.com", role = Role.DRIVER)
    )
) : AuthRepository {
    override val userSessionFlow: Flow<UserSession?> = emptyFlow()

    override suspend fun login(email: String, password: String): AppResult<Unit> =
        AppResult.failure(IllegalStateException("NotImplemented"))

    override suspend fun logout(): AppResult<Unit> =
        AppResult.failure(IllegalStateException("NotImplemented"))

    override suspend fun sendReset(email: String): AppResult<Unit> =
        AppResult.failure(IllegalStateException("NotImplemented"))

    override fun authState(): Flow<User?> = emptyFlow()

    override suspend fun currentUser(): AppResult<User?> = currentUserResult
}

/**
 * Fills a fully valid form: fuel level + every one of the 20 inspection items
 * answered (DriverChecklistViewModel.submit() now requires all of them, not
 * just one -- this is a real safety checklist, not an optional form).
 */
private fun DriverChecklistViewModel.fillAllItemsValidForm() {
    updateFuelLevel("FULL")
    updateTiresFrontLeftOk(true)
    updateTiresFrontRightOk(true)
    updateTiresRearLeftOk(true)
    updateTiresRearRightOk(true)
    updateBrakesOk(true)
    updateLightsOk(true)
    updateTurnSignalBrakeLightsOk(true)
    updateHornOk(true)
    updateWipersOk(true)
    updateMirrorsOk(true)
    updateSeatbeltOk(true)
    updateEngineOilOk(true)
    updateCoolantOk(true)
    updateBatteryOk(true)
    updateWasherFluidOk(true)
    updateCleanlinessOk(true)
    updateDocumentsOk(true)
    updateFireExtinguisherOk(true)
    updateFirstAidOk(true)
    updateWarningTriangleOk(true)
}

@OptIn(ExperimentalCoroutinesApi::class)
class DriverChecklistViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun initialLoad_setsStatusTodayTrue_whenAlreadySubmitted() {
        val checklistRepo = FakeChecklistRepository(hasChecklistResult = AppResult.success(true))
        val vm = DriverChecklistViewModel(
            SubmitChecklistUseCase(checklistRepo),
            GetChecklistStatusUseCase(checklistRepo),
            FakeAuthRepository()
        )

        val state = vm.state.value
        assertFalse(state.statusLoading)
        assertEquals(true, state.statusToday)
        assertNull(state.errorMessageRes)
    }

    @Test
    fun initialLoad_setsStatusTodayFalse_whenNotYetSubmitted() {
        val checklistRepo = FakeChecklistRepository(hasChecklistResult = AppResult.success(false))
        val vm = DriverChecklistViewModel(
            SubmitChecklistUseCase(checklistRepo),
            GetChecklistStatusUseCase(checklistRepo),
            FakeAuthRepository()
        )

        assertEquals(false, vm.state.value.statusToday)
    }

    @Test
    fun initialLoad_setsErrorMessage_whenStatusCheckFails() {
        val checklistRepo = FakeChecklistRepository(
            hasChecklistResult = AppResult.failure(IllegalStateException("PERMISSION_DENIED"))
        )
        val vm = DriverChecklistViewModel(
            SubmitChecklistUseCase(checklistRepo),
            GetChecklistStatusUseCase(checklistRepo),
            FakeAuthRepository()
        )

        val state = vm.state.value
        assertFalse(state.statusLoading)
        assertNull(state.statusToday)
        assertEquals(R.string.driver_error_checklist_status_failed, state.errorMessageRes)
    }

    @Test
    fun submit_succeeds_setsJustSubmittedAndLocksForm() {
        val checklistRepo = FakeChecklistRepository(
            hasChecklistResult = AppResult.success(false),
            submitResult = AppResult.success(Unit)
        )
        val vm = DriverChecklistViewModel(
            SubmitChecklistUseCase(checklistRepo),
            GetChecklistStatusUseCase(checklistRepo),
            FakeAuthRepository()
        )
        vm.fillAllItemsValidForm()

        vm.submit()

        val state = vm.state.value
        assertFalse(state.submitting)
        assertEquals(true, state.justSubmitted)
        assertEquals(true, state.statusToday)
        assertFalse(state.showValidationErrors)
        assertEquals(1, checklistRepo.submitCallCount)
    }

    @Test
    fun submit_setsAlreadySubmittedError_whenRepositoryRejectsAsDuplicate() {
        // Client-side status check says "not submitted yet" (e.g. stale read),
        // but the repository itself rejects the write as a duplicate — the
        // typed-exception path, not a string-matched one.
        val checklistRepo = FakeChecklistRepository(
            hasChecklistResult = AppResult.success(false),
            submitResult = AppResult.failure(AlreadySubmittedException("Sudah dikirim hari ini"))
        )
        val vm = DriverChecklistViewModel(
            SubmitChecklistUseCase(checklistRepo),
            GetChecklistStatusUseCase(checklistRepo),
            FakeAuthRepository()
        )
        vm.fillAllItemsValidForm()

        vm.submit()

        val state = vm.state.value
        assertFalse(state.submitting)
        assertEquals(R.string.driver_error_already_submitted_today, state.errorMessageRes)
        assertEquals(true, state.statusToday)
    }

    @Test
    fun submit_setsGenericError_whenRepositoryFailsWithOtherThrowable() {
        val checklistRepo = FakeChecklistRepository(
            hasChecklistResult = AppResult.success(false),
            submitResult = AppResult.failure(IllegalStateException("network unavailable"))
        )
        val vm = DriverChecklistViewModel(
            SubmitChecklistUseCase(checklistRepo),
            GetChecklistStatusUseCase(checklistRepo),
            FakeAuthRepository()
        )
        vm.fillAllItemsValidForm()

        vm.submit()

        val state = vm.state.value
        assertFalse(state.submitting)
        assertEquals(R.string.driver_error_checklist_submit_failed, state.errorMessageRes)
        // Only AlreadySubmittedException flips statusToday — a generic failure must not.
        assertEquals(false, state.statusToday)
    }

    @Test
    fun submit_showsValidationError_andNeverCallsRepository_whenFuelLevelBlank() {
        val checklistRepo = FakeChecklistRepository(hasChecklistResult = AppResult.success(false))
        val vm = DriverChecklistViewModel(
            SubmitChecklistUseCase(checklistRepo),
            GetChecklistStatusUseCase(checklistRepo),
            FakeAuthRepository()
        )
        // fuelLevel left blank on purpose.
        vm.updateBrakesOk(true)

        vm.submit()

        val state = vm.state.value
        assertEquals(R.string.driver_error_checklist_fuel_required, state.errorMessageRes)
        assertEquals(0, checklistRepo.submitCallCount)
    }

    @Test
    fun submit_showsValidationError_andNeverCallsRepository_whenAnySingleItemUnanswered() {
        val checklistRepo = FakeChecklistRepository(hasChecklistResult = AppResult.success(false))
        val vm = DriverChecklistViewModel(
            SubmitChecklistUseCase(checklistRepo),
            GetChecklistStatusUseCase(checklistRepo),
            FakeAuthRepository()
        )
        vm.fillAllItemsValidForm()
        // Leave exactly one of the 20 items unanswered -- this must still block
        // submission, matching the task's "every item required" safety intent
        // rather than the old "at least one item" minimum.
        vm.updateWarningTriangleOk(null)

        vm.submit()

        val state = vm.state.value
        assertEquals(R.string.driver_error_checklist_all_items_required, state.errorMessageRes)
        assertEquals(0, checklistRepo.submitCallCount)
    }

    @Test
    fun submit_succeeds_emitsSubmittedEventOnce() {
        val checklistRepo = FakeChecklistRepository(
            hasChecklistResult = AppResult.success(false),
            submitResult = AppResult.success(Unit)
        )
        val vm = DriverChecklistViewModel(
            SubmitChecklistUseCase(checklistRepo),
            GetChecklistStatusUseCase(checklistRepo),
            FakeAuthRepository()
        )
        vm.fillAllItemsValidForm()

        vm.submit()
        // The event is sent after a short delay (lets the button show its
        // confirmed state before Home takes over) -- flush that virtual time.
        testDispatcher.scheduler.advanceUntilIdle()

        val event = runBlocking {
            withTimeout(1_000) { vm.events.first() }
        }
        assertTrue(event is DriverChecklistEvent.Submitted, "expected Submitted event, got $event")
    }

    @Test
    fun submit_fails_emitsNoSubmittedEvent() {
        val checklistRepo = FakeChecklistRepository(
            hasChecklistResult = AppResult.success(false),
            submitResult = AppResult.failure(IllegalStateException("network unavailable"))
        )
        val vm = DriverChecklistViewModel(
            SubmitChecklistUseCase(checklistRepo),
            GetChecklistStatusUseCase(checklistRepo),
            FakeAuthRepository()
        )
        vm.fillAllItemsValidForm()

        vm.submit()
        testDispatcher.scheduler.advanceUntilIdle()

        val event = runBlocking {
            try {
                withTimeout(200) { vm.events.first() }
            } catch (e: TimeoutCancellationException) {
                null
            }
        }
        assertNull(event)
    }

    @Test
    fun submit_isNoOp_whenAlreadySubmittedTodayAccordingToClientState() {
        val checklistRepo = FakeChecklistRepository(hasChecklistResult = AppResult.success(true))
        val vm = DriverChecklistViewModel(
            SubmitChecklistUseCase(checklistRepo),
            GetChecklistStatusUseCase(checklistRepo),
            FakeAuthRepository()
        )
        vm.fillAllItemsValidForm()

        vm.submit()

        // The client-side guard (statusToday already true) must short-circuit
        // before ever calling the repository's submitChecklist.
        assertEquals(R.string.driver_error_already_submitted_today, vm.state.value.errorMessageRes)
        assertEquals(0, checklistRepo.submitCallCount)
    }
}
