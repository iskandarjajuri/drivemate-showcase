package com.drivemate.showcase.feature.driver

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivemate.showcase.R
import com.drivemate.showcase.core.model.Announcement
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.usecase.broadcast.ObserveAnnouncementsUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DriverAnnouncementsUiState(
    val isLoading: Boolean = true,
    val announcements: List<Announcement> = emptyList(),
    @param:StringRes val errorRes: Int? = null,
    val readIds: Set<String> = emptySet(),
    val expandedId: String? = null
)

class DriverAnnouncementsViewModel(
    private val observeAnnouncements: ObserveAnnouncementsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(DriverAnnouncementsUiState())
    val state: StateFlow<DriverAnnouncementsUiState> = _state.asStateFlow()

    private var job: Job? = null

    init { refresh() }

    fun refresh() {
        job?.cancel()
        _state.update { it.copy(isLoading = true, errorRes = null) }
        job = viewModelScope.launch {
            observeAnnouncements().collect { result ->
                _state.update { current ->
                    when (result) {
                        is AppResult.Success -> current.copy(
                            isLoading = false,
                            announcements = result.data,
                            errorRes = null
                        )
                        is AppResult.Failure -> current.copy(
                            isLoading = false,
                            errorRes = R.string.announcement_error_load_failed
                        )
                    }
                }
            }
        }
    }

    fun toggleExpand(id: String) {
        _state.update { current ->
            val newExpanded = if (current.expandedId == id) null else id
            val newRead = if (newExpanded != null) current.readIds + id else current.readIds
            current.copy(expandedId = newExpanded, readIds = newRead)
        }
    }

    // TODO Phase-4: Announcements push notification deep link
}
