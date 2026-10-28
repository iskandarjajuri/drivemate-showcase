package com.drivemate.showcase.domain.usecase.account

import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.account.AccountDeletionError
import com.drivemate.showcase.domain.account.AccountDeletionEvaluation
import com.drivemate.showcase.domain.account.AccountDeletionRequest
import com.drivemate.showcase.domain.account.evaluateAccountDeletionRequest
import com.drivemate.showcase.domain.repository.AccountDeletionRepository
import com.drivemate.showcase.domain.repository.AuthRepository

/**
 * Mengirim permintaan penghapusan akun untuk pengguna yang sedang login (Blok 5).
 *
 * Uid diambil dari sesi, **tidak pernah** dari parameter: satu-satunya akun yang boleh
 * diminta hapus adalah milik penelepon sendiri, dan menerimanya sebagai argumen membuat
 * pemanggil berikutnya bisa mengirim uid orang lain tanpa melanggar tipe apa pun. Rules
 * menolaknya juga, tapi lapis pertama sebaiknya tidak pernah menyusun permintaan itu.
 */
class RequestAccountDeletionUseCase(
    private val authRepo: AuthRepository,
    private val repo: AccountDeletionRepository,
) {
    sealed interface Outcome {
        /** Permintaan tercatat. Tidak ada data yang dihapus oleh langkah ini. */
        data object Submitted : Outcome

        /** Ditolak sebelum menyentuh jaringan. */
        data class Invalid(val error: AccountDeletionError) : Outcome

        data class Failed(val throwable: Throwable) : Outcome
    }

    suspend operator fun invoke(reason: String?, nowMillis: Long): Outcome {
        val uid = when (val session = authRepo.currentUser()) {
            is AppResult.Success -> session.data?.uid
            is AppResult.Failure -> return Outcome.Failed(session.throwable)
        }
        if (uid.isNullOrBlank()) {
            return Outcome.Invalid(AccountDeletionError.NOT_SIGNED_IN)
        }

        // Permintaan yang sudah ada dibaca lebih dulu supaya "sudah pernah mengirim" bisa
        // dijawab dengan kalimat yang benar, bukan dengan galat izin dari rules.
        val existing: AccountDeletionRequest? = when (val res = repo.getRequest(uid)) {
            is AppResult.Success -> res.data
            is AppResult.Failure -> return Outcome.Failed(res.throwable)
        }

        return when (val evaluation = evaluateAccountDeletionRequest(uid, existing, reason)) {
            is AccountDeletionEvaluation.Rejected -> Outcome.Invalid(evaluation.error)
            is AccountDeletionEvaluation.Allowed ->
                when (val res = repo.submitRequest(uid, evaluation.payloadReason, nowMillis)) {
                    is AppResult.Success -> Outcome.Submitted
                    is AppResult.Failure -> Outcome.Failed(res.throwable)
                }
        }
    }
}

/** Membaca permintaan milik pengguna yang sedang login, untuk ditampilkan di layar akun. */
class GetAccountDeletionRequestUseCase(
    private val authRepo: AuthRepository,
    private val repo: AccountDeletionRepository,
) {
    suspend operator fun invoke(): AppResult<AccountDeletionRequest?> {
        val uid = when (val session = authRepo.currentUser()) {
            is AppResult.Success -> session.data?.uid
            is AppResult.Failure -> return AppResult.Failure(session.throwable)
        }
        if (uid.isNullOrBlank()) return AppResult.Success(null)
        return repo.getRequest(uid)
    }
}
