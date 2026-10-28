package com.drivemate.showcase.feature.announcement

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivemate.showcase.R
import com.drivemate.showcase.core.model.Announcement
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.usecase.broadcast.DeleteAnnouncementUseCase
import com.drivemate.showcase.domain.usecase.broadcast.ObserveAnnouncementsUseCase
import com.drivemate.showcase.domain.usecase.broadcast.SendAnnouncementUseCase
import com.drivemate.showcase.domain.usecase.broadcast.UpdateAnnouncementUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AnnouncementFeedUiState(
    val isLoading: Boolean = true,
    val announcements: List<Announcement> = emptyList(),
    @param:StringRes val errorRes: Int? = null
)

enum class AnnouncementFeedbackTone {
    Success,
    Error
}

class AnnouncementsViewModel(
    private val observeAnnouncements: ObserveAnnouncementsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnnouncementFeedUiState())
    val uiState: StateFlow<AnnouncementFeedUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        observeJob?.cancel()
        _uiState.value = _uiState.value.copy(isLoading = true, errorRes = null)
        observeJob = viewModelScope.launch {
            observeAnnouncements().collect { result ->
                _uiState.value = when (result) {
                    is AppResult.Success -> AnnouncementFeedUiState(
                        isLoading = false,
                        announcements = result.data,
                        errorRes = null
                    )
                    is AppResult.Failure -> _uiState.value.copy(
                        isLoading = false,
                        errorRes = R.string.announcement_error_load_failed
                    )
                }
            }
        }
    }
}
