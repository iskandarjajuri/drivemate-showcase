package com.drivemate.showcase.domain.calendar

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * Aturan penyuntingan Work Calendar: diff draft-vs-server dan validasi sebelum publish.
 *
 * ## Kenapa di commonMain
 * Semua ini dulu `private` di `AdminWorkCalendarViewModel` Android. Layar Admin Work Calendar
 * iOS read-only, dan menambahkan penyuntingan di sana berarti menulis ulang aturannya dalam
 * Swift — dua platform lalu bisa berbeda pendapat soal "apa yang berubah" dan "apa yang boleh
 * dipublish", pada data yang mengatur jam kerja SELURUH driver.
 *
 * Alasan kedua sama pentingnya: `shared/src/iosMain` dan Swift tidak punya unit test yang jalan
 * di mesin Linux, sementara commonMain punya. Menaruh aturannya di sini membuatnya benar-benar
 * teruji, dan karena Android memakai fungsi yang SAMA, suite Android yang sudah ada ikut
 * menjaganya.
 *
 * Semua fungsi di file ini MURNI — nol jam sistem, nol I/O.
 */

/** Batas jumlah override sebelum publish diblokir. Sama untuk kedua platform. */
const val OVERRIDE_PUBLISH_BLOCK_THRESHOLD: Int = 30

/** Ringkasan perubahan draft terhadap kalender yang tersimpan di server. */
data class CalendarDiffSummary(
    val defaultRuleChanges: Int = 0,
    val overrideChanges: Int = 0
) {
    val totalChanges: Int get() = defaultRuleChanges + overrideChanges

    companion object {
        val Empty = CalendarDiffSummary()
    }
}

/**
 * Kenapa sebuah draft tidak boleh dipublish.
 *
 * Sengaja enum, bukan `@StringRes Int` seperti versi Android lama: commonMain tidak boleh tahu
 * apa pun soal resource Android, dan iOS butuh nilai yang sama untuk dipetakan ke
 * `Localizable.strings`-nya sendiri. Tiap platform memetakan enum ini ke teksnya masing-masing.
 */
enum class CalendarDraftError {
    /** Aturan default (weekday atau Jumat) tidak masuk akal — mis. mulai >= selesai. */
    INVALID_DEFAULT_RULE,

    /** Jumlah override sudah mencapai [OVERRIDE_PUBLISH_BLOCK_THRESHOLD]. */
    OVERRIDE_LIMIT_REACHED,

    /** Ada override yang bentuknya tidak sah. */
    INVALID_OVERRIDE,

    /** Tidak ada perubahan untuk dipublish. */
    NO_CHANGES,

    /** Catatan audit wajib diisi. */
    AUDIT_NOTE_REQUIRED
}

/** Hitung selisih draft terhadap server. */
fun calculateCalendarDiff(
    server: WorkCalendarYear,
    draft: WorkCalendarYear
): CalendarDiffSummary {
    var defaultRuleChanges = 0
    if (server.defaultRules.weekdayRule != draft.defaultRules.weekdayRule) defaultRuleChanges += 1
    if (server.defaultRules.fridayRule != draft.defaultRules.fridayRule) defaultRuleChanges += 1

    val serverMap = server.overrides.associateBy { it.date.toEpochDays().toInt() }
    val draftMap = draft.overrides.associateBy { it.date.toEpochDays().toInt() }
    // Union kunci, bukan hanya kunci draft: override yang DIHAPUS juga sebuah perubahan.
    val overrideChanges = (serverMap.keys + draftMap.keys).count { epochDay ->
        !sameCalendarOverride(serverMap[epochDay], draftMap[epochDay])
    }

    return CalendarDiffSummary(
        defaultRuleChanges = defaultRuleChanges,
        overrideChanges = overrideChanges
    )
}

/**
 * Validasi draft. `null` berarti boleh dipublish.
 *
 * @param auditNote catatan audit; kosong ditolak karena perubahan kalender kerja harus bisa
 *   ditelusuri siapa mengubah apa dan kenapa.
 */
fun validateCalendarDraft(
    draft: WorkCalendarYear,
    diff: CalendarDiffSummary,
    auditNote: String
): CalendarDraftError? {
    if (auditNote.trim().isEmpty()) return CalendarDraftError.AUDIT_NOTE_REQUIRED
    if (diff.totalChanges <= 0) return CalendarDraftError.NO_CHANGES
    if (!isValidWorkdayRule(draft.defaultRules.weekdayRule) ||
        !isValidWorkdayRule(draft.defaultRules.fridayRule)
    ) {
        return CalendarDraftError.INVALID_DEFAULT_RULE
    }
    if (draft.overrides.size >= OVERRIDE_PUBLISH_BLOCK_THRESHOLD) {
        return CalendarDraftError.OVERRIDE_LIMIT_REACHED
    }
    if (draft.overrides.any { !isValidCalendarOverride(it) }) {
        return CalendarDraftError.INVALID_OVERRIDE
    }
    return null
}

/**
 * Sebuah aturan sah kalau mulai < selesai DAN istirahatnya muat di dalam rentang itu.
 *
 * `breakMinutes < duration`, bukan `<=`: istirahat yang persis sepanjang shift menyisakan nol
 * menit kerja, yang bukan hari kerja melainkan salah input.
 */
fun isValidWorkdayRule(rule: WorkdayRule): Boolean {
    val start = rule.startLocalTime.minutesOfDay()
    val end = rule.endLocalTime.minutesOfDay()
    if (start >= end) return false
    val duration = end - start
    return rule.breakMinutes >= 0 && rule.breakMinutes < duration
}

/**
 * Override sah bila: hari libur TIDAK membawa aturan jam (tidak ada jam kerja untuk diatur),
 * atau hari kerja membawa aturan yang sah / tidak membawa aturan sama sekali (pakai default).
 */
fun isValidCalendarOverride(override: WorkdayOverride): Boolean {
    val ruleOverride = override.ruleOverride
    return when {
        !override.isWorkday -> ruleOverride == null
        ruleOverride == null -> true
        else -> isValidWorkdayRule(ruleOverride)
    }
}

/**
 * Dua override dianggap sama bila isi efektifnya sama. Catatan dinormalkan lebih dulu supaya
 * `null`, `""`, dan `"   "` tidak pernah dihitung sebagai perubahan — kalau tidak, membuka
 * lalu menutup form penyuntingan sudah tampak seperti "ada yang berubah".
 */
fun sameCalendarOverride(left: WorkdayOverride?, right: WorkdayOverride?): Boolean {
    if (left == null || right == null) return left == right
    return left.isWorkday == right.isWorkday &&
        left.ruleOverride == right.ruleOverride &&
        normalizeCalendarNote(left.note) == normalizeCalendarNote(right.note)
}

fun normalizeCalendarNote(note: String?): String? = note?.trim()?.takeIf { it.isNotEmpty() }

/** Menit sejak tengah malam. Cermin `toMinutes` di ViewModel Android (`:519`). */
internal fun LocalTime.minutesOfDay(): Int = (hour * 60) + minute

/** Hasil mutasi draft. */
enum class CalendarDraftMutation {
    /** Draft berubah. */
    APPLIED,

    /** Tidak ada yang berubah (input kosong, atau hasilnya identik). */
    NO_OP,

    /** Ditolak karena akan melewati [OVERRIDE_PUBLISH_BLOCK_THRESHOLD]. */
    REJECTED_LIMIT
}

/** Draft hasil mutasi + statusnya. [draft] tidak berubah bila status bukan [CalendarDraftMutation.APPLIED]. */
data class CalendarDraftMutationOutcome(
    val draft: WorkCalendarYear,
    val status: CalendarDraftMutation
)

/**
 * Terapkan satu jenis override ke sekumpulan tanggal.
 *
 * Batas override dicek terhadap jumlah PROYEKSI, bukan jumlah sekarang: tanggal yang sudah
 * punya override diganti, bukan ditambah, jadi menghitungnya sebagai penambahan akan menolak
 * penyuntingan yang sebenarnya sah.
 *
 * Catatan yang sudah ada DIPERTAHANKAN saat jenisnya berubah — admin yang mengubah libur jadi
 * hari kerja tidak kehilangan alasan yang sudah ditulisnya.
 */
fun applyOverrideKindToDraft(
    draft: WorkCalendarYear,
    dateEpochDays: Collection<Int>,
    kind: OverrideKind,
    customRule: WorkdayRule? = null
): CalendarDraftMutationOutcome {
    if (dateEpochDays.isEmpty()) return CalendarDraftMutationOutcome(draft, CalendarDraftMutation.NO_OP)
    if (kind == OverrideKind.CUSTOM && customRule == null) {
        return CalendarDraftMutationOutcome(draft, CalendarDraftMutation.NO_OP)
    }

    val byEpochDay = draft.overrides.associateBy { it.date.toEpochDays().toInt() }.toMutableMap()
    val alreadyPresent = dateEpochDays.count { byEpochDay.containsKey(it) }
    val projected = byEpochDay.size - alreadyPresent + dateEpochDays.size
    if (projected > OVERRIDE_PUBLISH_BLOCK_THRESHOLD) {
        return CalendarDraftMutationOutcome(draft, CalendarDraftMutation.REJECTED_LIMIT)
    }

    dateEpochDays.forEach { epochDay ->
        val date = LocalDate.fromEpochDays(epochDay)
        val existingNote = normalizeCalendarNote(byEpochDay[epochDay]?.note)
        byEpochDay[epochDay] = when (kind) {
            OverrideKind.HOLIDAY ->
                WorkdayOverride(date = date, isWorkday = false, ruleOverride = null, note = existingNote)
            OverrideKind.WORKDAY_SWAP ->
                WorkdayOverride(date = date, isWorkday = true, ruleOverride = null, note = existingNote)
            OverrideKind.CUSTOM ->
                WorkdayOverride(date = date, isWorkday = true, ruleOverride = customRule, note = existingNote)
        }
    }
    val updated = draft.copy(overrides = byEpochDay.values.sortedBy { it.date.toEpochDays() })
    return if (updated == draft) {
        CalendarDraftMutationOutcome(draft, CalendarDraftMutation.NO_OP)
    } else {
        CalendarDraftMutationOutcome(updated, CalendarDraftMutation.APPLIED)
    }
}

/** Hapus override pada satu tanggal — kembali memakai aturan default. */
fun removeOverrideFromDraft(draft: WorkCalendarYear, dateEpochDay: Int): CalendarDraftMutationOutcome {
    val target = LocalDate.fromEpochDays(dateEpochDay)
    val updated = draft.copy(overrides = draft.overrides.filterNot { it.date == target })
    return if (updated.overrides.size == draft.overrides.size) {
        CalendarDraftMutationOutcome(draft, CalendarDraftMutation.NO_OP)
    } else {
        CalendarDraftMutationOutcome(updated, CalendarDraftMutation.APPLIED)
    }
}
