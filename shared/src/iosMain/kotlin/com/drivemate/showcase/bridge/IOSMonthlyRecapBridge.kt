package com.drivemate.showcase.bridge

import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.domain.calendar.monthEpochDayRange
import com.drivemate.showcase.domain.report.MonthlyDriverRecap
import com.drivemate.showcase.domain.report.MonthlyDriverRecapCsvLabels
import com.drivemate.showcase.domain.report.buildMonthlyDriverRecapCsvSection
import com.drivemate.showcase.domain.report.recapMinutesToHm
import com.drivemate.showcase.domain.usecase.report.ExportReportCsvRequest
import com.drivemate.showcase.domain.usecase.report.ExportReportCsvUseCase
import com.drivemate.showcase.domain.usecase.report.GetMonthlyDriverRecapUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * Rekap bulanan per driver untuk iOS — padanan `AdminMonthlyRecapViewModel` Android
 * (B2/D3 pada PRD §7, Blok 4.3; sisi iOS = Blok 6.5 "admin **melihat**: laporan, ekspor").
 *
 * ## Nol method bridge Swift baru, dan itu bukan kebetulan
 * [GetMonthlyDriverRecapUseCase] hanya bergantung pada tiga repository yang di iOS SUDAH
 * nyata: `IOSAdminDataRepository.getWorkdaysByRange`, `IOSWorkCalendarRepository.getYear`,
 * dan `IOSUserDirectoryRepository.getAllDriverDisplayNames`. Jadi seluruh fitur ini hidup
 * tanpa satu pun query Firestore baru di Swift — berbeda dari riwayat pelanggaran
 * kecepatan, yang memang butuh bentuk query baru.
 *
 * ## Nol aritmetika di sini, dan itu kunci penerimaan 4.3
 * Syaratnya: angka rekap **identik** dengan Riwayat Kerja driver untuk bulan yang sama.
 * Satu-satunya cara menjamin itu adalah memanggil rumus yang sama, bukan menulis rumus
 * kedua yang kebetulan sepakat — lihat KDoc `MonthlyDriverRecap`. Karena itu file ini nol
 * penjumlahan menit, nol pembulatan, nol ambang lembur; label "H:MM" pun diambil dari
 * [recapMinutesToHm], fungsi yang sama dipakai kolom CSV-nya.
 *
 * ## `todayEpochDay` dan `parseHm` diputuskan DI SINI, di Asia/Jakarta
 * Keduanya masuk sebagai parameter use case justru supaya tidak ada yang membaca jam
 * sistem di dalam. Diberikan dari [IOSDriverTimezone] — zona yang sama dipakai
 * [IOSWorkHistoryBridge], sehingga layar rekap admin dan layar Riwayat Kerja driver tidak
 * bisa berbeda pendapat soal hari mana yang "belum tiba" pada bulan yang sedang berjalan.
 * Itu jenis selisih yang paling sulit dilacak karena hilang sendiri begitu bulannya lewat.
 *
 * ## Label CSV datang dari Swift
 * `commonMain` tidak punya akses `Localizable.strings`, jadi kesebelas label diterima
 * sebagai parameter — persis alasan yang sama kenapa versi Android meneruskan
 * `stringResource`. Yang TIDAK datang dari Swift adalah bentuk barisnya: perakitan CSV
 * tetap di [buildMonthlyDriverRecapCsvSection] supaya berkas yang beredar lewat email
 * tidak bisa berbeda angka dari layar yang menghasilkannya.
 */
internal class IOSMonthlyRecapBridge(
    private val scope: CoroutineScope,
    private val getMonthlyRecap: GetMonthlyDriverRecapUseCase,
    private val exportCsv: ExportReportCsvUseCase = ExportReportCsvUseCase()
) {

    /** Rekap terakhir yang berhasil dimuat — sumber untuk ekspor CSV. */
    private var lastRecap: MonthlyDriverRecap? = null

    /**
     * Bulan berjalan menurut Asia/Jakarta, untuk state awal layar.
     *
     * Memakai ulang [IOSWorkHistoryMonthRef] alih-alih tipe baru: aritmetika maju/mundur
     * bulannya identik, dan pergantian tahun di Januari/Desember adalah tempat off-by-one
     * klasik yang tidak perlu ditulis dua kali.
     */
    fun currentMonth(): IOSWorkHistoryMonthRef {
        val date = LocalDate.fromEpochDays(TimeUtil.todayEpochDay(IOSDriverTimezone).toInt())
        return IOSWorkHistoryMonthRef(year = date.year, month = date.monthNumber)
    }

    fun loadMonth(
        year: Int,
        month: Int,
        completion: (IOSMonthlyRecapSnapshot?, String?) -> Unit
    ) {
        if (month < 1 || month > 12) {
            completion(null, "Invalid month: $month")
            return
        }
        val range = monthEpochDayRange(year, month)
        scope.launch {
            val res = getMonthlyRecap(
                year = year,
                month = month,
                startEpochDay = range.first,
                endEpochDay = range.last,
                todayEpochDay = TimeUtil.todayEpochDay(IOSDriverTimezone).toInt(),
                parseHm = { hm, day -> TimeUtil.parseHm(hm, day.toLong(), IOSDriverTimezone) }
            )
            when (res) {
                is AppResult.Success -> {
                    lastRecap = res.data.recap
                    completion(res.data.recap.toIosSnapshot(res.data.isTruncated), null)
                }
                is AppResult.Failure -> {
                    // Snapshot lama TIDAK dibuang di sini; Swift yang memutuskan menahannya.
                    completion(null, res.throwable.message ?: "Failed to load monthly recap.")
                }
            }
        }
    }

    /**
     * Rakit CSV dari rekap yang SEDANG tampil.
     *
     * Sengaja memakai [lastRecap], bukan memuat ulang: berkas yang dibagikan harus berisi
     * angka yang persis dilihat admin saat menekan ekspor. Memuat ulang membuka celah kecil
     * tapi nyata di mana berkasnya berbeda dari layarnya — dan itu selisih yang beredar
     * lewat email tanpa layar di sebelahnya.
     *
     * `null` bila belum ada rekap yang berhasil dimuat.
     */
    fun buildCsv(labels: IOSMonthlyRecapCsvLabels): String? {
        val recap = lastRecap ?: return null
        return exportCsv(
            ExportReportCsvRequest(
                sections = listOf(
                    buildMonthlyDriverRecapCsvSection(
                        recap = recap,
                        labels = MonthlyDriverRecapCsvLabels(
                            sectionTitle = labels.sectionTitle,
                            driver = labels.driver,
                            scheduledDays = labels.scheduledDays,
                            workedDays = labels.workedDays,
                            absentDays = labels.absentDays,
                            normalHours = labels.normalHours,
                            normalMinutes = labels.normalMinutes,
                            overtimeHours = labels.overtimeHours,
                            overtimeMinutes = labels.overtimeMinutes,
                            totalRow = labels.totalRow,
                            unknownDriver = labels.unknownDriver
                        )
                    )
                )
            )
        )
    }
}

/**
 * Kesebelas label kolom CSV, dilokalkan di Swift.
 *
 * Dibawa sebagai satu tipe, bukan sebelas parameter: method bridge dengan sebelas `String`
 * berurutan adalah tempat yang sangat mudah untuk menukar dua argumen tanpa error compile,
 * dan hasilnya berkas dengan judul kolom bertukar.
 */
data class IOSMonthlyRecapCsvLabels(
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
    val unknownDriver: String
)

/**
 * Satu bulan rekap, siap dirender.
 *
 * [scheduledWorkdayCountPerDriver] dibawa terpisah dari total karena satuannya berbeda:
 * kalender kerja sama untuk semua driver, jadi menjumlahkannya menghasilkan hari-orang yang
 * terbaca sebagai hari. Alasan lengkapnya di KDoc `MonthlyDriverRecap`.
 */
data class IOSMonthlyRecapSnapshot(
    val year: Int,
    val month: Int,
    val rows: List<IOSMonthlyRecapRowSnapshot>,
    val scheduledWorkdayCountPerDriver: Int,
    val totalWorkedDayCount: Int,
    /** Total jam kerja lintas driver, sudah "H:MM" — jam TIDAK dibungkus 24. */
    val totalNormalLabel: String,
    val totalOvertimeLabel: String,
    /**
     * Bulan ini masih punya hari yang belum tiba, jadi selisih
     * terjadwal-vs-dikerjakan itu WAJAR dan layar harus mengatakannya. Tanpa ini admin
     * membaca "terjadwal 22, dikerjakan 5" dan wajar menyimpulkan ada 17 hari absen.
     */
    val hasFutureDays: Boolean,
    /** Batas baca tersentuh — angkanya bisa kurang, dan itu harus terlihat di layar. */
    val isTruncated: Boolean
)

/** Satu baris rekap: seorang driver, satu bulan. */
data class IOSMonthlyRecapRowSnapshot(
    val driverUid: String,
    /** `null` bila direktori pengguna tidak memuatnya — UI menampilkan penanda, bukan UID. */
    val driverName: String?,
    val scheduledDayCount: Int,
    val workedDayCount: Int,
    val absentScheduledDayCount: Int,
    /** "H:MM", bentuk yang sama dengan kolom CSV-nya. */
    val normalLabel: String,
    val overtimeLabel: String
)

internal fun MonthlyDriverRecap.toIosSnapshot(isTruncated: Boolean): IOSMonthlyRecapSnapshot {
    val totalNormal = (totalWorkedMinutes - totalOvertimeMinutes).coerceAtLeast(0)
    return IOSMonthlyRecapSnapshot(
        year = year,
        month = month,
        rows = rows.map { row ->
            val normal = (row.summary.totalWorkedMinutes - row.summary.totalOvertimeMinutes)
                .coerceAtLeast(0)
            IOSMonthlyRecapRowSnapshot(
                driverUid = row.driverUid,
                driverName = row.driverName,
                scheduledDayCount = row.summary.scheduledWorkdayCount,
                workedDayCount = row.summary.workedDayCount,
                absentScheduledDayCount = row.absentScheduledDayCount,
                normalLabel = recapMinutesToHm(normal),
                overtimeLabel = recapMinutesToHm(row.summary.totalOvertimeMinutes)
            )
        },
        scheduledWorkdayCountPerDriver = scheduledWorkdayCountPerDriver,
        totalWorkedDayCount = totalWorkedDayCount,
        totalNormalLabel = recapMinutesToHm(totalNormal),
        totalOvertimeLabel = recapMinutesToHm(totalOvertimeMinutes),
        hasFutureDays = hasFutureDays,
        isTruncated = isTruncated
    )
}
