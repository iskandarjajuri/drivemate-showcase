package com.drivemate.showcase.feature.settings

import com.drivemate.showcase.core.model.ChatContact
import com.drivemate.showcase.core.model.DriverProfile
import com.drivemate.showcase.core.model.Role
import com.drivemate.showcase.core.model.SopStatus
import com.drivemate.showcase.core.model.User
import com.drivemate.showcase.core.model.UserSession
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.AuthRepository
import com.drivemate.showcase.domain.repository.UserDirectoryRepository
import com.drivemate.showcase.domain.usecase.user.GetUserDisplayNameUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeDriverSettingsPreferences(
    initial: DriverSettingsValues = DriverSettingsValues()
) : DriverSettingsPreferences {
    private val _values = MutableStateFlow(initial)
    override val values: Flow<DriverSettingsValues> = _values

    override suspend fun setLanguageTag(tag: String?) {
        _values.value = _values.value.copy(languageTag = tag)
    }

    override suspend fun setChecklistReminderEnabled(enabled: Boolean) {
        _values.value = _values.value.copy(checklistReminderEnabled = enabled)
    }

    override suspend fun setChecklistReminderTime(hour: Int, minute: Int) {
        _values.value = _values.value.copy(checklistReminderHour = hour, checklistReminderMinute = minute)
    }

    override suspend fun setTapOutReminderEnabled(enabled: Boolean) {
        _values.value = _values.value.copy(tapOutReminderEnabled = enabled)
    }

    override suspend fun setAnnouncementsEnabled(enabled: Boolean) {
        _values.value = _values.value.copy(announcementsEnabled = enabled)
    }

    override suspend fun setNewMessagesEnabled(enabled: Boolean) {
        _values.value = _values.value.copy(newMessagesEnabled = enabled)
    }
}

private class FakeAuthRepository : AuthRepository {
    override val userSessionFlow: Flow<UserSession?> = emptyFlow()
    override suspend fun login(email: String, password: String): AppResult<Unit> =
        AppResult.failure(IllegalStateException("NotImplemented"))
    override suspend fun logout(): AppResult<Unit> = AppResult.failure(IllegalStateException("NotImplemented"))
    override suspend fun sendReset(email: String): AppResult<Unit> =
        AppResult.failure(IllegalStateException("NotImplemented"))
    override fun authState(): Flow<User?> = emptyFlow()
    override suspend fun currentUser(): AppResult<User?> =
        AppResult.success(User(uid = "driver-1", email = "driver1@example.com", role = Role.DRIVER))
}

private class FakeUserDirectoryRepository(
    var displayNameResult: AppResult<String?> = AppResult.success("Iskandar Jajuri")
) : UserDirectoryRepository {
    override suspend fun getUserDisplayName(uid: String): AppResult<String?> = displayNameResult
    override suspend fun getAllDriverDisplayNames(): AppResult<Map<String, String>> = AppResult.success(emptyMap())
    override suspend fun getDriverSopStatuses(): AppResult<List<SopStatus>> = AppResult.success(emptyList())
    override suspend fun getDriverSopStatus(uid: String): AppResult<SopStatus?> = AppResult.success(null)
    override suspend fun acceptSop(uid: String, acceptedAtMillis: Long): AppResult<Unit> = AppResult.success(Unit)
    override suspend fun getManagedDrivers(managerUid: String): AppResult<List<DriverProfile>> = AppResult.success(emptyList())
    override suspend fun getUsersByRoles(roles: Set<Role>): AppResult<List<ChatContact>> = AppResult.success(emptyList())
}

@OptIn(ExperimentalCoroutinesApi::class)
class DriverSettingsViewModelTest {

    @BeforeTest
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    private fun buildViewModel(prefs: DriverSettingsPreferences = FakeDriverSettingsPreferences()) =
        DriverSettingsViewModel(
            prefs = prefs,
            getUserDisplayName = GetUserDisplayNameUseCase(FakeUserDirectoryRepository()),
            authRepository = FakeAuthRepository()
        )

    @Test
    fun initialState_loadsDefaultsFromPreferences() {
        val vm = buildViewModel()

        val state = vm.state.value
        assertFalse(state.checklistReminderEnabled)
        assertEquals(7, state.checklistReminderHour)
        assertEquals(0, state.checklistReminderMinute)
        assertEquals(null, state.languageTag)
    }

    @Test
    fun initialState_loadsDriverNameAndRole() {
        val vm = buildViewModel()

        val state = vm.state.value
        assertEquals("Iskandar Jajuri", state.driverName)
        assertEquals(Role.DRIVER, state.driverRole)
    }

    @Test
    fun setLanguageTag_updatesStateThroughPreferences() {
        val prefs = FakeDriverSettingsPreferences()
        val vm = buildViewModel(prefs)

        vm.setLanguageTag("ja")

        assertEquals("ja", vm.state.value.languageTag)
    }

    @Test
    fun setChecklistReminderEnabled_updatesState() {
        val vm = buildViewModel()

        vm.setChecklistReminderEnabled(true)

        assertTrue(vm.state.value.checklistReminderEnabled)
    }

    @Test
    fun setChecklistReminderTime_updatesState() {
        val vm = buildViewModel()

        vm.setChecklistReminderTime(hour = 6, minute = 45)

        assertEquals(6, vm.state.value.checklistReminderHour)
        assertEquals(45, vm.state.value.checklistReminderMinute)
    }
}
