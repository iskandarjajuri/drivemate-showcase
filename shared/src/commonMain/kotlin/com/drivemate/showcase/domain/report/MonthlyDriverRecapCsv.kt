package com.drivemate.showcase.domain.report

import com.drivemate.showcase.domain.usecase.report.ReportCsvSection

/**
 * Ekspor CSV rekap bulanan — D3 pada PRD §7 (Blok 4.3).
 *
 * MEMFORMAT, tidak menghitung. Setiap angka yang keluar di sini berasal dari
 * [MonthlyDriverRecap] apa adanya; nol penjumlahan, nol pembulatan, nol konversi satuan
 * selain menit→"H:MM" yang murni tampilan. Satu file CSV yang angkanya berbeda dari layar
 * yang menghasilkannya adalah bentuk kebohongan yang paling sulit dibantah, karena ia
 * beredar lewat email tanpa layar di sebelahnya.
 *
 * Menit **juga** dikeluarkan mentah di kolom tersendiri, bukan hanya "H:MM": penerima CSV
 * menjumlahkan kolom di spreadsheet, dan "8:30" tidak bisa dijumlahkan tanpa parsing.
 *
 * Label diterima sebagai parameter, bukan diambil dari `stringResource`: file ini ada di
 * `commonMain` yang tidak punya akses resource Android, dan justru itu yang membuatnya
 * bisa diuji.
 */
data class MonthlyDriverRecapCsvLabels(
    val sectionTitle: String,
    val driver: String,
    val scheduledDays: String,
    val workedDays: String,
    val absentDays: String,
    val normalHours: String,
    val normalMinutes: String,
    val overtimeHours: String,
    val overtimeMinutes: String,
    val totalRow: String,
    val unknownDriver: String,
)

/**
 * Menit menjadi "H:MM" — jam TIDAK dibatasi 24, dan itu disengaja.
 *
 * Ini durasi kumulatif satu bulan, bukan jam dinding: 187 jam kerja harus terbaca "187:30",
 * bukan "19:30" sisa modulo. Repo ini punya `minutesToHm` di `domain/calendar`, tapi ia
 * `internal` di modulnya dan dipakai konteks per-hari; menyalin perilakunya ke sini dengan
 * satu test yang menjaganya lebih murah daripada melebarkan visibilitasnya.
 */
internal fun recapMinutesToHm(minutes: Int): String {
    val safe = minutes.coerceAtLeast(0)
    val hours = safe / 60
    val mins = safe % 60
    return "$hours:${if (mins < 10) "0$mins" else "$mins"}"
}

/**
 * Satu seksi CSV berisi satu baris per driver plus baris TOTAL.
 *
 * Baris TOTAL sengaja mengosongkan kolom "hari terjadwal": kalender kerja sama untuk semua
 * driver, jadi menjumlahkannya menghasilkan hari-orang yang terbaca sebagai hari. Angkanya
 * sudah ada di setiap baris driver.
 */
fun buildMonthlyDriverRecapCsvSection(
    recap: MonthlyDriverRecap,
    labels: MonthlyDriverRecapCsvLabels,
): ReportCsvSection {
    val headers = listOf(
        labels.driver,
        labels.scheduledDays,
        labels.workedDays,
        labels.absentDays,
        labels.normalHours,
        labels.normalMinutes,
        labels.overtimeHours,
        labels.overtimeMinutes,
    )
    val driverRows = recap.rows.map { row ->
        val normal = (row.summary.totalWorkedMinutes - row.summary.totalOvertimeMinutes)
            .coerceAtLeast(0)
        listOf(
            row.driverName ?: labels.unknownDriver,
            row.summary.scheduledWorkdayCount.toString(),
            row.summary.workedDayCount.toString(),
            row.absentScheduledDayCount.toString(),
            recapMinutesToHm(normal),
            normal.toString(),
            recapMinutesToHm(row.summary.totalOvertimeMinutes),
            row.summary.totalOvertimeMinutes.toString(),
        )
    }
    val totalNormal = (recap.totalWorkedMinutes - recap.totalOvertimeMinutes).coerceAtLeast(0)
    val totalRow = listOf(
        labels.totalRow,
        "",
        recap.totalWorkedDayCount.toString(),
        "",
        recapMinutesToHm(totalNormal),
        totalNormal.toString(),
        recapMinutesToHm(recap.totalOvertimeMinutes),
        recap.totalOvertimeMinutes.toString(),
    )
    return ReportCsvSection(
        title = labels.sectionTitle,
        headers = headers,
        rows = driverRows + listOf(totalRow),
    )
}
