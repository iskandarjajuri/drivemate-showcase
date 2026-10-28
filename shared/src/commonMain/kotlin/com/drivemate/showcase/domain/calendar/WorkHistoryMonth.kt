package com.drivemate.showcase.domain.calendar

import com.drivemate.showcase.core.model.AttendanceReferenceWindow
import com.drivemate.showcase.core.model.WorkDay
import kotlinx.datetime.LocalDate

/**
 * State satu hari di layar Riwayat Kerja — hasil JOIN attendance x Work Calendar.
 *
 * [OFF_WEEKEND] dan [OFF_COMPANY_HOLIDAY] sengaja TERPISAH dari [SCHEDULED_ABSENT]: "libur"
 * (memang bukan hari kerja) dan "tidak hadir" (harusnya kerja, tapi absen) adalah dua fakta
 * berbeda dan tidak boleh tampil sama.
 */
enum class WorkHistoryDayState {
    /** Ada catatan kerja aktual pada hari itu. */
    WORKED,

    /** Akhir pekan biasa, tanpa catatan kerja. */
    OFF_WEEKEND,

    /** Libur khusus perusahaan, tanpa catatan kerja. */
    OFF_COMPANY_HOLIDAY,

    /** Terjadwal kerja tapi tidak ada catatan kerja — inilah "tidak hadir". */
    SCHEDULED_ABSENT,

    /** Tanggal belum terjadi; belum boleh dinilai hadir/absen. */
    FUTURE
}

/** Breakdown menit normal vs lembur untuk satu hari. */
data class WorkHistoryDayMinutes(
    val normalMinutes: Int,
    /**
     * Lembur **terbayar** — sudah lewat ambang 30 menit dan dibulatkan ke bawah ke kelipatan
     * 15 menit. Inilah angka yang tampil ke driver, karena inilah yang dibayar.
     */
    val overtimeMinutes: Int,
    /**
     * Lembur **mentah**: kelebihan sebenarnya atas jadwal, sebelum ambang dan pembulatan.
     *
     * Disimpan berdampingan, bukan dibuang. Sengketa upah harus bisa direkonstruksi, dan itu
     * mustahil kalau selisih antara yang dikerjakan dan yang dibayar tidak tercatat di mana
     * pun. Default-nya sama dengan [overtimeMinutes] supaya pemanggil lama tetap terkompilasi.
     */
    val rawOvertimeMinutes: Int = overtimeMinutes
) {
    val totalMinutes: Int get() = normalMinutes + overtimeMinutes

    /** Menit yang dikerjakan melebihi jadwal tapi tidak terbayar sebagai lembur. */
    val unpaidOvertimeMinutes: Int get() = (rawOvertimeMinutes - overtimeMinutes).coerceAtLeast(0)
}

/** Satu baris agenda: tanggal + klasifikasi + catatan kerja (kalau ada). */
data class WorkHistoryDay(
    val dateEpochDay: Int,
    val state: WorkHistoryDayState,
    val calendarKind: DayCalendarKind,
    val workDay: WorkDay?,
    val minutes: WorkHistoryDayMinutes?
)

/** Agregat satu bulan untuk 3 kartu statistik. */
data class WorkHistoryMonthSummary(
    val totalWorkedMinutes: Int,
    val totalOvertimeMinutes: Int,
    val workedDayCount: Int,
    val scheduledWorkdayCount: Int
)

/**
 * Tentukan state satu hari.
 *
 * @param hasWorkRecord true kalau ada [WorkDay] untuk tanggal itu.
 * @param todayEpochDay hari ini, disuntikkan — fungsi ini tidak pernah membaca jam sistem
 *   supaya bisa diuji deterministik dan dipakai ulang di iOS.
 */
fun resolveWorkHistoryDayState(
    calendarKind: DayCalendarKind,
    hasWorkRecord: Boolean,
    dateEpochDay: Int,
    todayEpochDay: Int
): WorkHistoryDayState {
    if (hasWorkRecord) return WorkHistoryDayState.WORKED
    if (dateEpochDay > todayEpochDay) return WorkHistoryDayState.FUTURE
    return when (calendarKind) {
        DayCalendarKind.WEEKEND -> WorkHistoryDayState.OFF_WEEKEND
        DayCalendarKind.COMPANY_HOLIDAY -> WorkHistoryDayState.OFF_COMPANY_HOLIDAY
        DayCalendarKind.SCHEDULED_WORKDAY -> WorkHistoryDayState.SCHEDULED_ABSENT
    }
}

/**
 * Pecah satu hari kerja jadi menit normal vs lembur.
 *
 * ## Berubah 7 September 2026 (Blok 0.2) — dan angkanya memang bergeser
 * Sebelumnya fungsi ini menjumlahkan `zones.earlyOvertimeMinutes + zones.lateOvertimeMinutes`
 * dari [computeAttendanceTimelineZones] — "Mesin B" pada PRD §17, yang mengukur **kapan**
 * seseorang bekerja, bukan **berapa banyak**. Akibatnya driver yang hanya MENGGESER shiftnya
 * (masuk 07:00, keluar 16:00, tetap 9 jam) melihat 30 menit lembur di Riwayat Kerja, sementara
 * catatan resmi di `workdays.overtimeMinutes` mencatat 0. Dua angka untuk hari yang sama, dan
 * yang dipakai menghitung upah adalah yang satunya.
 *
 * Sekarang angkanya diambil dari **catatan resmi** — nilai yang ditulis Cloud Function memakai
 * Mesin A (`functions/src/index.ts:332-333`). Satu angka, ditulis sekali, dibaca di mana-mana.
 * Itu jaminan terkuat bahwa driver, admin, dan slip gaji melihat hal yang sama; menghitung
 * ulang di sini akan membuka celah drift yang persis ingin ditutup.
 *
 * ## Fallback, dan kenapa ia BUKAN zona
 * Dokumen yang belum pernah diproses Cloud Function menyimpan `overtimeMinutes = 0` walau
 * harinya jelas melampaui jadwal, dan nol itu tidak bisa dibedakan dari nol yang sah. Kalau
 * catatan resmi mengatakan nol tapi Mesin A atas jam masuk/keluar yang sama mengatakan lebih,
 * yang dipakai adalah hitungan Mesin A — **tetap Mesin A**, bukan zona. Fallback yang memakai
 * zona akan menghidupkan kembali cacat yang baru saja dihapus.
 *
 * [computeAttendanceTimelineZones] sendiri tidak dihapus dan tidak diubah: ia tetap dipakai
 * Live Timer untuk menggambar posisi jam. **Zona untuk gambar, Mesin A untuk angka.**
 *
 * @param breakMinutes istirahat menurut aturan hari itu, dibutuhkan fallback Mesin A.
 *   [AttendanceReferenceWindow] tidak membawanya, dan justru ketidaktahuan itulah yang membuat
 *   zona tidak bisa dipakai untuk angka.
 */
fun computeWorkHistoryDayMinutes(
    workDay: WorkDay,
    referenceWindow: AttendanceReferenceWindow,
    breakMinutes: Int
): WorkHistoryDayMinutes {
    val clockIn = workDay.clockInMillis.takeIf { it > 0L }
    val clockOut = workDay.clockOutMillis.takeIf { it > 0L } ?: return WorkHistoryDayMinutes(0, 0)

    val total = workDay.totalMinutes.coerceAtLeast(0)
    val recorded = workDay.overtimeMinutes.coerceAtLeast(0)

    val overtime = if (recorded > 0) {
        recorded
    } else {
        // Catatan resmi nol: bisa sah, bisa juga dokumen yang belum diproses. Mesin A atas
        // jam yang sama yang memutuskan.
        computeDayWorkBreakdown(
            clockInMillis = clockIn,
            clockOutMillis = clockOut,
            window = referenceWindow,
            breakMinutes = breakMinutes
        ).overtimeMinutes
    }.coerceAtMost(total)

    // Ambang 30 menit + pembulatan ke bawah kelipatan 15 (keputusan Iskandar 7 Sep 2026).
    // Menit yang terpangkas jatuh ke NORMAL, tidak hilang — partisi normal+lembur harus tetap
    // berjumlah total, karena bar normal-vs-lembur di Agenda bergantung padanya.
    val payable = payableOvertimeMinutes(overtime)
    return WorkHistoryDayMinutes(
        normalMinutes = (total - payable).coerceAtLeast(0),
        overtimeMinutes = payable,
        rawOvertimeMinutes = overtime
    )
}

/** Agregasi bulan dari baris-baris agenda yang sudah dibangun. */
fun summarizeWorkHistoryMonth(
    days: List<WorkHistoryDay>,
    scheduledWorkdayCount: Int
): WorkHistoryMonthSummary {
    var worked = 0
    var overtime = 0
    var workedDays = 0
    for (day in days) {
        val m = day.minutes ?: continue
        worked += m.totalMinutes
        overtime += m.overtimeMinutes
        if (day.state == WorkHistoryDayState.WORKED) workedDays += 1
    }
    return WorkHistoryMonthSummary(
        totalWorkedMinutes = worked,
        totalOvertimeMinutes = overtime,
        workedDayCount = workedDays,
        scheduledWorkdayCount = scheduledWorkdayCount
    )
}

/**
 * Jendela referensi untuk memisahkan menit normal vs lembur pada SATU hari.
 *
 * Rantai prioritas, urut:
 * 1. Rencana eksplisit hari itu ([WorkDay.plannedStartMillis]/[WorkDay.plannedEndMillis]) —
 *    kalau keduanya ada, itu yang menang dan [AttendanceReferenceWindow.isExplicitPlan] = true.
 * 2. [WorkSpec] Work Calendar untuk tanggal itu (menghormati override libur/swap).
 * 3. Jam kerja standar 07:30–16:30.
 *
 * ## Kenapa fungsi ini ada di commonMain
 * Sebelumnya ini `private` di `DriverWorkHistoryAgendaViewModel` (Android). Itu satu-satunya
 * logika Work History yang tidak mendarat di commonMain, dan
 * [computeWorkHistoryDayMinutes] menuntutnya untuk SETIAP hari — artinya port iOS pasti
 * menulis ulang rantai prioritas di atas dalam Swift, dan dua platform akan mendrift pada
 * perhitungan lembur. Justru drift itu yang ingin dicegah dengan memakai ulang
 * `computeAttendanceTimelineZones`.
 *
 * Fungsi murni: `zone` dan `parseHm` disuntikkan lewat parameter, tidak ada pembacaan jam
 * sistem, mengikuti pola [resolveWorkHistoryDayState] yang menerima `todayEpochDay`.
 *
 * @param parseHm penerjemah "HH:mm" + epochDay -> epoch millis. Disuntikkan supaya fungsi ini
 *   tetap murni dan bisa diuji tanpa bergantung implementasi waktu platform; pemanggil nyata
 *   menyerahkan `TimeUtil::parseHm`.
 */
fun resolveWorkHistoryReferenceWindow(
    workDay: WorkDay,
    calendar: WorkCalendarYear,
    dateEpochDay: Int,
    parseHm: (String, Int) -> Long
): AttendanceReferenceWindow {
    val plannedStart = workDay.plannedStartMillis
    val plannedEnd = workDay.plannedEndMillis
    if (plannedStart != null && plannedEnd != null) {
        return AttendanceReferenceWindow(plannedStart, plannedEnd, isExplicitPlan = true)
    }
    val spec = resolveWorkSpecForDate(calendar, dateEpochDay)
    val startHm = spec?.let { minutesToHm(it.startMinutes) } ?: DEFAULT_WORK_START_HM
    val endHm = spec?.let { minutesToHm(it.endMinutes) } ?: DEFAULT_WORK_END_HM
    return AttendanceReferenceWindow(
        startMillis = parseHm(startHm, dateEpochDay),
        endMillis = parseHm(endHm, dateEpochDay),
        isExplicitPlan = false
    )
}

/** Jam kerja standar — cadangan terakhir kalau kalender maupun rencana tidak memberi apa pun. */
const val DEFAULT_WORK_START_HM: String = "07:30"
const val DEFAULT_WORK_END_HM: String = "16:30"

/** Menit-sejak-tengah-malam -> "HH:mm". Di-clamp supaya nilai kalender rusak tidak melempar. */
internal fun minutesToHm(minutes: Int): String {
    val h = (minutes / 60).coerceIn(0, 23)
    val m = (minutes % 60).coerceIn(0, 59)
    return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
}

/**
 * Seluruh isi satu bulan layar Riwayat Kerja, siap dirender.
 *
 * [leadingBlankCount] sengaja ada di sini, bukan dihitung di UI. Grid mini kalender
 * Senin-dulu, dan `kotlinx.datetime.DayOfWeek.MONDAY.ordinal == 0` — sementara
 * `Calendar.current.firstWeekday` di iOS/en_US adalah MINGGU. Port naif yang
 * menghitung sel kosong di Swift menggeser SELURUH grid satu kolom, dan itu
 * kesalahan yang tidak kelihatan salah (kalender tetap "terlihat wajar", cuma
 * tanggalnya berpindah hari). Dihitung di Kotlin sekali, jebakan itu hilang.
 */
data class WorkHistoryMonthData(
    val year: Int,
    val month: Int,
    val days: List<WorkHistoryDay>,
    val summary: WorkHistoryMonthSummary,
    val leadingBlankCount: Int,
    val todayEpochDay: Int
)

/**
 * Rakit satu bulan penuh: JOIN attendance x Work Calendar, hitung menit normal/lembur
 * per hari, agregasi bulan, plus offset grid.
 *
 * ## Kenapa ini di commonMain dan dipakai KEDUA platform
 * `shared/src/iosMain` tidak dikompilasi di mesin Linux, jadi kode bridge iOS
 * sebetulnya sama tak-terverifikasinya dengan Swift. commonMain adalah satu-satunya
 * tempat logika ini bisa benar-benar diuji (`:shared:allTests` jalan di JVM host).
 * Dan kalau hanya iOS yang memakainya, test-test itu cuma membuktikan jalur iOS
 * konsisten dengan DIRINYA SENDIRI — nol bukti soal kesepakatan Android<->iOS,
 * padahal justru drift itu yang mau dicegah. Karena Android ikut memakainya, suite
 * Maestro Android yang sudah ada berlaku sebagai bukti perilaku untuk fungsi yang
 * nanti dipanggil iOS.
 *
 * Fungsi MURNI: `todayEpochDay` dan `parseHm` disuntikkan, nol pembacaan jam sistem.
 *
 * @param month 1..12
 * @param workDays catatan kerja bulan itu; urutan tidak penting, di-index per tanggal.
 * @param parseHm ubah "HH:mm" + epoch-day jadi epoch millis (butuh zona waktu, yang
 *   tidak tersedia di commonMain — lihat [resolveWorkHistoryReferenceWindow]).
 */
fun buildWorkHistoryMonth(
    year: Int,
    month: Int,
    calendar: WorkCalendarYear,
    workDays: List<WorkDay>,
    todayEpochDay: Int,
    parseHm: (String, Int) -> Long
): WorkHistoryMonthData {
    val range = monthEpochDayRange(year, month)
    val byDate = workDays.associateBy { it.dateEpochDay }

    val days = range.map { epochDay ->
        val wd = byDate[epochDay]
        val kind = resolveDayCalendarKind(calendar, epochDay)
        WorkHistoryDay(
            dateEpochDay = epochDay,
            state = resolveWorkHistoryDayState(
                calendarKind = kind,
                hasWorkRecord = wd != null,
                dateEpochDay = epochDay,
                todayEpochDay = todayEpochDay
            ),
            calendarKind = kind,
            workDay = wd,
            minutes = wd?.let {
                computeWorkHistoryDayMinutes(
                    workDay = it,
                    referenceWindow = resolveWorkHistoryReferenceWindow(
                        workDay = it,
                        calendar = calendar,
                        dateEpochDay = epochDay,
                        parseHm = parseHm
                    ),
                    // Diturunkan dari kalender, bukan literal: hari libur yang dikerjakan
                    // tidak punya WorkSpec, dan menuliskan "60" di sini akan menjadi
                    // definisi istirahat kelima di repo ini.
                    breakMinutes = resolveWorkSpecForDate(calendar, epochDay)?.breakMinutes
                        ?: calendar.defaultRules.weekdayRule.breakMinutes
                )
            }
        )
    }

    return WorkHistoryMonthData(
        year = year,
        month = month,
        days = days,
        summary = summarizeWorkHistoryMonth(
            days = days,
            scheduledWorkdayCount = scheduledWorkdayCountInMonth(calendar, year, month)
        ),
        leadingBlankCount = leadingBlankCountForMonth(year, month),
        todayEpochDay = todayEpochDay
    )
}

/**
 * Sel kosong sebelum tanggal 1 pada grid Senin-dulu. Senin -> 0, Minggu -> 6.
 */
fun leadingBlankCountForMonth(year: Int, month: Int): Int =
    LocalDate(year, month, 1).dayOfWeek.ordinal

/** Satu bulan kalender yang sedang dilihat. `month` selalu 1..12. */
data class WorkHistoryMonthRef(
    val year: Int,
    val month: Int
)

/**
 * Geser bulan yang ditampilkan sebanyak [delta] bulan (boleh negatif).
 *
 * Ada di commonMain karena pergantian tahun di Januari/Desember adalah off-by-one
 * klasik, dan navigator bulan ada di KEDUA platform — kalau masing-masing menulis
 * `if (month == 1) year - 1` sendiri, salah satunya cepat atau lambat salah dan
 * gejalanya cuma "bulannya aneh", tanpa crash.
 */
fun shiftWorkHistoryMonth(year: Int, month: Int, delta: Int): WorkHistoryMonthRef {
    // Hitung lewat indeks bulan absolut supaya delta > 12 dan delta negatif besar
    // tetap benar tanpa cabang khusus.
    val zeroBased = year * 12 + (month - 1) + delta
    return WorkHistoryMonthRef(
        year = zeroBased.floorDiv(12),
        month = zeroBased.mod(12) + 1
    )
}
