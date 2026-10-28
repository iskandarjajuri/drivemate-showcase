package com.drivemate.showcase.domain.repository

import com.drivemate.showcase.core.model.SpeedViolation
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.safety.SpeedViolationPage

/** Tulis dan baca event pelanggaran kecepatan (append-only) di `speed_violations`. */
interface SpeedViolationRepository {
    suspend fun reportViolation(violation: SpeedViolation): AppResult<Unit>

    /**
     * Baca pelanggaran terbaru sejak [sinceMillis], terbaru lebih dulu, paling banyak [limit].
     *
     * **Tanpa parameter `driverUid`, dan itu keputusan sadar.** Menyaring per driver di
     * server berarti `where driverUid == X` **dan** rentang `occurredAtMillis`, yang menuntut
     * indeks komposit yang tidak ada di production — query-nya akan gagal di perangkat
     * sesudah lolos semua gate di sini. Menyaring satu field saja (`occurredAtMillis`, field
     * yang sama dengan `orderBy`) tidak butuh indeks tambahan, dan layar admin memang ingin
     * seluruh driver sekaligus. Penyaringan per driver dilakukan di memori oleh pemanggil.
     *
     * `firestore.rules:1450` mengizinkan `read` untuk admin (semua), expat (driver
     * kelolaannya), dan driver (miliknya sendiri). Query tanpa penyaring hanya lolos untuk
     * admin — jalur expat/driver butuh penyaring `driverUid` dan karena itu butuh indeks
     * komposit; belum dibuka di sini.
     */
    suspend fun listRecentViolations(
        sinceMillis: Long,
        limit: Int,
    ): AppResult<SpeedViolationPage>
}
