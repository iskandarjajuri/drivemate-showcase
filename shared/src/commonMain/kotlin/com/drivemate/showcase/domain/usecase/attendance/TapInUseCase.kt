package com.drivemate.showcase.domain.usecase.attendance

import com.drivemate.showcase.core.model.Attendance
import com.drivemate.showcase.core.model.TodayAttendance
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.domain.error.AttendanceError
import kotlinx.datetime.TimeZone
import com.drivemate.showcase.domain.repository.AttendanceRepository

class TapInUseCase(private val repo: AttendanceRepository) {
    suspend operator fun invoke(att: Attendance): AppResult<TodayAttendance> {
        val zone = TimeZone.of("Asia/Jakarta")
        val epochDay = TimeUtil.epochDayFromMillis(att.timestampMillis, zone)
        when (val existing = repo.getTodayAttendance(att.driverUid, epochDay)) {
            is AppResult.Failure -> return AppResult.failure(existing.throwable)
            is AppResult.Success -> {
                if (existing.data?.clockInMillis != null) {
                    return AppResult.failure(AttendanceError.AlreadyClockedIn)
                }
            }
        }

        return when (val checkIn = repo.checkIn(att)) {
            is AppResult.Failure -> AppResult.failure(checkIn.throwable)
            is AppResult.Success -> {
                when (val upsert = repo.upsertTodayClockIn(att.driverUid, epochDay, att.timestampMillis)) {
                    is AppResult.Success -> upsert
                    is AppResult.Failure -> {
                        AppResult.success(
                            TodayAttendance(
                                driverUid = att.driverUid,
                                epochDay = epochDay,
                                clockInMillis = att.timestampMillis,
                                clockOutMillis = null,
                                updatedAtMillis = att.timestampMillis
                            )
                        )
                    }
                }
            }
        }
    }
}
