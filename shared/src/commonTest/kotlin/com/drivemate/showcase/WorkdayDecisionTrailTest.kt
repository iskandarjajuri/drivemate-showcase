package com.drivemate.showcase

import com.drivemate.showcase.core.model.WorkDay
import com.drivemate.showcase.domain.workday.WORKDAY_DECISION_NOTE_MAX_LENGTH
import com.drivemate.showcase.domain.workday.WorkdayAdminDecisionError
import com.drivemate.showcase.domain.workday.WorkdayAdminDecisionResult
import com.drivemate.showcase.domain.workday.WorkdayDecision
import com.drivemate.showcase.domain.workday.WorkdayDecisionRole
import com.drivemate.showcase.domain.workday.WorkdayDecisionStep
import com.drivemate.showcase.domain.workday.WorkdayDecisionTrailFields
import com.drivemate.showcase.domain.workday.buildWorkdayDecisionStepPayload
import com.drivemate.showcase.domain.workday.effectiveDecision
import com.drivemate.showcase.domain.workday.evaluateAdminWorkdayDecision
import com.drivemate.showcase.domain.workday.mapWorkdayDecisionTrail
import com.drivemate.showcase.domain.workday.resolveDecisionTrail
import com.drivemate.showcase.domain.workday.workdayDecisionStepPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Blok 4.2 — jejak keputusan dua langkah. */
class WorkdayDecisionTrailTest {

    private val supervisorUid = "expat-1"
    private val adminUid = "admin-1"
    private val now = 1_700_000_000_000L

    private fun workday(
        status: String? = null,
        approvedByUid: String? = null,
        approvedAtMillis: Long? = null,
        rejectedByUid: String? = null,
        rejectedAtMillis: Long? = null,
        trail: Map<*, *>? = null,
    ) = WorkDay(
        id = "wd-1",
        driverUid = "driver-1",
        dateEpochDay = 20_000,
        clockInMillis = now,
        clockOutMillis = now + 8 * 3_600_000L,
        totalMinutes = 480,
        status = status,
        approvedByUid = approvedByUid,
        approvedAtMillis = approvedAtMillis,
        rejectedByUid = rejectedByUid,
        rejectedAtMillis = rejectedAtMillis,
        decisionTrail = mapWorkdayDecisionTrail(trail),
    )

    private fun stepMap(
        status: String,
        byUid: String,
        atMillis: Long? = null,
        note: String? = null,
    ): Map<String, Any?> = buildMap {
        put(WorkdayDecisionTrailFields.STATUS, status)
        put(WorkdayDecisionTrailFields.BY_UID, byUid)
        atMillis?.let { put(WorkdayDecisionTrailFields.AT_MILLIS, it) }
        note?.let { put(WorkdayDecisionTrailFields.NOTE, it) }
    }

    private fun trailMap(
        supervisor: Map<String, Any?>? = null,
        admin: Map<String, Any?>? = null,
    ): Map<String, Any?> = buildMap {
        supervisor?.let { put(WorkdayDecisionTrailFields.SUPERVISOR, it) }
        admin?.let { put(WorkdayDecisionTrailFields.ADMIN, it) }
    }

    // ─── Pembacaan slot ───────────────────────────────────────────────────────

    @Test
    fun missingTrailFieldMapsToNull() {
        assertNull(mapWorkdayDecisionTrail(null))
    }

    @Test
    fun emptyTrailFieldMapsToNull() {
        assertNull(mapWorkdayDecisionTrail(emptyMap<String, Any?>()))
    }

    @Test
    fun bothStepsAreRead() {
        val trail = mapWorkdayDecisionTrail(
            trailMap(
                supervisor = stepMap("approved", supervisorUid, now),
                admin = stepMap("approved", adminUid, now + 1_000, note = "Sudah dicek"),
            )
        )
        assertEquals(2, trail?.recordedStepCount)
        assertEquals(true, trail?.isComplete)
        assertEquals(supervisorUid, trail?.supervisorStep?.actorUid)
        assertEquals(WorkdayDecisionRole.ADMIN, trail?.adminStep?.role)
        assertEquals("Sudah dicek", trail?.adminStep?.note)
        assertEquals(true, trail?.adminAgreesWithSupervisor)
    }

    @Test
    fun supervisorStepNeverCarriesANote() {
        // Rules melarang kunci `note` pada slot supervisor; pembacaannya ikut membuangnya
        // supaya layar tidak pernah menampilkan catatan yang tidak bisa ditulis ulang.
        val trail = mapWorkdayDecisionTrail(
            trailMap(supervisor = stepMap("approved", supervisorUid, now, note = "seharusnya hilang"))
        )
        assertNull(trail?.supervisorStep?.note)
    }

    @Test
    fun stepWithoutActorIsDropped() {
        val trail = mapWorkdayDecisionTrail(
            trailMap(admin = mapOf(WorkdayDecisionTrailFields.STATUS to "approved"))
        )
        assertNull(trail)
    }

    @Test
    fun stepWithBlankActorIsDropped() {
        val trail = mapWorkdayDecisionTrail(trailMap(admin = stepMap("approved", "   ", now)))
        assertNull(trail)
    }

    @Test
    fun stepWithPendingStatusIsDropped() {
        // "pending" bukan keputusan. Langkah yang mencatatnya bukan langkah.
        val trail = mapWorkdayDecisionTrail(trailMap(admin = stepMap("pending", adminUid, now)))
        assertNull(trail)
    }

    @Test
    fun stepWithUnknownStatusIsDropped() {
        val trail = mapWorkdayDecisionTrail(trailMap(admin = stepMap("disetujui", adminUid, now)))
        assertNull(trail)
    }

    @Test
    fun stepMissingTimeStillCountsButCarriesNoTime() {
        // Waktu yang hilang tidak membatalkan langkahnya: pelaku dan keputusannya masih
        // informasi yang benar, dan menyembunyikannya akan membuat langkah itu lenyap.
        val trail = mapWorkdayDecisionTrail(trailMap(admin = stepMap("rejected", adminUid)))
        assertEquals(adminUid, trail?.adminStep?.actorUid)
        assertNull(trail?.adminStep?.atMillis)
    }

    @Test
    fun decisionFromCodeIsCaseAndSpaceTolerantButNeverInvents() {
        assertEquals(WorkdayDecision.APPROVED, WorkdayDecision.fromCode("  Approved "))
        assertNull(WorkdayDecision.fromCode("pending"))
        assertNull(WorkdayDecision.fromCode(null))
        assertNull(WorkdayDecision.fromCode(""))
    }

    // ─── Pengisian langkah 1 untuk dokumen lama ───────────────────────────────

    @Test
    fun legacyApprovedWorkdayInfersSupervisorStep() {
        val trail = workday(
            status = "approved",
            approvedByUid = supervisorUid,
            approvedAtMillis = now,
        ).resolveDecisionTrail()

        assertEquals(WorkdayDecision.APPROVED, trail.supervisorStep?.decision)
        assertEquals(supervisorUid, trail.supervisorStep?.actorUid)
        assertEquals(now, trail.supervisorStep?.atMillis)
        assertTrue(trail.supervisorStepIsInferred)
        assertNull(trail.adminStep)
        assertNull(trail.adminAgreesWithSupervisor)
    }

    @Test
    fun legacyRejectedWorkdayInfersSupervisorStep() {
        val trail = workday(
            status = "rejected",
            rejectedByUid = supervisorUid,
            rejectedAtMillis = now,
        ).resolveDecisionTrail()

        assertEquals(WorkdayDecision.REJECTED, trail.supervisorStep?.decision)
        assertEquals(supervisorUid, trail.supervisorStep?.actorUid)
        assertTrue(trail.supervisorStepIsInferred)
    }

    @Test
    fun recordedSupervisorStepIsPreferredOverInference() {
        // Dokumen yang PUNYA slot langkah 1 tidak boleh dibaca dari `approvedByUid` —
        // justru inilah kasus yang slot itu ada untuk menyelamatkan: setelah override
        // admin, `approvedByUid` sudah berisi uid admin.
        val trail = workday(
            status = "rejected",
            rejectedByUid = adminUid,
            trail = trailMap(supervisor = stepMap("approved", supervisorUid, now)),
        ).resolveDecisionTrail()

        assertEquals(supervisorUid, trail.supervisorStep?.actorUid)
        assertEquals(WorkdayDecision.APPROVED, trail.supervisorStep?.decision)
        assertFalse(trail.supervisorStepIsInferred)
    }

    @Test
    fun recordedAdminStepSurvivesWhenSupervisorStepIsInferred() {
        val trail = workday(
            status = "approved",
            approvedByUid = supervisorUid,
            trail = trailMap(admin = stepMap("approved", adminUid, now)),
        ).resolveDecisionTrail()

        assertTrue(trail.supervisorStepIsInferred)
        assertEquals(adminUid, trail.adminStep?.actorUid)
        assertEquals(2, trail.recordedStepCount)
    }

    @Test
    fun pendingWorkdayHasNoSteps() {
        val workDay = workday(status = "pending")
        val trail = workDay.resolveDecisionTrail()
        assertEquals(0, trail.recordedStepCount)
        assertFalse(trail.supervisorStepIsInferred)
        assertNull(workDay.effectiveDecision())
    }

    @Test
    fun approvedByUidWithoutStatusStillInfersStepOne() {
        // `decisionStatusFromData` di rules menyimpulkan status dari field pelaku; sisi
        // Kotlin harus sepakat, kalau tidak layar dan server berbeda pendapat soal apakah
        // langkah 1 sudah ada.
        val workDay = workday(approvedByUid = supervisorUid, approvedAtMillis = now)
        assertEquals(WorkdayDecision.APPROVED, workDay.effectiveDecision())
        assertEquals(supervisorUid, workDay.resolveDecisionTrail().supervisorStep?.actorUid)
    }

    // ─── Validasi langkah 2 ───────────────────────────────────────────────────

    private fun evaluate(
        workDay: WorkDay,
        target: WorkdayDecision = WorkdayDecision.APPROVED,
        note: String? = null,
        actorUid: String = adminUid,
    ) = evaluateAdminWorkdayDecision(
        trail = workDay.resolveDecisionTrail(),
        documentDecision = workDay.effectiveDecision(),
        target = target,
        note = note,
        actorUid = actorUid,
        nowMillis = now + 5_000,
    )

    private fun approvedBySupervisor() = workday(
        status = "approved",
        approvedByUid = supervisorUid,
        approvedAtMillis = now,
        trail = trailMap(supervisor = stepMap("approved", supervisorUid, now)),
    )

    @Test
    fun adminDecisionOnReviewedWorkdayIsAccepted() {
        val result = evaluate(approvedBySupervisor(), note = " Angka sudah dicek ulang ")
        val accepted = result as WorkdayAdminDecisionResult.Accepted
        assertEquals(WorkdayDecisionRole.ADMIN, accepted.step.role)
        assertEquals(WorkdayDecision.APPROVED, accepted.step.decision)
        assertEquals(adminUid, accepted.step.actorUid)
        assertEquals(now + 5_000, accepted.step.atMillis)
        assertEquals("Angka sudah dicek ulang", accepted.step.note)
    }

    @Test
    fun adminDecisionWithoutNoteIsAccepted() {
        // Menyetujui peninjauan supervisor tidak menuntut alasan; yang menuntut alasan
        // adalah TIDAK setuju, dan itu lewat jalur override.
        val accepted = evaluate(approvedBySupervisor()) as WorkdayAdminDecisionResult.Accepted
        assertNull(accepted.step.note)
    }

    @Test
    fun blankNoteBecomesNoNote() {
        val accepted = evaluate(approvedBySupervisor(), note = "   ")
            as WorkdayAdminDecisionResult.Accepted
        assertNull(accepted.step.note)
    }

    @Test
    fun adminDecisionOnPendingWorkdayIsRejected() {
        val result = evaluate(workday(status = "pending"))
        assertEquals(
            WorkdayAdminDecisionError.SUPERVISOR_STEP_MISSING,
            (result as WorkdayAdminDecisionResult.Rejected).error,
        )
    }

    @Test
    fun adminDecisionDisagreeingWithDocumentIsRejected() {
        val result = evaluate(approvedBySupervisor(), target = WorkdayDecision.REJECTED)
        assertEquals(
            WorkdayAdminDecisionError.DECISION_MISMATCH,
            (result as WorkdayAdminDecisionResult.Rejected).error,
        )
    }

    @Test
    fun blankActorIsRejected() {
        val result = evaluate(approvedBySupervisor(), actorUid = "  ")
        assertEquals(
            WorkdayAdminDecisionError.ACTOR_REQUIRED,
            (result as WorkdayAdminDecisionResult.Rejected).error,
        )
    }

    @Test
    fun rerecordingTheSameDecisionIsRejected() {
        val workDay = workday(
            status = "approved",
            approvedByUid = supervisorUid,
            trail = trailMap(
                supervisor = stepMap("approved", supervisorUid, now),
                admin = stepMap("approved", "admin-2", now + 1_000),
            ),
        )
        val result = evaluate(workDay)
        assertEquals(
            WorkdayAdminDecisionError.ALREADY_RECORDED,
            (result as WorkdayAdminDecisionResult.Rejected).error,
        )
    }

    @Test
    fun staleAdminStepMayBeRerecordedAfterAnOverride() {
        // Setelah override mengubah keputusan dokumen, langkah 2 yang lama tidak lagi
        // sepakat dengannya — mencatat ulang justru yang benar.
        val workDay = workday(
            status = "rejected",
            rejectedByUid = adminUid,
            trail = trailMap(
                supervisor = stepMap("approved", supervisorUid, now),
                admin = stepMap("approved", adminUid, now + 1_000),
            ),
        )
        val result = evaluate(workDay, target = WorkdayDecision.REJECTED)
        assertTrue(result is WorkdayAdminDecisionResult.Accepted)
    }

    @Test
    fun noteAtTheLimitIsAcceptedAndOneOverIsRejected() {
        val atLimit = "x".repeat(WORKDAY_DECISION_NOTE_MAX_LENGTH)
        assertTrue(evaluate(approvedBySupervisor(), note = atLimit) is WorkdayAdminDecisionResult.Accepted)

        val overLimit = "x".repeat(WORKDAY_DECISION_NOTE_MAX_LENGTH + 1)
        val result = evaluate(approvedBySupervisor(), note = overLimit)
        assertEquals(
            WorkdayAdminDecisionError.NOTE_TOO_LONG,
            (result as WorkdayAdminDecisionResult.Rejected).error,
        )
    }

    @Test
    fun inferredSupervisorStepIsEnoughToRecordStepTwo() {
        // Dokumen production yang sudah disetujui expat tidak punya slot langkah 1. Kalau
        // simpulan itu tidak dihitung, langkah 2 tidak akan pernah bisa dicatat pada
        // seluruh data yang sudah ada.
        val workDay = workday(status = "approved", approvedByUid = supervisorUid, approvedAtMillis = now)
        assertTrue(evaluate(workDay) is WorkdayAdminDecisionResult.Accepted)
    }

    @Test
    fun adminStepIsStaleWhenTheDocumentDecisionMovedOn() {
        // Keadaan setelah `adminOverrideDecision`: status dokumen berubah, jejaknya tidak
        // disentuh. Langkah 2 masih mencatat `approved` sementara dokumennya `rejected`.
        val workDay = workday(
            status = "rejected",
            rejectedByUid = adminUid,
            trail = trailMap(
                supervisor = stepMap("approved", supervisorUid, now),
                admin = stepMap("approved", adminUid, now + 1_000),
            ),
        )
        val trail = workDay.resolveDecisionTrail()
        assertTrue(trail.adminStepIsStale(workDay.effectiveDecision()))
        // `adminAgreesWithSupervisor` bilang "sepakat" — dan itulah kenapa layar TIDAK
        // boleh menampilkannya selagi kedaluwarsa: keduanya sepakat pada keputusan yang
        // sudah ditinggalkan dokumennya.
        assertEquals(true, trail.adminAgreesWithSupervisor)
    }

    @Test
    fun adminStepIsNotStaleWhenItMatchesTheDocument() {
        val workDay = workday(
            status = "approved",
            approvedByUid = supervisorUid,
            trail = trailMap(
                supervisor = stepMap("approved", supervisorUid, now),
                admin = stepMap("approved", adminUid, now + 1_000),
            ),
        )
        assertFalse(workDay.resolveDecisionTrail().adminStepIsStale(workDay.effectiveDecision()))
    }

    @Test
    fun missingAdminStepIsNeverStale() {
        val workDay = workday(status = "approved", approvedByUid = supervisorUid)
        assertFalse(workDay.resolveDecisionTrail().adminStepIsStale(WorkdayDecision.APPROVED))
    }

    // ─── Payload ──────────────────────────────────────────────────────────────

    @Test
    fun payloadCarriesExactlyTheKeysRulesAllow() {
        val payload = buildWorkdayDecisionStepPayload(
            WorkdayDecisionStep(
                role = WorkdayDecisionRole.ADMIN,
                decision = WorkdayDecision.REJECTED,
                actorUid = adminUid,
                atMillis = now,
                note = "Alasan",
            )
        )
        assertEquals(setOf("status", "byUid", "atMillis", "note"), payload.keys)
        assertEquals("rejected", payload["status"])
        assertEquals(adminUid, payload["byUid"])
        assertEquals(now, payload["atMillis"])
    }

    @Test
    fun payloadOmitsNoteEntirelyWhenAbsent() {
        // Rules mengizinkan `note` tidak ada, tapi menolak kunci di luar keempatnya —
        // mengirim `note = null` akan menambah kunci yang nilainya bukan string.
        val payload = buildWorkdayDecisionStepPayload(
            WorkdayDecisionStep(
                role = WorkdayDecisionRole.SUPERVISOR,
                decision = WorkdayDecision.APPROVED,
                actorUid = supervisorUid,
                atMillis = now,
            )
        )
        assertEquals(setOf("status", "byUid", "atMillis"), payload.keys)
    }

    @Test
    fun stepPathsMatchTheRulesContract() {
        assertEquals("decisionTrail.supervisor", workdayDecisionStepPath(WorkdayDecisionRole.SUPERVISOR))
        assertEquals("decisionTrail.admin", workdayDecisionStepPath(WorkdayDecisionRole.ADMIN))
    }
}
