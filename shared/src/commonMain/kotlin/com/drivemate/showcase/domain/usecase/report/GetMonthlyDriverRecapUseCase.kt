package com.drivemate.showcase.domain.usecase.report

import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.calendar.WorkCalendarYear
import com.drivemate.showcase.domain.repository.AdminDataRepository
import com.drivemate.showcase.domain.repository.UserDirectoryRepository
import com.drivemate.showcase.domain.repository.WorkCalendarRepository
import com.drivemate.showcase.domain.report.MonthlyDriverRecap
import com.drivemate.showcase.domain.report.buildMonthlyDriverRecap

/**
 * Merakit rekap bulanan per driver (Blok 4.3).
 *
 * ## Kenapa SATU query untuk semua driver, bukan satu per driver
 * `AdminDataRepository.getWorkdaysByRange` dengan `driverUid = null` memfilter
 * `dateEpochDay` pada rentang bulan dan mengurutkan berdasarkan **field yang sama**, jadi
 * ia tidak butuh indeks komposit apa pun. Versi per-driver akan menjadi
 * `driverUid ==` + rentang `dateEpochDay` × N driver: indeksnya memang ada di production
 * (`firestore.indexes.json`: `workdays` `driverUid` ASC + `dateEpochDay` DESC), tapi N
 * query untuk data yang bisa diambil sekali adalah pemborosan yang ikut memperbesar
 * peluang salah satunya gagal dan rekapnya sunyi-sunyi kurang satu driver.
 *
 * Jebakan yang dihindari: emulator Firestore membuat indeks otomatis, jadi bentuk query
 * yang butuh indeks tak-ada-di-production **lulus setiap gate lokal** lalu gagal di
 * perangkat. Karena itu bentuk query di sini sengaja meniru yang sudah terbukti dipakai.
 *
 * ## Kenapa daftar driver datang dari direktori, bukan dari catatan kerjanya
 * Kalau daftarnya diturunkan dari dokumen `workdays` bulan itu, driver yang **tidak masuk
 * sama sekali** tidak akan punya baris — dan absen penuh justru temuan yang paling dicari
 * di rekap bulanan. Jadi rosternya `getAllDriverDisplayNames()`, dan catatan kerja hanya
 * mengisinya.
 *
 * ## Kalender yang tidak terbaca
 * Jatuh ke `WorkCalendarYear(year)`, sama seperti sisi driver. Alasannya ada di komentar
 * di dalam fungsinya, dan itu bukan detail: perbedaan perlakuan di sini membuat dua layar
 * tidak sepakat soal bulan yang sama.
 *
 * ## `limit`
 * Satu bulan × seluruh driver. `DEFAULT_LIMIT` dipilih longgar, dan
 * [MonthlyDriverRecapResult.isTruncated] melaporkan kalau batasnya benar-benar tersentuh —
 * rekap upah yang diam-diam terpotong lebih buruk daripada rekap yang bilang "tidak
 * lengkap".
 */
class GetMonthlyDriverRecapUseCase(
    private val adminRepo: AdminDataRepository,
    private val calendarRepo: WorkCalendarRepository,
    private val directoryRepo: UserDirectoryRepository,
) {
    data class MonthlyDriverRecapResult(
        val recap: MonthlyDriverRecap,
        val calendar: WorkCalendarYear,
        /** Batas baca tersentuh — angkanya bisa kurang, dan itu harus terlihat di layar. */
        val isTruncated: Boolean,
    )

    suspend operator fun invoke(
        year: Int,
        month: Int,
        startEpochDay: Int,
        endEpochDay: Int,
        todayEpochDay: Int,
        parseHm: (String, Int) -> Long,
        limit: Int = DEFAULT_LIMIT,
    ): AppResult<MonthlyDriverRecapResult> {
        // Kalender tidak terbaca -> aturan default tahun itu, BUKAN gagal total.
        //
        // Ini menyamai `DriverWorkHistoryAgendaViewModel:124-132` dengan sengaja, dan itu
        // bagian dari kunci penerimaan 4.3. Versi pertama fungsi ini meneruskan
        // kegagalannya, dan hasilnya terlihat langsung di emulator: dokumen
        // `work_calendars/2026` belum ada, jadi layar Riwayat Kerja driver menampilkan
        // angka (memakai default) sementara rekap admin menampilkan "gagal dimuat" untuk
        // bulan yang SAMA. Dua layar yang tidak sepakat bahkan soal apakah datanya ada.
        //
        // Konsekuensinya libur khusus perusahaan tidak terlihat saat fallback aktif —
        // tapi itu konsekuensi yang sama persis yang sudah diterima sisi driver, jadi
        // kedua layar tetap melaporkan hari terjadwal yang sama.
        val calendar = when (val res = calendarRepo.getYear(year)) {
            is AppResult.Success -> res.data
            is AppResult.Failure -> WorkCalendarYear(year = year)
        }
        val driverNames = when (val res = directoryRepo.getAllDriverDisplayNames()) {
            is AppResult.Success -> res.data
            is AppResult.Failure -> return AppResult.Failure(res.throwable)
        }
        val workDays = when (
            val res = adminRepo.getWorkdaysByRange(
                startEpochDay = startEpochDay,
                endEpochDay = endEpochDay,
                driverUid = null,
                limit = limit,
            )
        ) {
            is AppResult.Success -> res.data
            is AppResult.Failure -> return AppResult.Failure(res.throwable)
        }
        return AppResult.Success(
            MonthlyDriverRecapResult(
                recap = buildMonthlyDriverRecap(
                    year = year,
                    month = month,
                    calendar = calendar,
                    driverUids = driverNames.keys.sorted(),
                    workDays = workDays,
                    driverNames = driverNames,
                    todayEpochDay = todayEpochDay,
                    parseHm = parseHm,
                ),
                calendar = calendar,
                isTruncated = workDays.size >= limit,
            )
        )
    }

    companion object {
        const val DEFAULT_LIMIT: Int = 2000
    }
}
