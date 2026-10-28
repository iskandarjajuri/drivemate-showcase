package com.drivemate.showcase.domain.safety

import com.drivemate.showcase.core.model.SpeedLimitConfig

/**
 * Pemetaan dan validasi `app_config/safety` — C2 pada PRD §7 (Blok 3.4).
 *
 * Sampai sekarang dokumen ini diisi tangan lewat Firebase Console. Layar admin
 * menggantikannya, dan itu memindahkan satu risiko baru ke dalam aplikasi: **fail-closed
 * bisa berubah jadi fail-open lewat konfigurasi.** Dua jalur konkretnya:
 *
 * 1. **Dokumen setengah terisi.** `enabled = true` tanpa `speedLimitKph` yang sah harus
 *    tetap berarti deteksi MATI, bukan deteksi dengan batas entah berapa. Itulah tugas
 *    [mapSpeedLimitConfigOrNull] — dan sekarang ia fungsi murni yang bisa diuji, bukan
 *    logika yang terkubur di dalam repository platform.
 * 2. **Angka yang tidak masuk akal.** Salah ketik `800` alih-alih `80` membuat deteksi
 *    tidak pernah menyala sementara layar tetap menampilkan "aktif" — mati total, tapi
 *    terlihat hidup. `firestore.rules:1436` hanya menuntut `> 0`, jadi rules TIDAK
 *    menangkap ini. [validateSpeedLimitInput] yang menangkapnya, sebelum tulisan terjadi.
 *
 * Fungsi MURNI: nol I/O, nol pembacaan jam sistem.
 */

/**
 * Batas bawah yang diterima, km/jam.
 *
 * Di bawah ini setiap perjalanan normal menjadi pelanggaran, dan daftar pelanggaran yang
 * selalu penuh sama tidak berguna dengan yang selalu kosong.
 */
const val SAFETY_SPEED_LIMIT_MIN_KPH: Double = 20.0

/**
 * Batas atas yang diterima, km/jam.
 *
 * PP 79/2013 menetapkan batas tertinggi jalan bebas hambatan 100 km/jam. 120 memberi ruang
 * di atas angka itu tanpa menerima nilai yang artinya "matikan deteksi diam-diam".
 * Perusahaan boleh lebih ketat; yang ditolak adalah yang lebih longgar dari akal sehat.
 */
const val SAFETY_SPEED_LIMIT_MAX_KPH: Double = 120.0

/**
 * Petakan dokumen mentah `app_config/safety` menjadi [SpeedLimitConfig], atau `null`.
 *
 * `null` berarti **fail-closed**: deteksi mati total, tanpa banner dan tanpa notifikasi.
 * Itu keputusan sadar — satu alert kecepatan palsu memanggil tiga orang sekaligus
 * (driver, expat, admin), jadi diam lebih baik daripada salah bunyi.
 *
 * `enabled` yang hilang dibaca `false`, bukan `true`: bidang yang tidak ada bukan izin.
 */
fun mapSpeedLimitConfigOrNull(map: Map<String, Any?>?): SpeedLimitConfig? {
    if (map == null) return null
    val limit = (map["speedLimitKph"] as? Number)?.toDouble() ?: return null
    if (limit <= 0.0) return null
    return SpeedLimitConfig(
        speedLimitKph = limit,
        enabled = map["enabled"] as? Boolean ?: false,
    )
}

/** Hasil validasi masukan batas kecepatan. Enum, bukan `@StringRes`: tiap platform memetakannya sendiri. */
enum class SafetyConfigInputError {
    /** Kolom kosong. */
    EMPTY,
    /** Bukan angka. */
    NOT_A_NUMBER,
    /** Di luar [SAFETY_SPEED_LIMIT_MIN_KPH]..[SAFETY_SPEED_LIMIT_MAX_KPH]. */
    OUT_OF_RANGE,
}

/**
 * Validasi teks batas kecepatan yang diketik admin.
 *
 * Mengembalikan angka yang sudah bersih, atau alasan penolakannya. **Tidak meng-clamp**:
 * memaksa `800` menjadi `120` juga menyembunyikan salah ketiknya, dan admin akan mengira
 * ia menyimpan 800.
 *
 * Koma diterima sebagai pemisah desimal — papan ketik Indonesia menghasilkan koma, dan
 * menolaknya sebagai "bukan angka" akan terbaca seperti bug, bukan seperti aturan.
 */
fun validateSpeedLimitInput(raw: String): SafetyConfigInputResult {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) {
        return SafetyConfigInputResult.Invalid(SafetyConfigInputError.EMPTY)
    }
    val value = trimmed.replace(',', '.').toDoubleOrNull()
        ?: return SafetyConfigInputResult.Invalid(SafetyConfigInputError.NOT_A_NUMBER)
    if (value < SAFETY_SPEED_LIMIT_MIN_KPH || value > SAFETY_SPEED_LIMIT_MAX_KPH) {
        return SafetyConfigInputResult.Invalid(SafetyConfigInputError.OUT_OF_RANGE)
    }
    return SafetyConfigInputResult.Valid(value)
}

/**
 * Hasil validasi masukan.
 *
 * Tipe sendiri, dan **sengaja TIDAK dinamai `Result`**: nama itu akan mem-bayangi
 * `kotlin.Result` di seluruh paket ini, dan bayangan tipe adalah jenis kebingungan yang
 * baru terasa berbulan-bulan kemudian. Juga bukan `AppResult`, karena yang dibawa adalah
 * alasan penolakan bertipe enum — bukan `Throwable`, dan bukan kegagalan lintas-layer.
 */
sealed interface SafetyConfigInputResult {
    data class Valid(val speedLimitKph: Double) : SafetyConfigInputResult
    data class Invalid(val error: SafetyConfigInputError) : SafetyConfigInputResult
}

/**
 * Susun dokumen yang akan ditulis — **tepat tiga kunci**, tidak lebih.
 *
 * `firestore.rules:1431` memakai `hasOnlyAllowed`, jadi satu kunci tambahan membuat
 * seluruh tulisan `PERMISSION_DENIED`. Merakitnya di satu fungsi bersama, bukan di tiap
 * platform, supaya kedua platform tidak bisa berbeda pendapat soal bentuk yang sah.
 */
fun buildSafetyConfigPayload(
    speedLimitKph: Double,
    enabled: Boolean,
    updatedAtMillis: Long,
): Map<String, Any> = mapOf(
    "speedLimitKph" to speedLimitKph,
    "enabled" to enabled,
    "updatedAtMillis" to updatedAtMillis,
)
