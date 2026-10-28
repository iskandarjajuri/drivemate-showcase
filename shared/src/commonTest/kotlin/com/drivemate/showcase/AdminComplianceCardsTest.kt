package com.drivemate.showcase

import com.drivemate.showcase.domain.admin.AdminComplianceState
import com.drivemate.showcase.domain.admin.ComplianceRatio
import com.drivemate.showcase.domain.admin.computeAdminComplianceCards
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Blok 2.1 — kartu kepatuhan berpenyebut (A1 pada PRD §7). */
class AdminComplianceCardsTest {

    private val twelve = (1..12).map { "d$it" }.toSet()

    @Test
    fun holidayReportsNoScheduleNotZero() {
        // Kriteria terima PRD: pada hari libur kartu berbunyi "Tidak ada jadwal kerja hari ini",
        // BUKAN "0". Keduanya berarti hal yang sangat berbeda.
        val cards = computeAdminComplianceCards(
            isCompanyWorkday = false,
            activeDriverUids = twelve,
            attendanceInUids = emptySet(),
            checklistSubmittedUids = emptySet(),
            safetySubmittedUids = emptySet(),
            sopAcceptedUids = emptySet(),
        )
        assertEquals(AdminComplianceState.NO_SCHEDULE_TODAY, cards.state)
        assertEquals(0, cards.totalOutstanding, "angka tidak bermakna di keadaan ini")
    }

    @Test
    fun twelveScheduledNineCheckedInReadsNineOfTwelve() {
        // Kriteria terima PRD, kata per kata: 12 terjadwal, 9 absen masuk -> "9/12".
        val cards = computeAdminComplianceCards(
            isCompanyWorkday = true,
            activeDriverUids = twelve,
            attendanceInUids = (1..9).map { "d$it" }.toSet(),
            checklistSubmittedUids = emptySet(),
            safetySubmittedUids = emptySet(),
            sopAcceptedUids = emptySet(),
        )
        assertEquals(AdminComplianceState.READY, cards.state)
        assertEquals(9, cards.attendance.done)
        assertEquals(12, cards.attendance.scheduled)
        assertEquals(3, cards.attendance.outstanding, "tiga orang terlewat — inilah yang dicari admin")
        assertEquals(75, cards.attendance.percent)
    }

    @Test
    fun numeratorIsIntersectedNeverExceedsDenominator() {
        // Driver yang dikecualikan tapi tetap mengirim checklist tidak boleh membuat
        // pembilang melebihi penyebut. "13/12" merusak kepercayaan pada seluruh kartu.
        val cards = computeAdminComplianceCards(
            isCompanyWorkday = true,
            activeDriverUids = twelve,
            attendanceInUids = twelve + "hantu",
            checklistSubmittedUids = twelve + setOf("hantu", "hantu2"),
            safetySubmittedUids = emptySet(),
            sopAcceptedUids = emptySet(),
            excludedDriverUids = setOf("d12"),
        )
        assertEquals(11, cards.attendance.scheduled)
        assertEquals(11, cards.attendance.done)
        assertEquals(11, cards.checklist.done, "dua UID asing diabaikan")
        assertTrue(cards.attendance.isComplete)
    }

    @Test
    fun exclusionShrinksBothSides() {
        val cards = computeAdminComplianceCards(
            isCompanyWorkday = true,
            activeDriverUids = twelve,
            attendanceInUids = (1..9).map { "d$it" }.toSet(),
            checklistSubmittedUids = emptySet(),
            safetySubmittedUids = emptySet(),
            sopAcceptedUids = emptySet(),
            excludedDriverUids = setOf("d10", "d11", "d12"),
        )
        // d10..d12 dikecualikan dan memang belum absen, jadi 9/9 — beres.
        assertEquals(9, cards.attendance.scheduled)
        assertEquals(9, cards.attendance.done)
        assertEquals(0, cards.attendance.outstanding)
    }

    @Test
    fun percentIsNullNotZeroWhenNothingScheduled() {
        // Pembagian nol bukan "0%", ia tak terdefinisi. Menampilkan 0% akan berbunyi seperti
        // kegagalan total padahal tidak ada yang perlu dikerjakan.
        val ratio = ComplianceRatio(done = 0, scheduled = 0)
        assertNull(ratio.percent)
        assertEquals(0, ratio.outstanding)
        assertEquals(false, ratio.isComplete, "nol dari nol bukan 'lengkap'")
    }

    @Test
    fun sopNumeratorIsCumulativeNotToday() {
        // SOP sekali diterima berlaku terus, jadi pembilangnya kumulatif sementara penyebutnya
        // tetap terjadwal-hari-ini. Perbedaan ini disengaja.
        val cards = computeAdminComplianceCards(
            isCompanyWorkday = true,
            activeDriverUids = twelve,
            attendanceInUids = emptySet(),
            checklistSubmittedUids = emptySet(),
            safetySubmittedUids = emptySet(),
            sopAcceptedUids = twelve,        // semuanya sudah menerima, entah kapan
        )
        assertEquals(12, cards.sop.done)
        assertEquals(12, cards.sop.scheduled)
        assertEquals(0, cards.attendance.done, "absensi tetap nol — kartu tidak saling mewarnai")
    }

    @Test
    fun totalOutstandingSumsAllFourOnlyWhenReady() {
        val cards = computeAdminComplianceCards(
            isCompanyWorkday = true,
            activeDriverUids = (1..10).map { "d$it" }.toSet(),
            attendanceInUids = (1..8).map { "d$it" }.toSet(),      // 2 belum
            checklistSubmittedUids = (1..7).map { "d$it" }.toSet(), // 3 belum
            safetySubmittedUids = (1..10).map { "d$it" }.toSet(),   // 0 belum
            sopAcceptedUids = (1..9).map { "d$it" }.toSet(),        // 1 belum
        )
        assertEquals(2 + 3 + 0 + 1, cards.totalOutstanding)
    }

    @Test
    fun everyStateIsDistinguishable() {
        // Empat keadaan, empat nilai berbeda. Kalau dua di antaranya bisa tampak sama,
        // admin tidak bisa membedakan "tidak ada tugas" dari "gagal memuat".
        assertEquals(4, AdminComplianceState.entries.size)
        assertEquals(
            AdminComplianceState.entries.size,
            AdminComplianceState.entries.toSet().size,
        )
    }

    @Test
    fun zeroDriversOnWorkdayIsReadyNotNoSchedule()
    {
        // Hari kerja tanpa driver aktif sama sekali: READY dengan penyebut 0, bukan
        // NO_SCHEDULE_TODAY. Yang pertama berarti "tidak ada orang", yang kedua "tidak ada
        // hari kerja" — dan admin perlu bisa membedakannya.
        val cards = computeAdminComplianceCards(
            isCompanyWorkday = true,
            activeDriverUids = emptySet(),
            attendanceInUids = emptySet(),
            checklistSubmittedUids = emptySet(),
            safetySubmittedUids = emptySet(),
            sopAcceptedUids = emptySet(),
        )
        assertEquals(AdminComplianceState.READY, cards.state)
        assertEquals(0, cards.attendance.scheduled)
        assertNull(cards.attendance.percent)
    }
}
