package com.drivemate.showcase.bridge

import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.domain.calendar.CalendarDiffSummary
import com.drivemate.showcase.domain.calendar.CalendarDraftMutation
import com.drivemate.showcase.domain.calendar.OverrideKind
import com.drivemate.showcase.domain.calendar.WorkCalendarYear
import com.drivemate.showcase.domain.calendar.WorkdayOverride
import com.drivemate.showcase.domain.calendar.applyOverrideKindToDraft
import com.drivemate.showcase.domain.calendar.calculateCalendarDiff
import com.drivemate.showcase.domain.calendar.normalizeCalendarNote
import com.drivemate.showcase.domain.calendar.removeOverrideFromDraft
import com.drivemate.showcase.domain.calendar.validateCalendarDraft
import com.drivemate.showcase.domain.usecase.calendar.GetWorkCalendarYearUseCase
import com.drivemate.showcase.domain.usecase.calendar.UpsertWorkCalendarYearUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.drivemate.showcase.domain.calendar.WorkdayRule
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * Penyuntingan Work Calendar untuk iOS — padanan `AdminWorkCalendarViewModel` Android.
 *
 * Layar iOS sebelumnya **read-only**: satu-satunya tombolnya prev/next bulan dan alert nama
 * libur. Admin iOS sama sekali tidak bisa menandai libur perusahaan atau menukar hari kerja,
 * padahal itu data yang mengatur jam kerja SELURUH driver.
 *
 * ## Draft dipegang di Kotlin, bukan di Swift
 * Seluruh state penyuntingan (server vs draft, diff, validasi) ada di sini dan aturannya di
 * commonMain. Kalau draft-nya hidup di SwiftUI, dua platform akan punya jawaban sendiri-sendiri
 * untuk "apa yang berubah" dan "apa yang boleh dipublish" — dan yang satu tidak teruji.
 *
 * ## Publish bukan "simpan otomatis"
 * Mengubah draft TIDAK menulis apa pun. Penulisan hanya terjadi lewat [publish], yang menuntut
 * catatan audit — sama seperti Android. Perubahan kalender kerja harus bisa ditelusuri.
 */
internal class IOSWorkCalendarEditor(
    private val scope: CoroutineScope,
    private val getCalendarUseCase: GetWorkCalendarYearUseCase,
    private val upsertCalendarUseCase: UpsertWorkCalendarYearUseCase
) {
    private var server: WorkCalendarYear? = null
    private var draft: WorkCalendarYear? = null

    /** Muat kalender tahun tertentu dan mulai dari draft bersih. */
    fun load(year: Int, completion: (IOSWorkCalendarEditorSnapshot?, String?) -> Unit) {
        scope.launch {
            when (val result = getCalendarUseCase(year)) {
                is AppResult.Success -> {
                    server = result.data
                    draft = result.data
                    completion(snapshot(), null)
                }
                is AppResult.Failure -> {
                    server = null
                    draft = null
                    completion(null, result.throwable.message ?: "Failed to load work calendar.")
                }
            }
        }
    }

    /** Buang seluruh suntingan, kembali ke isi server. */
    fun discardDraft(): IOSWorkCalendarEditorSnapshot? {
        draft = server
        return snapshot()
    }

    /**
     * Tandai satu tanggal.
     *
     * @param kindRaw "HOLIDAY" / "WORKDAY_SWAP" / "CUSTOM".
     * @param customStartMinutes jam mulai untuk CUSTOM; diabaikan untuk jenis lain.
     * @param customEndMinutes jam selesai untuk CUSTOM.
     * @param customBreakMinutes istirahat untuk CUSTOM.
     * @return snapshot baru, atau null bila belum ada kalender dimuat.
     */
    fun applyOverride(
        dateEpochDay: Int,
        kindRaw: String,
        customStartMinutes: Int = 0,
        customEndMinutes: Int = 0,
        customBreakMinutes: Int = 0,
        completion: (IOSWorkCalendarEditorSnapshot?, String?) -> Unit
    ) {
        val currentDraft = draft ?: run { completion(null, null); return }
        val kind = when (kindRaw) {
            "HOLIDAY" -> OverrideKind.HOLIDAY
            "WORKDAY_SWAP" -> OverrideKind.WORKDAY_SWAP
            "CUSTOM" -> OverrideKind.CUSTOM
            else -> {
                completion(null, "Unsupported override kind: $kindRaw")
                return
            }
        }
        // Aturan jam HANYA dibuat untuk CUSTOM. Mengirimkannya untuk jenis lain akan
        // membuat hari libur membawa jam kerja — kombinasi yang ditolak
        // `isValidCalendarOverride`, dan penolakannya baru muncul saat publish.
        val customRule = if (kind == OverrideKind.CUSTOM) {
            WorkdayRule(
                startLocalTime = minutesToLocalTime(customStartMinutes),
                endLocalTime = minutesToLocalTime(customEndMinutes),
                breakMinutes = customBreakMinutes.coerceAtLeast(0)
            )
        } else {
            null
        }
        val outcome = applyOverrideKindToDraft(currentDraft, listOf(dateEpochDay), kind, customRule)
        if (outcome.status == CalendarDraftMutation.REJECTED_LIMIT) {
            // Sentinel, bukan teks siap-tampil: UI yang melokalkannya.
            completion(snapshot(), "OVERRIDE_LIMIT_REACHED")
            return
        }
        draft = outcome.draft
        completion(snapshot(), null)
    }

    /** Hapus penandaan satu tanggal — kembali ke aturan default. */
    fun clearOverride(dateEpochDay: Int, completion: (IOSWorkCalendarEditorSnapshot?, String?) -> Unit) {
        val currentDraft = draft ?: run { completion(null, null); return }
        draft = removeOverrideFromDraft(currentDraft, dateEpochDay).draft
        completion(snapshot(), null)
    }

    /** Setel catatan satu tanggal. Hanya berlaku bila tanggal itu sudah punya override. */
    fun setNote(
        dateEpochDay: Int,
        note: String?,
        completion: (IOSWorkCalendarEditorSnapshot?, String?) -> Unit
    ) {
        val currentDraft = draft ?: run { completion(null, null); return }
        val target = LocalDate.fromEpochDays(dateEpochDay)
        val existing = currentDraft.overrides.firstOrNull { it.date == target }
            ?: run { completion(snapshot(), null); return }
        val updated: List<WorkdayOverride> = currentDraft.overrides.map {
            if (it.date == target) it.copy(note = normalizeCalendarNote(note)) else it
        }
        draft = currentDraft.copy(overrides = updated)
        completion(snapshot(), null)
    }

    /**
     * Ubah aturan jam default (weekday atau Jumat).
     *
     * Validasinya TIDAK dilakukan di sini melainkan saat publish, lewat
     * `validateCalendarDraft` — satu tempat, sama seperti Android. Menolak lebih awal di
     * sini berarti admin tidak bisa mengetik "09:00" lalu memperbaiki jam selesainya,
     * karena keadaan sementara yang tidak valid akan tertolak sebelum sempat diperbaiki.
     *
     * @param isFriday true untuk aturan Jumat, false untuk weekday.
     */
    fun updateDefaultRule(
        isFriday: Boolean,
        startMinutes: Int,
        endMinutes: Int,
        breakMinutes: Int,
        completion: (IOSWorkCalendarEditorSnapshot?, String?) -> Unit
    ) {
        val currentDraft = draft ?: run { completion(null, null); return }
        val rule = WorkdayRule(
            startLocalTime = minutesToLocalTime(startMinutes),
            endLocalTime = minutesToLocalTime(endMinutes),
            breakMinutes = breakMinutes.coerceAtLeast(0)
        )
        val rules = currentDraft.defaultRules
        draft = currentDraft.copy(
            defaultRules = if (isFriday) rules.copy(fridayRule = rule) else rules.copy(weekdayRule = rule)
        )
        completion(snapshot(), null)
    }

    /**
     * Tulis draft ke Firestore. Validasi dijalankan lebih dulu dengan fungsi commonMain
     * yang SAMA dipakai Android, jadi aturan tolak/terima kedua platform identik.
     */
    fun publish(auditNote: String, completion: (Boolean, String?) -> Unit) {
        val currentServer = server
        val currentDraft = draft
        if (currentServer == null || currentDraft == null) {
            completion(false, null)
            return
        }
        val diff = calculateCalendarDiff(currentServer, currentDraft)
        val error = validateCalendarDraft(currentDraft, diff, auditNote)
        if (error != null) {
            // Nama enum diteruskan apa adanya; Swift memetakannya ke Localizable.strings.
            completion(false, error.name)
            return
        }
        scope.launch {
            val payload = currentDraft.copy(updatedAtMillis = TimeUtil.nowEpochMillis())
            when (val result = upsertCalendarUseCase(payload)) {
                is AppResult.Success -> {
                    // Muat ulang dari server, bukan menganggap draft sudah jadi kebenaran —
                    // server bisa menormalkan sesuatu, dan diff berikutnya harus dihitung
                    // terhadap apa yang benar-benar tersimpan.
                    load(payload.year) { _, _ -> }
                    completion(true, null)
                }
                is AppResult.Failure ->
                    completion(false, result.throwable.message ?: "Failed to publish work calendar.")
            }
        }
    }

    private fun snapshot(): IOSWorkCalendarEditorSnapshot? {
        val currentServer = server ?: return null
        val currentDraft = draft ?: return null
        val diff = calculateCalendarDiff(currentServer, currentDraft)
        // Hanya override yang BENAR-BENAR membawa aturan jam. `!!` di bawah aman karena
        // filter ini; memakai jam default untuk yang lain akan menampilkannya seolah punya
        // jam khusus padahal tidak.
        val customOverrides = currentDraft.overrides
            .filter { it.isWorkday && it.ruleOverride != null }
            .sortedBy { it.date.toEpochDays() }
        return IOSWorkCalendarEditorSnapshot(
            year = currentDraft.year,
            calendar = currentDraft.toIosSnapshot(),
            defaultRuleChanges = diff.defaultRuleChanges,
            overrideChanges = diff.overrideChanges,
            totalChanges = diff.totalChanges,
            overrideCount = currentDraft.overrides.size,
            overrideLimit = com.drivemate.showcase.domain.calendar
                .OVERRIDE_PUBLISH_BLOCK_THRESHOLD,
            editedDates = currentDraft.overrides
                .map { it.date.toEpochDays().toInt() }
                .sorted(),
            weekdayStartMinutes = currentDraft.defaultRules.weekdayRule.startLocalTime.minutesOfDayPublic(),
            weekdayEndMinutes = currentDraft.defaultRules.weekdayRule.endLocalTime.minutesOfDayPublic(),
            weekdayBreakMinutes = currentDraft.defaultRules.weekdayRule.breakMinutes,
            fridayStartMinutes = currentDraft.defaultRules.fridayRule.startLocalTime.minutesOfDayPublic(),
            fridayEndMinutes = currentDraft.defaultRules.fridayRule.endLocalTime.minutesOfDayPublic(),
            fridayBreakMinutes = currentDraft.defaultRules.fridayRule.breakMinutes,
            customRuleDates = customOverrides.map { it.date.toEpochDays().toInt() },
            customRuleStartMinutes = customOverrides.map { it.ruleOverride!!.startLocalTime.minutesOfDayPublic() },
            customRuleEndMinutes = customOverrides.map { it.ruleOverride!!.endLocalTime.minutesOfDayPublic() },
            customRuleBreakMinutes = customOverrides.map { it.ruleOverride!!.breakMinutes }
        )
    }
}

/**
 * Draft kalender siap dirender.
 *
 * [calendar] memakai snapshot read-only yang sudah ada, jadi grid bulan yang sekarang tetap
 * dipakai apa adanya — termasuk perbaikan libur-tanpa-catatan.
 */
data class IOSWorkCalendarEditorSnapshot(
    val year: Int,
    val calendar: IOSWorkCalendarSnapshot,
    val defaultRuleChanges: Int,
    val overrideChanges: Int,
    val totalChanges: Int,
    val overrideCount: Int,
    val overrideLimit: Int,
    /** Epoch-day yang punya override di draft — untuk menandai sel yang tersunting. */
    val editedDates: List<Int>,
    /**
     * Aturan jam default. Dibawa sebagai MENIT sejak tengah malam, bukan string "HH:mm":
     * `DatePicker` iOS bekerja dengan komponen jam/menit, dan mengonversi lewat string
     * berarti dua parser (Kotlin dan Swift) yang bisa berbeda pendapat soal format.
     */
    val weekdayStartMinutes: Int,
    val weekdayEndMinutes: Int,
    val weekdayBreakMinutes: Int,
    val fridayStartMinutes: Int,
    val fridayEndMinutes: Int,
    val fridayBreakMinutes: Int,
    /**
     * Tanggal yang punya jam KHUSUS, beserta jamnya. Sejajar dengan [customRuleStartMinutes]
     * dan [customRuleEndMinutes] pada indeks yang sama.
     *
     * Tiga list sejajar, bukan list of struct: struct Kotlin yang di-nest melewati batas
     * ObjC menjadi tipe pembungkus yang canggung dipakai di Swift, dan bentuk ini sudah
     * dipakai `computeLiveMetrics` di repo ini.
     */
    val customRuleDates: List<Int>,
    val customRuleStartMinutes: List<Int>,
    val customRuleEndMinutes: List<Int>,
    val customRuleBreakMinutes: List<Int>
)

/** Menit sejak tengah malam. Nama berbeda dari helper internal commonMain agar tidak bentrok. */
private fun LocalTime.minutesOfDayPublic(): Int = (hour * 60) + minute

/**
 * Menit -> [LocalTime], di-clamp ke rentang satu hari.
 *
 * Clamp, bukan lempar: nilai di luar rentang hanya bisa datang dari bug UI, dan menjatuhkan
 * layar penyuntingan kalender karena itu jauh lebih buruk daripada menormalkannya.
 */
private fun minutesToLocalTime(minutes: Int): LocalTime {
    val safe = minutes.coerceIn(0, 24 * 60 - 1)
    return LocalTime(hour = safe / 60, minute = safe % 60)
}
