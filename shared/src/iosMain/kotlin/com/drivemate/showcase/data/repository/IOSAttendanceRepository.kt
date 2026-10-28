package com.drivemate.showcase.data.repository

import com.drivemate.showcase.bridge.IOSAttendanceDailyPayload
import com.drivemate.showcase.bridge.IOSAttendanceEventPayload
import com.drivemate.showcase.bridge.IOSPlatformBridgeRegistry
import com.drivemate.showcase.core.model.Attendance
import com.drivemate.showcase.core.model.TodayAttendance
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.core.util.attendanceDailyDocId
import com.drivemate.showcase.domain.repository.AttendanceRepository
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.TimeZone
import kotlin.coroutines.resume

class IOSAttendanceRepository : AttendanceRepository {
    private val attendanceBridge
        get() = IOSPlatformBridgeRegistry.attendanceBridge
            ?: error("IOSAttendancePlatformBridge is not installed")

    override suspend fun checkIn(att: Attendance): AppResult<Unit> =
        suspendCancellableCoroutine { cont ->
            attendanceBridge.checkIn(
                driverUid = att.driverUid,
                timestampMillis = att.timestampMillis,
                note = att.note,
                latitude = att.latitude,
                longitude = att.longitude,
                locationName = att.locationName
            ) { errorMessage ->
                if (!cont.isCompleted) cont.resume(
                    if (errorMessage == null) AppResult.success(Unit)
                    else AppResult.failure(IllegalStateException(errorMessage))
                )
            }
        }

    override suspend fun checkOut(
        timestampMillis: Long,
        locationName: String?,
        latitude: Double?,
        longitude: Double?
    ): AppResult<Unit> = suspendCancellableCoroutine { cont ->
        attendanceBridge.checkOut(
            timestampMillis = timestampMillis,
            locationName = locationName,
            latitude = latitude,
            longitude = longitude
        ) { errorMessage ->
            if (!cont.isCompleted) cont.resume(
                if (errorMessage == null) AppResult.success(Unit)
                else AppResult.failure(IllegalStateException(errorMessage))
            )
        }
    }

    override suspend fun upsertTodayClockIn(
        driverUid: String,
        epochDay: Long,
        nowMillis: Long
    ): AppResult<TodayAttendance> = suspendCancellableCoroutine { cont ->
        val docId = attendanceDailyDocId(driverUid, epochDay)
        attendanceBridge.upsertClockIn(
            docId = docId,
            driverUid = driverUid,
            epochDay = epochDay,
            nowMillis = nowMillis
        ) { payload, errorMessage ->
            if (!cont.isCompleted) cont.resume(
                if (payload != null) AppResult.success(payload.toModel())
                else AppResult.failure(IllegalStateException(errorMessage ?: "upsertClockIn failed"))
            )
        }
    }

    override suspend fun upsertTodayClockOut(
        driverUid: String,
        epochDay: Long,
        nowMillis: Long
    ): AppResult<TodayAttendance> = suspendCancellableCoroutine { cont ->
        val docId = attendanceDailyDocId(driverUid, epochDay)
        attendanceBridge.upsertClockOut(
            docId = docId,
            driverUid = driverUid,
            epochDay = epochDay,
            nowMillis = nowMillis
        ) { payload, errorMessage ->
            if (!cont.isCompleted) cont.resume(
                if (payload != null) AppResult.success(payload.toModel())
                else AppResult.failure(IllegalStateException(errorMessage ?: "upsertClockOut failed"))
            )
        }
    }

    override suspend fun getTodayAttendance(
        driverUid: String,
        epochDay: Long
    ): AppResult<TodayAttendance?> = suspendCancellableCoroutine { cont ->
        val docId = attendanceDailyDocId(driverUid, epochDay)
        attendanceBridge.getTodayAttendance(
            docId = docId,
            driverUid = driverUid,
            epochDay = epochDay
        ) { payload, errorMessage ->
            if (!cont.isCompleted) cont.resume(
                when {
                    errorMessage != null -> AppResult.failure(IllegalStateException(errorMessage))
                    else -> AppResult.success(payload?.toModel())
                }
            )
        }
    }

    override suspend fun getAttendancesByEpochDayRange(
        driverUid: String,
        startEpochDay: Long,
        endEpochDay: Long
    ): AppResult<List<Attendance>> = suspendCancellableCoroutine { cont ->
        val startMillis = TimeUtil.parseHm("00:00", startEpochDay, ATTENDANCE_ZONE)
        val endMillisExclusive = TimeUtil.parseHm("00:00", endEpochDay + 1L, ATTENDANCE_ZONE)
        attendanceBridge.getAttendancesByRange(
            driverUid = driverUid,
            startMillis = startMillis,
            endMillisExclusive = endMillisExclusive
        ) { payloads, errorMessage ->
            if (!cont.isCompleted) cont.resume(
                when {
                    errorMessage != null -> AppResult.failure(IllegalStateException(errorMessage))
                    else -> AppResult.success((payloads ?: emptyList()).map { it.toModel() })
                }
            )
        }
    }

    override suspend fun getToday(driverUid: String): AppResult<Attendance?> =
        suspendCancellableCoroutine { cont ->
            attendanceBridge.getToday(driverUid) { payload, errorMessage ->
                if (!cont.isCompleted) cont.resume(
                    when {
                        errorMessage != null -> AppResult.failure(IllegalStateException(errorMessage))
                        else -> AppResult.success(payload?.toModel())
                    }
                )
            }
        }

    companion object {
        private val ATTENDANCE_ZONE: TimeZone = TimeZone.of("Asia/Jakarta")
    }
}

private fun IOSAttendanceDailyPayload.toModel() = TodayAttendance(
    driverUid = driverUid,
    epochDay = epochDay,
    clockInMillis = clockInMillis,
    clockOutMillis = clockOutMillis,
    updatedAtMillis = updatedAtMillis
)

private fun IOSAttendanceEventPayload.toModel() = Attendance(
    driverUid = driverUid,
    timestampMillis = timestampMillis,
    latitude = latitude,
    longitude = longitude,
    note = note,
    locationName = locationName
)
