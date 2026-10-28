package com.drivemate.showcase.bridge

import com.drivemate.showcase.core.model.Attendance
import com.drivemate.showcase.core.model.UserSession
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.domain.usecase.attendance.GetDriverAttendanceHistoryUseCase
import com.drivemate.showcase.domain.usecase.attendance.GetTodayAttendanceUseCase
import com.drivemate.showcase.domain.usecase.attendance.TapInUseCase
import com.drivemate.showcase.domain.usecase.attendance.TapOutUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class IOSAttendanceBridge(
    private val scope: CoroutineScope,
    private val tapIn: TapInUseCase,
    private val tapOut: TapOutUseCase,
    private val getTodayAttendance: GetTodayAttendanceUseCase,
    private val getAttendanceHistory: GetDriverAttendanceHistoryUseCase,
    private val currentSession: () -> UserSession?
) {
    fun tapIn(completion: (IOSAttendanceSnapshot?, String?) -> Unit) {
        val session = currentSession() ?: run {
            completion(null, null)
            return
        }
        val nowMillis = TimeUtil.nowEpochMillis()
        val att = Attendance(
            driverUid = session.uid,
            timestampMillis = nowMillis,
            latitude = null,
            longitude = null
        )
        scope.launch {
            when (val result = tapIn.invoke(att)) {
                is AppResult.Success -> completion(result.data.toIosSnapshot(), null)
                is AppResult.Failure -> completion(null, result.throwable.message)
            }
        }
    }

    fun tapOut(completion: (IOSAttendanceSnapshot?, String?) -> Unit) {
        val session = currentSession() ?: run {
            completion(null, null)
            return
        }
        scope.launch {
            when (val result = tapOut.invoke(session.uid)) {
                is AppResult.Success -> completion(result.data.toIosSnapshot(), null)
                is AppResult.Failure -> completion(null, result.throwable.message)
            }
        }
    }

    fun loadTodayAttendance(completion: (IOSAttendanceSnapshot?, String?) -> Unit) {
        val session = currentSession() ?: run {
            completion(null, null)
            return
        }
        val epochDay = TimeUtil.todayEpochDay(IOSDriverTimezone)
        scope.launch {
            when (val result = getTodayAttendance.invoke(session.uid, epochDay)) {
                is AppResult.Success -> completion(result.data?.toIosSnapshot(), null)
                is AppResult.Failure -> completion(
                    null,
                    result.throwable.message ?: "Failed to load today's attendance."
                )
            }
        }
    }

    fun loadAttendanceHistory(completion: (List<IOSAttendanceSnapshot>, String?) -> Unit) {
        val session = currentSession() ?: run {
            completion(emptyList(), null)
            return
        }
        val range = TimeUtil.rollingThreeMonthWindowEpochDays(zone = IOSDriverTimezone)
        scope.launch {
            when (val result = getAttendanceHistory.invoke(session.uid, range.first.toLong(), range.last.toLong())) {
                is AppResult.Success -> completion(result.data.toDailySnapshots(), null)
                is AppResult.Failure -> completion(
                    emptyList(),
                    result.throwable.message ?: "Failed to load attendance history."
                )
            }
        }
    }
}
