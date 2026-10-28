package com.drivemate.showcase

import com.drivemate.showcase.core.model.WorkDay
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.domain.calendar.WorkCalendarYear
import com.drivemate.showcase.domain.calendar.buildWorkHistoryMonth
import com.drivemate.showcase.domain.report.MonthlyDriverRecapCsvLabels
import com.drivemate.showcase.domain.report.buildMonthlyDriverRecap
import com.drivemate.showcase.domain.report.buildMonthlyDriverRecapCsvSection
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Blok 4.3 — rekap bulanan per driver + ekspor CSV.
 *
 * ## Apa yang test ini BISA dan TIDAK BISA buktikan
 * Rekap memanggil `buildWorkHistoryMonth`, fungsi yang persis dipakai layar Riwayat Kerja
 * driver. Jadi menuliskan "rekap == buildWorkHistoryMonth" adalah **tautologi** — ia
 * membuktikan sebuah fungsi sama dengan dirinya sendiri, dan tidak boleh disebut sebagai
 * bukti kunci penerimaan 4.3.
 *
 * Yang benar-benar dijaga di sini:
 * 1. rekap tidak menambahkan aritmetika sendiri di ATAS pemanggilan itu (agregasi lintas
 *    driver, angka turunan, dan format CSV);
 * 2. pengelompokan per driver benar — catatan driver lain tidak bocor ke baris tetangganya,
 *    yang adalah cara paling mudah rekap upah menjadi salah tanpa terlihat salah;
 * 3. `todayEpochDay` benar-benar disuntikkan, sehingga bulan berjalan tidak bergantung jam
 *    mesin yang menjalankannya.
 *
 * Yang **tidak** bisa dibuktikan di sini: bahwa `AdminDataRepository.getWorkdaysByRange`
 * dan `WorkDayRepository.getWorkDaysByRange` memetakan dokumen yang sama menjadi `WorkDay`
 * yang sama. Itu dua mapper Firestore di `androidMain`; kesepakatannya diperiksa dengan
 * membandingkan kedua layar pada bulan yang sama di emulator, dan hasilnya ditulis di body
 * PR — bukan diklaim dari sini.
 */
class MonthlyDriverRecapTest {

    private val jakarta = TimeZone.of("Asia/Jakarta")
    private val parseHm: (String, Int) -> Long = { hm, day ->
        TimeUtil.parseHm(hm, day.toLong(), jakarta)
    }

    private val year = 2026
    private val month = 9
    private val calendar = WorkCalendarYear(year = year)

    private fun epochDay(d: Int) = LocalDate(year, month, d).toEpochDays().toInt()

    /** 08:00–17:00 pada tanggal `d`: 9 jam kotor, 8 jam net, 0 lembur. */
    private fun workedDay(uid: String, d: Int, totalMinutes: Int, overtimeMinutes: Int): WorkDay {
        val day = epochDay(d)
        val dayStart = day * 24L * 60L * 60L * 1000L
        return WorkDay(
            id = "${uid}_$day",
            driverUid = uid,
            dateEpochDay = day,
            clockInMillis = dayStart + 8 * 3_600_000L,
            clockOutMillis = dayStart + 17 * 3_600_000L,
            totalMinutes = totalMinutes,
            overtimeMinutes = overtimeMinutes,
        )
    }

    // 30 September 2026 — seluruh bulan sudah lewat, jadi status harinya stabil.
    private val today = epochDay(30)

    private fun recap(
        driverUids: List<String>,
        workDays: List<WorkDay>,
        names: Map<String, String> = emptyMap(),
        todayEpochDay: Int = today,
    ) = buildMonthlyDriverRecap(
        year = year,
        month = month,
        calendar = calendar,
        driverUids = driverUids,
        workDays = workDays,
        driverNames = names,
        todayEpochDay = todayEpochDay,
        parseHm = parseHm,
    )

    // ─── Pengelompokan per driver ─────────────────────────────────────────────

    @Test
    fun eachDriverGetsExactlyItsOwnWorkdays() {
        // Tanggal 1 dan 2 September 2026 adalah Selasa dan Rabu — hari kerja.
        val rows = recap(
            driverUids = listOf("a", "b"),
            workDays = listOf(
                workedDay("a", 1, totalMinutes = 480, overtimeMinutes = 0),
                workedDay("a", 2, totalMinutes = 540, overtimeMinutes = 60),
                workedDay("b", 1, totalMinutes = 480, overtimeMinutes = 0),
            ),
        ).rows.associateBy { it.driverUid }

        assertEquals(2, rows.getValue("a").summary.workedDayCount)
        assertEquals(1, rows.getValue("b").summary.workedDayCount)
        assertEquals(1020, rows.getValue("a").summary.totalWorkedMinutes)
        assertEquals(480, rows.getValue("b").summary.totalWorkedMinutes)
        // Lembur driver a TIDAK boleh muncul di baris b.
        assertEquals(60, rows.getValue("a").summary.totalOvertimeMinutes)
        assertEquals(0, rows.getValue("b").summary.totalOvertimeMinutes)
    }

    @Test
    fun driverWithNoRecordsStillGetsAZeroRow() {
        // Absen penuh adalah temuan, bukan alasan menghilangkan drivernya dari daftar.
        val recap = recap(driverUids = listOf("a", "ghost"), workDays = listOf(
            workedDay("a", 1, totalMinutes = 480, overtimeMinutes = 0),
        ))
        val ghost = recap.rows.first { it.driverUid == "ghost" }
        assertEquals(0, ghost.summary.workedDayCount)
        assertEquals(0, ghost.summary.totalWorkedMinutes)
        assertTrue(ghost.summary.scheduledWorkdayCount > 0, "hari terjadwal tetap dari kalender")
        assertEquals(ghost.summary.scheduledWorkdayCount, ghost.absentScheduledDayCount)
    }

    @Test
    fun workdaysOfDriversNotAskedForAreIgnored() {
        val recap = recap(
            driverUids = listOf("a"),
            workDays = listOf(
                workedDay("a", 1, totalMinutes = 480, overtimeMinutes = 0),
                workedDay("outsider", 2, totalMinutes = 600, overtimeMinutes = 120),
            ),
        )
        assertEquals(1, recap.rows.size)
        assertEquals(480, recap.totalWorkedMinutes)
        assertEquals(0, recap.totalOvertimeMinutes)
    }

    @Test
    fun duplicateDriverUidsProduceOneRow() {
        val recap = recap(driverUids = listOf("a", "a"), workDays = emptyList())
        assertEquals(1, recap.rows.size)
    }

    // ─── Tidak ada aritmetika kedua ───────────────────────────────────────────

    @Test
    fun recapRowDelegatesToTheSameMonthBuilderTheDriverScreenUses() {
        // Bukan tautologi dalam bentuk yang berguna: yang dijaga adalah rekap TIDAK
        // menyisipkan langkah apa pun antara `buildWorkHistoryMonth` dan angka yang
        // ditampilkan — bukan bahwa aritmetikanya benar (itu tugas
        // WorkHistoryMonthBuilderTest / OvertimeFifteenMinuteRoundingTest).
        val workDays = listOf(
            workedDay("a", 1, totalMinutes = 524, overtimeMinutes = 44),
            workedDay("a", 2, totalMinutes = 480, overtimeMinutes = 0),
        )
        val direct = buildWorkHistoryMonth(
            year = year,
            month = month,
            calendar = calendar,
            workDays = workDays,
            todayEpochDay = today,
            parseHm = parseHm,
        ).summary
        val row = recap(driverUids = listOf("a"), workDays = workDays).rows.single()
        assertEquals(direct, row.summary)
    }

    @Test
    fun futureScheduledDaysAreNotCountedAsAbsent() {
        // Test paling penting di file ini, dan ia GAGAL pada implementasi pertama 4.3
        // yang menghitung absen sebagai "hari terjadwal - hari kerja": selisih itu buta
        // terhadap `todayEpochDay`, jadi rekap bulan BERJALAN melaporkan setiap hari
        // kerja yang belum tiba sebagai tidak hadir.
        val workDays = listOf(workedDay("a", 1, totalMinutes = 480, overtimeMinutes = 0))
        val asOfDay2 = recap(listOf("a"), workDays, todayEpochDay = epochDay(2)).rows.single()
        val asOfDay30 = recap(listOf("a"), workDays, todayEpochDay = epochDay(30)).rows.single()

        // 2 September 2026 adalah Rabu — hari kerja tanpa catatan, satu-satunya absen
        // sejauh ini. Tanggal 3-30 belum terjadi: FUTURE, bukan absen.
        assertEquals(1, asOfDay2.absentScheduledDayCount)
        // Begitu seluruh bulan lewat, semua hari terjadwal selain tanggal 1 jadi absen.
        assertEquals(
            asOfDay30.summary.scheduledWorkdayCount - 1,
            asOfDay30.absentScheduledDayCount,
        )
        // Dan inilah sebabnya absen TIDAK BISA diturunkan dari summary: summary-nya
        // identik untuk kedua nilai `today`. Angka yang dihitung dari dua bilangan di
        // dalamnya mustahil ikut bergerak.
        assertEquals(asOfDay2.summary, asOfDay30.summary)
        assertEquals(
            buildWorkHistoryMonth(year, month, calendar, workDays, epochDay(2), parseHm).summary,
            asOfDay2.summary,
        )
    }

    // ─── Angka turunan ────────────────────────────────────────────────────────

    @Test
    fun aWorkedWeekendDoesNotCancelAMissedScheduledDay() {
        // Bug kedua dari selisih "terjadwal - dikerjakan", dan yang lebih berbahaya:
        // 5 dan 6 September 2026 adalah Sabtu dan Minggu. Driver yang mengerjakan akhir
        // pekan tapi bolos satu hari kerja punya workedDayCount DI ATAS hari terjadwal,
        // sehingga selisihnya negatif lalu di-clamp ke 0 — absennya hilang tanpa jejak.
        val allButDayTwo = (1..30).filter { it != 2 }
            .map { workedDay("a", it, totalMinutes = 480, overtimeMinutes = 0) }
        val row = recap(listOf("a"), allButDayTwo).rows.single()
        assertTrue(
            row.summary.workedDayCount > row.summary.scheduledWorkdayCount,
            "akhir pekan ikut dikerjakan",
        )
        // Tanggal 2 (Rabu) benar-benar bolong dan harus terhitung tepat satu absen.
        assertEquals(1, row.absentScheduledDayCount)
    }

    @Test
    fun absentCountIsNeverNegative() {
        // Sekarang dijamin oleh konstruksinya: ia MENGHITUNG hari ber-state
        // SCHEDULED_ABSENT, bukan mengurangkan dua angka lalu men-clamp hasilnya.
        val allDays = (1..30).map { workedDay("a", it, totalMinutes = 480, overtimeMinutes = 0) }
        assertEquals(0, recap(listOf("a"), allDays).rows.single().absentScheduledDayCount)
    }

    @Test
    fun scheduledDaysAreReportedPerDriverNotSummedAcrossDrivers() {
        // Menjumlahkan hari terjadwal lintas driver menghasilkan hari-orang yang terbaca
        // sebagai hari — angka yang salah satuannya, bukan salah hitungannya.
        val recap = recap(driverUids = listOf("a", "b", "c"), workDays = emptyList())
        val perDriver = recap.rows.first().summary.scheduledWorkdayCount
        assertEquals(perDriver, recap.scheduledWorkdayCountPerDriver)
        assertTrue(recap.scheduledWorkdayCountPerDriver < perDriver * 3)
    }

    @Test
    fun namesAreTrimmedAndBlankBecomesNull() {
        val rows = recap(
            driverUids = listOf("a", "b"),
            workDays = emptyList(),
            names = mapOf("a" to "  Driver Satu  ", "b" to "   "),
        ).rows.associateBy { it.driverUid }
        assertEquals("Driver Satu", rows.getValue("a").driverName)
        assertNull(rows.getValue("b").driverName)
    }

    @Test
    fun crossDriverTotalsAreTheSumOfTheRows() {
        val recap = recap(
            driverUids = listOf("a", "b"),
            workDays = listOf(
                workedDay("a", 1, totalMinutes = 540, overtimeMinutes = 60),
                workedDay("b", 1, totalMinutes = 480, overtimeMinutes = 0),
                workedDay("b", 2, totalMinutes = 495, overtimeMinutes = 15),
            ),
        )
        assertEquals(
            recap.rows.sumOf { it.summary.totalWorkedMinutes },
            recap.totalWorkedMinutes,
        )
        assertEquals(
            recap.rows.sumOf { it.summary.totalOvertimeMinutes },
            recap.totalOvertimeMinutes,
        )
        assertEquals(3, recap.totalWorkedDayCount)
    }

    @Test
    fun hasFutureDaysMarksAMonthThatIsNotOverYet() {
        // Dipakai layar untuk menjelaskan kenapa "terjadwal" jauh lebih besar dari
        // "dikerjakan + tidak hadir" pada bulan berjalan.
        assertTrue(
            recap(listOf("a"), emptyList(), todayEpochDay = epochDay(2)).hasFutureDays,
            "tanggal 3-30 belum tiba",
        )
        assertTrue(
            !recap(listOf("a"), emptyList(), todayEpochDay = epochDay(30)).hasFutureDays,
            "seluruh bulan sudah lewat",
        )
    }

    // ─── CSV ──────────────────────────────────────────────────────────────────

    private val labels = MonthlyDriverRecapCsvLabels(
        sectionTitle = "Rekap Bulanan",
        driver = "Driver",
        scheduledDays = "Hari terjadwal",
        workedDays = "Hari kerja",
        absentDays = "Tidak hadir",
        normalHours = "Jam normal",
        normalMinutes = "Menit normal",
        overtimeHours = "Jam lembur",
        overtimeMinutes = "Menit lembur",
        totalRow = "TOTAL",
        unknownDriver = "-",
    )

    @Test
    fun csvNumbersEqualTheRecapNumbersExactly() {
        val recap = recap(
            driverUids = listOf("a"),
            workDays = listOf(workedDay("a", 1, totalMinutes = 540, overtimeMinutes = 60)),
            names = mapOf("a" to "Driver Satu"),
        )
        val section = buildMonthlyDriverRecapCsvSection(recap, labels)
        val row = section.rows.first()
        val summary = recap.rows.single().summary
        assertEquals("Driver Satu", row[0])
        assertEquals(summary.scheduledWorkdayCount.toString(), row[1])
        assertEquals(summary.workedDayCount.toString(), row[2])
        // Menit mentah ada di kolom sendiri supaya penerima CSV bisa menjumlahkan kolom.
        assertEquals((summary.totalWorkedMinutes - summary.totalOvertimeMinutes).toString(), row[5])
        assertEquals(summary.totalOvertimeMinutes.toString(), row[7])
    }

    @Test
    fun csvTotalRowLeavesScheduledDaysBlank() {
        val recap = recap(driverUids = listOf("a", "b"), workDays = emptyList())
        val section = buildMonthlyDriverRecapCsvSection(recap, labels)
        val total = section.rows.last()
        assertEquals("TOTAL", total[0])
        assertEquals("", total[1], "hari terjadwal tidak dijumlahkan lintas driver")
        assertEquals("", total[3], "tidak hadir juga tidak dijumlahkan")
    }

    @Test
    fun csvHasOneRowPerDriverPlusTotal() {
        val section = buildMonthlyDriverRecapCsvSection(
            recap(driverUids = listOf("a", "b", "c"), workDays = emptyList()),
            labels,
        )
        assertEquals(4, section.rows.size)
        assertEquals(8, section.headers.size)
        section.rows.forEach { assertEquals(8, it.size, "setiap baris harus selebar headernya") }
    }

    @Test
    fun csvUnknownDriverUsesThePlaceholderNotAnEmptyCell() {
        val section = buildMonthlyDriverRecapCsvSection(
            recap(driverUids = listOf("nameless"), workDays = emptyList()),
            labels,
        )
        assertEquals("-", section.rows.first()[0])
    }

    @Test
    fun cumulativeHoursAreNotWrappedAtTwentyFour() {
        // 187 jam kerja sebulan harus terbaca "187:30", bukan sisa modulo 24.
        val allDays = (1..30).map { workedDay("a", it, totalMinutes = 480, overtimeMinutes = 0) }
        val section = buildMonthlyDriverRecapCsvSection(recap(listOf("a"), allDays), labels)
        val normalHm = section.rows.first()[4]
        val normalMinutes = section.rows.first()[5].toInt()
        assertEquals("${normalMinutes / 60}:${(normalMinutes % 60).toString().padStart(2, '0')}", normalHm)
        assertTrue(normalMinutes / 60 > 24, "sebulan kerja melebihi 24 jam")
    }
}
