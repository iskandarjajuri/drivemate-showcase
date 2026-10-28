package com.drivemate.showcase

import com.drivemate.showcase.domain.checklist.CHECKLIST_FOLLOW_UP_NOTE_MAX_LENGTH
import com.drivemate.showcase.domain.checklist.ChecklistFollowUp
import com.drivemate.showcase.domain.checklist.ChecklistFollowUpError
import com.drivemate.showcase.domain.checklist.ChecklistFollowUpFields
import com.drivemate.showcase.domain.checklist.ChecklistFollowUpResult
import com.drivemate.showcase.domain.checklist.ChecklistFollowUpStatus
import com.drivemate.showcase.domain.checklist.ChecklistIssueCandidate
import com.drivemate.showcase.domain.checklist.outstandingChecklistIssues
import com.drivemate.showcase.domain.checklist.buildChecklistFollowUpPayload
import com.drivemate.showcase.domain.checklist.evaluateChecklistFollowUp
import com.drivemate.showcase.domain.checklist.mapChecklistFollowUp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Blok 4.1 — tindak lanjut temuan checklist (C3 pada PRD §7). */
class ChecklistFollowUpTest {

    private val now = 1_757_000_000_000L
    private val actor = "admin-1"

    private fun move(
        current: ChecklistFollowUp,
        target: ChecklistFollowUpStatus,
        ownerUid: String? = null,
        note: String? = null,
    ) = evaluateChecklistFollowUp(current, target, ownerUid, note, actor, now)

    private fun allowed(r: ChecklistFollowUpResult) =
        (r as? ChecklistFollowUpResult.Allowed)?.followUp

    private fun rejected(r: ChecklistFollowUpResult) =
        (r as? ChecklistFollowUpResult.Rejected)?.error

    private val new = ChecklistFollowUp()
    private val reviewed = ChecklistFollowUp(ChecklistFollowUpStatus.REVIEWED, ownerUid = "own-1")
    private val closed = ChecklistFollowUp(
        ChecklistFollowUpStatus.CLOSED, ownerUid = "own-1", note = "rem diganti"
    )

    // ── Tepi yang diizinkan ─────────────────────────────────────────────────────

    @Test
    fun newToReviewedNeedsAnOwnerAndGetsOne() {
        val f = allowed(move(new, ChecklistFollowUpStatus.REVIEWED, ownerUid = "own-1"))
        assertEquals(ChecklistFollowUpStatus.REVIEWED, f?.status)
        assertEquals("own-1", f?.ownerUid)
        assertEquals(now, f?.updatedAtMillis)
        assertEquals(actor, f?.updatedByUid)
    }

    @Test
    fun reviewedToClosedNeedsANoteAndGetsOne() {
        val f = allowed(move(reviewed, ChecklistFollowUpStatus.CLOSED, note = "rem diganti"))
        assertEquals(ChecklistFollowUpStatus.CLOSED, f?.status)
        assertEquals("rem diganti", f?.note)
        assertTrue(f?.isClosed ?: false)
    }

    @Test
    fun closedCanBeReopenedWithANoteBecauseTypoMustBeFixable() {
        // Yang tidak boleh bukan koreksinya, tapi koreksi yang tak berjejak.
        val f = allowed(move(closed, ChecklistFollowUpStatus.REVIEWED, note = "salah tutup"))
        assertEquals(ChecklistFollowUpStatus.REVIEWED, f?.status)
        assertEquals("salah tutup", f?.note)
    }

    // ── Tepi yang ditolak ───────────────────────────────────────────────────────

    @Test
    fun newCannotJumpStraightToClosed() {
        // Menutup temuan yang belum pernah ditinjau adalah persis penyelesaian di atas
        // kertas yang mekanisme ini ada untuk mencegah.
        assertEquals(
            ChecklistFollowUpError.CANNOT_CLOSE_UNREVIEWED,
            rejected(move(new, ChecklistFollowUpStatus.CLOSED, note = "sudah beres kok")),
        )
    }

    @Test
    fun nothingCanGoBackToNew() {
        assertEquals(
            ChecklistFollowUpError.CANNOT_RETURN_TO_NEW,
            rejected(move(reviewed, ChecklistFollowUpStatus.NEW)),
        )
        assertEquals(
            ChecklistFollowUpError.CANNOT_RETURN_TO_NEW,
            rejected(move(closed, ChecklistFollowUpStatus.NEW, note = "apa pun")),
        )
    }

    @Test
    fun reviewedWithoutOwnerIsRejected() {
        assertEquals(
            ChecklistFollowUpError.OWNER_REQUIRED,
            rejected(move(new, ChecklistFollowUpStatus.REVIEWED)),
        )
        assertEquals(
            ChecklistFollowUpError.OWNER_REQUIRED,
            rejected(move(new, ChecklistFollowUpStatus.REVIEWED, ownerUid = "   ")),
        )
    }

    @Test
    fun closingWithoutANoteIsRejected() {
        assertEquals(
            ChecklistFollowUpError.NOTE_REQUIRED,
            rejected(move(reviewed, ChecklistFollowUpStatus.CLOSED)),
        )
        assertEquals(
            ChecklistFollowUpError.NOTE_REQUIRED,
            rejected(move(reviewed, ChecklistFollowUpStatus.CLOSED, note = "   ")),
        )
    }

    @Test
    fun reopeningWithoutANoteIsRejected() {
        assertEquals(
            ChecklistFollowUpError.NOTE_REQUIRED,
            rejected(move(closed, ChecklistFollowUpStatus.REVIEWED)),
        )
    }

    @Test
    fun reopeningWithTheSameNoteAsTheClosingNoteIsRejected() {
        // Menyalin catatan penutup menjadi alasan pembukaan tidak menjelaskan apa pun.
        // `firestore.rules` menuntut hal yang sama; di sana pembandingan ini WAJIB karena
        // update Firestore bersifat merge — catatan lama masih terbawa di
        // `request.resource.data` dan akan lolos sebagai "ada catatan".
        assertEquals(
            ChecklistFollowUpError.NOTE_REQUIRED,
            rejected(move(closed, ChecklistFollowUpStatus.REVIEWED, note = "rem diganti")),
        )
    }

    @Test
    fun stayingOnTheSameStatusNeedsNoNewNote() {
        // Menyimpan ulang `closed` untuk mengganti penanggung jawab tidak boleh menuntut
        // catatan kedua — tidak ada perpindahan yang perlu dijelaskan.
        val f = allowed(move(closed, ChecklistFollowUpStatus.CLOSED, ownerUid = "own-2"))
        assertEquals("own-2", f?.ownerUid)
        assertEquals("rem diganti", f?.note)
    }

    @Test
    fun existingNoteSurvivesWhenNoNewOneIsGiven() {
        // Menghapus catatan penutup karena kolomnya dibiarkan kosong akan menghilangkan
        // satu-satunya keterangan yang dimiliki temuan itu.
        val f = allowed(move(closed, ChecklistFollowUpStatus.CLOSED, note = ""))
        assertEquals("rem diganti", f?.note)
    }

    @Test
    fun noteIsTrimmedAndCapped() {
        val long = "x".repeat(CHECKLIST_FOLLOW_UP_NOTE_MAX_LENGTH + 50)
        val f = allowed(move(reviewed, ChecklistFollowUpStatus.CLOSED, note = "  $long  "))
        assertEquals(CHECKLIST_FOLLOW_UP_NOTE_MAX_LENGTH, f?.note?.length)
    }

    // ── Pemetaan & payload ──────────────────────────────────────────────────────

    @Test
    fun documentWithoutFollowUpFieldsIsNewNotUnknown() {
        // Keadaan SELURUH dokumen production yang sudah ada. Membacanya sebagai "tidak
        // diketahui" akan membuat setiap temuan lama jadi keadaan keempat yang tak bisa
        // ditindak.
        assertEquals(ChecklistFollowUpStatus.NEW, mapChecklistFollowUp(null).status)
        assertEquals(ChecklistFollowUpStatus.NEW, mapChecklistFollowUp(emptyMap()).status)
        assertTrue(mapChecklistFollowUp(emptyMap()).isOutstanding)
    }

    @Test
    fun unknownStatusCodeFallsBackToNew() {
        val f = mapChecklistFollowUp(mapOf(ChecklistFollowUpFields.STATUS to "Selesai"))
        assertEquals(ChecklistFollowUpStatus.NEW, f.status)
    }

    @Test
    fun statusCodeIsCaseInsensitiveOnRead() {
        // Toleran saat MEMBACA, ketat saat MENULIS — dokumen yang disunting tangan lewat
        // Console tidak boleh diam-diam jadi "new".
        assertEquals(
            ChecklistFollowUpStatus.CLOSED,
            mapChecklistFollowUp(mapOf(ChecklistFollowUpFields.STATUS to " CLOSED ")).status,
        )
    }

    @Test
    fun payloadOmitsNullsAndKeepsCodesLowercase() {
        val payload = buildChecklistFollowUpPayload(ChecklistFollowUp())
        assertEquals(setOf(ChecklistFollowUpFields.STATUS), payload.keys)
        assertEquals("new", payload[ChecklistFollowUpFields.STATUS])
    }

    @Test
    fun payloadRoundTripsThroughTheMapper() {
        val f = allowed(move(reviewed, ChecklistFollowUpStatus.CLOSED, note = "rem diganti"))!!
        val back = mapChecklistFollowUp(buildChecklistFollowUpPayload(f))
        assertEquals(f.status, back.status)
        assertEquals(f.ownerUid, back.ownerUid)
        assertEquals(f.note, back.note)
        assertEquals(f.updatedAtMillis, back.updatedAtMillis)
        assertEquals(f.updatedByUid, back.updatedByUid)
    }

    @Test
    fun outstandingIsTrueUntilClosed() {
        assertTrue(new.isOutstanding)
        assertTrue(reviewed.isOutstanding)
        assertEquals(false, closed.isOutstanding)
    }

    // ── Berhenti menagih setelah ditutup ────────────────────────────────────────

    private fun candidate(
        id: String,
        uid: String,
        day: Int = 20_703,
        hasIssue: Boolean = true,
        status: ChecklistFollowUpStatus = ChecklistFollowUpStatus.NEW,
        submittedAt: Long = 1L,
    ) = ChecklistIssueCandidate(id, uid, day, submittedAt, hasIssue, status)

    @Test
    fun closedFindingNoLongerNags() {
        // Inilah yang membuat Blok 4.1 lebih dari pencatatan. Tanpa ini, baris pengecualian
        // yang sama tetap muncul setelah temuannya ditangani.
        val out = outstandingChecklistIssues(
            listOf(
                candidate("cl1", "d1", status = ChecklistFollowUpStatus.NEW),
                candidate("cl2", "d2", status = ChecklistFollowUpStatus.REVIEWED),
                candidate("cl3", "d3", status = ChecklistFollowUpStatus.CLOSED),
            ),
            20_703,
        )
        assertEquals(mapOf("d1" to "cl1", "d2" to "cl2"), out)
    }

    @Test
    fun reviewedStillNagsBecauseItIsNotDoneYet() {
        val out = outstandingChecklistIssues(
            listOf(candidate("cl1", "d1", status = ChecklistFollowUpStatus.REVIEWED)),
            20_703,
        )
        assertEquals(mapOf("d1" to "cl1"), out)
    }

    @Test
    fun cleanChecklistAndOtherDaysAreIgnored() {
        val out = outstandingChecklistIssues(
            listOf(
                candidate("cl1", "d1", hasIssue = false),
                candidate("cl2", "d2", day = 20_702),
            ),
            20_703,
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun latestSubmissionWinsIfADriverSomehowHasTwo() {
        // Hanya mungkin kalau kontrak doc-id `{uid}_{epochDay}` dilanggar; kalaupun
        // terjadi, yang mencerminkan keadaan kendaraan paling baru adalah yang terakhir.
        val out = outstandingChecklistIssues(
            listOf(
                candidate("lama", "d1", submittedAt = 100L),
                candidate("baru", "d1", submittedAt = 200L),
            ),
            20_703,
        )
        assertEquals(mapOf("d1" to "baru"), out)
    }

    @Test
    fun blankOwnerAndNoteAreReadAsAbsent() {
        val f = mapChecklistFollowUp(
            mapOf(
                ChecklistFollowUpFields.STATUS to "reviewed",
                ChecklistFollowUpFields.OWNER_UID to "  ",
                ChecklistFollowUpFields.NOTE to "",
            )
        )
        assertNull(f.ownerUid)
        assertNull(f.note)
    }
}
