package com.drivemate.showcase.domain.report

import com.drivemate.showcase.core.model.WorkDay
import com.drivemate.showcase.domain.calendar.WorkCalendarYear
import com.drivemate.showcase.domain.calendar.WorkHistoryDayState
import com.drivemate.showcase.domain.calendar.WorkHistoryMonthData
import com.drivemate.showcase.domain.calendar.WorkHistoryMonthSummary
import com.drivemate.showcase.domain.calendar.buildWorkHistoryMonth

/**
 * Rekap bulanan per driver — B2 pada PRD §7 (Blok 4.3).
 *
 * ## Yang paling penting soal file ini: ia TIDAK MENGHITUNG APA PUN
 * Kunci penerimaan 4.3 adalah "angka rekap **identik** dengan Riwayat Kerja driver untuk
 * bulan yang sama". Satu-satunya cara menjamin itu bukan menulis rumus kedua yang
 * kebetulan sepakat, melainkan **memanggil rumus yang sama**. Jadi seluruh aritmetika di
 * sini adalah satu panggilan ke [buildWorkHistoryMonth] per driver — fungsi yang persis
 * dipakai layar Riwayat Kerja driver Android (`DriverWorkHistoryAgendaViewModel:146`) dan
 * bridge iOS-nya (`IOSWorkHistoryBridge:103`).
 *
 * Tidak ada penjumlahan menit, tidak ada pembulatan, tidak ada ambang lembur di file ini.
 * Kalau nanti ada yang tergoda menambahkannya "supaya lebih cepat" atau "supaya rekapnya
 * beda dikit", itulah momen 4.3 gagal: dua rumus di dua layar untuk angka yang menjadi
 * dasar upah. Ambang 30 menit dan pembulatan 15 menit tinggal di
 * `payableOvertimeMinutes`, dipanggil dari `computeWorkHistoryDayMinutes`, titik.
 *
 * Satu-satunya angka yang tidak datang langsung dari [WorkHistoryMonthSummary] adalah
 * [MonthlyDriverRecapRow.absentScheduledDayCount], dan ia pun bukan rumus baru: ia
 * **mencacah** hari ber-state [WorkHistoryDayState.SCHEDULED_ABSENT] dari hasil pemanggilan
 * yang sama. Percobaan pertama menurunkannya dengan pengurangan, dan itu salah dua kali —
 * lihat KDoc properti itu, kedua kesalahannya sekarang dijaga test.
 *
 * ## Yang benar-benar dijamin, dan yang tidak
 * **Dijamin:** untuk `WorkDay` yang sama, `WorkCalendarYear` yang sama, `todayEpochDay`
 * yang sama, dan `parseHm` yang sama, angka rekap identik dengan Riwayat Kerja — karena
 * fungsinya satu.
 *
 * **TIDAK dijamin oleh file ini:** bahwa kedua layar benar-benar menerima input yang sama.
 * `WorkDay` untuk rekap dibaca `AdminDataRepository.getWorkdaysByRange`, sedangkan layar
 * driver memakai `WorkDayRepository.getWorkDaysByRange` — dua mapper Firestore yang
 * berbeda, keduanya menggabungkan jam dari `attendance_daily`. Selama keduanya
 * menghasilkan `clockInMillis`/`clockOutMillis`/`totalMinutes`/`overtimeMinutes`/
 * `plannedStartMillis`/`plannedEndMillis` yang sama, angkanya sama. Itu **asumsi lapis
 * data**, bukan sesuatu yang bisa dibuktikan unit test di sini, dan karena itu diperiksa
 * dengan membandingkan kedua layar pada bulan yang sama di emulator.
 *
 * `todayEpochDay` **disuntikkan**, tidak dibaca dari jam sistem: `resolveWorkHistoryDayState`
 * memakainya, jadi dua layar dengan nilai berbeda akan berbeda hitungan hari untuk bulan
 * yang sedang berjalan — dan itu jenis selisih yang paling sulit dilacak karena hilang
 * sendiri begitu bulannya berlalu.
 *
 * Fungsi MURNI: nol I/O, nol pembacaan jam sistem.
 */

/** Satu baris rekap: seorang driver, satu bulan. */
data class MonthlyDriverRecapRow(
    val driverUid: String,
    /** Nama tampilan, atau `null` kalau direktori penggunanya tidak memuatnya. */
    val driverName: String?,
    /**
     * Hasil [buildWorkHistoryMonth] untuk driver ini, disimpan APA ADANYA.
     *
     * Baris ini sengaja memegang seluruh hasilnya, bukan hanya [WorkHistoryMonthData.summary]:
     * setiap angka yang dilaporkan rekap kemudian menjadi pembacaan atas satu objek yang
     * sama, sehingga tidak ada dua angka yang bisa berasal dari dua panggilan berbeda.
     */
    val monthData: WorkHistoryMonthData,
) {
    /** Ringkasan bulan — persis yang dipakai layar Riwayat Kerja driver. */
    val summary: WorkHistoryMonthSummary get() = monthData.summary

    /**
     * Hari terjadwal yang TIDAK dikerjakan **dan sudah terjadi**.
     *
     * ## Kenapa DIHITUNG dari state harian, bukan dikurangkan
     * Versi pertama 4.3 memakai `scheduledWorkdayCount - workedDayCount`. Itu salah dua
     * kali, dan keduanya dijaga test sekarang:
     *
     * 1. **Bulan berjalan melaporkan absen hantu.** Kedua angka di [summary] buta terhadap
     *    `todayEpochDay` — `scheduledWorkdayCount` datang dari kalender, `workedDayCount`
     *    dari ada tidaknya catatan. Selisihnya karena itu menghitung setiap hari kerja yang
     *    BELUM TIBA sebagai tidak hadir: rekap September yang dibuka tanggal 2 melaporkan
     *    21 absen padahal baru satu hari terlewat.
     * 2. **Akhir pekan yang dikerjakan menutupi bolos beneran.** `workedDayCount` ikut
     *    menghitung hari libur yang dikerjakan, jadi ia bisa melampaui hari terjadwal dan
     *    membuat selisihnya negatif — lalu `coerceAtLeast(0)` menyembunyikannya sebagai
     *    "0 absen". Driver yang kerja Sabtu tapi bolos Rabu tampak hadir penuh.
     *
     * [WorkHistoryDayState.SCHEDULED_ABSENT] sudah berarti persis "hari kerja, tanpa
     * catatan, tanggalnya sudah lewat" — definisi yang sama yang dipakai layar driver untuk
     * mewarnai selnya merah. Menghitungnya memakai ulang definisi itu alih-alih menyusun
     * definisi kedua, dan `coerceAtLeast` jadi tidak perlu: sebuah cacahan tidak bisa negatif.
     */
    val absentScheduledDayCount: Int
        get() = monthData.days.count { it.state == WorkHistoryDayState.SCHEDULED_ABSENT }
}

/** Rekap satu bulan untuk sekumpulan driver. */
data class MonthlyDriverRecap(
    val year: Int,
    val month: Int,
    val rows: List<MonthlyDriverRecapRow>,
) {
    /**
     * Total lintas driver.
     *
     * `scheduledWorkdayCount` **tidak** dijumlahkan sebagai total baris: kalender kerja
     * sama untuk semua driver, jadi menjumlahkannya menghasilkan "jumlah hari terjadwal ×
     * jumlah driver", yang terbaca sebagai hari padahal satuannya hari-orang. Yang
     * dijumlahkan hanya menit dan hari-kerja aktual; hari terjadwal dilaporkan sebagai
     * angka per driver (identik untuk semua) lewat [scheduledWorkdayCountPerDriver].
     */
    val totalWorkedMinutes: Int get() = rows.sumOf { it.summary.totalWorkedMinutes }
    val totalOvertimeMinutes: Int get() = rows.sumOf { it.summary.totalOvertimeMinutes }
    val totalWorkedDayCount: Int get() = rows.sumOf { it.summary.workedDayCount }

    /** Hari terjadwal bulan itu menurut kalender kerja; sama untuk setiap driver. */
    val scheduledWorkdayCountPerDriver: Int
        get() = rows.firstOrNull()?.summary?.scheduledWorkdayCount ?: 0

    /**
     * Bulan ini masih punya hari yang belum tiba.
     *
     * Ada supaya layar bisa mengatakannya. Tanpa itu admin membaca "hari terjadwal 22,
     * dikerjakan 5, tidak hadir 1" dan wajar bertanya ke mana 16 hari sisanya — angkanya
     * benar, tapi barisnya tidak menjelaskan bahwa 16 hari itu belum terjadi.
     *
     * Diturunkan dari state harian yang sama, bukan dari perbandingan tanggal baru:
     * [WorkHistoryDayState.FUTURE] SUDAH berarti "tanggalnya melewati `todayEpochDay`".
     */
    val hasFutureDays: Boolean
        get() = rows.firstOrNull()?.monthData?.days
            ?.any { it.state == WorkHistoryDayState.FUTURE } ?: false
}

/**
 * Membangun rekap bulanan untuk daftar driver yang diminta.
 *
 * @param driverUids driver yang HARUS muncul di rekap, apa pun ada tidaknya catatan kerja.
 *   Driver tanpa satu pun catatan tetap mendapat baris bernilai nol — itu justru informasi
 *   yang dicari admin ("siapa yang tidak masuk sama sekali"), dan menghilangkannya dari
 *   daftar membuat absen penuh terlihat seperti driver yang tidak ada.
 * @param workDays catatan kerja bulan itu untuk SEMUA driver sekaligus; dikelompokkan di
 *   sini per `driverUid`. Catatan milik uid di luar [driverUids] diabaikan.
 * @param driverNames nama tampilan per uid, sejauh diketahui.
 * @param todayEpochDay disuntikkan; lihat KDoc file.
 * @param parseHm penerjemah "HH:mm" + epochDay -> epoch millis (butuh zona waktu, yang
 *   tidak tersedia di commonMain).
 */
fun buildMonthlyDriverRecap(
    year: Int,
    month: Int,
    calendar: WorkCalendarYear,
    driverUids: List<String>,
    workDays: List<WorkDay>,
    driverNames: Map<String, String>,
    todayEpochDay: Int,
    parseHm: (String, Int) -> Long,
): MonthlyDriverRecap {
    val byDriver = workDays.groupBy { it.driverUid }
    val rows = driverUids.distinct().map { uid ->
        MonthlyDriverRecapRow(
            driverUid = uid,
            driverName = driverNames[uid]?.trim()?.takeIf { it.isNotEmpty() },
            // SATU panggilan, nol aritmetika lokal. Lihat KDoc file.
            monthData = buildWorkHistoryMonth(
                year = year,
                month = month,
                calendar = calendar,
                workDays = byDriver[uid].orEmpty(),
                todayEpochDay = todayEpochDay,
                parseHm = parseHm,
            ),
        )
    }
    return MonthlyDriverRecap(year = year, month = month, rows = rows)
}
