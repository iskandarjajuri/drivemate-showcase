package com.drivemate.showcase.domain.usecase.safety

import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.SpeedViolationRepository
import com.drivemate.showcase.domain.safety.SpeedViolationPage

/** Baca riwayat pelanggaran kecepatan (C1, Blok 3.3). Admin-only — lihat repository. */
class GetSpeedViolationsUseCase(
    private val repo: SpeedViolationRepository
) {
    suspend operator fun invoke(
        sinceMillis: Long,
        limit: Int = DEFAULT_LIMIT,
    ): AppResult<SpeedViolationPage> = repo.listRecentViolations(sinceMillis, limit)

    companion object {
        /**
         * Batas dokumen per pemuatan.
         *
         * Ada batasnya karena koleksi ini append-only dan tidak pernah dipangkas — tanpa
         * batas, layar ini melambat secara diam-diam seiring umur pemakaian. 200 cukup
         * untuk jendela default 30 hari pada armada sebesar ini, dan kalau terpotong itu
         * dikatakan di layar, bukan didiamkan.
         */
        const val DEFAULT_LIMIT: Int = 200

        /** Jendela default: 30 hari. */
        const val DEFAULT_WINDOW_DAYS: Int = 30
    }
}
