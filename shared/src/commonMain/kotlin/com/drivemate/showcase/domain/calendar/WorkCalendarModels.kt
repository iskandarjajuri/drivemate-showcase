package com.drivemate.showcase.domain.calendar

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * Work calendar single source of truth.
 * Rules are defined in docs/work_calendar/SSOT.md.
 */
const val DEFAULT_WORK_CALENDAR_TIMEZONE_ID = "Asia/Jakarta"

/**
 * Calendar for a single year.
 * Rules are defined in docs/work_calendar/SSOT.md.
 */
data class WorkCalendarYear(
    val year: Int,
    val timezoneId: String = DEFAULT_WORK_CALENDAR_TIMEZONE_ID,
    val defaultRules: DefaultRules = DefaultRules(),
    val overrides: List<WorkdayOverride> = emptyList(),
    val updatedAtMillis: Long? = null,
)

/**
 * Alias for default work rules (weekday + Friday).
 * Rules are defined in docs/work_calendar/SSOT.md.
 */
typealias DefaultWorkRules = DefaultRules

/**
 * Default schedules for weekdays and Friday.
 * Rules are defined in docs/work_calendar/SSOT.md.
 */
data class DefaultRules(
    val weekdayRule: WorkdayRule = WorkdayRule(
        startLocalTime = LocalTime(7, 30),
        endLocalTime = LocalTime(16, 30),
        breakMinutes = 60,
    ),
    /**
     * Jumat: jendela lebih panjang 20 menit untuk akomodasi salat Jumat, dengan istirahat
     * **80** menit — bukan 60.
     *
     * Dengan istirahat 60, kerja bersih terjadwal Jumat adalah 500 menit dan total sepekan
     * 4×480 + 500 = 2420 menit, melampaui batas 40 jam (2400) pada PP 35/2021. Sejak DriveMate
     * menjadi dasar perhitungan upah (7 September 2026), selisih 20 menit itu berhenti menjadi
     * ketidakrapian dan mulai menjadi selisih pada slip gaji.
     *
     * Yang TIDAK berubah: menit lembur. `lembur = (durasi − istirahat) − (jendela − istirahat)`,
     * jadi istirahat saling menghapus. Perubahan ini menggeser jam normal, bukan lembur.
     */
    val fridayRule: WorkdayRule = WorkdayRule(
        startLocalTime = LocalTime(7, 30),
        endLocalTime = LocalTime(16, 50),
        breakMinutes = 80,
    ),
)

/**
 * Defines a day's start, end, and break duration.
 * Rules are defined in docs/work_calendar/SSOT.md.
 */
data class WorkdayRule(
    val startLocalTime: LocalTime,
    val endLocalTime: LocalTime,
    val breakMinutes: Int,
)

/**
 * Minute-based work spec for a single day.
 * Rules are defined in docs/work_calendar/SSOT.md.
 */
data class WorkSpec(
    val startMinutes: Int,
    val endMinutes: Int,
    val breakMinutes: Int,
)

/**
 * Override kind for a specific date.
 * Rules are defined in docs/work_calendar/SSOT.md.
 */
enum class OverrideKind {
    HOLIDAY,
    WORKDAY_SWAP,
    CUSTOM
}

/**
 * Date-specific calendar override.
 * Rules are defined in docs/work_calendar/SSOT.md.
 */
data class WorkdayOverride(
    val date: LocalDate,
    val isWorkday: Boolean,
    val ruleOverride: WorkdayRule? = null,
    val note: String? = null,
)

/**
 * Year-based calendar key (one calendar per year).
 */
data class WorkCalendarKey(
    val year: Int,
)

/**
 * A driver work session used for net/overtime computation.
 * Rules are defined in docs/work_calendar/SSOT.md.
 */
data class WorkSession(
    val clockInMillis: Long,
    val clockOutMillis: Long?,
    val breakMinutesOverride: Int? = null,
)

/**
 * Result of daily computation.
 * Rules are defined in docs/work_calendar/SSOT.md.
 */
data class ComputedWorkSummary(
    val netMinutes: Int,
    val scheduledNetMinutes: Int,
    val overtimeMinutes: Int,
    val isWorkday: Boolean,
    val isInProgress: Boolean,
)
