package com.drivemate.showcase.domain.repository

import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.account.AccountDeletionRequest

/**
 * Permintaan penghapusan akun (Blok 5, PRD §20).
 *
 * ## Interface ini sengaja TIDAK punya `deleteAccount()`
 * Bukan karena belum sempat. Akun driver dimiliki perusahaan dan catatan yang menempel
 * padanya adalah bukti upah; apa yang terjadi setelah permintaan masuk adalah **kebijakan
 * retensi** yang belum diputuskan (PRD Q5). Menambahkan method penghapusan di sini berarti
 * memutuskannya lewat pintu belakang — di layer yang paling mudah dipanggil dan paling sulit
 * ditarik kembali.
 *
 * Sesi berikutnya yang hendak menambahkannya: itu bukan pekerjaan repository. Ia butuh
 * Cloud Function ber-Admin-SDK (menghapus user Auth mustahil dari klien tanpa login ulang),
 * dan butuh jawaban Iskandar lebih dulu.
 */
interface AccountDeletionRepository {

    /** Permintaan milik [uid], atau `null` bila belum pernah ada. */
    suspend fun getRequest(uid: String): AppResult<AccountDeletionRequest?>

    /**
     * Mencatat permintaan baru untuk [uid] dengan status `pending`.
     *
     * Menimpa dokumen lama bila permintaan sebelumnya sudah ditangani — rules yang menjaga
     * bahwa menimpa saat masih `pending` ditolak, jadi pengecekan klien bukan satu-satunya
     * penjaga.
     */
    suspend fun submitRequest(
        uid: String,
        reason: String?,
        nowMillis: Long,
    ): AppResult<Unit>
}
