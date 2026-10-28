package com.drivemate.showcase.domain.usecase.admin

import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.checklist.ChecklistFollowUp
import com.drivemate.showcase.domain.checklist.ChecklistFollowUpResult
import com.drivemate.showcase.domain.checklist.ChecklistFollowUpStatus
import com.drivemate.showcase.domain.checklist.evaluateChecklistFollowUp
import com.drivemate.showcase.domain.repository.AdminDataRepository

/**
 * Pindahkan status tindak lanjut temuan checklist (C3, Blok 4.1).
 *
 * Mesin statusnya dievaluasi **di sini**, bukan di ViewModel: dengan begitu pemanggil kedua
 * mana pun — iOS, skrip admin — mewarisi aturan yang sama tanpa harus mengingatnya. Yang
 * ditolak tidak pernah sampai ke repository, jadi tidak ada tulisan yang dikirim hanya untuk
 * ditolak `firestore.rules` beberapa ratus milidetik kemudian.
 */
class UpdateChecklistFollowUpUseCase(
    private val repo: AdminDataRepository
) {
    /** Hasil: keadaan baru bila sah, atau alasan penolakan yang bisa dipetakan ke teks. */
    suspend operator fun invoke(
        checklistId: String,
        current: ChecklistFollowUp,
        target: ChecklistFollowUpStatus,
        ownerUid: String?,
        note: String?,
        actorUid: String,
        nowMillis: Long,
    ): Outcome = when (
        val evaluated = evaluateChecklistFollowUp(
            current = current,
            target = target,
            ownerUid = ownerUid,
            note = note,
            actorUid = actorUid,
            nowMillis = nowMillis,
        )
    ) {
        is ChecklistFollowUpResult.Rejected -> Outcome.Invalid(evaluated)
        is ChecklistFollowUpResult.Allowed ->
            when (val res = repo.updateChecklistFollowUp(checklistId, evaluated.followUp)) {
                is AppResult.Success -> Outcome.Saved(evaluated.followUp)
                is AppResult.Failure -> Outcome.WriteFailed(res.throwable)
            }
    }

    /**
     * Tiga hasil, bukan dua.
     *
     * "Ditolak aturan" dan "gagal menulis" adalah hal yang berbeda bagi pengguna: yang
     * pertama diperbaiki dengan mengubah masukan, yang kedua dengan mencoba lagi. Meleburnya
     * jadi satu `Failure` akan membuat layar menyuruh admin mencoba lagi sesuatu yang tidak
     * akan pernah berhasil.
     */
    sealed interface Outcome {
        data class Saved(val followUp: ChecklistFollowUp) : Outcome
        data class Invalid(val rejected: ChecklistFollowUpResult.Rejected) : Outcome
        data class WriteFailed(val throwable: Throwable) : Outcome
    }
}
