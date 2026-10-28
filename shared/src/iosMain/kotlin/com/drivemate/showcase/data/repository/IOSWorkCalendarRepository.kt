package com.drivemate.showcase.data.repository

import com.drivemate.showcase.bridge.IOSPlatformBridgeRegistry
import com.drivemate.showcase.bridge.IOSWorkCalendarDayPayload
import com.drivemate.showcase.bridge.IOSWorkCalendarOverrideWritePayload
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.domain.calendar.DEFAULT_WORK_CALENDAR_TIMEZONE_ID
import com.drivemate.showcase.domain.calendar.OverrideKind
import com.drivemate.showcase.domain.calendar.WorkCalendarYear
import com.drivemate.showcase.domain.calendar.WorkdayOverride
import com.drivemate.showcase.domain.calendar.WorkdayRule
import com.drivemate.showcase.domain.calendar.kind
import com.drivemate.showcase.domain.error.CalendarError
import com.drivemate.showcase.domain.repository.WorkCalendarRepository
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.coroutines.resume

class IOSWorkCalendarRepository : WorkCalendarRepository {
    private val workCalendarBridge
        get() = IOSPlatformBridgeRegistry.workCalendarBridge
            ?: error("IOSWorkCalendarPlatformBridge is not installed")

    override suspend fun getYear(year: Int): AppResult<WorkCalendarYear> =
        suspendCancellableCoroutine { cont ->
            workCalendarBridge.getWorkCalendar(year) { payloads, errorMessage ->
                if (!cont.isCompleted) {
                    cont.resume(
                        when {
                            errorMessage != null -> AppResult.failure(errorMessage.toCalendarError())
                            payloads == null -> AppResult.failure(CalendarError.NotFound)
                            else -> AppResult.success(payloads.toCalendarYear(year))
                        }
                    )
                }
            }
        }

    /**
     * Mirrors `FirestoreWorkCalendarRepository.upsertYear` (androidMain): a set-with-merge
     * write of `work_calendars/{year}`, same document shape as `toDto()`/`toMap()` there.
     * `updatedAtMillis` defaults to now only when the domain object doesn't already carry
     * one, matching Android's `updatedAtMillis ?: nowMillis`.
     */
    override suspend fun upsertYear(calendar: WorkCalendarYear): AppResult<Unit> =
        suspendCancellableCoroutine { cont ->
            val nowMillis = TimeUtil.nowEpochMillis()
            val weekday = calendar.defaultRules.weekdayRule
            val friday = calendar.defaultRules.fridayRule
            workCalendarBridge.upsertWorkCalendar(
                year = calendar.year,
                timeZoneId = calendar.timezoneId.takeIf { it.isNotBlank() }
                    ?: DEFAULT_WORK_CALENDAR_TIMEZONE_ID,
                updatedAtMillis = calendar.updatedAtMillis ?: nowMillis,
                weekdayStartMin = weekday.toStartMinutes(),
                weekdayEndMin = weekday.toEndMinutes(),
                weekdayBreakMin = weekday.breakMinutes,
                fridayStartMin = friday.toStartMinutes(),
                fridayEndMin = friday.toEndMinutes(),
                fridayBreakMin = friday.breakMinutes,
                overrides = calendar.overrides.map { it.toWritePayload(nowMillis) }
            ) { errorMessage ->
                if (!cont.isCompleted) {
                    cont.resume(
                        if (errorMessage != null) AppResult.failure(errorMessage.toCalendarError())
                        else AppResult.success(Unit)
                    )
                }
            }
        }

    override suspend fun getActiveYear(nowEpochDay: Int, zoneId: String): AppResult<Int> = try {
        AppResult.success(LocalDate.fromEpochDays(nowEpochDay).year)
    } catch (t: Throwable) {
        AppResult.failure(CalendarError.Unknown(t))
    }
}

// Mirrors WorkdayRule.toSpecDto() (androidMain): minutes-since-midnight, not a
// LocalTime — matches the workday_plans/work_calendars wire format.
private fun WorkdayRule.toStartMinutes(): Int = (startLocalTime.hour * 60) + startLocalTime.minute
private fun WorkdayRule.toEndMinutes(): Int = (endLocalTime.hour * 60) + endLocalTime.minute

// Mirrors WorkdayOverride.toDto() (androidMain), including the timestamp override:
// every override gets `updatedAtMillis = nowMillis` on write regardless of when it
// was originally created — Android does not preserve a prior per-override timestamp.
// `kind` here is the commonMain WorkdayOverride.kind extension (WorkCalendarResolver.kt)
// — the same derivation Android's own toDto() uses, not a re-implementation.
private fun WorkdayOverride.toWritePayload(nowMillis: Long): IOSWorkCalendarOverrideWritePayload {
    val kindRaw = when (kind) {
        OverrideKind.HOLIDAY -> "HOLIDAY"
        OverrideKind.WORKDAY_SWAP -> "WORKDAY_SWAP"
        OverrideKind.CUSTOM -> "CUSTOM"
    }
    return IOSWorkCalendarOverrideWritePayload(
        dateEpochDay = date.toEpochDays(),
        kind = kindRaw,
        hasWorkSpec = ruleOverride != null,
        workSpecStartMin = ruleOverride?.toStartMinutes() ?: 0,
        workSpecEndMin = ruleOverride?.toEndMinutes() ?: 0,
        workSpecBreakMin = ruleOverride?.breakMinutes ?: 0,
        note = note,
        updatedAtMillis = nowMillis
    )
}

private fun String.toCalendarError(): Throwable {
    return when {
        contains("permission", ignoreCase = true) -> CalendarError.PermissionDenied
        contains("not found", ignoreCase = true) -> CalendarError.NotFound
        else -> CalendarError.Unknown(IllegalStateException(this))
    }
}

private fun List<IOSWorkCalendarDayPayload>.toCalendarYear(year: Int): WorkCalendarYear {
    return WorkCalendarYear(
        year = year,
        overrides = mapNotNull { it.toOverrideOrNull(year) }.sortedBy { it.date.toEpochDays() }
    )
}

private fun IOSWorkCalendarDayPayload.toOverrideOrNull(expectedYear: Int): WorkdayOverride? {
    val date = LocalDate.fromEpochDays(epochDay.toInt())
    if (date.year != expectedYear) return null

    val holidayLabel = holidayName?.trim()?.takeIf { it.isNotEmpty() }
    val defaultWorkday = date.isDefaultWorkday()

    return when {
        !isWorkday && (defaultWorkday || holidayLabel != null) ->
            WorkdayOverride(date = date, isWorkday = false, note = holidayLabel)
        isWorkday && !defaultWorkday ->
            WorkdayOverride(date = date, isWorkday = true)
        else -> null
    }
}

private fun LocalDate.isDefaultWorkday(): Boolean = when (dayOfWeek) {
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY -> true
    DayOfWeek.SATURDAY,
    DayOfWeek.SUNDAY -> false
}
