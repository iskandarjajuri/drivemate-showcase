package com.drivemate.showcase.data.repository

import com.drivemate.showcase.bridge.IOSPlatformBridgeRegistry
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.account.AccountDeletionRequest
import com.drivemate.showcase.domain.account.buildAccountDeletionRequestPayload
import com.drivemate.showcase.domain.account.mapAccountDeletionRequest
import com.drivemate.showcase.domain.repository.AccountDeletionRepository
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Implementasi iOS untuk [AccountDeletionRepository] — gerbang toko aplikasi (Blok 5, PRD §20).
 *
 * ## Kenapa berkas ini ada, dan kenapa iOS-lah platform yang paling membutuhkannya
 * Pedoman Apple 5.1.1(v) menuntut jalur penghapusan akun **di dalam aplikasi**. Sampai berkas
 * ini dibuat, jalur itu ada di Android saja: `FirebaseAccountDeletionRepository` +
 * `AccountSettingsScreen` (PR #300), sementara iOS nol berkas. Artinya satu-satunya platform
 * yang benar-benar MEWAJIBKAN mekanisme ini adalah platform yang tidak memilikinya — dan
 * `docs/WORKPLAN_DRIVEMATE.md` Blok 5.b sempat menyebut gerbang itu `selesai`. Koreksinya ikut
 * dalam perubahan yang sama dengan berkas ini, bukan menyusul.
 *
 * ## Nol logika di sini — semuanya dipinjam dari commonMain
 * Kelas ini hanya menjembatani I/O. Evaluasi ([com.drivemate.showcase.domain.account.evaluateAccountDeletionRequest]),
 * penyusunan payload ([buildAccountDeletionRequestPayload]), dan pemetaan dokumen
 * ([mapAccountDeletionRequest]) semuanya fungsi murni di commonMain yang **sama persis**
 * dipanggil Android. Itu bukan kerapian gaya: satu-satunya cara menjamin kedua platform tidak
 * berbeda pendapat soal "sudah ada permintaan menunggu" atau soal bentuk dokumen yang ditulis
 * adalah memanggil fungsi yang sama, bukan menulis fungsi kedua yang kebetulan sepakat.
 *
 * Ia juga TIDAK menghapus apa pun — lihat KDoc [AccountDeletionRepository] untuk alasannya.
 */
class IOSFirebaseAccountDeletionRepository : AccountDeletionRepository {

    private val bridge
        get() = IOSPlatformBridgeRegistry.userDirectoryBridge
            ?: error("IOSUserDirectoryPlatformBridge is not installed")

    /**
     * Dokumen yang tidak ada menghasilkan `Success(null)`, **bukan** `Failure`.
     *
     * Mayoritas pengguna tidak pernah meminta penghapusan akun, jadi "tidak ada dokumen"
     * adalah keadaan normal layar ini. Mengembalikannya sebagai kegagalan akan membuat layar
     * Akun menampilkan galat merah kepada hampir semua orang yang membukanya.
     */
    override suspend fun getRequest(uid: String): AppResult<AccountDeletionRequest?> =
        suspendCancellableCoroutine { cont ->
            bridge.getAccountDeletionRequest(uid) { data, error ->
                if (!cont.isCompleted) cont.resume(
                    if (error != null) AppResult.failure(IllegalStateException(error))
                    else AppResult.success(mapAccountDeletionRequest(data))
                )
            }
        }

    override suspend fun submitRequest(
        uid: String,
        reason: String?,
        nowMillis: Long,
    ): AppResult<Unit> = suspendCancellableCoroutine { cont ->
        val payload = buildAccountDeletionRequestPayload(uid, nowMillis, reason)
        bridge.submitAccountDeletionRequest(uid, payload) { error ->
            if (!cont.isCompleted) cont.resume(
                if (error != null) AppResult.failure(IllegalStateException(error))
                else AppResult.success(Unit)
            )
        }
    }
}
