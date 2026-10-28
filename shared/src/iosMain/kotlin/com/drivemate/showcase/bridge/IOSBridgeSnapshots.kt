package com.drivemate.showcase.bridge

import com.drivemate.showcase.core.model.Announcement
import com.drivemate.showcase.core.model.ApprovalStatus
import com.drivemate.showcase.core.model.Attendance
import com.drivemate.showcase.core.model.AttendanceDailyState
import com.drivemate.showcase.core.model.AttendanceEntry
import com.drivemate.showcase.core.model.AttendanceReferenceWindow
import com.drivemate.showcase.core.model.BulkActionResult
import com.drivemate.showcase.core.model.ChecklistDetail
import com.drivemate.showcase.core.model.ChecklistSummary
import com.drivemate.showcase.core.model.DriverTodaySchedule
import com.drivemate.showcase.core.model.Location
import com.drivemate.showcase.core.model.SafetyDetail
import com.drivemate.showcase.core.model.SafetySummary
import com.drivemate.showcase.core.model.TodayAttendance
import com.drivemate.showcase.core.model.TrackingStatus
import com.drivemate.showcase.core.model.UserSession
import com.drivemate.showcase.core.model.WorkDay
import com.drivemate.showcase.core.model.approvalStatus
import com.drivemate.showcase.core.model.endOfWorkMillis
import com.drivemate.showcase.core.model.isFinalizedByStatusOrActor
import com.drivemate.showcase.core.model.rejectionReasonOrFallback
import com.drivemate.showcase.core.model.startOfWorkMillis
import com.drivemate.showcase.core.model.toFirestore
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.domain.calendar.WorkCalendarYear
import com.drivemate.showcase.domain.calendar.WorkHistoryDay
import com.drivemate.showcase.domain.calendar.WorkHistoryMonthData
import com.drivemate.showcase.domain.calendar.resolveWorkSpecForDate
import com.drivemate.showcase.domain.workday.WorkdayDecisionRole
import com.drivemate.showcase.domain.workday.WorkdayDecisionStep
import com.drivemate.showcase.domain.workday.effectiveDecision
import com.drivemate.showcase.domain.workday.resolveDecisionTrail
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

internal val IOSDriverTimezone = TimeZone.of("Asia/Jakarta")

enum class IOSRoleResolutionState {
    IDLE,
    RESOLVING,
    RESOLVED,
    TRANSIENT_FAILURE,
    INVALID_ACCOUNT
}

data class IOSSessionSnapshot(
    val uid: String,
    val email: String,
    val roleRaw: String?
)

data class IOSRoleResolutionSnapshot(
    val stateRaw: String
)

data class IOSTrackingSnapshot(
    val hideLocationWhenOffDuty: Boolean
)

/**
 * R4 — today's planned start/end for the driver, mirrors Android's
 * `ScheduleCard` input. Times are pre-formatted (HH:mm, Asia/Jakarta) like every
 * other snapshot's `*Label` fields — Swift only branches on presence, it never
 * formats millis itself.
 */
data class IOSScheduleSnapshot(
    val plannedStartLabel: String?,
    val plannedEndLabel: String?,
    val planSourceRaw: String?
)

/**
 * R-Attendance — the Live Timer Hero timeline's reference window, mirrors
 * Android's `AttendanceReferenceWindow` (`WorkdayPlan.kt`) exactly. Raw millis,
 * not pre-formatted labels: unlike [IOSScheduleSnapshot], Swift needs these for
 * live per-second arithmetic (elapsed time, zone-boundary minutes), not just to
 * render a static string.
 */
data class IOSAttendanceReferenceWindowSnapshot(
    val startMillis: Long,
    val endMillis: Long,
    val isExplicitPlan: Boolean
)

data class IOSDriverWorkdaySnapshot(
    val id: String,
    val dateLabel: String,
    val totalMinutes: Int,
    val statusRaw: String,
    val clockInLabel: String?,
    val clockOutLabel: String?,
    val overtimeMinutes: Int,
    val rejectReason: String?,
    val hasChecklist: Boolean,
    val hasSafetyDeclaration: Boolean,
    val driverUid: String,
    val driverName: String?,
    // Nilai mentah di bawah ini dipakai dialog edit rencana kerja (Expat).
    // Label di atas sudah diformat untuk tampilan, jadi tidak bisa dipakai
    // untuk menghitung millis — dialog butuh epoch day dan millis asli.
    val dateEpochDay: Int,
    val clockInMillis: Long,
    val clockOutMillis: Long,
    val plannedStartMillis: Long?,
    val plannedEndMillis: Long?,
    // Fallback jam kerja standar (07:30/16:30) pada tanggal ini, dihitung di
    // Kotlin lewat WorkDay.startOfWorkMillis()/endOfWorkMillis() supaya iOS
    // tidak menduplikasi konstanta jam kerja.
    val defaultStartMillis: Long,
    val defaultEndMillis: Long,
    /**
     * Apakah hari kerja ini punya penyimpangan terhadap rencana.
     *
     * Dibawa sebagai Boolean, bukan daftar flag-nya: satu-satunya konsumen adalah baris
     * daftar, yang hanya menanyakan `deviationFlags.isNotEmpty()`
     * (`ExpatDriverDetailScreen.kt:293`). Mengekspos daftarnya berarti menambah bentuk data
     * yang tidak ada yang membaca, dan bentuk seperti itu berakhir tidak sinkron.
     */
    val hasDeviation: Boolean,
    /**
     * Apakah penyimpangannya cukup serius sehingga review ditahan.
     *
     * Menentukan WARNA chip temuan, bukan ada-tidaknya: Android memakai `Issue` saat ditahan
     * dan `Pending` saat tidak (`ExpatDriverDetailScreen.kt:294`). Penyimpangan yang ditahan
     * menunggu tindakan expat; yang tidak ditahan hanya perlu diketahui.
     */
    val reviewHold: Boolean
)

data class IOSAnnouncementSnapshot(
    val id: String,
    val title: String,
    val body: String,
    val authorLabel: String?,
    val dateLabel: String,
    val timeLabel: String
)

data class IOSAttendanceSnapshot(
    val dateLabel: String,
    val clockInLabel: String?,
    val clockOutLabel: String?,
    val statusRaw: String,
    val totalMinutes: Int,
    // Raw millis alongside the formatted labels above — the Live Timer Hero
    // (Attendance screen) needs these for live per-second arithmetic (elapsed
    // time, timeline zone fractions), which a formatted "HH:mm" string can't
    // support. Every other consumer of this snapshot keeps using the labels.
    val clockInMillis: Long?,
    val clockOutMillis: Long?
)

/**
 * One inspection item on the driver checklist form.
 *
 * Carries no answer: the only read path available for a driver's own checklist is
 * `ChecklistRepository.hasChecklist`, which returns a single Boolean for "was one
 * submitted today" and cannot return per-item answers. This class used to have an
 * `isOk` field that the bridge always set to `false`, and the form hydrated from
 * it — so a driver reopening a submitted checklist saw all 18 items marked
 * "Issue" regardless of what they had actually submitted. Do not reintroduce it
 * without a repository method that genuinely returns the stored answers.
 */
data class IOSChecklistItemSnapshot(
    val key: String,
    val label: String
)

data class IOSChecklistSnapshot(
    val id: String,
    val dateLabel: String,
    val isSubmitted: Boolean,
    val items: List<IOSChecklistItemSnapshot>
)

data class IOSSafetySnapshot(
    val dateLabel: String,
    val isSubmitted: Boolean,
    val declaredFit: Boolean,
    val notes: String?,
    val submittedAtLabel: String?
)

data class IOSWorkCalendarSnapshot(
    val year: Int,
    val workDays: List<String>,
    val holidays: List<IOSHolidaySnapshot>
)

data class IOSHolidaySnapshot(
    val date: String,
    /**
     * Catatan libur. **Nullable dengan sengaja** — libur perusahaan boleh tidak bercatatan,
     * dan sebelumnya entri seperti itu dibuang total sehingga hari liburnya hilang dari
     * kalender admin iOS. UI yang menyediakan label penggantinya, supaya teks user-visible
     * tetap terlokalkan.
     */
    val name: String?
)

data class IOSDriverLocationSnapshot(
    val uid: String,
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val updatedAtLabel: String,
    val statusRaw: String,
    /**
     * Millis mentah di samping label terformat di atas — mengikuti presedens
     * `IOSDriverWorkdaySnapshot`. Metrik "terpantau vs basi" butuh membandingkan usia
     * terhadap ambang, dan label "2026-08-17 14:30" tidak bisa dipakai untuk itu tanpa
     * mem-parsing ulang di Swift, yang berarti parser kedua yang bisa berbeda pendapat.
     */
    val updatedAtMillis: Long
)

/**
 * A driver in a roster list.
 *
 * Carries no vehicle: there is no vehicle field in the user/driver domain model
 * (`User`/`DriverProfile`), and the `vehicleNumber` this used to declare was
 * hardcoded to `null` by [IOSUserDirectoryBridge] on every path. Three SwiftUI
 * screens rendered it behind `if let`, so it was dead code that read as a real
 * feature. Do not reintroduce it without a backing domain field.
 */
data class IOSDriverProfileSnapshot(
    val uid: String,
    val displayName: String,
    val statusRaw: String
)

data class IOSChatMessageSnapshot(
    val id: String,
    val senderUid: String,
    val text: String,
    /**
     * Tanggal pesan, terpisah dari [timeLabel].
     *
     * Tanpa ini gelembung hanya menampilkan jam, dan percakapan yang membentang beberapa hari
     * terlihat seperti terjadi dalam satu hari — setiap gelembung berbunyi "08:14" tanpa cara
     * membedakan yang hari ini dari yang minggu lalu. Android sudah menampilkan tanggal+jam
     * (`ChatScreens.kt:278`).
     *
     * Dibawa terpisah, bukan digabung ke `timeLabel`: nama field harus tetap jujur, dan
     * pemisahnya urusan penyajian.
     */
    val dateLabel: String,
    val timeLabel: String,
    val isMine: Boolean
)

/**
 * A chat counterparty for the signed-in user's role, resolved via
 * `GetChatContactsUseCase` — the same path Android uses.
 *
 * Replaces the previous practice of feeding chat screens the *driver roster*
 * (`IOSDriverProfileSnapshot`), which offered every role thread pairings that
 * `firestore.rules:194-203` rejects. Carries [roleRaw] so the row can show a role
 * label the way Android does.
 */
data class IOSChatContactSnapshot(
    val uid: String,
    val displayName: String,
    val roleRaw: String,
    val email: String?
)

data class IOSChatThreadSnapshot(
    val threadId: String,
    val partnerUid: String,
    val partnerName: String,
    val lastMessagePreview: String,
    val lastTimeLabel: String,
    val unreadCount: Int
)

/**
 * SOP acceptance status for one driver.
 *
 * Deliberately carries no version field: the domain model only records
 * `sopAccepted` + `sopAcceptedAtMillis` ([com.drivemate.showcase.core.model]
 * `AdminData.kt`), and Android surfaces no SOP version on any screen. A previous
 * iOS-only `CURRENT_SOP_VERSION = "1.0"` constant was rendered as if it were real
 * data — do not reintroduce it without an actual backing field.
 */
data class IOSSopStatusSnapshot(
    val requiresAcceptance: Boolean,
    val acceptedDateLabel: String?
)

/**
 * Outcome of a bulk approve/reject run.
 *
 * A bulk action is deliberately NOT all-or-nothing: the repository walks the ids and
 * reports per-item outcomes, so a run can partly succeed. Surfacing
 * [skippedCount]/[failedCount] separately matters — "skipped" means the workday was
 * not in a state that can be decided (already final), while "failed" means the write
 * itself did not land.
 */
data class IOSBulkActionResultSnapshot(
    val attemptedCount: Int,
    val successCount: Int,
    val skippedCount: Int,
    val failedCount: Int
)

data class IOSAdminAttendanceSnapshot(
    val id: String,
    val driverUid: String,
    val dateLabel: String,
    val timeLabel: String,
    val typeRaw: String,
    val locationName: String?,
    /**
     * Epoch-day mentah di samping label. Menghitung aktivitas per hari butuh perbandingan
     * numerik; mem-parsing ulang "2026-08-17" di Swift berarti parser kedua yang bisa
     * berbeda pendapat. Presedens: `IOSDriverWorkdaySnapshot`.
     */
    val dateEpochDay: Int,
    /** Millis mentah — feed diurutkan numerik, dan label jam tidak bisa dipakai untuk itu. */
    val timestampMillis: Long
)

data class IOSAdminChecklistSummarySnapshot(
    val id: String,
    val driverUid: String,
    val driverName: String?,
    val dateLabel: String,
    val submittedAtLabel: String?,
    val hasIssue: Boolean,
    /**
     * Kode mentah status tindak lanjut (Blok 4.1): "new"/"reviewed"/"closed", null bila
     * dokumen lama tanpa field. Daftar pengecualian memakainya supaya temuan yang sudah
     * ditutup berhenti menagih — tanpanya daftar menampilkan hal yang sudah selesai.
     */
    val followUpStatusRaw: String?,
    /**
     * Epoch-day mentah di samping label. Menghitung aktivitas per hari butuh perbandingan
     * numerik; mem-parsing ulang "2026-08-17" di Swift berarti parser kedua yang bisa
     * berbeda pendapat. Presedens: `IOSDriverWorkdaySnapshot`.
     */
    val dateEpochDay: Int,
    /** Millis mentah waktu submit — untuk pengurutan feed. 0 bila belum disubmit. */
    val submittedAtMillis: Long
)

data class IOSAdminSafetySummarySnapshot(
    val id: String,
    val driverUid: String,
    val driverName: String?,
    val dateLabel: String,
    val submittedAtLabel: String?,
    val fitToWork: Boolean,
    val confirmed: Boolean,
    /**
     * Epoch-day mentah di samping label. Menghitung aktivitas per hari butuh perbandingan
     * numerik; mem-parsing ulang "2026-08-17" di Swift berarti parser kedua yang bisa
     * berbeda pendapat. Presedens: `IOSDriverWorkdaySnapshot`.
     */
    val dateEpochDay: Int,
    /** Millis mentah waktu submit — untuk pengurutan feed. 0 bila belum disubmit. */
    val submittedAtMillis: Long
)

data class IOSAdminLocationSnapshot(
    val driverUid: String,
    val latitude: Double?,
    val longitude: Double?,
    val updatedAtLabel: String,
    val locationName: String?,
    /** Millis mentah — aturan perhatian "lokasi basi" butuh perbandingan numerik. */
    val updatedAtMillis: Long
)

data class IOSAdminChecklistCheckItem(
    val labelKey: String,
    val stateRaw: String
)

data class IOSAdminChecklistDetailSnapshot(
    val id: String,
    val driverName: String?,
    val driverEmail: String?,
    val dateLabel: String,
    val submittedAtLabel: String?,
    val hasIssue: Boolean,
    val vehicleId: String?,
    val odometerKm: Int?,
    val fuelLevel: String?,
    val tirePressureNote: String?,
    val notes: String?,
    val checks: List<IOSAdminChecklistCheckItem>,
    // Blok 4.1 — tindak lanjut temuan.
    /** Kode mentah "new"/"reviewed"/"closed". */
    val followUpStatusRaw: String,
    val followUpOwnerUid: String?,
    val followUpNote: String?,
    val followUpUpdatedAtLabel: String?
)

data class IOSAdminSafetyCheckItem(
    val labelKey: String,
    val isChecked: Boolean
)

data class IOSAdminSafetyDetailSnapshot(
    val id: String,
    val driverName: String?,
    val driverEmail: String?,
    val dateLabel: String,
    val submittedAtLabel: String?,
    val fitToWork: Boolean,
    val confirmed: Boolean,
    val sleepHours: Int?,
    val hazardAwareness: String?,
    val weatherCondition: String?,
    val routeRiskNote: String?,
    val incidentReport: String?,
    val checks: List<IOSAdminSafetyCheckItem>
)

data class IOSAdminWorkdayDetailSnapshot(
    val id: String,
    val driverUid: String,
    val driverName: String?,
    val dateLabel: String,
    val statusRaw: String,
    val clockInLabel: String?,
    val clockOutLabel: String?,
    val totalMinutes: Int,
    val overtimeMinutes: Int,
    val hasChecklist: Boolean,
    val hasSafetyDeclaration: Boolean,
    val checklistSubmittedAtLabel: String?,
    val safetySubmittedAtLabel: String?,
    val rejectReason: String?,
    val approvalNote: String?,
    val approverName: String?,
    val approvedAtLabel: String?,
    val rejectedAtLabel: String?,
    val adjustedBy: String?,
    val adjustedReason: String?,
    val adjustedAtLabel: String?,
    val reviewHold: Boolean,
    /**
     * True once the workday carries a final decision (status approved/rejected, or
     * an approver/rejecter uid). Admin override is only offered for finalized
     * workdays — a pending one is approved/rejected normally, not overridden.
     *
     * Computed in Kotlin via `WorkDay.isFinalizedByStatusOrActor()` so the rule
     * stays in `commonMain` and Swift never re-derives it.
     */
    val isFinalized: Boolean,
    // ── Blok 4.2 — jejak keputusan dua langkah ──────────────────────────────
    val trailSupervisorStep: IOSAdminWorkdayTrailStepSnapshot?,
    val trailAdminStep: IOSAdminWorkdayTrailStepSnapshot?,
    /** Langkah 1 disimpulkan dari field lama (bukan slot-nya) — nama bisa tertimpa. */
    val trailSupervisorInferred: Boolean,
    /** "agree" / "disagree" / null (belum kedua-duanya tercatat). */
    val trailAgreementRaw: String?,
    /** Langkah 2 tertinggal dari keputusan dokumen sekarang (override tanpa jejak). */
    val trailAdminStale: Boolean
)

/**
 * Satu langkah jejak keputusan, siap dirender SwiftUI.
 * `atLabel` sudah diformat di Asia/Jakarta; `byUid` MENTAH — nama pelaku diselesaikan
 * Swift lewat roster (Android melakukan hal yang sama).
 */
data class IOSAdminWorkdayTrailStepSnapshot(
    /** "supervisor" / "admin". */
    val roleRaw: String,
    /** "approved" / "rejected". */
    val decisionRaw: String,
    val byUid: String,
    val atLabel: String?,
    val note: String?
)

internal fun BulkActionResult.toIosSnapshot(): IOSBulkActionResultSnapshot =
    IOSBulkActionResultSnapshot(
        attemptedCount = attemptedCount,
        successCount = successCount,
        skippedCount = skippedCount,
        failedCount = failedCount
    )

internal fun UserSession.toIosSnapshot(): IOSSessionSnapshot {
    val safeEmail = email?.trim().orEmpty()
    return IOSSessionSnapshot(
        uid = uid,
        email = safeEmail,
        roleRaw = role?.toFirestore()
    )
}

internal fun IOSRoleResolutionState.toIosSnapshot(): IOSRoleResolutionSnapshot =
    IOSRoleResolutionSnapshot(
        stateRaw = when (this) {
            IOSRoleResolutionState.IDLE -> "idle"
            IOSRoleResolutionState.RESOLVING -> "resolving"
            IOSRoleResolutionState.RESOLVED -> "resolved"
            IOSRoleResolutionState.TRANSIENT_FAILURE -> "transient_failure"
            IOSRoleResolutionState.INVALID_ACCOUNT -> "invalid_account"
        }
    )

internal fun TrackingStatus.toIosSnapshot(): IOSTrackingSnapshot =
    IOSTrackingSnapshot(
        hideLocationWhenOffDuty = hideLocationWhenOffDuty
    )

internal fun DriverTodaySchedule.toIosSnapshot(): IOSScheduleSnapshot =
    IOSScheduleSnapshot(
        plannedStartLabel = plannedStartMillis?.let { TimeUtil.formatHm(it, IOSDriverTimezone) },
        plannedEndLabel = plannedEndMillis?.let { TimeUtil.formatHm(it, IOSDriverTimezone) },
        planSourceRaw = planSource?.toRaw()
    )

internal fun AttendanceReferenceWindow.toIosSnapshot(): IOSAttendanceReferenceWindowSnapshot =
    IOSAttendanceReferenceWindowSnapshot(
        startMillis = startMillis,
        endMillis = endMillis,
        isExplicitPlan = isExplicitPlan
    )

internal fun WorkDay.toIosSnapshot(): IOSDriverWorkdaySnapshot {
    val dateLabel = TimeUtil.formatYmdFromEpochDay(dateEpochDay, IOSDriverTimezone)
    val statusRaw = when (approvalStatus()) {
        ApprovalStatus.APPROVED -> "approved"
        ApprovalStatus.REJECTED -> "rejected"
        ApprovalStatus.PENDING -> "pending"
    }
    val clockInLabel = clockInMillis.takeIf { it > 0L }
        ?.let { TimeUtil.formatHm(it, IOSDriverTimezone) }
    val clockOutLabel = clockOutMillis.takeIf { it > 0L }
        ?.let { TimeUtil.formatHm(it, IOSDriverTimezone) }
    return IOSDriverWorkdaySnapshot(
        id = id,
        dateLabel = dateLabel,
        totalMinutes = totalMinutes.coerceAtLeast(0),
        statusRaw = statusRaw,
        clockInLabel = clockInLabel,
        clockOutLabel = clockOutLabel,
        overtimeMinutes = overtimeMinutes.coerceAtLeast(0),
        rejectReason = rejectionReasonOrFallback(),
        hasChecklist = hasChecklist == true,
        hasSafetyDeclaration = hasSafetyDeclaration == true,
        driverUid = driverUid,
        driverName = driverName?.trim()?.takeIf { it.isNotEmpty() },
        dateEpochDay = dateEpochDay,
        clockInMillis = clockInMillis,
        clockOutMillis = clockOutMillis,
        plannedStartMillis = plannedStartMillis,
        plannedEndMillis = plannedEndMillis,
        defaultStartMillis = startOfWorkMillis(),
        defaultEndMillis = endOfWorkMillis(),
        hasDeviation = deviationFlags.isNotEmpty(),
        reviewHold = reviewHold
    )
}

internal fun Announcement.toIosSnapshot(): IOSAnnouncementSnapshot {
    val dateLabel = TimeUtil.formatYmdFromEpochDay(
        epochDay = TimeUtil.epochDayOf(createdAtMillis, IOSDriverTimezone).toInt(),
        zone = IOSDriverTimezone
    )
    val timeLabel = TimeUtil.formatHm(createdAtMillis, IOSDriverTimezone)
    val authorLabel = createdByName?.trim()?.takeIf { it.isNotBlank() }
        ?: createdByEmail?.substringBefore("@")?.trim()?.takeIf { it.isNotBlank() }
    return IOSAnnouncementSnapshot(
        id = id,
        title = title,
        body = body,
        authorLabel = authorLabel,
        dateLabel = dateLabel,
        timeLabel = timeLabel
    )
}

internal fun TodayAttendance.toIosSnapshot(): IOSAttendanceSnapshot {
    val dateLabel = TimeUtil.formatYmdFromEpochDay(epochDay.toInt(), IOSDriverTimezone)
    val clockInLabel = clockInMillis?.takeIf { it > 0L }?.let { TimeUtil.formatHm(it, IOSDriverTimezone) }
    val clockOutLabel = clockOutMillis?.takeIf { it > 0L }?.let { TimeUtil.formatHm(it, IOSDriverTimezone) }
    val totalMinutes = if (clockInMillis != null && clockOutMillis != null) {
        ((clockOutMillis - clockInMillis) / 60000L).toInt().coerceAtLeast(0)
    } else 0
    val statusRaw = when (state) {
        AttendanceDailyState.NOT_STARTED -> "not_started"
        AttendanceDailyState.OPEN -> "open"
        AttendanceDailyState.CLOSED -> "closed"
    }
    return IOSAttendanceSnapshot(
        dateLabel = dateLabel,
        clockInLabel = clockInLabel,
        clockOutLabel = clockOutLabel,
        statusRaw = statusRaw,
        totalMinutes = totalMinutes,
        clockInMillis = clockInMillis?.takeIf { it > 0L },
        clockOutMillis = clockOutMillis?.takeIf { it > 0L }
    )
}

internal fun List<Attendance>.toDailySnapshots(): List<IOSAttendanceSnapshot> {
    val zone = IOSDriverTimezone
    val grouped = groupBy { TimeUtil.epochDayOf(it.timestampMillis, zone) }
    return grouped.entries.sortedByDescending { it.key }.map { (epochDay, events) ->
        val inEvent = events.firstOrNull { it.note != "OUT" }
        val outEvent = events.firstOrNull { it.note == "OUT" }
        val dateLabel = TimeUtil.formatYmdFromEpochDay(epochDay.toInt(), zone)
        val clockInLabel = inEvent?.let { TimeUtil.formatHm(it.timestampMillis, zone) }
        val clockOutLabel = outEvent?.let { TimeUtil.formatHm(it.timestampMillis, zone) }
        val totalMinutes = if (inEvent != null && outEvent != null) {
            ((outEvent.timestampMillis - inEvent.timestampMillis) / 60000L).toInt().coerceAtLeast(0)
        } else 0
        IOSAttendanceSnapshot(
            dateLabel = dateLabel,
            clockInLabel = clockInLabel,
            clockOutLabel = clockOutLabel,
            statusRaw = when {
                inEvent == null -> "not_started"
                outEvent == null -> "open"
                else -> "closed"
            },
            totalMinutes = totalMinutes,
            clockInMillis = inEvent?.timestampMillis,
            clockOutMillis = outEvent?.timestampMillis
        )
    }
}

internal fun WorkCalendarYear.toIosSnapshot(): IOSWorkCalendarSnapshot {
    val startEpochDay = kotlinx.datetime.LocalDate(year, 1, 1).toEpochDays()
    val endExclusiveEpochDay = kotlinx.datetime.LocalDate(year + 1, 1, 1).toEpochDays()

    val workDays = buildList {
        for (epochDay in startEpochDay until endExclusiveEpochDay) {
            if (resolveWorkSpecForDate(this@toIosSnapshot, epochDay.toInt()) != null) {
                add(kotlinx.datetime.LocalDate.fromEpochDays(epochDay).toString())
            }
        }
    }

    // `map`, BUKAN `mapNotNull` — dan itu perbaikan bug, bukan preferensi gaya.
    //
    // Versi lama membungkus seluruh entri di dalam `override.note?...?.let { }`, jadi libur
    // perusahaan yang catatannya kosong DIBUANG DIAM-DIAM. Di layar Admin Work Calendar iOS
    // hari itu lalu tampil seperti akhir pekan biasa (tetap bukan hari kerja, tapi tidak
    // ditandai libur) dan ikut hilang dari hitungan libur. Android menandai libur dari
    // `!item.isWorkday` saja (`AdminWorkCalendarScreen.kt:1068`) — catatannya tidak pernah
    // jadi syarat. Ini divergensi yang menghilangkan data, bukan perbedaan tampilan.
    //
    // `name` sengaja nullable: label penggantinya adalah teks yang dilihat user, jadi
    // dilokalkan di UI, bukan di-hardcode di shared layer.
    val holidays = overrides
        .asSequence()
        .filter { !it.isWorkday }
        .map { override ->
            IOSHolidaySnapshot(
                date = override.date.toString(),
                name = override.note?.trim()?.takeIf { it.isNotEmpty() }
            )
        }
        .sortedBy { it.date }
        .toList()

    return IOSWorkCalendarSnapshot(
        year = year,
        workDays = workDays,
        holidays = holidays
    )
}

internal fun IOSLocationPayload.toIosSnapshot(): IOSDriverLocationSnapshot {
    val epochDay = TimeUtil.epochDayOf(updatedAtMs, IOSDriverTimezone).toInt()
    val dateLabel = TimeUtil.formatYmdFromEpochDay(epochDay, IOSDriverTimezone)
    val timeLabel = TimeUtil.formatHm(updatedAtMs, IOSDriverTimezone)
    return IOSDriverLocationSnapshot(
        uid = uid,
        displayName = displayName,
        latitude = latitude,
        longitude = longitude,
        updatedAtLabel = "$dateLabel $timeLabel",
        statusRaw = statusRaw,
        updatedAtMillis = updatedAtMs
    )
}

private fun submittedAtLabelOrNull(submittedAt: Long): String? =
    submittedAt.takeIf { it > 0L }?.let { millis ->
        val epochDay = TimeUtil.epochDayOf(millis, IOSDriverTimezone).toInt()
        val dateLabel = TimeUtil.formatYmdFromEpochDay(epochDay, IOSDriverTimezone)
        val timeLabel = TimeUtil.formatHm(millis, IOSDriverTimezone)
        "$dateLabel $timeLabel"
    }

internal fun AttendanceEntry.toIosAdminSnapshot(): IOSAdminAttendanceSnapshot {
    val epochDay = TimeUtil.epochDayOf(timestampMillis, IOSDriverTimezone).toInt()
    return IOSAdminAttendanceSnapshot(
        id = id,
        driverUid = driverUid,
        dateLabel = TimeUtil.formatYmdFromEpochDay(epochDay, IOSDriverTimezone),
        timeLabel = TimeUtil.formatHm(timestampMillis, IOSDriverTimezone),
        typeRaw = type,
        locationName = locationName?.trim()?.takeIf { it.isNotEmpty() },
        dateEpochDay = epochDay,
        timestampMillis = timestampMillis
    )
}

internal fun ChecklistSummary.toIosAdminSnapshot(): IOSAdminChecklistSummarySnapshot =
    IOSAdminChecklistSummarySnapshot(
        id = id,
        driverUid = driverUid,
        driverName = driverName?.trim()?.takeIf { it.isNotEmpty() },
        dateLabel = TimeUtil.formatYmdFromEpochDay(dateEpochDay, IOSDriverTimezone),
        submittedAtLabel = submittedAtLabelOrNull(submittedAt),
        hasIssue = hasIssue,
        followUpStatusRaw = followUpStatus.code,
        dateEpochDay = dateEpochDay,
        submittedAtMillis = submittedAt
    )

internal fun SafetySummary.toIosAdminSnapshot(): IOSAdminSafetySummarySnapshot =
    IOSAdminSafetySummarySnapshot(
        id = id,
        driverUid = driverUid,
        driverName = driverName?.trim()?.takeIf { it.isNotEmpty() },
        dateLabel = TimeUtil.formatYmdFromEpochDay(dateEpochDay, IOSDriverTimezone),
        submittedAtLabel = submittedAtLabelOrNull(submittedAt),
        fitToWork = fitToWork,
        confirmed = confirmed,
        dateEpochDay = dateEpochDay,
        submittedAtMillis = submittedAt
    )

internal fun Location.toIosAdminSnapshot(): IOSAdminLocationSnapshot {
    val epochDay = TimeUtil.epochDayOf(updatedAtMillis, IOSDriverTimezone).toInt()
    val dateLabel = TimeUtil.formatYmdFromEpochDay(epochDay, IOSDriverTimezone)
    val timeLabel = TimeUtil.formatHm(updatedAtMillis, IOSDriverTimezone)
    return IOSAdminLocationSnapshot(
        driverUid = driverUid,
        latitude = latitude,
        longitude = longitude,
        updatedAtLabel = "$dateLabel $timeLabel",
        locationName = locationName?.trim()?.takeIf { it.isNotEmpty() },
        updatedAtMillis = updatedAtMillis
    )
}

private fun dateTimeLabelOrNull(millis: Long?): String? =
    millis?.takeIf { it > 0L }?.let { value ->
        val epochDay = TimeUtil.epochDayOf(value, IOSDriverTimezone).toInt()
        val dateLabel = TimeUtil.formatYmdFromEpochDay(epochDay, IOSDriverTimezone)
        val timeLabel = TimeUtil.formatHm(value, IOSDriverTimezone)
        "$dateLabel $timeLabel"
    }

private fun triStateRaw(value: Boolean?): String = when (value) {
    true -> "ok"
    false -> "issue"
    null -> "na"
}

internal fun ChecklistDetail.toIosAdminDetailSnapshot(): IOSAdminChecklistDetailSnapshot {
    val c = checklist
    val checks = listOf(
        IOSAdminChecklistCheckItem("ios_checklist_item_brakes", triStateRaw(c.brakesOk)),
        IOSAdminChecklistCheckItem("ios_checklist_item_lights", triStateRaw(c.lightsOk)),
        // Urutan mengikuti AdminChecklistDetailScreen.kt Android: turn-signal tepat
        // sesudah lights (keduanya soal lampu), washer fluid sesudah wipers.
        IOSAdminChecklistCheckItem(
            "ios_checklist_item_turn_signal_brake_lights",
            triStateRaw(c.turnSignalBrakeLightsOk)
        ),
        IOSAdminChecklistCheckItem("ios_checklist_item_horn", triStateRaw(c.hornOk)),
        IOSAdminChecklistCheckItem("ios_checklist_item_wipers", triStateRaw(c.wipersOk)),
        IOSAdminChecklistCheckItem(
            "ios_checklist_item_washer_fluid",
            triStateRaw(c.washerFluidOk)
        ),
        IOSAdminChecklistCheckItem("ios_checklist_item_mirrors", triStateRaw(c.mirrorsOk)),
        IOSAdminChecklistCheckItem("ios_checklist_item_seatbelt", triStateRaw(c.seatbeltOk)),
        IOSAdminChecklistCheckItem("ios_checklist_item_engine_oil", triStateRaw(c.engineOilOk)),
        IOSAdminChecklistCheckItem("ios_checklist_item_coolant", triStateRaw(c.coolantOk)),
        IOSAdminChecklistCheckItem("ios_checklist_item_battery", triStateRaw(c.batteryOk)),
        IOSAdminChecklistCheckItem("ios_checklist_item_cleanliness", triStateRaw(c.cleanlinessOk)),
        IOSAdminChecklistCheckItem("ios_checklist_item_documents", triStateRaw(c.documentsOk)),
        IOSAdminChecklistCheckItem("ios_checklist_item_fire_extinguisher", triStateRaw(c.fireExtinguisherOk)),
        IOSAdminChecklistCheckItem("ios_checklist_item_first_aid", triStateRaw(c.firstAidOk)),
        IOSAdminChecklistCheckItem("ios_checklist_item_warning_triangle", triStateRaw(c.warningTriangleOk)),
        IOSAdminChecklistCheckItem("ios_checklist_item_tires_fl", triStateRaw(c.tiresFrontLeftOk)),
        IOSAdminChecklistCheckItem("ios_checklist_item_tires_fr", triStateRaw(c.tiresFrontRightOk)),
        IOSAdminChecklistCheckItem("ios_checklist_item_tires_rl", triStateRaw(c.tiresRearLeftOk)),
        IOSAdminChecklistCheckItem("ios_checklist_item_tires_rr", triStateRaw(c.tiresRearRightOk))
    )
    return IOSAdminChecklistDetailSnapshot(
        id = id,
        driverName = driverName?.trim()?.takeIf { it.isNotEmpty() },
        driverEmail = driverEmail?.trim()?.takeIf { it.isNotEmpty() },
        dateLabel = TimeUtil.formatYmdFromEpochDay(dateEpochDay, IOSDriverTimezone),
        submittedAtLabel = dateTimeLabelOrNull(submittedAt),
        hasIssue = c.hasIssue,
        vehicleId = c.vehicleId?.trim()?.takeIf { it.isNotEmpty() },
        odometerKm = c.odometerKm,
        fuelLevel = c.fuelLevel?.trim()?.takeIf { it.isNotEmpty() },
        tirePressureNote = c.tirePressureNote?.trim()?.takeIf { it.isNotEmpty() },
        notes = c.notes?.trim()?.takeIf { it.isNotEmpty() },
        checks = checks,
        followUpStatusRaw = followUp.status.code,
        followUpOwnerUid = followUp.ownerUid?.trim()?.takeIf { it.isNotEmpty() },
        followUpNote = followUp.note?.trim()?.takeIf { it.isNotEmpty() },
        followUpUpdatedAtLabel = dateTimeLabelOrNull(followUp.updatedAtMillis)
    )
}

internal fun SafetyDetail.toIosAdminDetailSnapshot(): IOSAdminSafetyDetailSnapshot {
    val d = declaration
    val checks = listOf(
        IOSAdminSafetyCheckItem("ios_admin_safety_detail_no_alcohol", d.noAlcoholOrDrugs),
        IOSAdminSafetyCheckItem("ios_admin_safety_detail_no_stress", d.noSevereStress),
        IOSAdminSafetyCheckItem("ios_admin_safety_detail_speed_limit", d.willFollowSpeedLimit),
        IOSAdminSafetyCheckItem("ios_admin_safety_detail_rest_breaks", d.willFollowRestBreaks),
        IOSAdminSafetyCheckItem("ios_admin_safety_detail_no_phone", d.willNotUsePhoneWhileDriving),
        IOSAdminSafetyCheckItem("ios_admin_safety_detail_seatbelt", d.willWearSeatbelt)
    )
    return IOSAdminSafetyDetailSnapshot(
        id = id,
        driverName = driverName?.trim()?.takeIf { it.isNotEmpty() },
        driverEmail = driverEmail?.trim()?.takeIf { it.isNotEmpty() },
        dateLabel = TimeUtil.formatYmdFromEpochDay(dateEpochDay, IOSDriverTimezone),
        submittedAtLabel = dateTimeLabelOrNull(submittedAt),
        fitToWork = d.fitToWork,
        confirmed = d.confirmed,
        sleepHours = d.sleepHours,
        hazardAwareness = d.hazardAwareness?.trim()?.takeIf { it.isNotEmpty() },
        weatherCondition = d.weatherCondition?.trim()?.takeIf { it.isNotEmpty() },
        routeRiskNote = d.routeRiskNote?.trim()?.takeIf { it.isNotEmpty() },
        incidentReport = d.incidentReport?.trim()?.takeIf { it.isNotEmpty() },
        checks = checks
    )
}

internal fun WorkDay.toIosAdminDetailSnapshot(): IOSAdminWorkdayDetailSnapshot {
    val statusRaw = when (approvalStatus()) {
        ApprovalStatus.APPROVED -> "approved"
        ApprovalStatus.REJECTED -> "rejected"
        ApprovalStatus.PENDING -> "pending"
    }
    // Jejak dihitung SEKALI supaya ketiga field trail membaca keadaan yang sama.
    val trail = resolveDecisionTrail()
    return IOSAdminWorkdayDetailSnapshot(
        id = id,
        driverUid = driverUid,
        driverName = driverName?.trim()?.takeIf { it.isNotEmpty() },
        dateLabel = TimeUtil.formatYmdFromEpochDay(dateEpochDay, IOSDriverTimezone),
        statusRaw = statusRaw,
        clockInLabel = clockInMillis.takeIf { it > 0L }?.let { TimeUtil.formatHm(it, IOSDriverTimezone) },
        clockOutLabel = clockOutMillis.takeIf { it > 0L }?.let { TimeUtil.formatHm(it, IOSDriverTimezone) },
        totalMinutes = totalMinutes.coerceAtLeast(0),
        overtimeMinutes = overtimeMinutes.coerceAtLeast(0),
        hasChecklist = hasChecklist == true,
        hasSafetyDeclaration = hasSafetyDeclaration == true,
        checklistSubmittedAtLabel = dateTimeLabelOrNull(checklistSubmittedAt),
        safetySubmittedAtLabel = dateTimeLabelOrNull(safetySubmittedAt),
        rejectReason = rejectionReasonOrFallback(),
        approvalNote = approvalNote?.trim()?.takeIf { it.isNotEmpty() },
        approverName = approverName?.trim()?.takeIf { it.isNotEmpty() },
        approvedAtLabel = dateTimeLabelOrNull(approvedAtMillis),
        rejectedAtLabel = dateTimeLabelOrNull(rejectedAtMillis),
        adjustedBy = adjustedBy?.trim()?.takeIf { it.isNotEmpty() },
        adjustedReason = adjustedReason?.trim()?.takeIf { it.isNotEmpty() },
        adjustedAtLabel = dateTimeLabelOrNull(adjustedAt),
        reviewHold = reviewHold,
        isFinalized = isFinalizedByStatusOrActor(),
        trailSupervisorStep = trail.supervisorStep?.toIosTrailSnapshot(),
        trailAdminStep = trail.adminStep?.toIosTrailSnapshot(),
        trailSupervisorInferred = trail.supervisorStepIsInferred,
        trailAgreementRaw = trail.adminAgreesWithSupervisor?.let { if (it) "agree" else "disagree" },
        trailAdminStale = trail.adminStepIsStale(effectiveDecision())
    )
}

private fun WorkdayDecisionStep.toIosTrailSnapshot(): IOSAdminWorkdayTrailStepSnapshot =
    IOSAdminWorkdayTrailStepSnapshot(
        roleRaw = when (role) {
            WorkdayDecisionRole.SUPERVISOR -> "supervisor"
            WorkdayDecisionRole.ADMIN -> "admin"
        },
        decisionRaw = decision.code,
        byUid = actorUid,
        atLabel = atMillis?.let { dateTimeLabelOrNull(it) },
        note = note?.trim()?.takeIf { it.isNotEmpty() }
    )

// ============================================================================
// Riwayat Kerja — Agenda View (padanan iOS untuk PR #235)
// ============================================================================

/**
 * Satu bulan Riwayat Kerja, siap dirender SwiftUI.
 *
 * ## Pembagian kerja Kotlin vs Swift — disengaja, jangan digeser
 * Yang Swift **hitung** tidak bisa diverifikasi di mesin Linux; yang Swift **render**
 * bisa dinilai mata di simulator. Jadi semua yang berupa perhitungan ada di sini:
 *  - [leadingBlankCount] — offset grid Senin-dulu, dihitung `leadingBlankCountForMonth`.
 *    Kalau ini dihitung di Swift, `Calendar.current.firstWeekday` (default MINGGU di
 *    en_US) menggeser seluruh grid satu kolom tanpa terlihat salah.
 *  - `clockInLabel`/`clockOutLabel` — "HH:mm" sudah diformat di zona Asia/Jakarta.
 *  - `stateRaw`/`calendarKindRaw` — string, mengikuti presedens `statusRaw`.
 *
 * Yang TIDAK diformat di sini, sengaja, karena Android juga tidak:
 *  - menit tetap `Int` mentah; teks jam dibentuk di UI dari format string terlokalisasi
 *    (Android: `driver_history_hours_format`) supaya urutan angka/satuan ikut locale.
 *  - nama hari & bulan diformat di UI dengan API tanggal sistem — Android memakai
 *    `DayOfWeek.getDisplayName(FULL, locale)`, jadi iOS memakai `DateFormatter`.
 *    Locale Jepang otomatis dapat kanji tunggal tanpa tabel string terpisah.
 */
data class IOSWorkHistoryMonthSnapshot(
    val year: Int,
    val month: Int,
    val days: List<IOSWorkHistoryDaySnapshot>,
    val summary: IOSWorkHistoryMonthSummarySnapshot,
    /** Sel kosong sebelum tanggal 1 pada grid Senin-dulu. Senin -> 0, Minggu -> 6. */
    val leadingBlankCount: Int,
    val todayEpochDay: Int
)

/**
 * Satu baris agenda.
 *
 * `dateEpochDay` dibawa mentah karena UI butuh memformat nama hari sendiri (lihat
 * catatan locale di [IOSWorkHistoryMonthSnapshot]) dan memakainya sebagai identitas
 * baris yang di-expand. **Peringatan untuk sisi Swift:** `epochDay * 86400` adalah
 * tengah malam UTC — `DateFormatter` dengan timezone default (`.current`) akan
 * merender nama hari SEBELUMNYA di perangkat mana pun di barat UTC. Pin timezone-nya.
 */
data class IOSWorkHistoryDaySnapshot(
    val dateEpochDay: Int,
    /** Nilai `WorkHistoryDayState`: WORKED / OFF_WEEKEND / OFF_COMPANY_HOLIDAY / SCHEDULED_ABSENT / FUTURE. */
    val stateRaw: String,
    /** Nilai `DayCalendarKind`: SCHEDULED_WORKDAY / WEEKEND / COMPANY_HOLIDAY. */
    val calendarKindRaw: String,
    /** Tanggal dalam bulan (1..31) — dipakai sel kalender mini, hindari hitung ulang di Swift. */
    val dayOfMonth: Int,
    val clockInLabel: String?,
    val clockOutLabel: String?,
    val normalMinutes: Int,
    val overtimeMinutes: Int,
    val totalMinutes: Int,
    /** true kalau hari itu punya dokumen `workdays` — beda dari `stateRaw == "WORKED"` hanya pada FUTURE. */
    val hasWorkRecord: Boolean,
    val hasChecklist: Boolean,
    val hasSafetyDeclaration: Boolean,
    /** Ada breakdown menit yang layak digambar sebagai bar dua-warna. */
    val hasMinutes: Boolean
)

data class IOSWorkHistoryMonthSummarySnapshot(
    val totalWorkedMinutes: Int,
    val totalOvertimeMinutes: Int,
    val workedDayCount: Int,
    val scheduledWorkdayCount: Int
)

internal fun WorkHistoryMonthData.toIosSnapshot(): IOSWorkHistoryMonthSnapshot =
    IOSWorkHistoryMonthSnapshot(
        year = year,
        month = month,
        days = days.map { it.toIosSnapshot() },
        summary = IOSWorkHistoryMonthSummarySnapshot(
            totalWorkedMinutes = summary.totalWorkedMinutes.coerceAtLeast(0),
            totalOvertimeMinutes = summary.totalOvertimeMinutes.coerceAtLeast(0),
            workedDayCount = summary.workedDayCount.coerceAtLeast(0),
            scheduledWorkdayCount = summary.scheduledWorkdayCount.coerceAtLeast(0)
        ),
        leadingBlankCount = leadingBlankCount,
        todayEpochDay = todayEpochDay
    )

internal fun WorkHistoryDay.toIosSnapshot(): IOSWorkHistoryDaySnapshot {
    val wd = workDay
    return IOSWorkHistoryDaySnapshot(
        dateEpochDay = dateEpochDay,
        stateRaw = state.name,
        calendarKindRaw = calendarKind.name,
        dayOfMonth = LocalDate.fromEpochDays(dateEpochDay).dayOfMonth,
        clockInLabel = wd?.clockInMillis?.takeIf { it > 0L }
            ?.let { TimeUtil.formatHm(it, IOSDriverTimezone) },
        clockOutLabel = wd?.clockOutMillis?.takeIf { it > 0L }
            ?.let { TimeUtil.formatHm(it, IOSDriverTimezone) },
        normalMinutes = minutes?.normalMinutes?.coerceAtLeast(0) ?: 0,
        overtimeMinutes = minutes?.overtimeMinutes?.coerceAtLeast(0) ?: 0,
        totalMinutes = minutes?.totalMinutes?.coerceAtLeast(0) ?: 0,
        hasWorkRecord = wd != null,
        hasChecklist = wd?.hasChecklist == true,
        hasSafetyDeclaration = wd?.hasSafetyDeclaration == true,
        hasMinutes = (minutes?.totalMinutes ?: 0) > 0
    )
}

// ============================================================================
// Expat Home — statistik review per-driver (padanan ExpatHomeViewModel Android)
// ============================================================================

/**
 * Satu driver kelolaan beserta ringkasan reviewnya.
 *
 * Sebelum ini iOS hanya menampilkan NAMA. Expat tidak punya cara melihat siapa yang punya
 * tunggakan review — padahal memutuskan itu adalah inti pekerjaannya, dan Android sudah
 * menampilkannya sejak lama.
 */
data class IOSManagedDriverSummarySnapshot(
    val uid: String,
    val displayName: String,
    val email: String?,
    val totalDays: Int,
    val approvedCount: Int,
    val rejectedCount: Int,
    val pendingCount: Int,
    /** Tanggal workday terakhir, sudah diformat di Asia/Jakarta. null bila belum ada. */
    val lastWorkdayLabel: String?
)

/** Ringkasan seluruh driver kelolaan, untuk header. */
data class IOSExpatSummaryHeaderSnapshot(
    val totalDrivers: Int,
    val pendingTotal: Int,
    /**
     * Kapan angka ini terakhir dimuat, sudah diformat di Asia/Jakarta.
     *
     * Ditampilkan karena tunggakan review adalah angka yang ditindaklanjuti: expat perlu tahu
     * apakah yang dilihatnya baru atau sisa sesi setengah jam lalu — tanpa itu, "0 menunggu"
     * yang basi terbaca sama meyakinkannya dengan yang segar.
     */
    val lastRefreshLabel: String
)

/**
 * Metrik live Admin Home.
 *
 * Angka MENTAH, bukan teks: Android memformatnya lewat string resource, dan iOS harus
 * melakukan hal yang sama supaya kedua platform menampilkan angka yang sepakat.
 */
data class IOSAdminLiveMetricsSnapshot(
    val trackedDriversCount: Int,
    val staleDriversCount: Int,
    val openWorkdayCount: Int,
    val needsAttentionCount: Int
)

/**
 * Satu rasio kepatuhan: berapa yang beres dari berapa yang terjadwal.
 *
 * [percentOrNull] `null` bila [scheduled] nol — pembagian nol **bukan** "0%", ia tak
 * terdefinisi. Dibawa sebagai `Int?` alih-alih dihitung di Swift supaya aturan itu hidup di satu
 * tempat yang sama dengan Android (`ComplianceRatio.percent` commonMain).
 */
data class IOSComplianceRatioSnapshot(
    val done: Int,
    val scheduled: Int,
    val percentOrNull: Int?,
    /** Berapa yang BELUM. Inilah yang sebenarnya dicari admin. */
    val outstanding: Int
)

/**
 * Kartu kepatuhan hari ini untuk Admin Home iOS — padanan `AdminComplianceCards` Android
 * (PRD §7.A1, SSOT desain §2.3).
 *
 * ## Kenapa `stateRaw`, bukan rasio bernullable
 * [AdminComplianceState] punya **empat** keadaan yang harus berbeda secara visual, dan
 * menggabungkannya jadi "rasio nol" adalah kesalahan yang PRD §7.A1 sebut eksplisit:
 * `NO_SCHEDULE_TODAY` berarti "tidak ada yang perlu dikerjakan hari ini", sementara `READY`
 * dengan penyebut nol berarti "ada yang perlu dikerjakan dan tak seorang pun mengerjakannya".
 * `ERROR` terpisah dari nol sungguhan dengan alasan yang sama — dan itu pernah jadi bug nyata di
 * repo ini, banner galat ter-render di belakang top bar sehingga kegagalan memuat terlihat
 * seperti "tidak ada data".
 *
 * Karena itu keadaan dibawa sebagai token mentah dan **mengalahkan** angkanya: pada keadaan
 * selain `READY`, keempat rasio sengaja nol dan Swift tidak boleh merendernya sebagai angka.
 *
 * ## [scheduledDriverCount] ada di sini, bukan dihitung ulang
 * Rail status §1 menampilkan "N terjadwal", dan angka itu **harus** penyebut yang sama dipakai
 * keempat rasio. Membiarkan Swift menghitungnya sendiri dari roster membuka celah di mana rail
 * dan kartu di layar yang sama berbeda pendapat soal berapa yang terjadwal hari ini.
 */
data class IOSAdminComplianceSnapshot(
    /** Nilai `AdminComplianceState`: LOADING / ERROR / NO_SCHEDULE_TODAY / READY. */
    val stateRaw: String,
    val attendance: IOSComplianceRatioSnapshot,
    val checklist: IOSComplianceRatioSnapshot,
    val safety: IOSComplianceRatioSnapshot,
    val sop: IOSComplianceRatioSnapshot,
    /** Penyebut bersama. Nol pada keadaan selain READY. */
    val scheduledDriverCount: Int,
    /** Total belum-beres lintas keempat kartu. Nol pada keadaan selain READY, tanpa berpura-pura bermakna. */
    val totalOutstanding: Int,
    /** Hari ini hari kerja terjadwal (untuk aturan perhatian "belum absen"). */
    val isScheduledWorkday: Boolean,
    /** Millis jadwal mulai hari ini; nol bila bukan hari kerja (pemicu "belum absen" mati). */
    val scheduleStartMillis: Long
)

/**
 * Aktivitas hari ini + tren terhadap kemarin (Admin Home).
 *
 * Angka MENTAH — pemformatan dan penentuan warna tren ada di UI, sama seperti Android.
 */
data class IOSAdminOverviewSnapshot(
    val checklistSubmitted: Int,
    val safetySubmitted: Int,
    val attendanceIn: Int,
    val attendanceOut: Int,
    val checklistTrendPercent: Int,
    val safetyTrendPercent: Int,
    val attendanceTrendPercent: Int
)

/**
 * Satu baris daftar pengecualian "Perlu tindakan" (PRD §7.A2).
 *
 * Angka MENTAH — pemformatan copy dan pemetaan warna ada di Swift (L10n), sama seperti
 * Android yang memformat di `AdminTodayComponents.kt`. Alasan `kindRaw` dibawa sebagai
 * string bukan enum: Swift tidak bisa switch exhaustively atas enum Kotlin, dan urutan
 * kegentingan sudah terkunci di urutan deklarasi [AdminExceptionKind] commonMain — daftar
 * ini sudah terurut saat keluar dari [buildAdminExceptionList].
 */
data class IOSAdminExceptionSnapshot(
    /** Nilai `AdminExceptionKind`: SAFETY_NOT_FIT / OVERTIME_VIOLATION / SPEED_VIOLATION / CHECKLIST_ISSUE / OVERTIME_WARNING / NOT_CLOCKED_IN / WORKDAY_PENDING_TOO_LONG / SOP_NOT_ACCEPTED. */
    val kindRaw: String,
    val driverUid: String,
    /** null berarti nama belum termuat — UI menampilkan penanda, BUKAN UID. */
    val driverName: String?,
    /** Tujuan aksi utama; null bila pemicu tidak menunjuk satu record. */
    val recordId: String?,
    /** Millis kapan masalah muncul; 0 untuk pemicu tanpa momen tunggal (mis. SOP). */
    val occurredAtMillis: Long,
    /** Angka pendukung, mis. menit lembur atau berapa hari menunggu. */
    val magnitude: Int?,
    /** Batas yang dilampaui, bila pemicunya soal batas. */
    val limitMinutes: Int?,
    /** Nilai `OvertimeLimitScope` (DAILY/WEEKLY), bila relevan. */
    val scopeRaw: String?
)

/**
 * Daftar pengecualian + keadaan muat — cermin `IOSAdminComplianceSnapshot.stateRaw`.
 * LOADING / ERROR / READY.
 */
data class IOSAdminExceptionListSnapshot(
    val stateRaw: String,
    val exceptions: List<IOSAdminExceptionSnapshot>
)

/**
 * Satu kejadian di activity feed Admin Home.
 *
 * Waktu sudah diformat di Asia/Jakarta — feed ini hanya dibaca, tidak dihitung ulang, jadi
 * tidak perlu membawa millis mentah.
 */
data class IOSAdminActivitySnapshot(
    /** Nilai `AdminActivityType`: ATTENDANCE_IN / ATTENDANCE_OUT / CHECKLIST_SUBMITTED / SAFETY_SUBMITTED / SOP_ACKNOWLEDGED. */
    val typeRaw: String,
    val driverUid: String,
    val recordId: String,
    val timeLabel: String,
    val dateLabel: String,
    /** Millis mentah — partisi laci aktivitas "hari ini/kemarin" butuh perbandingan numerik. */
    val timestampMillis: Long
)
