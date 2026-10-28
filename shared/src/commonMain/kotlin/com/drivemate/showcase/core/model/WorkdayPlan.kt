package com.drivemate.showcase.core.model

import com.drivemate.showcase.core.util.TimeUtil
import kotlinx.datetime.TimeZone

enum class PlanSource {
    DEFAULT,
    EXPLICIT,
    ADHOC,
    WEEKEND,
    MANUAL_ADMIN;

    fun toRaw(): String = name.lowercase()

    companion object {
        fun fromRaw(raw: String?): PlanSource? {
            val normalized = raw?.trim()?.uppercase() ?: return null
            return entries.firstOrNull { it.name == normalized }
        }
    }
}

enum class PlanAuthor {
    ADMIN,
    EXPAT;

    fun toRaw(): String = name.lowercase()

    companion object {
        fun fromRaw(raw: String?): PlanAuthor? {
            val normalized = raw?.trim()?.uppercase() ?: return null
            return entries.firstOrNull { it.name == normalized }
        }
    }
}

data class WorkdayPlan(
    val driverUid: String,
    val dateEpochDay: Int,
    val plannedStartMillis: Long,
    val plannedEndMillis: Long,
    val source: PlanAuthor?,
    val createdAt: Long?,
    val createdByUid: String?,
    val note: String?
)

/** Planned window read straight from `workday_plans`, before any `workdays` doc exists for the day. */
data class ExplicitWorkdayPlanWindow(
    val plannedStartMillis: Long,
    val plannedEndMillis: Long
)

/** Best-effort schedule preview for the driver home card — never a hard error, only a preview. */
data class DriverTodaySchedule(
    val plannedStartMillis: Long?,
    val plannedEndMillis: Long?,
    val planSource: PlanSource?
)

enum class DeviationFlag {
    EARLY_UNPLANNED,
    LATE_UNPLANNED,
    WEEKEND_UNPLANNED,
    EXCESSIVE_OT;

    fun toRaw(): String = name.lowercase()

    companion object {
        fun fromRaw(raw: String?): DeviationFlag? {
            val normalized = raw?.trim()?.uppercase() ?: return null
            return entries.firstOrNull { it.name == normalized }
        }
    }
}

fun parseDeviationFlags(raw: List<String>?): List<DeviationFlag> {
    if (raw.isNullOrEmpty()) return emptyList()
    return raw.mapNotNull(DeviationFlag::fromRaw)
}

fun shouldHoldReview(flags: List<DeviationFlag>): Boolean {
    return flags.any {
        it == DeviationFlag.EARLY_UNPLANNED ||
            it == DeviationFlag.LATE_UNPLANNED ||
            it == DeviationFlag.EXCESSIVE_OT
    }
}

fun computeDeviationFlags(
    isWeekend: Boolean,
    planStartMillis: Long?,
    planEndMillis: Long?,
    clockInMillis: Long?,
    clockOutMillis: Long?,
    graceMinutes: Int = 15,
    thresholdMinutes: Int = 30
): List<DeviationFlag> {
    if (clockInMillis == null && clockOutMillis == null) return emptyList()

    if (isWeekend && planStartMillis == null && planEndMillis == null) {
        return listOf(DeviationFlag.WEEKEND_UNPLANNED)
    }

    val flags = mutableListOf<DeviationFlag>()
    val graceMs = graceMinutes * 60 * 1000L
    val thresholdMs = thresholdMinutes * 60 * 1000L

    if (planStartMillis != null && clockInMillis != null) {
        val earlyMs = planStartMillis - clockInMillis
        if (earlyMs >= thresholdMs && earlyMs > graceMs) {
            flags.add(DeviationFlag.EARLY_UNPLANNED)
        }
    }

    if (planEndMillis != null && clockOutMillis != null) {
        val lateMs = clockOutMillis - planEndMillis
        if (lateMs >= thresholdMs && lateMs > graceMs) {
            flags.add(DeviationFlag.LATE_UNPLANNED)
        }
    }

    return flags
}

/**
 * Jendela referensi jadwal untuk timeline attendance driver hari ini.
 * Kalau [DriverTodaySchedule] tidak punya plan (mis. belum ada [PlanSource.EXPLICIT]),
 * jatuh ke jam kerja standar ([WORK_START_HM]/[WORK_END_HM]) — satu sumber kebenaran
 * yang sama dipakai iOS lewat `defaultStartMillis`/`defaultEndMillis` di bridge snapshot.
 */
data class AttendanceReferenceWindow(
    val startMillis: Long,
    val endMillis: Long,
    val isExplicitPlan: Boolean
)

fun resolveAttendanceReferenceWindow(
    schedule: DriverTodaySchedule,
    epochDay: Int,
    zone: TimeZone = TimeZone.currentSystemDefault()
): AttendanceReferenceWindow {
    val start = schedule.plannedStartMillis
        ?: TimeUtil.parseHm(WORK_START_HM, epochDay.toLong(), zone)
    val end = schedule.plannedEndMillis
        ?: TimeUtil.parseHm(WORK_END_HM, epochDay.toLong(), zone)
    return AttendanceReferenceWindow(
        startMillis = start,
        endMillis = end,
        isExplicitPlan = schedule.plannedStartMillis != null && schedule.plannedEndMillis != null
    )
}

/**
 * Segmen 3-zona timeline attendance: lembur-awal (sebelum jendela referensi),
 * normal (di dalam jendela), lembur-akhir (setelah jendela). Setiap segmen
 * overtime hanya "ada" (menitnya > 0) kalau memang terjadi — tidak pernah
 * negatif, tidak pernah placeholder kosong yang tetap dirender.
 */
data class AttendanceTimelineZones(
    val referenceStartMillis: Long,
    val referenceEndMillis: Long,
    val earlyOvertimeMinutes: Int,
    val lateOvertimeMinutes: Int
) {
    val hasEarlyOvertime: Boolean get() = earlyOvertimeMinutes > 0
    val hasLateOvertime: Boolean get() = lateOvertimeMinutes > 0
}

fun computeAttendanceTimelineZones(
    referenceWindow: AttendanceReferenceWindow,
    clockInMillis: Long?,
    nowOrClockOutMillis: Long
): AttendanceTimelineZones {
    val earlyMinutes = if (clockInMillis != null && clockInMillis < referenceWindow.startMillis) {
        ((referenceWindow.startMillis - clockInMillis) / 60_000L).toInt()
    } else {
        0
    }
    val lateMinutes = if (nowOrClockOutMillis > referenceWindow.endMillis) {
        ((nowOrClockOutMillis - referenceWindow.endMillis) / 60_000L).toInt()
    } else {
        0
    }
    return AttendanceTimelineZones(
        referenceStartMillis = referenceWindow.startMillis,
        referenceEndMillis = referenceWindow.endMillis,
        earlyOvertimeMinutes = earlyMinutes,
        lateOvertimeMinutes = lateMinutes
    )
}
