package com.drivemate.showcase

import com.drivemate.showcase.domain.calendar.CalendarDiffSummary
import com.drivemate.showcase.domain.calendar.OverrideKind
import com.drivemate.showcase.domain.calendar.CalendarDraftMutation
import com.drivemate.showcase.domain.calendar.applyOverrideKindToDraft
import com.drivemate.showcase.domain.calendar.removeOverrideFromDraft
import com.drivemate.showcase.domain.calendar.CalendarDraftError
import com.drivemate.showcase.domain.calendar.OVERRIDE_PUBLISH_BLOCK_THRESHOLD
import com.drivemate.showcase.domain.calendar.WorkCalendarYear
import com.drivemate.showcase.domain.calendar.WorkdayOverride
import com.drivemate.showcase.domain.calendar.WorkdayRule
import com.drivemate.showcase.domain.calendar.calculateCalendarDiff
import com.drivemate.showcase.domain.calendar.isValidCalendarOverride
import com.drivemate.showcase.domain.calendar.isValidWorkdayRule
import com.drivemate.showcase.domain.calendar.normalizeCalendarNote
import com.drivemate.showcase.domain.calendar.sameCalendarOverride
import com.drivemate.showcase.domain.calendar.validateCalendarDraft
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Aturan penyuntingan Work Calendar — dipakai KEDUA platform.
 *
 * Ini bukan sekadar coverage. Data yang diatur di sini menentukan jam kerja SELURUH driver:
 * salah diff berarti perubahan diam-diam tidak terpublish, salah validasi berarti kalender
 * rusak masuk production. Sebelum ini semuanya `private` di ViewModel Android dan nol test.
 */
class WorkCalendarDraftTest {

    private fun rule(start: String, end: String, breakMinutes: Int = 60): WorkdayRule {
        val (sh, sm) = start.split(":").map { it.toInt() }
        val (eh, em) = end.split(":").map { it.toInt() }
        return WorkdayRule(
            startLocalTime = LocalTime(sh, sm),
            endLocalTime = LocalTime(eh, em),
            breakMinutes = breakMinutes
        )
    }

    private fun calendar(
        year: Int = 2026,
        overrides: List<WorkdayOverride> = emptyList()
    ) = WorkCalendarYear(year = year, overrides = overrides)

    private fun override(
        y: Int = 2026, m: Int = 8, d: Int = 17,
        isWorkday: Boolean = false,
        ruleOverride: WorkdayRule? = null,
        note: String? = null
    ) = WorkdayOverride(LocalDate(y, m, d), isWorkday, ruleOverride, note)

    // ---------- Diff ----------

    @Test
    fun identical_calendars_report_no_changes() {
        val c = calendar(overrides = listOf(override(note = "Kemerdekaan")))
        val diff = calculateCalendarDiff(c, c)
        assertEquals(0, diff.totalChanges)
        assertEquals(CalendarDiffSummary.Empty, diff)
    }

    @Test
    fun an_added_override_counts_as_one_change() {
        val diff = calculateCalendarDiff(calendar(), calendar(overrides = listOf(override())))
        assertEquals(1, diff.overrideChanges)
        assertEquals(1, diff.totalChanges)
    }

    @Test
    fun a_REMOVED_override_also_counts_as_a_change() {
        // Union kunci server+draft. Kalau hanya kunci draft yang dilihat, menghapus libur
        // tampak seperti "tidak ada perubahan" dan penghapusannya tidak pernah terpublish.
        val diff = calculateCalendarDiff(calendar(overrides = listOf(override())), calendar())
        assertEquals(1, diff.overrideChanges)
    }

    @Test
    fun changing_only_the_note_is_still_a_change() {
        val diff = calculateCalendarDiff(
            calendar(overrides = listOf(override(note = "Libur A"))),
            calendar(overrides = listOf(override(note = "Libur B")))
        )
        assertEquals(1, diff.overrideChanges)
    }

    @Test
    fun blank_and_null_notes_are_NOT_a_change() {
        // Membuka lalu menutup form penyuntingan tidak boleh terlihat seperti ada perubahan.
        for (blank in listOf("", "   ", "\n")) {
            val diff = calculateCalendarDiff(
                calendar(overrides = listOf(override(note = null))),
                calendar(overrides = listOf(override(note = blank)))
            )
            assertEquals(0, diff.overrideChanges, "note=<$blank>")
        }
    }

    @Test
    fun each_changed_default_rule_counts_separately() {
        val server = calendar()
        val draft = server.copy(
            defaultRules = server.defaultRules.copy(
                weekdayRule = rule("08:00", "17:00"),
                fridayRule = rule("08:00", "16:00")
            )
        )
        val diff = calculateCalendarDiff(server, draft)
        assertEquals(2, diff.defaultRuleChanges)
    }

    @Test
    fun default_rule_and_override_changes_are_summed() {
        val server = calendar()
        val draft = server.copy(
            defaultRules = server.defaultRules.copy(weekdayRule = rule("08:00", "17:00")),
            overrides = listOf(override())
        )
        val diff = calculateCalendarDiff(server, draft)
        assertEquals(1, diff.defaultRuleChanges)
        assertEquals(1, diff.overrideChanges)
        assertEquals(2, diff.totalChanges)
    }

    // ---------- Validasi aturan ----------

    @Test
    fun a_rule_ending_before_it_starts_is_invalid() {
        assertFalse(isValidWorkdayRule(rule("17:00", "08:00")))
        assertFalse(isValidWorkdayRule(rule("08:00", "08:00")))
    }

    @Test
    fun a_break_as_long_as_the_shift_is_invalid() {
        // 08:00-17:00 = 540 menit. Istirahat 540 menyisakan nol menit kerja.
        assertFalse(isValidWorkdayRule(rule("08:00", "17:00", breakMinutes = 540)))
        assertTrue(isValidWorkdayRule(rule("08:00", "17:00", breakMinutes = 539)))
    }

    @Test
    fun a_negative_break_is_invalid() {
        assertFalse(isValidWorkdayRule(rule("08:00", "17:00", breakMinutes = -1)))
    }

    @Test
    fun a_zero_break_is_valid() {
        assertTrue(isValidWorkdayRule(rule("08:00", "17:00", breakMinutes = 0)))
    }

    // ---------- Validasi override ----------

    @Test
    fun a_holiday_carrying_working_hours_is_invalid() {
        // Hari libur tidak punya jam kerja untuk diatur; membawa ruleOverride berarti
        // dua fakta yang bertentangan tersimpan di satu dokumen.
        assertFalse(isValidCalendarOverride(override(isWorkday = false, ruleOverride = rule("08:00", "17:00"))))
        assertTrue(isValidCalendarOverride(override(isWorkday = false, ruleOverride = null)))
    }

    @Test
    fun a_workday_override_may_omit_the_rule_and_use_the_default() {
        assertTrue(isValidCalendarOverride(override(isWorkday = true, ruleOverride = null)))
    }

    @Test
    fun a_workday_override_with_a_broken_rule_is_invalid() {
        assertFalse(isValidCalendarOverride(override(isWorkday = true, ruleOverride = rule("17:00", "08:00"))))
    }

    // ---------- Validasi publish ----------

    @Test
    fun publishing_without_an_audit_note_is_rejected() {
        val diff = CalendarDiffSummary(overrideChanges = 1)
        for (blank in listOf("", "   ")) {
            assertEquals(
                CalendarDraftError.AUDIT_NOTE_REQUIRED,
                validateCalendarDraft(calendar(), diff, blank)
            )
        }
    }

    @Test
    fun publishing_with_nothing_changed_is_rejected() {
        assertEquals(
            CalendarDraftError.NO_CHANGES,
            validateCalendarDraft(calendar(), CalendarDiffSummary.Empty, "alasan")
        )
    }

    @Test
    fun the_audit_note_is_checked_before_the_change_count() {
        // Urutan penting untuk pesan yang berguna: admin yang lupa mengisi catatan harus
        // diberi tahu itu, bukan "tidak ada perubahan".
        assertEquals(
            CalendarDraftError.AUDIT_NOTE_REQUIRED,
            validateCalendarDraft(calendar(), CalendarDiffSummary.Empty, "")
        )
    }

    @Test
    fun a_broken_default_rule_blocks_publishing() {
        val draft = calendar().let {
            it.copy(defaultRules = it.defaultRules.copy(weekdayRule = rule("18:00", "09:00")))
        }
        assertEquals(
            CalendarDraftError.INVALID_DEFAULT_RULE,
            validateCalendarDraft(draft, CalendarDiffSummary(defaultRuleChanges = 1), "alasan")
        )
    }

    @Test
    fun reaching_the_override_limit_blocks_publishing() {
        val many = (1..OVERRIDE_PUBLISH_BLOCK_THRESHOLD).map {
            override(m = 1, d = 1).copy(date = LocalDate.fromEpochDays(20_000 + it))
        }
        assertEquals(
            CalendarDraftError.OVERRIDE_LIMIT_REACHED,
            validateCalendarDraft(calendar(overrides = many), CalendarDiffSummary(overrideChanges = 1), "alasan")
        )
        // Satu di bawah batas masih boleh.
        assertNull(
            validateCalendarDraft(
                calendar(overrides = many.dropLast(1)),
                CalendarDiffSummary(overrideChanges = 1),
                "alasan"
            )
        )
    }

    @Test
    fun an_invalid_override_blocks_publishing() {
        assertEquals(
            CalendarDraftError.INVALID_OVERRIDE,
            validateCalendarDraft(
                calendar(overrides = listOf(override(isWorkday = false, ruleOverride = rule("08:00", "17:00")))),
                CalendarDiffSummary(overrideChanges = 1),
                "alasan"
            )
        )
    }

    @Test
    fun a_clean_draft_with_a_note_and_changes_passes() {
        assertNull(
            validateCalendarDraft(
                calendar(overrides = listOf(override(note = "Kemerdekaan"))),
                CalendarDiffSummary(overrideChanges = 1),
                "libur nasional"
            )
        )
    }

    // ---------- Helper ----------

    @Test
    fun notes_are_normalized_consistently() {
        assertNull(normalizeCalendarNote(null))
        assertNull(normalizeCalendarNote(""))
        assertNull(normalizeCalendarNote("   "))
        assertEquals("Libur", normalizeCalendarNote("  Libur  "))
    }

    @Test
    fun two_nulls_are_the_same_override_but_null_vs_value_is_not() {
        assertTrue(sameCalendarOverride(null, null))
        assertFalse(sameCalendarOverride(null, override()))
        assertFalse(sameCalendarOverride(override(), null))
    }
}

/**
 * Mutasi draft — batas override dan pertahanan catatan.
 *
 * Dipisah dari suite di atas karena ini tentang MENGUBAH draft, bukan menilainya; keduanya
 * dipakai Android dan iOS lewat fungsi yang sama.
 */
class WorkCalendarDraftMutationTest {

    private fun cal(overrides: List<WorkdayOverride> = emptyList()) =
        WorkCalendarYear(year = 2026, overrides = overrides)

    private fun ov(epochDay: Int, isWorkday: Boolean = false, note: String? = null) =
        WorkdayOverride(LocalDate.fromEpochDays(epochDay), isWorkday, null, note)

    @Test
    fun applying_a_holiday_adds_an_override() {
        val out = applyOverrideKindToDraft(cal(), listOf(20_000), OverrideKind.HOLIDAY)
        assertEquals(CalendarDraftMutation.APPLIED, out.status)
        assertEquals(1, out.draft.overrides.size)
        assertFalse(out.draft.overrides.first().isWorkday)
    }

    @Test
    fun an_existing_note_survives_a_kind_change() {
        // Admin yang mengubah libur jadi hari kerja tidak boleh kehilangan alasannya.
        val start = cal(listOf(ov(20_000, isWorkday = false, note = "Cuti bersama")))
        val out = applyOverrideKindToDraft(start, listOf(20_000), OverrideKind.WORKDAY_SWAP)
        assertEquals(CalendarDraftMutation.APPLIED, out.status)
        assertTrue(out.draft.overrides.first().isWorkday)
        assertEquals("Cuti bersama", out.draft.overrides.first().note)
    }

    @Test
    fun re_applying_the_same_kind_is_a_no_op() {
        val start = cal(listOf(ov(20_000, isWorkday = false)))
        val out = applyOverrideKindToDraft(start, listOf(20_000), OverrideKind.HOLIDAY)
        assertEquals(CalendarDraftMutation.NO_OP, out.status)
    }

    @Test
    fun replacing_an_existing_override_does_NOT_count_toward_the_limit() {
        // Jumlah PROYEKSI, bukan jumlah sekarang + jumlah target. Kalau salah, menyunting
        // ulang tanggal yang sudah punya override akan ditolak padahal sah.
        val full = (0 until OVERRIDE_PUBLISH_BLOCK_THRESHOLD).map { ov(20_000 + it) }
        val out = applyOverrideKindToDraft(cal(full), listOf(20_000), OverrideKind.WORKDAY_SWAP)
        assertEquals(CalendarDraftMutation.APPLIED, out.status)
        assertEquals(OVERRIDE_PUBLISH_BLOCK_THRESHOLD, out.draft.overrides.size)
    }

    @Test
    fun exceeding_the_limit_is_rejected_and_leaves_the_draft_untouched() {
        val full = (0 until OVERRIDE_PUBLISH_BLOCK_THRESHOLD).map { ov(20_000 + it) }
        val before = cal(full)
        val out = applyOverrideKindToDraft(before, listOf(29_999), OverrideKind.HOLIDAY)
        assertEquals(CalendarDraftMutation.REJECTED_LIMIT, out.status)
        assertEquals(before, out.draft)
    }

    @Test
    fun custom_without_a_rule_is_rejected_as_no_op() {
        val out = applyOverrideKindToDraft(cal(), listOf(20_000), OverrideKind.CUSTOM, customRule = null)
        assertEquals(CalendarDraftMutation.NO_OP, out.status)
    }

    @Test
    fun an_empty_target_list_changes_nothing() {
        val out = applyOverrideKindToDraft(cal(), emptyList(), OverrideKind.HOLIDAY)
        assertEquals(CalendarDraftMutation.NO_OP, out.status)
    }

    @Test
    fun overrides_stay_sorted_by_date() {
        val out = applyOverrideKindToDraft(cal(), listOf(20_010, 20_000, 20_005), OverrideKind.HOLIDAY)
        val days = out.draft.overrides.map { it.date.toEpochDays().toInt() }
        assertEquals(days.sorted(), days)
    }

    @Test
    fun removing_an_override_applies_and_removing_a_missing_one_is_a_no_op() {
        val start = cal(listOf(ov(20_000)))
        assertEquals(CalendarDraftMutation.APPLIED, removeOverrideFromDraft(start, 20_000).status)
        assertEquals(0, removeOverrideFromDraft(start, 20_000).draft.overrides.size)
        assertEquals(CalendarDraftMutation.NO_OP, removeOverrideFromDraft(start, 29_999).status)
    }
}
