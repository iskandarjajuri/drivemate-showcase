package com.drivemate.showcase.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivemate.showcase.core.model.Role
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.AuthRepository
import com.drivemate.showcase.domain.usecase.user.GetUserDisplayNameUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DriverSettingsUiState(
    val driverName: String? = null,
    val driverRole: Role? = null,
    val driverEmail: String? = null,
    val languageTag: String? = null,
    val checklistReminderEnabled: Boolean = false,
    val checklistReminderHour: Int = 7,
    val checklistReminderMinute: Int = 0,
    val tapOutReminderEnabled: Boolean = false,
    val announcementsEnabled: Boolean = true,
    val newMessagesEnabled: Boolean = true
)

class DriverSettingsViewModel(
    private val prefs: DriverSettingsPreferences,
    private val getUserDisplayName: GetUserDisplayNameUseCase,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DriverSettingsUiState())
    val state: StateFlow<DriverSettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.values.collect { values ->
                _state.value = _state.value.copy(
                    languageTag = values.languageTag,
                    checklistReminderEnabled = values.checklistReminderEnabled,
                    checklistReminderHour = values.checklistReminderHour,
                    checklistReminderMinute = values.checklistReminderMinute,
                    tapOutReminderEnabled = values.tapOutReminderEnabled,
                    announcementsEnabled = values.announcementsEnabled,
                    newMessagesEnabled = values.newMessagesEnabled
                )
            }
        }
        loadProfile()
    }

    private fun loadProfile() = viewModelScope.launch {
        val user = when (val res = authRepository.currentUser()) {
            is AppResult.Success -> res.data
            is AppResult.Failure -> null
        } ?: return@launch
        val name = when (val res = getUserDisplayName(user.uid)) {
            is AppResult.Success -> res.data
            is AppResult.Failure -> null
        }
        _state.value = _state.value.copy(driverName = name, driverRole = user.role, driverEmail = user.email)
    }

    fun setLanguageTag(tag: String?) = viewModelScope.launch { prefs.setLanguageTag(tag) }

    fun setChecklistReminderEnabled(enabled: Boolean) =
        viewModelScope.launch { prefs.setChecklistReminderEnabled(enabled) }

    fun setChecklistReminderTime(hour: Int, minute: Int) =
        viewModelScope.launch { prefs.setChecklistReminderTime(hour, minute) }

    fun setTapOutReminderEnabled(enabled: Boolean) =
        viewModelScope.launch { prefs.setTapOutReminderEnabled(enabled) }

    fun setAnnouncementsEnabled(enabled: Boolean) =
        viewModelScope.launch { prefs.setAnnouncementsEnabled(enabled) }

    fun setNewMessagesEnabled(enabled: Boolean) =
        viewModelScope.launch { prefs.setNewMessagesEnabled(enabled) }
}
