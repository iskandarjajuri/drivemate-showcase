package com.drivemate.showcase

import com.drivemate.showcase.core.model.AttendanceEntry
import com.drivemate.showcase.core.model.WorkDay
import com.drivemate.showcase.core.util.CsvWriter
import com.drivemate.showcase.domain.usecase.report.ExportReportCsvRequest
import com.drivemate.showcase.domain.usecase.report.ExportReportCsvUseCase
import com.drivemate.showcase.domain.calendar.payableOvertimeMinutes
import com.drivemate.showcase.domain.usecase.report.ReportCsvSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `CsvWriter` — dipakai KEDUA platform, dan sebelum ini **nol test**.
 *
 * Dua hal yang ditangani file ini punya konsekuensi nyata kalau salah:
 *  - **Escaping.** Nama lokasi yang mengandung koma atau tanda kutip akan menggeser seluruh
 *    kolom pada baris itu. Gejalanya bukan crash — file-nya terbuka dengan senang hati di
 *    Excel, hanya isinya yang salah kolom, dan tidak ada yang menyadarinya.
 *  - **Masking PII.** UID driver tidak boleh keluar mentah ke file yang dibagikan; aturan
 *    keamanan repo ini menuntutnya.
 *
 * Ditulis saat menambahkan export CSV ke iOS: iOS akan memanggil fungsi yang sama, jadi
 * membiarkannya tanpa test berarti menggandakan risiko yang tidak terlihat.
 */
class CsvWriterTest {

    // ---------- Escaping ----------

    @Test
    fun a_plain_cell_is_not_quoted() {
        assertEquals("Budi", CsvWriter.escapeCsvCell("Budi"))
    }

    @Test
    fun a_cell_containing_a_comma_is_quoted() {
        // Tanpa kutip, "Jakarta, Pusat" jadi DUA kolom dan seluruh sisa baris bergeser.
        assertEquals("\"Jakarta, Pusat\"", CsvWriter.escapeCsvCell("Jakarta, Pusat"))
    }

    @Test
    fun a_quote_inside_a_cell_is_doubled_and_the_cell_is_quoted() {
        assertEquals("\"He said \"\"hi\"\"\"", CsvWriter.escapeCsvCell("He said \"hi\""))
    }

    @Test
    fun newlines_inside_a_cell_are_quoted_so_the_row_does_not_split() {
        // Catatan penolakan bisa multi-baris. Tanpa kutip, satu baris data jadi dua.
        assertEquals("\"baris1\nbaris2\"", CsvWriter.escapeCsvCell("baris1\nbaris2"))
        assertEquals("\"baris1\rbaris2\"", CsvWriter.escapeCsvCell("baris1\rbaris2"))
    }

    @Test
    fun a_null_cell_becomes_empty_not_the_text_null() {
        assertEquals("", CsvWriter.escapeCsvCell(null))
    }

    // ---------- Bentuk tabel ----------

    @Test
    fun a_short_row_is_padded_so_columns_stay_aligned() {
        // Baris pendek yang tidak dipad akan menggeser kolom pada baris berikutnya.
        val csv = CsvWriter.tableToCsv(listOf("A", "B", "C"), listOf(listOf("1")))
        val dataLine = csv.lines()[1]
        assertEquals("1,,", dataLine)
    }

    @Test
    fun an_over_long_row_is_truncated_to_the_header_width() {
        val csv = CsvWriter.tableToCsv(listOf("A", "B"), listOf(listOf("1", "2", "3")))
        assertEquals("1,2", csv.lines()[1])
    }

    @Test
    fun the_header_is_always_the_first_line() {
        val csv = CsvWriter.tableToCsv(listOf("Date", "Type"), listOf(listOf("2026-08-17", "in")))
        assertEquals("Date,Type", csv.lines().first())
    }

    @Test
    fun a_table_with_no_rows_still_emits_its_header() {
        // File tanpa header tidak bisa ditafsirkan penerimanya.
        val csv = CsvWriter.tableToCsv(listOf("Date", "Type"), emptyList())
        assertEquals("Date,Type", csv.lines().first())
    }

    // ---------- Masking PII ----------

    @Test
    fun raw_driver_uids_never_appear_in_attendance_export() {
        val uid = "aBcDeF1234567890"
        val csv = CsvWriter.attendanceToCsv(
            rows = listOf(
                AttendanceEntry(
                    id = "a1",
                    driverUid = uid,
                    type = "in",
                    timestampMillis = 1_755_000_000_000,
                    locationName = "Gate 1",
                    latitude = -6.2,
                    longitude = 106.8
                )
            )
        )
        assertFalse(csv.contains(uid), "UID mentah bocor ke CSV:\n$csv")
        assertTrue(csv.contains("User-7890"), "mask 4 digit terakhir tidak ditemukan:\n$csv")
    }

    @Test
    fun a_location_name_with_a_comma_does_not_shift_the_columns() {
        val csv = CsvWriter.attendanceToCsv(
            rows = listOf(
                AttendanceEntry(
                    id = "a2",
                    driverUid = "uid1234",
                    type = "in",
                    timestampMillis = 1_755_000_000_000,
                    locationName = "Jakarta, Pusat",
                    latitude = null,
                    longitude = null
                )
            )
        )
        val headerCount = csv.lines()[0].split(",").size
        // Baris data dikutip dengan benar, jadi jumlah koma tingkat-atasnya tetap sama.
        assertTrue(csv.contains("\"Jakarta, Pusat\""), csv)
        assertEquals(8, headerCount)
    }

    // ---------- Workday export ----------

    private fun workDay(
        totalMinutes: Int = 540,
        overtimeMinutes: Int = 0,
        approved: Boolean? = null,
        note: String? = null
    ) = WorkDay(
        id = "wd1",
        driverUid = "uid9876",
        dateEpochDay = 20_668,
        clockInMillis = 1_754_000_000_000,
        clockOutMillis = 1_754_030_000_000,
        totalMinutes = totalMinutes,
        overtimeMinutes = overtimeMinutes,
        approvedByExpat = approved,
        rejectionNote = note
    )

    @Test
    fun workday_status_reflects_the_approval_flag() {
        assertTrue(CsvWriter.workDaysToAdminCsv(listOf(workDay(approved = true))).contains("approved"))
        assertTrue(CsvWriter.workDaysToAdminCsv(listOf(workDay(approved = false))).contains("rejected"))
        assertTrue(CsvWriter.workDaysToAdminCsv(listOf(workDay(approved = null))).contains("pending"))
    }

    @Test
    fun the_admin_export_reports_RECORDED_overtime_and_says_so_in_the_header() {
        // Blok 4.4. Ekspor ini melaporkan isi dokumen, BUKAN lembur terbayar: 44 menit
        // tersimpan keluar sebagai 44, sementara CSV Rekap Bulanan untuk hari yang sama
        // menuliskan 30 (ambang 30 + pembulatan 15). Keduanya benar untuk pertanyaan yang
        // berbeda, jadi headernya yang harus membedakannya — kalau tidak, pembaca yang
        // membandingkan dua file menyimpulkan salah satunya bug.
        val csv = CsvWriter.workDaysToAdminCsv(listOf(workDay(overtimeMinutes = 44)))
        val headers = csv.lines()[0].split(",")
        val overtimeColumn = headers.indexOf("RecordedOvertimeMinutes")
        assertTrue(overtimeColumn >= 0, "header harus menyebut TERCATAT:\n$csv")
        assertEquals("44", csv.lines()[1].split(",")[overtimeColumn], csv)
        assertEquals(30, payableOvertimeMinutes(44), "pembanding: yang terbayar memang beda")
    }

    @Test
    fun a_recorded_overtime_below_zero_is_reported_as_zero() {
        // Lembur negatif tidak masuk akal dan terbaca sebagai data rusak oleh penerima file.
        val csv = CsvWriter.workDaysToAdminCsv(listOf(workDay(overtimeMinutes = -240)))
        assertFalse(csv.contains("-240"), csv)
        val overtimeColumn = csv.lines()[0].split(",").indexOf("RecordedOvertimeMinutes")
        assertEquals("0", csv.lines()[1].split(",")[overtimeColumn], csv)
    }

    @Test
    fun a_multiline_cell_stays_on_one_csv_row() {
        // Dulu diuji lewat workDaysToCsv (dihapus di Blok 4.4 — ia punya rumus lembur
        // ketiga, `totalMinutes - 480`). Sifat yang dijaga tetap sama dan tetap penting:
        // teks multi-baris tidak boleh memecah satu baris data menjadi dua.
        val csv = CsvWriter.tableToCsv(listOf("Note"), listOf(listOf("alasan\nbaris kedua")))
        assertTrue(csv.contains("\"alasan\nbaris kedua\""), csv)
    }

    @Test
    fun admin_workday_export_masks_the_approver_too() {
        val csv = CsvWriter.workDaysToAdminCsv(listOf(workDay(approved = true)))
        assertFalse(csv.contains("uid9876"), "UID driver bocor:\n$csv")
    }

    // ---------- Penggabungan section ----------

    @Test
    fun an_empty_request_produces_an_empty_string() {
        assertEquals("", ExportReportCsvUseCase()(ExportReportCsvRequest(sections = emptyList())))
    }

    @Test
    fun each_section_starts_with_its_own_title_line() {
        val csv = ExportReportCsvUseCase()(
            ExportReportCsvRequest(
                sections = listOf(
                    ReportCsvSection("Ringkasan", listOf("A"), listOf(listOf("1"))),
                    ReportCsvSection("Driver", listOf("B"), listOf(listOf("2")))
                )
            )
        )
        val lines = csv.lines()
        assertEquals("Ringkasan", lines[0])
        assertTrue(csv.contains("Driver"), csv)
    }

    @Test
    fun a_section_title_containing_a_comma_is_escaped() {
        val csv = ExportReportCsvUseCase()(
            ExportReportCsvRequest(
                sections = listOf(ReportCsvSection("Ringkasan, total", listOf("A"), emptyList()))
            )
        )
        assertEquals("\"Ringkasan, total\"", csv.lines()[0])
    }
}
