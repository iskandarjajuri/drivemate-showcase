package com.drivemate.showcase.domain.admin

/**
 * Batas lembur legal dan evaluasinya — B1 pada PRD §7.
 *
 * ## Kenapa dua tingkat, bukan satu
 * Satu ambang hanya bisa memberi tahu admin setelah pelanggarannya terjadi, dan pada saat itu
 * tidak ada lagi yang bisa dilakukan selain mencatatnya. Tingkat **peringatan** pada 75%
 * memberi ruang untuk bertindak lebih dulu.
 *
 * ## Seberapa dini peringatannya benar-benar tiba — jujur soal ini
 * Fungsi ini murni; yang menentukan waktunya adalah pemanggilnya. Pemanggil pertama
 * ([buildAdminExceptionList] lewat `AdminHomeViewModel`) menyuapkan `overtimeMinutes` dari
 * dokumen hari kerja, dan angka itu **baru ada setelah tap-out** — `computeDailySummary`
 * (Kotlin maupun salinannya di `functions/src/index.ts`) melewati sesi yang belum ditutup.
 * Akibatnya:
 * - cakupan **PEKANAN** benar-benar preventif: ia menumpuk dari hari-hari yang sudah
 *   tertutup, jadi admin melihat 14 dari 18 jam pada Kamis pagi dan masih bisa mengubah
 *   jadwal Kamis–Jumat;
 * - cakupan **HARIAN** bersifat retrospektif untuk sekarang — ia menyala pada tap-out, bukan
 *   saat jam keempat sedang berjalan.
 *
 * Membuat harian ikut preventif berarti memproyeksikan hari yang masih terbuka dengan
 * `computeDayWorkBreakdown(clockIn, now, …)`. Itu masih Mesin A, bukan mesin kedua, tapi ia
 * butuh sumber `now`, jendela acuan, dan menit istirahat per hari (**Jumat 80**) — pekerjaan
 * tersendiri, bukan efek samping berkas ini.
 *
 * ## Perusahaan boleh lebih ketat, TIDAK PERNAH lebih longgar
 * Konfigurasi yang melonggarkan batas **di-clamp ke regulasi**, bukan diterima. Kalau tidak,
 * satu salah ketik di konfigurasi membuat aplikasi ikut melanggar PP 35/2021 tanpa ada yang
 * menyadarinya — dan aplikasi yang menjadi dasar upah tidak boleh bisa dikonfigurasi menjadi
 * salah.
 *
 * Fungsi MURNI: menit disuntikkan, nol pembacaan jam sistem.
 */

/** PP 35/2021: lembur maksimum **4 jam per hari**. */
const val REGULATION_DAILY_MAX_MINUTES: Int = 4 * 60

/** PP 35/2021: lembur maksimum **18 jam per pekan berjalan**. */
const val REGULATION_WEEKLY_MAX_MINUTES: Int = 18 * 60

/** Ambang peringatan default, sebagai persen dari batas. */
const val DEFAULT_OVERTIME_WARNING_RATIO_PERCENT: Int = 75

/**
 * Batas yang berlaku. Nilai di luar akal atau yang melonggarkan regulasi dikoreksi di
 * konstruktor, jadi tidak ada jalur yang bisa memegang batas tidak sah.
 *
 * @param dailyMaxMinutes di-clamp ke `1..`[REGULATION_DAILY_MAX_MINUTES]. Nol atau negatif
 *   bukan "tanpa lembur" — itu konfigurasi rusak, dan menerimanya akan menandai SETIAP menit
 *   lembur sebagai pelanggaran.
 */
class OvertimeLimits(
    dailyMaxMinutes: Int = REGULATION_DAILY_MAX_MINUTES,
    weeklyMaxMinutes: Int = REGULATION_WEEKLY_MAX_MINUTES,
    warningRatioPercent: Int = DEFAULT_OVERTIME_WARNING_RATIO_PERCENT,
) {
    /**
     * Di-clamp ke `1..`[REGULATION_DAILY_MAX_MINUTES]. Nol atau negatif bukan "tanpa lembur" —
     * itu konfigurasi rusak, dan menerimanya akan menandai SETIAP menit lembur sebagai
     * pelanggaran.
     */
    val dailyMaxMinutes: Int =
        if (dailyMaxMinutes <= 0) REGULATION_DAILY_MAX_MINUTES
        else dailyMaxMinutes.coerceAtMost(REGULATION_DAILY_MAX_MINUTES)

    val weeklyMaxMinutes: Int =
        if (weeklyMaxMinutes <= 0) REGULATION_WEEKLY_MAX_MINUTES
        else weeklyMaxMinutes.coerceAtMost(REGULATION_WEEKLY_MAX_MINUTES)

    val warningRatioPercent: Int =
        if (warningRatioPercent in 1..99) warningRatioPercent
        else DEFAULT_OVERTIME_WARNING_RATIO_PERCENT

    fun dailyWarningMinutes(): Int = (dailyMaxMinutes * warningRatioPercent) / 100
    fun weeklyWarningMinutes(): Int = (weeklyMaxMinutes * warningRatioPercent) / 100

    override fun toString(): String =
        "OvertimeLimits(daily=$dailyMaxMinutes, weekly=$weeklyMaxMinutes, warn=$warningRatioPercent%)"
}

/** Cakupan batas yang dievaluasi. */
enum class OvertimeLimitScope { DAILY, WEEKLY }

/** Tingkat kepatuhan terhadap satu batas. */
enum class OvertimeLimitLevel { OK, WARNING, VIOLATION }

/**
 * Hasil evaluasi satu cakupan.
 *
 * [actualMinutes] dan [limitMinutes] dibawa **berdampingan** karena PRD §7.B1 menuntut
 * keduanya disebut: *"keduanya menyebut angka dan batasnya, bukan cuma label warna"*. Label
 * warna tanpa angka memberi tahu admin bahwa ada masalah tanpa memberi tahu seberapa besar.
 */
data class OvertimeLimitStatus(
    val scope: OvertimeLimitScope,
    val level: OvertimeLimitLevel,
    val actualMinutes: Int,
    val limitMinutes: Int,
    val warningMinutes: Int,
)

/**
 * Evaluasi lembur harian dan pekanan terhadap batasnya.
 *
 * **Selalu mengembalikan dua entri**, termasuk yang `OK`. Menyembunyikan yang OK membuat
 * "tidak ada masalah harian" tak bisa dibedakan dari "belum dihitung", dan itu perbedaan yang
 * sama pentingnya dengan pelanggaran itu sendiri.
 *
 * Tepat pada batas **belum** pelanggaran: "maksimum 4 jam" berarti 4 jam masih diizinkan.
 * Tepat pada ambang peringatan **sudah** peringatan, karena peringatan gunanya mendahului.
 */
fun evaluateOvertimeLimits(
    dailyMinutes: Int,
    weeklyMinutes: Int,
    limits: OvertimeLimits = OvertimeLimits(),
): List<OvertimeLimitStatus> {
    fun evaluate(scope: OvertimeLimitScope, actualRaw: Int, max: Int, warn: Int): OvertimeLimitStatus {
        val actual = actualRaw.coerceAtLeast(0)
        val level = when {
            actual > max -> OvertimeLimitLevel.VIOLATION
            actual >= warn -> OvertimeLimitLevel.WARNING
            else -> OvertimeLimitLevel.OK
        }
        return OvertimeLimitStatus(scope, level, actual, max, warn)
    }
    return listOf(
        evaluate(
            OvertimeLimitScope.DAILY, dailyMinutes,
            limits.dailyMaxMinutes, limits.dailyWarningMinutes(),
        ),
        evaluate(
            OvertimeLimitScope.WEEKLY, weeklyMinutes,
            limits.weeklyMaxMinutes, limits.weeklyWarningMinutes(),
        ),
    )
}
