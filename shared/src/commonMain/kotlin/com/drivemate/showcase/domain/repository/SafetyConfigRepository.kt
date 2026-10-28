package com.drivemate.showcase.domain.repository

import com.drivemate.showcase.core.model.SpeedLimitConfig
import com.drivemate.showcase.core.util.AppResult

/**
 * Baca dan tulis konfigurasi keselamatan global (`app_config/safety`).
 *
 * Interface BARU dan terpisah — sengaja tidak menambah method ke
 * `LocationRepository` atau repository `commonMain` lain, karena perubahan
 * signature interface yang sudah ada merambat ke kedua platform sekaligus
 * (Critical Constraint #2 di `CLAUDE.md`).
 */
interface SafetyConfigRepository {
    /** `null` kalau dokumen config belum ada — pemanggil WAJIB fail-closed. */
    suspend fun getSpeedLimitConfig(): AppResult<SpeedLimitConfig?>

    /**
     * Tulis ulang `app_config/safety` — admin saja (`firestore.rules:1429`).
     *
     * **Penulisan PENUH, bukan merge.** Rules memakai `hasOnlyAllowed` atas tepat tiga
     * kunci dan mendereferensi `speedLimitKph` serta `enabled`, jadi dokumen ini hanya
     * sah bila keduanya ada. Menulis penuh dengan tiga kunci itu membuat "dokumen
     * setengah terisi" tidak bisa lahir dari aplikasi sama sekali — bentuk kegagalan
     * yang paling berbahaya di sini, karena `enabled = true` tanpa batas yang sah
     * berarti deteksi mati sementara layar mengaku hidup.
     */
    suspend fun upsertSpeedLimitConfig(
        speedLimitKph: Double,
        enabled: Boolean,
    ): AppResult<Unit>
}
