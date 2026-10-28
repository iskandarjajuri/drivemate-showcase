package com.drivemate.showcase.domain.usecase.admin

import com.drivemate.showcase.core.model.WorkDay
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.AdminDataRepository
import com.drivemate.showcase.domain.workday.WorkdayAdminDecisionResult
import com.drivemate.showcase.domain.workday.WorkdayDecision
import com.drivemate.showcase.domain.workday.effectiveDecision
import com.drivemate.showcase.domain.workday.evaluateAdminWorkdayDecision
import com.drivemate.showcase.domain.workday.resolveDecisionTrail

/**
 * Mencatat **langkah 2** jejak keputusan: keputusan admin atas hari kerja yang sudah
 * ditinjau supervisor (Blok 4.2).
 *
 * Memvalidasi lebih dulu, lalu menulis. Validasinya diulang di sini meski layar sudah
 * menyembunyikan tombolnya karena keadaan yang menentukan boleh/tidak berasal dari dokumen
 * yang bisa berubah **antara** layar dimuat dan tombol ditekan — supervisor bisa saja
 * mencabut, atau admin lain sudah mencatat langkahnya. Rules memeriksa hal yang sama di
 * server; ini yang membuat penolakannya bisa dijelaskan dalam bahasa manusia alih-alih
 * muncul sebagai `PERMISSION_DENIED`.
 */
class RecordAdminWorkdayDecisionUseCase(
    private val repo: AdminDataRepository,
) {
    sealed interface Outcome {
        /** Tercatat. */
        data object Saved : Outcome

        /** Ditolak sebelum menyentuh jaringan — [rejected] menyebut sebabnya. */
        data class Invalid(val rejected: WorkdayAdminDecisionResult.Rejected) : Outcome

        /** Validasinya lolos tapi write-nya gagal. */
        data class WriteFailed(val throwable: Throwable) : Outcome
    }

    suspend operator fun invoke(
        workDay: WorkDay,
        target: WorkdayDecision,
        note: String?,
        actorUid: String,
        nowMillis: Long,
    ): Outcome {
        val evaluated = evaluateAdminWorkdayDecision(
            trail = workDay.resolveDecisionTrail(),
            documentDecision = workDay.effectiveDecision(),
            target = target,
            note = note,
            actorUid = actorUid,
            nowMillis = nowMillis,
        )
        val step = when (evaluated) {
            is WorkdayAdminDecisionResult.Rejected -> return Outcome.Invalid(evaluated)
            is WorkdayAdminDecisionResult.Accepted -> evaluated.step
        }
        return when (val res = repo.recordAdminWorkdayDecision(workDay.id, step)) {
            is AppResult.Success -> Outcome.Saved
            is AppResult.Failure -> Outcome.WriteFailed(res.throwable)
        }
    }
}
