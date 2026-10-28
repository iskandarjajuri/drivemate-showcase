package com.drivemate.showcase.domain.usecase.attendance

import com.drivemate.showcase.core.model.Attendance
import com.drivemate.showcase.core.model.TodayAttendance
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.domain.calendar.ComputedWorkSummary
import com.drivemate.showcase.domain.calendar.WorkCalendarYear
import com.drivemate.showcase.domain.calendar.WorkSession
import com.drivemate.showcase.domain.calendar.WorkSpec
import com.drivemate.showcase.domain.calendar.computeDailySummary
import com.drivemate.showcase.domain.repository.AttendanceRepository
import com.drivemate.showcase.domain.repository.WorkCalendarRepository
import com.drivemate.showcase.domain.usecase.calendar.ResolveWorkSpecForDateUseCase
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

data class AttendanceDailyReconcileStats(
    val scannedDays: Int,
    val repairedDays: Int,
    val noOpDays: Int,
    val failedDays: Int
)

data class DailyAttendanceSnapshot(
    val epochDay: Long,
    val clockInMillis: Long,
    val clockOutMillis: Long?,
    val workSpec: WorkSpec?,
    val summary: ComputedWorkSummary
)

fun buildDailyAttendanceSnapshot(
    epochDay: Long,
    events: List<Attendance>,
    calendar: WorkCalendarYear,
    resolveWorkSpecForDateUseCase: ResolveWorkSpecForDateUseCase = ResolveWorkSpecForDateUseCase()
): DailyAttendanceSnapshot? {
    if (events.isEmpty()) return null
    val sorted = events.sortedBy { it.timestampMillis }
    val firstIn = sorted.firstOrNull { it.normalizedType() == "IN" } ?: return null
    val lastOut = sorted
        .asReversed()
        .firstOrNull { it.normalizedType() == "OUT" && it.timestampMillis >= firstIn.timestampMillis }
    val session = WorkSession(
        clockInMillis = firstIn.timestampMillis,
        clockOutMillis = lastOut?.timestampMillis
    )
    val date = LocalDate.fromEpochDays(epochDay.toInt())
    val summary = computeDailySummary(
        calendar = calendar,
        date = date,
        sessions = listOf(session)
    )
    val workSpec = resolveWorkSpecForDateUseCase(calendar, epochDay.toInt())
    val resolvedClockOut = if (summary.isInProgress) null else session.clockOutMillis
    return DailyAttendanceSnapshot(
        epochDay = epochDay,
        clockInMillis = firstIn.timestampMillis,
        clockOutMillis = resolvedClockOut,
        workSpec = workSpec,
        summary = summary
    )
}

class ReconcileAttendanceDailyUseCase(
    private val attendanceRepository: AttendanceRepository,
    private val workCalendarRepository: WorkCalendarRepository,
    private val resolveWorkSpecForDateUseCase: ResolveWorkSpecForDateUseCase = ResolveWorkSpecForDateUseCase(),
) {
    suspend operator fun invoke(
        driverUid: String,
        startEpochDay: Long,
        endEpochDay: Long,
        zone: TimeZone = TimeZone.of("Asia/Jakarta")
    ): AppResult<AttendanceDailyReconcileStats> {
        if (startEpochDay > endEpochDay) {
            return AppResult.success(
                AttendanceDailyReconcileStats(
                    scannedDays = 0,
                    repairedDays = 0,
                    noOpDays = 0,
                    failedDays = 0
                )
            )
        }

        val attendances = when (
            val result = attendanceRepository.getAttendancesByEpochDayRange(
                driverUid = driverUid,
                startEpochDay = startEpochDay,
                endEpochDay = endEpochDay
            )
        ) {
            is AppResult.Failure -> return AppResult.failure(result.throwable)
            is AppResult.Success -> result.data
        }

        val byEpochDay = attendances.groupBy {
            TimeUtil.epochDayFromMillis(it.timestampMillis, zone)
        }
        val calendarByYear = mutableMapOf<Int, WorkCalendarYear>()

        var scannedDays = 0
        var repairedDays = 0
        var noOpDays = 0
        var failedDays = 0

        for (epochDay in startEpochDay..endEpochDay) {
            val dayAttendances = byEpochDay[epochDay].orEmpty()
            if (dayAttendances.isEmpty()) continue
            scannedDays += 1

            val date = LocalDate.fromEpochDays(epochDay.toInt())
            val calendar = calendarByYear.getOrPut(date.year) {
                when (val yearResult = workCalendarRepository.getYear(date.year)) {
                    is AppResult.Success -> yearResult.data
                    is AppResult.Failure -> WorkCalendarYear(
                        year = date.year,
                        timezoneId = zone.id
                    )
                }
            }
            val snapshot = buildDailyAttendanceSnapshot(
                epochDay = epochDay,
                events = dayAttendances,
                calendar = calendar,
                resolveWorkSpecForDateUseCase = resolveWorkSpecForDateUseCase
            ) ?: run {
                noOpDays += 1
                continue
            }

            val existing = when (
                val dayResult = attendanceRepository.getTodayAttendance(driverUid, epochDay)
            ) {
                is AppResult.Failure -> {
                    failedDays += 1
                    continue
                }
                is AppResult.Success -> dayResult.data
            }

            when (reconcileDay(
                driverUid = driverUid,
                snapshot = snapshot,
                existing = existing
            )) {
                ReconcileDayResult.CHANGED -> repairedDays += 1
                ReconcileDayResult.NO_OP -> noOpDays += 1
                ReconcileDayResult.FAILED -> failedDays += 1
            }
        }

        return AppResult.success(
            AttendanceDailyReconcileStats(
                scannedDays = scannedDays,
                repairedDays = repairedDays,
                noOpDays = noOpDays,
                failedDays = failedDays
            )
        )
    }

    private suspend fun reconcileDay(
        driverUid: String,
        snapshot: DailyAttendanceSnapshot,
        existing: TodayAttendance?
    ): ReconcileDayResult {
        var changed = false

        val needsClockIn = existing?.clockInMillis == null
        if (needsClockIn) {
            when (
                val upsertIn = attendanceRepository.upsertTodayClockIn(
                    driverUid = driverUid,
                    epochDay = snapshot.epochDay,
                    nowMillis = snapshot.clockInMillis
                )
            ) {
                is AppResult.Success -> changed = true
                is AppResult.Failure -> {
                    if (!isExpectedIdempotentError(upsertIn.throwable, "AlreadyClockedIn")) {
                        return ReconcileDayResult.FAILED
                    }
                }
            }
        }

        val expectedClockOut = snapshot.clockOutMillis
        val needsClockOut = expectedClockOut != null && existing?.clockOutMillis == null
        if (needsClockOut) {
            when (
                val upsertOut = attendanceRepository.upsertTodayClockOut(
                    driverUid = driverUid,
                    epochDay = snapshot.epochDay,
                    nowMillis = expectedClockOut
                )
            ) {
                is AppResult.Success -> changed = true
                is AppResult.Failure -> {
                    if (!isExpectedIdempotentError(upsertOut.throwable, "AlreadyClockedOut")) {
                        return ReconcileDayResult.FAILED
                    }
                }
            }
        }

        return if (changed) ReconcileDayResult.CHANGED else ReconcileDayResult.NO_OP
    }
}

private enum class ReconcileDayResult {
    CHANGED,
    NO_OP,
    FAILED
}

private fun Attendance.normalizedType(): String =
    note?.trim()?.uppercase().orEmpty()

private fun isExpectedIdempotentError(throwable: Throwable, expected: String): Boolean =
    throwable.message?.contains(expected, ignoreCase = false) == true
