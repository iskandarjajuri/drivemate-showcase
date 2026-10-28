package com.drivemate.showcase.data.repository

import com.google.firebase.firestore.FirebaseFirestoreException
import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.data.FirestoreConstants
import com.drivemate.showcase.data.firestore.FirestoreService
import com.drivemate.showcase.domain.calendar.DEFAULT_WORK_CALENDAR_TIMEZONE_ID
import com.drivemate.showcase.domain.calendar.DefaultRules
import com.drivemate.showcase.domain.calendar.OverrideKind
import com.drivemate.showcase.domain.calendar.WorkCalendarYear
import com.drivemate.showcase.domain.calendar.WorkSpec
import com.drivemate.showcase.domain.calendar.WorkdayOverride
import com.drivemate.showcase.domain.calendar.WorkdayRule
import com.drivemate.showcase.domain.calendar.kind
import com.drivemate.showcase.domain.error.CalendarError
import com.drivemate.showcase.domain.repository.WorkCalendarRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class FirestoreWorkCalendarRepository(
    private val fs: FirestoreService
) : WorkCalendarRepository {

    private companion object {
        const val TAG = "DM/WorkCalendarRepo"
        const val COLLECTION = "work_calendars"
        const val WEEKEND_DEFAULT = "OFF"
    }

    override suspend fun getYear(year: Int): AppResult<WorkCalendarYear> = try {
        val docId = year.toString()
        val map = withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
            fs.getDocumentMap(COLLECTION, docId)
        }
        if (map == null) {
            DMLog.w(TAG, "getYear: not found year=$year")
            return AppResult.failure(CalendarError.NotFound)
        }
        val dto = mapToCalendarDto(map, year)
            ?: return AppResult.failure(CalendarError.Unknown(IllegalStateException("calendar_invalid")))
        AppResult.success(dto.toDomain())
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        DMLog.e(TAG, "getYear failed: ${t.message}", t)
        AppResult.failure(toCalendarError(t))
    }

    override suspend fun upsertYear(calendar: WorkCalendarYear): AppResult<Unit> = try {
        val nowMillis = TimeUtil.nowEpochMillis()
        val dto = calendar.toDto(nowMillis)
        val path = "$COLLECTION/${calendar.year}"
        withTimeout(FirestoreConstants.WRITE_TIMEOUT_MS) {
            fs.setDocument(path, dto.toMap(), merge = true)
        }
        DMLog.d(TAG, "upsertYear success year=${calendar.year}")
        AppResult.success(Unit)
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        DMLog.e(TAG, "upsertYear failed: ${t.message}", t)
        AppResult.failure(toCalendarError(t))
    }

    override suspend fun getActiveYear(nowEpochDay: Int, zoneId: String): AppResult<Int> = try {
        val resolvedZone = zoneId.takeIf { it.isNotBlank() } ?: DEFAULT_WORK_CALENDAR_TIMEZONE_ID
        val year = LocalDate.fromEpochDays(nowEpochDay).year
        DMLog.d(TAG, "getActiveYear zone=$resolvedZone epochDay=$nowEpochDay year=$year")
        AppResult.success(year)
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        DMLog.e(TAG, "getActiveYear failed: ${t.message}", t)
        AppResult.failure(toCalendarError(t))
    }

    private fun toCalendarError(t: Throwable): CalendarError = when {
        t is CalendarError -> t
        t is FirebaseFirestoreException && t.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            CalendarError.PermissionDenied
        else -> CalendarError.Unknown(t)
    }

    private data class FirestoreWorkSpecDto(
        val startMin: Int,
        val endMin: Int,
        val breakMin: Int,
    )

    private data class FirestoreWorkdayOverrideDto(
        val dateEpochDay: Int,
        val kind: String,
        val workSpec: FirestoreWorkSpecDto?,
        val note: String?,
        val updatedAtMillis: Long?
    )

    private data class FirestoreWorkCalendarDto(
        val year: Int,
        val timeZoneId: String,
        val updatedAtMillis: Long,
        val weekdaySpec: FirestoreWorkSpecDto,
        val fridaySpec: FirestoreWorkSpecDto,
        val weekendDefault: String,
        val overrides: List<FirestoreWorkdayOverrideDto>
    )

    private fun mapToCalendarDto(map: Map<String, Any?>, fallbackYear: Int): FirestoreWorkCalendarDto? {
        val year = map.int("year") ?: fallbackYear
        val timeZoneId = map.string("timeZoneId") ?: DEFAULT_WORK_CALENDAR_TIMEZONE_ID
        val updatedAtMillis = map.long("updatedAtMillis") ?: 0L
        val defaultRulesMap = map.map("defaultRules")
        val mondayToThursday = defaultRulesMap?.map("mondayToThursday")
        val friday = defaultRulesMap?.map("friday")
        val weekendDefault = defaultRulesMap?.string("weekendDefault") ?: WEEKEND_DEFAULT

        val weekdaySpec = mondayToThursday?.toWorkSpecDto()
            ?: DefaultRules().weekdayRule.toSpecDto()
        val fridaySpec = friday?.toWorkSpecDto()
            ?: DefaultRules().fridayRule.toSpecDto()

        val overrides = map.list("overrides").mapNotNull { it.toOverrideDtoOrNull() }

        return FirestoreWorkCalendarDto(
            year = year,
            timeZoneId = timeZoneId,
            updatedAtMillis = updatedAtMillis,
            weekdaySpec = weekdaySpec,
            fridaySpec = fridaySpec,
            weekendDefault = weekendDefault,
            overrides = overrides
        )
    }

    private fun FirestoreWorkCalendarDto.toDomain(): WorkCalendarYear {
        return WorkCalendarYear(
            year = year,
            timezoneId = timeZoneId,
            defaultRules = DefaultRules(
                weekdayRule = weekdaySpec.toRule(),
                fridayRule = fridaySpec.toRule()
            ),
            overrides = overrides.mapNotNull { it.toDomainOrNull() },
            updatedAtMillis = updatedAtMillis
        )
    }

    private fun WorkCalendarYear.toDto(nowMillis: Long): FirestoreWorkCalendarDto {
        val weekdaySpec = defaultRules.weekdayRule.toSpecDto()
        val fridaySpec = defaultRules.fridayRule.toSpecDto()
        return FirestoreWorkCalendarDto(
            year = year,
            timeZoneId = timezoneId.takeIf { it.isNotBlank() } ?: DEFAULT_WORK_CALENDAR_TIMEZONE_ID,
            updatedAtMillis = updatedAtMillis ?: nowMillis,
            weekdaySpec = weekdaySpec,
            fridaySpec = fridaySpec,
            weekendDefault = WEEKEND_DEFAULT,
            overrides = overrides.map { it.toDto(nowMillis) }
        )
    }

    private fun FirestoreWorkCalendarDto.toMap(): Map<String, Any?> {
        val defaultRules = mapOf(
            "mondayToThursday" to weekdaySpec.toMap(),
            "friday" to fridaySpec.toMap(),
            "weekendDefault" to weekendDefault
        )
        val overridesMap = overrides.map { it.toMap() }
        return mapOf(
            "year" to year,
            "updatedAtMillis" to updatedAtMillis,
            "timeZoneId" to timeZoneId,
            "defaultRules" to defaultRules,
            "overrides" to overridesMap
        )
    }

    private fun FirestoreWorkdayOverrideDto.toDomainOrNull(): WorkdayOverride? {
        val date = LocalDate.fromEpochDays(dateEpochDay)
        return when (kind.uppercase()) {
            "HOLIDAY" -> WorkdayOverride(date = date, isWorkday = false, ruleOverride = null, note = note)
            "WORKDAY_SWAP", "WORKDAY" -> WorkdayOverride(date = date, isWorkday = true, ruleOverride = null, note = note)
            "CUSTOM" -> {
                val rule = workSpec?.toRule()
                WorkdayOverride(date = date, isWorkday = true, ruleOverride = rule, note = note)
            }
            else -> {
                DMLog.w(TAG, "Unknown override kind='$kind' for dateEpochDay=$dateEpochDay")
                null
            }
        }
    }

    private fun WorkdayOverride.toDto(nowMillis: Long): FirestoreWorkdayOverrideDto {
        val kind = when (this.kind) {
            OverrideKind.HOLIDAY -> "HOLIDAY"
            OverrideKind.WORKDAY_SWAP -> "WORKDAY_SWAP"
            OverrideKind.CUSTOM -> "CUSTOM"
        }
        return FirestoreWorkdayOverrideDto(
            dateEpochDay = date.toEpochDays().toInt(),
            kind = kind,
            workSpec = ruleOverride?.toSpecDto(),
            note = note,
            updatedAtMillis = nowMillis
        )
    }

    private fun FirestoreWorkdayOverrideDto.toMap(): Map<String, Any?> {
        return mapOf(
            "dateEpochDay" to dateEpochDay,
            "kind" to kind,
            "workSpec" to workSpec?.toMap(),
            "note" to note,
            "updatedAtMillis" to updatedAtMillis
        )
    }

    private fun Map<String, Any?>.toWorkSpecDto(): FirestoreWorkSpecDto? {
        val start = int("startMin") ?: return null
        val end = int("endMin") ?: return null
        val breakMin = int("breakMin") ?: return null
        return FirestoreWorkSpecDto(start, end, breakMin)
    }

    private fun FirestoreWorkSpecDto.toRule(): WorkdayRule {
        return WorkdayRule(
            startLocalTime = startMin.toLocalTime(),
            endLocalTime = endMin.toLocalTime(),
            breakMinutes = breakMin
        )
    }

    private fun WorkdayRule.toSpecDto(): FirestoreWorkSpecDto {
        val start = (startLocalTime.hour * 60) + startLocalTime.minute
        val end = (endLocalTime.hour * 60) + endLocalTime.minute
        return FirestoreWorkSpecDto(start, end, breakMinutes)
    }

    private fun FirestoreWorkSpecDto.toMap(): Map<String, Any?> {
        return mapOf(
            "startMin" to startMin,
            "endMin" to endMin,
            "breakMin" to breakMin
        )
    }

    private fun Map<String, Any?>.toOverrideDtoOrNull(): FirestoreWorkdayOverrideDto? {
        val dateEpochDay = int("dateEpochDay") ?: return null
        val kind = string("kind") ?: return null
        val workSpec = map("workSpec")?.toWorkSpecDto()
        val note = string("note")
        val updatedAtMillis = long("updatedAtMillis")
        return FirestoreWorkdayOverrideDto(
            dateEpochDay = dateEpochDay,
            kind = kind,
            workSpec = workSpec,
            note = note,
            updatedAtMillis = updatedAtMillis
        )
    }

    private fun Int.toLocalTime(): LocalTime {
        val hour = this / 60
        val minute = this % 60
        return LocalTime(hour, minute)
    }

    private fun Map<String, Any?>.string(key: String): String? = this[key] as? String

    private fun Map<String, Any?>.int(key: String): Int? = (this[key] as? Number)?.toInt()

    private fun Map<String, Any?>.long(key: String): Long? = (this[key] as? Number)?.toLong()

    private fun Map<String, Any?>.map(key: String): Map<String, Any?>? = this[key] as? Map<String, Any?>

    private fun Map<String, Any?>.list(key: String): List<Map<String, Any?>> {
        val raw = this[key] as? List<*> ?: return emptyList()
        return raw.mapNotNull { it as? Map<String, Any?> }
    }
}
