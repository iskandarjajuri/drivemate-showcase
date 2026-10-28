package com.drivemate.showcase.domain.usecase.safety

import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.SafetyConfigRepository
import com.drivemate.showcase.domain.safety.SAFETY_SPEED_LIMIT_MAX_KPH
import com.drivemate.showcase.domain.safety.SAFETY_SPEED_LIMIT_MIN_KPH

/**
 * Simpan konfigurasi batas kecepatan (C2, Blok 3.4). Admin saja.
 *
 * Memvalidasi ULANG rentangnya di sini, walau layar sudah memvalidasi. Bukan pekerjaan
 * ganda: `firestore.rules:1436` hanya menuntut `> 0`, jadi rules tidak akan menolak
 * `800` — dan use case adalah lapisan terakhir sebelum tulisan yang tidak bergantung pada
 * layar mana pun. Kalau nanti ada pemanggil kedua (iOS, skrip admin), ia mewarisi penjaga
 * ini tanpa harus mengingatnya.
 */
class UpsertSpeedLimitConfigUseCase(
    private val repo: SafetyConfigRepository
) {
    suspend operator fun invoke(
        speedLimitKph: Double,
        enabled: Boolean,
    ): AppResult<Unit> {
        if (speedLimitKph < SAFETY_SPEED_LIMIT_MIN_KPH ||
            speedLimitKph > SAFETY_SPEED_LIMIT_MAX_KPH
        ) {
            return AppResult.failure(
                IllegalArgumentException(
                    "speedLimitKph di luar rentang " +
                        "$SAFETY_SPEED_LIMIT_MIN_KPH..$SAFETY_SPEED_LIMIT_MAX_KPH"
                )
            )
        }
        return repo.upsertSpeedLimitConfig(speedLimitKph, enabled)
    }
}
