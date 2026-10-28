package com.drivemate.showcase.core.model

/**
 * Konfigurasi batas kecepatan global perusahaan, dibaca dari `app_config/safety`.
 *
 * **Fail-closed by design**: kalau dokumen `app_config/safety` belum ada di Firestore
 * (kondisi produksi hari pertama), repository mengembalikan `null` dan deteksi mati
 * total — tidak ada banner, tidak ada notifikasi. Ini keputusan sadar: satu alert
 * kecepatan palsu memanggil tiga orang sekaligus (driver, expat, admin), jadi diam
 * lebih baik daripada salah bunyi. Cara mengisi nilainya ada di
 * `AGENTS/DRIVEMATE_STATE.md`.
 */
data class SpeedLimitConfig(
    val speedLimitKph: Double,
    val enabled: Boolean
)
