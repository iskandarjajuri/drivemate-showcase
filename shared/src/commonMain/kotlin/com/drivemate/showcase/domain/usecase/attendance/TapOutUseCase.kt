package com.drivemate.showcase.domain.usecase.attendance

import com.drivemate.showcase.core.model.TodayAttendance
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.domain.error.AttendanceError
import com.drivemate.showcase.domain.repository.AttendanceRepository
import com.drivemate.showcase.domain.repository.ChecklistRepository
import com.drivemate.showcase.domain.repository.SafetyRepository
import kotlinx.datetime.TimeZone

class TapOutUseCase(
    private val repo: AttendanceRepository,
    private val checklistRepo: ChecklistRepository,
    private val safetyRepo: SafetyRepository,
    private val nowMillisProvider: () -> Long = { TimeUtil.nowEpochMillis() }
) {
    suspend operator fun invoke(
        driverUid: String,
        locationName: String? = null,
        latitude: Double? = null,
        longitude: Double? = null
    ): AppResult<TodayAttendance> {
        val zone = TimeZone.of("Asia/Jakarta")
        val nowMillis = nowMillisProvider()
        val epochDay = TimeUtil.epochDayFromMillis(nowMillis, zone)

        // Gate BEFORE branching into the two checkout paths below (the normal
        // path and tapOutFallbackWhenDailyMissing), so both are covered by one
        // check rather than duplicating it into each.
        val checklistDone = when (val res = checklistRepo.hasChecklist(driverUid, epochDay.toInt())) {
            is AppResult.Success -> res.data
            is AppResult.Failure -> return AppResult.failure(res.throwable)
        }
        val safetyDone = when (val res = safetyRepo.hasSafetyDeclaration(driverUid, epochDay.toInt())) {
            is AppResult.Success -> res.data
            is AppResult.Failure -> return AppResult.failure(res.throwable)
        }
        if (!checklistDone || !safetyDone) {
            return AppResult.failure(AttendanceError.ChecklistIncomplete)
        }

        var existingClockInMillis: Long? = null
        when (val existing = repo.getTodayAttendance(driverUid, epochDay)) {
            is AppResult.Failure -> return AppResult.failure(existing.throwable)
            is AppResult.Success -> {
                val today = existing.data
                if (today != null) {
                    val clockInMillis = today.clockInMillis
                    if (clockInMillis == null) {
                        return AppResult.failure(AttendanceError.NotClockedIn)
                    }
                    if (today.clockOutMillis != null) {
                        return AppResult.failure(AttendanceError.AlreadyClockedOut)
                    }
                    existingClockInMillis = clockInMillis
                } else {
                    return tapOutFallbackWhenDailyMissing(
                        driverUid = driverUid,
                        epochDay = epochDay,
                        nowMillis = nowMillis,
                        locationName = locationName,
                        latitude = latitude,
                        longitude = longitude
                    )
                }
            }
        }

        return when (val checkOut = repo.checkOut(nowMillis, locationName, latitude, longitude)) {
            is AppResult.Failure -> AppResult.failure(checkOut.throwable)
            is AppResult.Success -> {
                when (val upsert = repo.upsertTodayClockOut(driverUid, epochDay, nowMillis)) {
                    is AppResult.Success -> upsert
                    is AppResult.Failure -> {
                        AppResult.success(
                            TodayAttendance(
                                driverUid = driverUid,
                                epochDay = epochDay,
                                clockInMillis = existingClockInMillis,
                                clockOutMillis = nowMillis,
                                updatedAtMillis = nowMillis
                            )
                        )
                    }
                }
            }
        }
    }

    private suspend fun tapOutFallbackWhenDailyMissing(
        driverUid: String,
        epochDay: Long,
        nowMillis: Long,
        locationName: String?,
        latitude: Double?,
        longitude: Double?
    ): AppResult<TodayAttendance> {
        val latest = when (val today = repo.getToday(driverUid)) {
            is AppResult.Failure -> return AppResult.failure(today.throwable)
            is AppResult.Success -> today.data
        } ?: return AppResult.failure(AttendanceError.NotClockedIn)

        if (latest.note == "OUT") {
            return AppResult.failure(AttendanceError.AlreadyClockedOut)
        }

        return when (val checkOut = repo.checkOut(nowMillis, locationName, latitude, longitude)) {
            is AppResult.Failure -> AppResult.failure(checkOut.throwable)
            is AppResult.Success -> {
                when (val upsert = repo.upsertTodayClockOut(driverUid, epochDay, nowMillis)) {
                    is AppResult.Success -> upsert
                    is AppResult.Failure -> {
                        AppResult.success(
                            TodayAttendance(
                                driverUid = driverUid,
                                epochDay = epochDay,
                                clockInMillis = latest.timestampMillis,
                                clockOutMillis = nowMillis,
                                updatedAtMillis = nowMillis
                            )
                        )
                    }
                }
            }
        }
    }
}
