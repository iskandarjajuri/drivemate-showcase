package com.drivemate.showcase.domain.admin

import com.drivemate.showcase.core.model.Location
import com.drivemate.showcase.core.util.TrackingConstants

/**
 * Metrik "apakah ada yang perlu perhatian SEKARANG" di Admin Home — dipakai KEDUA platform.
 *
 * ## Kenapa di commonMain
 * Angka ini yang dipakai admin memutuskan apakah harus bertindak. Kalau Android dan iOS
 * menghitungnya sendiri-sendiri, dua admin yang melihat data yang sama bisa melihat jumlah
 * "perlu perhatian" yang berbeda — dan tidak ada yang tahu mana yang benar.
 *
 * Sebelum ini semuanya `private` di `AdminHomeViewModel` Android, dan iOS tidak punya sama
 * sekali.
 *
 * Fungsi MURNI: `nowMillis` disuntikkan, nol pembacaan jam sistem.
 */
data class AdminLiveMetrics(
    /** Driver yang lokasinya SEGAR — ini yang benar-benar terpantau. */
    val trackedDriversCount: Int = 0,
    /** Driver yang punya lokasi tapi sudah basi. */
    val staleDriversCount: Int = 0,
    /** Workday yang masih terbuka. */
    val openWorkdayCount: Int = 0,
    /** Total hal yang menunggu tindakan admin. */
    val needsAttentionCount: Int = 0
)

/**
 * Hal-hal yang menunggu tindakan, di luar kesegaran lokasi.
 *
 * Dipisah dari [AdminLiveMetrics] karena sumbernya berbeda: yang satu dari `locations`
 * real-time, yang lain dari agregasi harian.
 */
data class AdminAttentionCounts(
    val openWorkdayCount: Int = 0,
    val checklistMissingToday: Int = 0,
    val safetyMissingToday: Int = 0,
    val sopPendingCount: Int = 0
)

/**
 * Hitung metrik live dari daftar lokasi + hitungan yang menunggu.
 *
 * Sebuah lokasi hanya dihitung bila **punya koordinat**. Dokumen tanpa lat/lon bukan driver
 * yang terpantau maupun yang basi — ia tidak membawa informasi posisi sama sekali, dan
 * menghitungnya di salah satu ember akan membuat total tidak cocok dengan apa yang tampak
 * di peta.
 */
fun computeAdminLiveMetrics(
    locations: List<Location>,
    attention: AdminAttentionCounts,
    nowMillis: Long,
    staleAfterMillis: Long = TrackingConstants.STALE_AFTER_MILLIS
): AdminLiveMetrics {
    val positioned = locations.filter { it.latitude != null && it.longitude != null }
    val tracked = positioned.count { nowMillis - it.updatedAtMillis <= staleAfterMillis }
    val stale = positioned.size - tracked

    return AdminLiveMetrics(
        trackedDriversCount = tracked,
        staleDriversCount = stale,
        openWorkdayCount = attention.openWorkdayCount,
        // Penjumlahan, mengikuti Android: satu angka yang menjawab "ada berapa hal yang
        // menunggu saya?". Memisahkannya jadi beberapa badge membuat admin harus
        // menjumlahkan sendiri untuk tahu apakah hari ini bersih.
        needsAttentionCount = stale +
            attention.openWorkdayCount +
            attention.checklistMissingToday +
            attention.safetyMissingToday +
            attention.sopPendingCount
    )
}

/**
 * Tren jumlah driver terpantau terhadap baseline, dalam persen.
 *
 * `0` bila baseline nol — bukan 100 atau tak-hingga. Naik dari 0 ke 3 driver bukan
 * "peningkatan tak terhingga persen"; itu pembacaan yang menyesatkan dan bukan informasi.
 */
fun trackedDriversTrendPercent(current: Int, baseline: Int): Int {
    if (baseline <= 0) return 0
    val delta = (current - baseline).toDouble() * 100.0 / baseline.toDouble()
    // Pembulatan setengah-menjauhi-nol supaya -0.5 jadi -1, bukan 0: penurunan kecil tetap
    // terbaca sebagai penurunan.
    return if (delta < 0) (delta - 0.5).toInt() else (delta + 0.5).toInt()
}

/**
 * Tren hari-ini terhadap kemarin, dalam persen.
 *
 * Penyebutnya `max(yesterday, 1)`, bukan `yesterday`. Itu disengaja dan bukan sekadar
 * penghindaran bagi-nol: kalau kemarin nol dan hari ini 3, hasilnya +300% — sebuah angka
 * yang bisa dibaca dan proporsional. Mengembalikan 0 (seperti tren driver terpantau) akan
 * MENYEMBUNYIKAN lonjakan aktivitas dari nol, yang justru kejadian paling layak
 * diperhatikan admin.
 *
 * Berbeda dari [trackedDriversTrendPercent] yang mengembalikan 0 pada baseline nol: di sana
 * baseline adalah snapshot sesi, bukan hari sebelumnya, dan "naik dari 0" hanya berarti
 * pengamatan baru dimulai.
 */
fun dayOverDayTrendPercent(todayCount: Int, yesterdayCount: Int): Int {
    val denominator = maxOf(yesterdayCount, 1)
    val delta = (todayCount - yesterdayCount).toDouble() * 100.0 / denominator.toDouble()
    return if (delta < 0) (delta - 0.5).toInt() else (delta + 0.5).toInt()
}

/** Hitungan aktivitas hari ini untuk Admin Home. */
data class AdminOverviewCounts(
    val checklistSubmitted: Int = 0,
    val safetySubmitted: Int = 0,
    val attendanceIn: Int = 0,
    val attendanceOut: Int = 0
)

/**
 * Hitung aktivitas pada satu hari.
 *
 * Tipe absensi dicocokkan **case-insensitive**: Firestore menyimpan "IN"/"in" tergantung
 * jalur penulisnya, dan pencocokan sensitif-huruf akan diam-diam melaporkan nol tap-in.
 */
fun countAdminOverview(
    checklistDates: List<Int>,
    safetyDates: List<Int>,
    attendanceDates: List<Int>,
    attendanceTypes: List<String>,
    targetEpochDay: Int
): AdminOverviewCounts {
    val n = minOf(attendanceDates.size, attendanceTypes.size)
    var inCount = 0
    var outCount = 0
    for (i in 0 until n) {
        if (attendanceDates[i] != targetEpochDay) continue
        when (attendanceTypes[i].uppercase()) {
            "IN" -> inCount += 1
            "OUT" -> outCount += 1
        }
    }
    return AdminOverviewCounts(
        checklistSubmitted = checklistDates.count { it == targetEpochDay },
        safetySubmitted = safetyDates.count { it == targetEpochDay },
        attendanceIn = inCount,
        attendanceOut = outCount
    )
}

/** Jenis kejadian di activity feed Admin Home. */
enum class AdminActivityType {
    ATTENDANCE_IN,
    ATTENDANCE_OUT,
    CHECKLIST_SUBMITTED,
    SAFETY_SUBMITTED,
    SOP_ACKNOWLEDGED
}

/** Satu kejadian di activity feed. */
data class AdminActivityEvent(
    val type: AdminActivityType,
    val driverUid: String,
    val recordId: String,
    val timestampMillis: Long,
    val locationName: String? = null
)

/** Berapa kejadian terbaru yang ditampilkan. */
const val ADMIN_ACTIVITY_FEED_LIMIT: Int = 10

/**
 * Gabung, urutkan, dan batasi kejadian untuk activity feed — dipakai KEDUA platform.
 *
 * ## Kenapa di commonMain
 * Feed ini menjawab "apa yang baru saja terjadi?". Kalau kedua platform mengurutkan atau
 * membatasi berbeda, dua admin akan melihat kejadian TERAKHIR yang berbeda — dan yang satu
 * akan mengira kejadian yang dilihat rekannya tidak pernah terjadi.
 *
 * Kejadian dengan timestamp <= 0 DIBUANG: itu dokumen tanpa waktu yang sah, dan menempatkannya
 * di ujung daftar (entah paling atas atau paling bawah) sama-sama menyesatkan — feed yang
 * urut waktu tidak boleh memuat baris yang waktunya tidak diketahui.
 */
fun buildAdminActivityFeed(
    events: List<AdminActivityEvent>,
    limit: Int = ADMIN_ACTIVITY_FEED_LIMIT
): List<AdminActivityEvent> =
    events
        .filter { it.timestampMillis > 0L }
        .sortedByDescending { it.timestampMillis }
        .take(limit.coerceAtLeast(0))

/** Petakan tipe absensi mentah ke jenis kejadian. `null` bila tidak dikenal. */
fun adminActivityTypeFromAttendance(typeRaw: String): AdminActivityType? =
    when (typeRaw.uppercase()) {
        "IN" -> AdminActivityType.ATTENDANCE_IN
        "OUT" -> AdminActivityType.ATTENDANCE_OUT
        else -> null
    }
