package com.drivemate.showcase.feature.driver

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivemate.showcase.R
import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.model.WorkDay
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.usecase.report.GetWorkdayDetailUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DriverWorkdayDetailState(
    val loading: Boolean = false,
    @param:StringRes val errorRes: Int? = null,
    val data: WorkDay? = null
)

class DriverWorkdayDetailViewModel(
    private val getDetail: GetWorkdayDetailUseCase
) : ViewModel() {
    private companion object {
        const val TAG = "DM/DriverWorkdayDetail"
    }

    private val _state = MutableStateFlow(DriverWorkdayDetailState())
    val state: StateFlow<DriverWorkdayDetailState> = _state.asStateFlow()

    fun load(workdayId: String) {
        if (_state.value.loading) return
        _state.value = DriverWorkdayDetailState(loading = true)
        viewModelScope.launch {
            try {
                when (val result = getDetail(workdayId)) {
                    is AppResult.Success -> {
                        val data = result.data
                        _state.value = if (data == null) {
                            DriverWorkdayDetailState(errorRes = R.string.driver_error_workday_detail_not_found)
                        } else {
                            DriverWorkdayDetailState(data = data)
                        }
                    }

                    is AppResult.Failure -> {
                        DMLog.e(TAG, "load failed: ${result.throwable.message}", result.throwable)
                        _state.value = DriverWorkdayDetailState(errorRes = R.string.driver_error_workday_detail_load_failed)
                    }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                DMLog.e(TAG, "load failed: ${t.message}", t)
                _state.value = DriverWorkdayDetailState(errorRes = R.string.driver_error_workday_detail_load_failed)
            }
        }
    }
}

