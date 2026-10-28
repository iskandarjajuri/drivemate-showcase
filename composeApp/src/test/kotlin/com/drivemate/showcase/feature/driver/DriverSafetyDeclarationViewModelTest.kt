package com.drivemate.showcase.feature.driver

import com.drivemate.showcase.R
import com.drivemate.showcase.core.model.Role
import com.drivemate.showcase.core.model.SafetyDeclaration
import com.drivemate.showcase.core.model.User
import com.drivemate.showcase.core.model.UserSession
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.AuthRepository
import com.drivemate.showcase.domain.repository.SafetyRepository
import com.drivemate.showcase.domain.usecase.safetychecklist.GetSafetyDeclarationStatusUseCase
import com.drivemate.showcase.domain.usecase.safetychecklist.SubmitSafetyDeclarationUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

private class FakeSafetyRepository(
    var submitResult: AppResult<Unit> = AppResult.success(Unit),
    var hasDeclarationResult: AppResult<Boolean> = AppResult.success(false)
) : SafetyRepository {
    var lastSubmittedPayload: SafetyDeclaration? = null
        private set
    var submitCallCount = 0
        private set

    // A real backend flips hasSafetyDeclaration() to true right after a
    // successful write -- mirror that instead of a static canned value,
    // otherwise submit()'s trailing refreshStatus() call clobbers
    // statusToday back to whatever hasDeclarationResult was configured to
    // before the submit.
    private var submittedSuccessfully = false

    override suspend fun submitSafetyDeclaration(payload: SafetyDeclaration): AppResult<Unit> {
        submitCallCount++
        lastSubmittedPayload = payload
        if (submitResult is AppResult.Success) submittedSuccessfully = true
        return submitResult
    }

    override suspend fun hasSafetyDeclaration(driverUid: String, dateEpochDay: Int): AppResult<Boolean> =
        if (submittedSuccessfully) AppResult.success(true) else hasDeclarationResult
}

private class SafetyFakeAuthRepository(
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
 * Answers all 7 pledge statements Ya (true) plus the required hazard/weather
 * selections and the final confirmation -- the wizard's full happy path.
 */
private fun DriverSafetyDeclarationViewModel.fillAllPledgesTrue() {
    updateFitToWork(true)
    updateNoAlcoholOrDrugs(true)
    updateNoSevereStress(true)
    updateWillFollowSpeedLimit(true)
    updateWillFollowRestBreaks(true)
    updateWillNotUsePhone(true)
    updateWillWearSeatbelt(true)
    updateHazardAwareness("LOW")
    updateWeatherCondition("Clear")
    updateConfirmed(true)
}

@OptIn(ExperimentalCoroutinesApi::class)
class DriverSafetyDeclarationViewModelTest {

    @BeforeTest
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    private fun buildViewModel(
        safetyRepo: FakeSafetyRepository = FakeSafetyRepository(),
        authRepo: SafetyFakeAuthRepository = SafetyFakeAuthRepository()
    ) = DriverSafetyDeclarationViewModel(
        SubmitSafetyDeclarationUseCase(safetyRepo),
        GetSafetyDeclarationStatusUseCase(safetyRepo),
        authRepo
    )

    @Test
    fun submit_blocked_whenAnySinglePledgeUnanswered() {
        val safetyRepo = FakeSafetyRepository()
        val vm = buildViewModel(safetyRepo)
        // Every pledge answered except fitToWork -- there is no "unset"
        // setter, so this VM simply never calls updateFitToWork.
        vm.updateNoAlcoholOrDrugs(true)
        vm.updateNoSevereStress(true)
        vm.updateWillFollowSpeedLimit(true)
        vm.updateWillFollowRestBreaks(true)
        vm.updateWillNotUsePhone(true)
        vm.updateWillWearSeatbelt(true)
        vm.updateHazardAwareness("LOW")
        vm.updateWeatherCondition("Clear")
        vm.updateConfirmed(true)

        vm.submit()

        val state = vm.state.value
        assertEquals(R.string.driver_error_safety_required_fields, state.errorMessageRes)
        assertEquals(0, safetyRepo.submitCallCount)
    }

    @Test
    fun submit_blocked_whenHazardOrWeatherBlank() {
        val safetyRepo = FakeSafetyRepository()
        val vm = buildViewModel(safetyRepo)
        vm.fillAllPledgesTrue()
        vm.updateHazardAwareness("")

        vm.submit()

        assertEquals(R.string.driver_error_safety_required_fields, vm.state.value.errorMessageRes)
        assertEquals(0, safetyRepo.submitCallCount)
    }

    @Test
    fun submit_blocked_whenFinalConfirmationNotYes() {
        val safetyRepo = FakeSafetyRepository()
        val vm = buildViewModel(safetyRepo)
        vm.fillAllPledgesTrue()
        vm.updateConfirmed(false)

        vm.submit()

        assertEquals(R.string.driver_error_safety_confirm_required, vm.state.value.errorMessageRes)
        assertEquals(0, safetyRepo.submitCallCount)
    }

    @Test
    fun submit_succeeds_whenAllPledgesAnswered_evenWithSomeBelum() {
        // "Belum" on a non-final statement must NOT block submission -- it's
        // recorded, not rejected (matches the repository's existing
        // semantics: hasSafetyDeclaration is doc existence, not a value
        // check on any individual field).
        val safetyRepo = FakeSafetyRepository()
        val vm = buildViewModel(safetyRepo)
        vm.fillAllPledgesTrue()
        vm.updateNoSevereStress(false)
        vm.updateWillFollowRestBreaks(false)

        vm.submit()

        val state = vm.state.value
        assertNull(state.errorMessageRes)
        assertEquals(1, safetyRepo.submitCallCount)
        assertEquals(true, state.statusToday)
    }

    @Test
    fun submit_mapsBelumAnswersToFalse_notInverted() {
        // Direct regression guard for the Ya-always-maps-to-true discipline:
        // a statement answered "Belum" must serialize as false on its own
        // field, never silently flipped.
        val safetyRepo = FakeSafetyRepository()
        val vm = buildViewModel(safetyRepo)
        vm.fillAllPledgesTrue()
        vm.updateWillWearSeatbelt(false)

        vm.submit()

        val payload = safetyRepo.lastSubmittedPayload
        assertEquals(false, payload?.willWearSeatbelt)
        assertEquals(true, payload?.fitToWork)
        assertEquals(true, payload?.confirmed)
    }

    @Test
    fun submit_isNoOp_whenAlreadySubmittedTodayAccordingToClientState() {
        val safetyRepo = FakeSafetyRepository(hasDeclarationResult = AppResult.success(true))
        val vm = buildViewModel(safetyRepo)
        vm.fillAllPledgesTrue()

        vm.submit()

        assertEquals(R.string.driver_error_already_submitted_today, vm.state.value.errorMessageRes)
        assertEquals(0, safetyRepo.submitCallCount)
    }

    @Test
    fun initialLoad_setsStatusTodayFalse_whenNotYetSubmitted() {
        val safetyRepo = FakeSafetyRepository(hasDeclarationResult = AppResult.success(false))
        val vm = buildViewModel(safetyRepo)

        assertFalse(vm.state.value.statusLoading)
        assertEquals(false, vm.state.value.statusToday)
    }
}
