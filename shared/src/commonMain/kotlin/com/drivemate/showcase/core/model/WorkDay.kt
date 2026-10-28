package com.drivemate.showcase.core.model

import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.domain.workday.WorkdayDecisionTrail
import kotlinx.datetime.TimeZone

/**
 * Representasi ringkas satu hari kerja seorang driver.
 *
 * @param dateEpochDay jumlah hari sejak 1970-01-01 (zona lokal) — dipakai untuk hitung start/end shift.
 * @param clockInMillis epoch millis saat Tap-In (nullable kalau belum tap-in? -> di sini diwajibkan ada).
 * @param clockOutMillis epoch millis saat Tap-Out (boleh 0 jika belum tap-out — tergantung pipeline-mu).
 *
 * Catatan:
 * - Validasi domain (mis. clockOut >= clockIn) dilakukan di layer usecase/repo.
 * - Field adjusted* dipakai saat admin/expat melakukan koreksi manual.
 */
data class WorkDay(
    val id: String,
    val driverUid: String,
    val dateEpochDay: Int,
    val clockInMillis: Long,
    val clockOutMillis: Long,
    val totalMinutes: Int,
    val overtimeMinutes: Int = 0,
    val driverName: String? = null,
    val approvedByExpat: Boolean? = null,
    val rejectionNote: String? = null,
    val adjustedBy: String? = null,
    val adjustedAt: Long? = null,
    val adjustedReason: String? = null,
    val hasChecklist: Boolean? = null,
    val checklistSubmittedAt: Long? = null,
    val hasSafetyDeclaration: Boolean? = null,
    val safetySubmittedAt: Long? = null,
    val approverUid: String? = null,
    val approverName: String? = null,
    val status: String? = null,
    val approvedByUid: String? = null,
    val approvedAtMillis: Long? = null,
    val approvalNote: String? = null,
    val rejectedByUid: String? = null,
    val rejectedAtMillis: Long? = null,
    val rejectReason: String? = null,
    val plannedStartMillis: Long? = null,
    val plannedEndMillis: Long? = null,
    val planSource: PlanSource? = null,
    val deviationFlags: List<DeviationFlag> = emptyList(),
    val reviewHold: Boolean = false,
    /**
     * Jejak keputusan dua langkah, APA ADANYA dari field `decisionTrail` (Blok 4.2).
     *
     * `null` berarti dokumen tidak punya field itu — mayoritas data production. Untuk
     * ditampilkan, pakai `resolveDecisionTrail()` di `domain/workday`, yang mengisi
     * langkah 1 dari `approvedByUid`/`rejectedByUid` dan menandainya sebagai simpulan.
     */
    val decisionTrail: WorkdayDecisionTrail? = null,
    val clockCorrectionMeta: ClockCorrectionMeta? = null,
    val expatCorrectionHistoryCount: Int = 0,
    val isClockCorrected: Boolean = false,
    val originalClockInMillis: Long? = null,
    val originalClockOutMillis: Long? = null,
    val correctedByRole: String? = null,
    val correctedAtMillis: Long? = null
)

data class ClockCorrectionMeta(
    val correctedByRole: String,
    val correctedAtMillis: Long,
    val reason: String? = null
)

enum class ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED
}

/** Status approval ter-normalisasi untuk dipetakan ke label UI di platform. */
fun WorkDay.approvalStatus(): ApprovalStatus =
    when (
        normalizeApprovalStatus(
            statusRaw = status,
            approvedByExpat = approvedByExpat,
            approvedByUid = approvedByUid,
            rejectedByUid = rejectedByUid,
            rejectedAtMillis = rejectedAtMillis,
            rejectReason = rejectReason,
            rejectionNote = rejectionNote
        ).status
    ) {
        "approved" -> ApprovalStatus.APPROVED
        "rejected" -> ApprovalStatus.REJECTED
        else -> ApprovalStatus.PENDING
    }

fun WorkDay.isFinalizedByStatusOrActor(): Boolean {
    val normalized = status?.trim()?.lowercase()
    if (normalized == "approved" || normalized == "rejected") return true
    if (!approvedByUid.isNullOrBlank()) return true
    if (!rejectedByUid.isNullOrBlank()) return true
    return false
}

fun WorkDay.rejectionReasonOrFallback(): String? =
    rejectReason?.trim()?.takeIf { it.isNotEmpty() }
        ?: rejectionNote?.trim()?.takeIf { it.isNotEmpty() }

fun WorkDay.attendanceState(): AttendanceDailyState =
    resolveAttendanceDailyState(
        clockInMillis = clockInMillis.takeIf { it > 0L },
        clockOutMillis = clockOutMillis.takeIf { it > 0L }
    )

fun WorkDay.displayTotalMinutesOrNull(): Int? =
    if (attendanceState() == AttendanceDailyState.CLOSED) totalMinutes.coerceAtLeast(0) else null

fun WorkDay.displayOvertimeMinutesOrNull(): Int? =
    if (attendanceState() == AttendanceDailyState.CLOSED) overtimeMinutes.coerceAtLeast(0) else null

internal fun resolveApprovalStatus(
    statusRaw: String?,
    approvedByExpat: Boolean?,
    approvedByUid: String? = null,
    rejectedByUid: String?,
    rejectedAtMillis: Long?,
    rejectReason: String?,
    rejectionNote: String?
): Boolean? {
    return when (
        normalizeApprovalStatus(
            statusRaw = statusRaw,
            approvedByExpat = approvedByExpat,
            approvedByUid = approvedByUid,
            rejectedByUid = rejectedByUid,
            rejectedAtMillis = rejectedAtMillis,
            rejectReason = rejectReason,
            rejectionNote = rejectionNote
        ).status
    ) {
        "approved" -> true
        "rejected" -> false
        else -> null
    }
}

internal data class ApprovalNormalization(
    val status: String,
    val approvedByExpat: Boolean?
)

internal fun normalizeApprovalStatus(
    statusRaw: String?,
    approvedByExpat: Boolean?,
    approvedByUid: String?,
    rejectedByUid: String?,
    rejectedAtMillis: Long?,
    rejectReason: String?,
    rejectionNote: String?
): ApprovalNormalization {
    val normalized = statusRaw?.trim()?.lowercase()
    when (normalized) {
        "approved", "rejected", "pending" -> {
            val approvedFlag = when (normalized) {
                "approved" -> true
                "rejected" -> false
                else -> null
            }
            return ApprovalNormalization(status = normalized, approvedByExpat = approvedFlag)
        }
    }
    val hasRejectMeta = !rejectedByUid.isNullOrBlank()
        || (rejectedAtMillis != null && rejectedAtMillis > 0L)
        || !rejectReason.isNullOrBlank()
        || !rejectionNote.isNullOrBlank()
    val derivedStatus = when {
        hasRejectMeta -> "rejected"
        !approvedByUid.isNullOrBlank() || approvedByExpat == true -> "approved"
        else -> "pending"
    }
    val derivedApproved = when (derivedStatus) {
        "approved" -> true
        "rejected" -> false
        else -> null
    }
    return ApprovalNormalization(status = derivedStatus, approvedByExpat = derivedApproved)
}

/**
 * Jam kerja standar (bisa kamu pindah ke konfigurasi nantinya).
 * Internal (bukan private) supaya jadi satu sumber kebenaran yang bisa
 * dipakai ulang oleh logika lain di modul :shared (mis. resolusi jendela
 * referensi timeline attendance) tanpa duplikasi literal "07:30"/"16:30".
 */
internal const val WORK_START_HM = "07:30"   // 07:30
internal const val WORK_END_HM   = "16:30"   // 16:30

/**
 * Jam selesai standar hari **Jumat** — 20 menit lebih lambat, akomodasi salat Jumat.
 *
 * ## Kenapa literal ini terduplikasi
 * "16:50" juga ada di `DefaultRules.fridayRule` (`domain/calendar`). Menghapus duplikasinya
 * menuntut `core/model` mengimpor `domain/calendar`, dan itu **membalik arah ketergantungan
 * lapis** — `domain` yang bergantung pada `core`, tidak sebaliknya. Duplikasi satu literal
 * dinilai lebih murah daripada inversi itu, dengan syarat ia tidak bisa mendrift diam-diam:
 * `FridayAwareFallbackTest.fallbackAgreesWithCalendarDefaultRules` yang menjaganya.
 *
 * Sebelum 7 September 2026 fallback ini tidak tahu Jumat sama sekali, jadi
 * [endOfWorkMillis] mengembalikan 16:30 untuk hari Jumat. Yang paling terasa bukan lembur
 * semu, melainkan default dialog edit rencana kerja (`IOSBridgeSnapshots.kt` mengisi
 * `defaultEndMillis` dari sini): expat mengedit rencana Jumat, mendapat 16:30, dan
 * menyimpannya membuat rencana 20 menit lebih pendek dari jadwal sebenarnya.
 */
internal const val WORK_END_HM_FRIDAY = "16:50"

/** Epoch millis start shift pada tanggal `dateEpochDay`. Sama untuk semua hari. */
fun WorkDay.startOfWorkMillis(): Long =
    TimeUtil.parseHm(WORK_START_HM, dateEpochDay.toLong(), WORK_ZONE)

/**
 * Epoch millis end shift pada tanggal `dateEpochDay`.
 *
 * Jumat memakai [WORK_END_HM_FRIDAY]; hari lain [WORK_END_HM]. Sabtu/Minggu memakai jendela
 * hari kerja — bukan kelalaian: hari libur tidak punya jadwal untuk dilampaui, seluruh kerja
 * di sana memang dihitung lembur, jadi jendelanya tidak memengaruhi angka apa pun.
 */
fun WorkDay.endOfWorkMillis(): Long {
    val hm = if (isFridayEpochDay(dateEpochDay)) WORK_END_HM_FRIDAY else WORK_END_HM
    return TimeUtil.parseHm(hm, dateEpochDay.toLong(), WORK_ZONE)
}

/**
 * Zona jendela kerja: selalu **Asia/Jakarta**, tak peduli zona sistem perangkat.
 *
 * Sebelumnya fungsi ini memakai `TimeZone.currentSystemDefault()` (default [TimeUtil.parseHm]),
 * yang diam-diam bergantung pada setting perangkat: test lolos di mesin pengembang (WIB) tetapi
 * gagal di runner CI (UTC) — Android CI run 37423786856, 5 test FridayAwareFallbackTest, Okt 2026.
 * Lebih penting lagi, perangkat driver yang zona sistemnya bukan WIB mendapat jendela kerja
 * yang salah (default dialog edit rencana kerja ikut bergeser).
 */
private val WORK_ZONE: TimeZone = TimeZone.of("Asia/Jakarta")

/**
 * Apakah `epochDay` jatuh pada hari Jumat.
 *
 * 1 Januari 1970 (epochDay 0) adalah **Kamis**, jadi Jumat adalah `epochDay % 7 == 1`.
 * Dihitung aritmetik alih-alih lewat `LocalDate` supaya tetap murni dan tidak menyeret
 * konversi zona waktu ke dalam `core/model`.
 */
internal fun isFridayEpochDay(epochDay: Int): Boolean {
    val dow = ((epochDay % 7) + 7) % 7      // aman untuk epochDay negatif
    return dow == 1
}

/**
 * Terlambat jika clockIn lewat dari start shift + grace (default 5 menit).
 * Menggunakan [TimeUtil.parseHm] agar konsisten lintas platform (KMM).
 */
fun WorkDay.isLate(graceMinutes: Int = 5): Boolean {
    val graceMs = graceMinutes * 60 * 1000L
    val start = startOfWorkMillis()
    return clockInMillis > (start + graceMs)
}
