package com.drivemate.showcase.bridge

import com.drivemate.showcase.core.model.UserSession
import com.drivemate.showcase.core.model.toFirestore
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.domain.calendar.WorkCalendarYear
import com.drivemate.showcase.domain.calendar.buildWorkHistoryMonth
import com.drivemate.showcase.domain.calendar.monthEpochDayRange
import com.drivemate.showcase.domain.calendar.shiftWorkHistoryMonth
import com.drivemate.showcase.domain.usecase.calendar.GetWorkCalendarYearUseCase
import com.drivemate.showcase.domain.usecase.report.GetWorkDaysByRangeUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * Riwayat Kerja (Agenda View) untuk iOS — padanan `DriverWorkHistoryAgendaViewModel` Android.
 *
 * ## Kenapa bridge BARU, bukan memakai yang sudah ada
 * Ketiga jalur iOS yang ada tidak memadai, dan ini sudah dicek satu per satu:
 *  - [IOSDriverWorkdayBridge.loadDriverWorkdays] mengunci jendela rolling 3 bulan
 *    (`TimeUtil.rollingThreeMonthWindowEpochDays`) — layar ini butuh SATU bulan sembarang
 *    yang bisa dinavigasi maju/mundur.
 *  - `loadAttendanceReferenceWindow` mengunci HARI INI, sementara layar ini menghitung
 *    lembur untuk tanggal LAMPAU.
 *  - `IOSWorkCalendarSnapshot` LOSSY tepat di titik yang layar ini butuhkan: `holidays`
 *    memakai `mapNotNull { override.note?... }`, jadi libur perusahaan **tanpa** `note`
 *    dibuang diam-diam dan dari Swift tidak bisa dibedakan dari akhir pekan — persis
 *    kolaps 3-state yang `resolveDayCalendarKind` dibuat untuk memperbaiki.
 *
 * Karena itu bridge ini memakai [WorkCalendarYear] **langsung** dari use case, bukan lewat
 * `IOSWorkCalendarSnapshot`. **Jangan "rapikan" ini nanti dengan memakai snapshot itu** —
 * kelossyannya akan kembali sebagai libur perusahaan yang hilang, tanpa error apa pun.
 *
 * ## Kenapa nol logika tanggal di sini
 * Seluruh perakitan bulan ada di [buildWorkHistoryMonth] (`commonMain`), fungsi yang SAMA
 * yang dipanggil ViewModel Android. `shared/src/iosMain` tidak dikompilasi di mesin Linux,
 * jadi apa pun yang ditulis di sini setara tak-terverifikasinya dengan Swift; menaruh
 * logikanya di commonMain membuat suite `:shared:allTests` dan Maestro Android berlaku
 * sebagai buktinya.
 */
internal class IOSWorkHistoryBridge(
    private val scope: CoroutineScope,
    private val getWorkDaysByRange: GetWorkDaysByRangeUseCase,
    private val getWorkCalendarYear: GetWorkCalendarYearUseCase,
    private val currentSession: () -> UserSession?
) {

    /** Cache kalender per tahun — pindah bulan dalam tahun sama tidak menembak Firestore lagi. */
    private val calendarCache = mutableMapOf<Int, WorkCalendarYear>()

    /**
     * Bulan yang sedang berjalan menurut Asia/Jakarta, untuk state awal layar.
     *
     * Dihitung di Kotlin, bukan di Swift, supaya "bulan ini" di kedua platform diputuskan
     * di zona yang sama — perangkat yang zonanya bukan WIB tidak boleh membuka bulan lain.
     */
    fun currentMonth(): IOSWorkHistoryMonthRef {
        val date = LocalDate.fromEpochDays(TimeUtil.todayEpochDay(IOSDriverTimezone).toInt())
        return IOSWorkHistoryMonthRef(year = date.year, month = date.monthNumber)
    }

    /**
     * Muat satu bulan.
     *
     * Kegagalan baca kalender **tidak** menggagalkan layar: jatuh ke aturan default tahun itu,
     * sama seperti Android (`DriverWorkHistoryAgendaViewModel.load`). Konsekuensinya libur
     * khusus perusahaan tidak terlihat, tapi akhir pekan dan "tidak hadir" tetap benar —
     * jauh lebih berguna daripada layar error. Kegagalan baca `workdays` LAIN: itu data inti
     * layar ini, jadi dilaporkan sebagai error.
     */
    fun loadMonth(
        year: Int,
        month: Int,
        completion: (IOSWorkHistoryMonthSnapshot?, String?) -> Unit
    ) {
        if (month < 1 || month > 12) {
            completion(null, "Invalid month: $month")
            return
        }
        val session = currentSession()
        if (session == null || session.role?.toFirestore() != "driver") {
            completion(null, null)
            return
        }

        val range = monthEpochDayRange(year, month)
        scope.launch {
            val calendar = calendarCache[year] ?: when (val res = getWorkCalendarYear(year)) {
                is AppResult.Success -> res.data.also { calendarCache[year] = it }
                is AppResult.Failure -> WorkCalendarYear(year = year).also { calendarCache[year] = it }
            }

            val workDays = when (val res = getWorkDaysByRange(session.uid, range.first, range.last)) {
                is AppResult.Success -> res.data
                is AppResult.Failure -> {
                    completion(null, res.throwable.message ?: "Failed to load work history.")
                    return@launch
                }
            }

            val data = buildWorkHistoryMonth(
                year = year,
                month = month,
                calendar = calendar,
                workDays = workDays,
                todayEpochDay = TimeUtil.todayEpochDay(IOSDriverTimezone).toInt(),
                parseHm = { hm, day -> TimeUtil.parseHm(hm, day.toLong(), IOSDriverTimezone) }
            )
            completion(data.toIosSnapshot(), null)
        }
    }
}

/**
 * Bulan yang ditampilkan. Aritmetika maju/mundur bulan ada di [previous]/[next] supaya
 * Swift tidak menuliskannya sendiri — pergantian tahun di Januari/Desember adalah tempat
 * off-by-one klasik.
 */
data class IOSWorkHistoryMonthRef(
    val year: Int,
    val month: Int
) {
    fun previous(): IOSWorkHistoryMonthRef = shifted(-1)

    fun next(): IOSWorkHistoryMonthRef = shifted(+1)

    private fun shifted(delta: Int): IOSWorkHistoryMonthRef {
        // Didelegasikan ke commonMain, fungsi yang sama dipakai ViewModel Android.
        val ref = shiftWorkHistoryMonth(year, month, delta)
        return IOSWorkHistoryMonthRef(year = ref.year, month = ref.month)
    }
}
