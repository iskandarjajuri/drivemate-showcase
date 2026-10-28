package com.drivemate.showcase.bridge

import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.usecase.calendar.GetWorkCalendarYearUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class IOSWorkCalendarBridge(
    private val getCalendarUseCase: GetWorkCalendarYearUseCase,
    private val scope: CoroutineScope
) {
    fun loadWorkCalendar(year: Int, completion: (IOSWorkCalendarSnapshot?) -> Unit) {
        scope.launch {
            when (val result = getCalendarUseCase(year)) {
                is AppResult.Success -> completion(result.data.toIosSnapshot())
                is AppResult.Failure -> completion(null)
            }
        }
    }
}
