package com.drivemate.showcase

import com.drivemate.showcase.domain.account.ACCOUNT_DELETION_REASON_MAX_LENGTH
import com.drivemate.showcase.domain.account.AccountDeletionEvaluation
import com.drivemate.showcase.domain.account.AccountDeletionError
import com.drivemate.showcase.domain.account.AccountDeletionFields
import com.drivemate.showcase.domain.account.AccountDeletionRequest
import com.drivemate.showcase.domain.account.AccountDeletionStatus
import com.drivemate.showcase.domain.account.buildAccountDeletionRequestPayload
import com.drivemate.showcase.domain.account.evaluateAccountDeletionRequest
import com.drivemate.showcase.domain.account.mapAccountDeletionRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Blok 5 — gerbang toko aplikasi, jalur penghapusan akun di dalam aplikasi.
 *
 * ## Yang test ini BISA dan TIDAK BISA buktikan
 * **Bisa:** aturan siapa boleh mengirim, bentuk payload, dan pemetaan baliknya.
 *
 * **TIDAK bisa:** bahwa aplikasi memenuhi pedoman Apple 5.1.1(v). Itu bergantung pada apa
 * yang terjadi SETELAH permintaan masuk, dan itu kebijakan yang belum diputuskan Iskandar.
 * Yang dijaga di sini justru sebaliknya: bahwa lapis ini **tidak** menghapus apa pun.
 */
class AccountDeletionRequestTest {

    private fun pending(uid: String = "driver-1") = AccountDeletionRequest(
        uid = uid,
        status = AccountDeletionStatus.PENDING,
        requestedAtMillis = 1_000L,
    )

    // ─── Siapa boleh mengirim ─────────────────────────────────────────────────

    @Test
    fun aSignedOutUserCannotRequestDeletion() {
        listOf(null, "", "   ").forEach { uid ->
            assertEquals(
                AccountDeletionEvaluation.Rejected(AccountDeletionError.NOT_SIGNED_IN),
                evaluateAccountDeletionRequest(uid, existing = null, reason = null),
                "uid=${uid.orEmpty()}",
            )
        }
    }

    @Test
    fun aSecondRequestIsRejectedWhileTheFirstIsStillPending() {
        // Bukan sekadar kerapian: dua permintaan untuk satu orang membuat admin mengira ada
        // dua orang yang ingin keluar.
        assertEquals(
            AccountDeletionEvaluation.Rejected(AccountDeletionError.ALREADY_PENDING),
            evaluateAccountDeletionRequest("driver-1", existing = pending(), reason = null),
        )
    }

    @Test
    fun aRequestCanBeSentAgainAfterTheFirstOneWasHandled() {
        // Ditolak karena retensi hari ini tidak boleh mengunci seseorang selamanya.
        listOf(AccountDeletionStatus.REJECTED, AccountDeletionStatus.COMPLETED).forEach { status ->
            val handled = pending().copy(status = status)
            assertTrue(
                evaluateAccountDeletionRequest("driver-1", handled, null)
                    is AccountDeletionEvaluation.Allowed,
                "status=$status seharusnya boleh mengirim ulang",
            )
        }
    }

    // ─── Alasan ───────────────────────────────────────────────────────────────

    @Test
    fun aReasonAtTheLimitIsAcceptedAndOneCharacterOverIsNot() {
        val atLimit = "a".repeat(ACCOUNT_DELETION_REASON_MAX_LENGTH)
        assertTrue(
            evaluateAccountDeletionRequest("driver-1", null, atLimit)
                is AccountDeletionEvaluation.Allowed,
        )
        assertEquals(
            AccountDeletionEvaluation.Rejected(AccountDeletionError.REASON_TOO_LONG),
            evaluateAccountDeletionRequest("driver-1", null, atLimit + "a"),
        )
    }

    @Test
    fun aBlankReasonBecomesNullInsteadOfAnEmptyString() {
        listOf(null, "", "   ", "\n\t").forEach { raw ->
            val result = evaluateAccountDeletionRequest("driver-1", null, raw)
            assertEquals(
                AccountDeletionEvaluation.Allowed(payloadReason = null),
                result,
                "raw=${raw?.replace("\n", "\\n")}",
            )
        }
    }

    @Test
    fun aReasonIsTrimmedBeforeTheLengthIsChecked() {
        // Spasi di ujung tidak boleh menghabiskan jatah karakter pengguna.
        val padded = "  " + "a".repeat(ACCOUNT_DELETION_REASON_MAX_LENGTH) + "  "
        assertEquals(
            AccountDeletionEvaluation.Allowed("a".repeat(ACCOUNT_DELETION_REASON_MAX_LENGTH)),
            evaluateAccountDeletionRequest("driver-1", null, padded),
        )
    }

    // ─── Payload ──────────────────────────────────────────────────────────────

    @Test
    fun thePayloadNeverCarriesAnAdminOnlyFieldAndOmitsAnAbsentReason() {
        // Kunci apa pun di luar daftar ini membuat seluruh tulisan PERMISSION_DENIED,
        // karena rules memakai hasOnly.
        val payload = buildAccountDeletionRequestPayload("driver-1", nowMillis = 42L, reason = "  ")
        assertEquals(
            setOf(
                AccountDeletionFields.UID,
                AccountDeletionFields.STATUS,
                AccountDeletionFields.REQUESTED_AT,
            ),
            payload.keys,
        )
        assertEquals("driver-1", payload[AccountDeletionFields.UID])
        assertEquals(AccountDeletionStatus.PENDING.code, payload[AccountDeletionFields.STATUS])
        assertEquals(42L, payload[AccountDeletionFields.REQUESTED_AT])
    }

    @Test
    fun aRequestIsAlwaysCreatedAsPendingNeverAsCompleted() {
        // Klien tidak boleh bisa menulis status akhir — itu wewenang admin.
        val payload = buildAccountDeletionRequestPayload("driver-1", 42L, "keluar perusahaan")
        assertEquals(AccountDeletionStatus.PENDING.code, payload[AccountDeletionFields.STATUS])
        assertEquals("keluar perusahaan", payload[AccountDeletionFields.REASON])
    }

    // ─── Pemetaan balik ───────────────────────────────────────────────────────

    @Test
    fun anUnknownStatusMapsToNullRatherThanSilentlyBecomingPending() {
        // Fail-closed. Dokumen berstatus asing yang tampil sebagai "menunggu" membuat admin
        // mengira ada pekerjaan yang belum selesai.
        listOf("archived", "", "PENDING_REVIEW", null).forEach { code ->
            assertNull(
                mapAccountDeletionRequest(
                    mapOf(
                        AccountDeletionFields.UID to "driver-1",
                        AccountDeletionFields.STATUS to code,
                        AccountDeletionFields.REQUESTED_AT to 1L,
                    )
                ),
                "status=$code",
            )
        }
    }

    @Test
    fun aStatusIsMatchedCaseInsensitivelyAndTrimmed() {
        val mapped = mapAccountDeletionRequest(
            mapOf(
                AccountDeletionFields.UID to "driver-1",
                AccountDeletionFields.STATUS to "  Completed ",
                AccountDeletionFields.REQUESTED_AT to 1L,
            )
        )
        assertEquals(AccountDeletionStatus.COMPLETED, mapped?.status)
        assertFalse(mapped!!.isOpen)
    }

    @Test
    fun aDocumentMissingItsTimestampOrUidDoesNotMapAtAll() {
        assertNull(
            mapAccountDeletionRequest(
                mapOf(
                    AccountDeletionFields.UID to "driver-1",
                    AccountDeletionFields.STATUS to "pending",
                )
            ),
            "tanpa requestedAtMillis",
        )
        assertNull(
            mapAccountDeletionRequest(
                mapOf(
                    AccountDeletionFields.STATUS to "pending",
                    AccountDeletionFields.REQUESTED_AT to 1L,
                )
            ),
            "tanpa uid",
        )
        assertNull(mapAccountDeletionRequest(null))
    }

    @Test
    fun aRoundTripThroughThePayloadPreservesWhatTheUserTyped() {
        val payload = buildAccountDeletionRequestPayload("driver-1", 99L, "pindah kerja")
        val mapped = mapAccountDeletionRequest(payload)
        assertEquals("driver-1", mapped?.uid)
        assertEquals(AccountDeletionStatus.PENDING, mapped?.status)
        assertEquals(99L, mapped?.requestedAtMillis)
        assertEquals("pindah kerja", mapped?.reason)
        assertTrue(mapped!!.isOpen)
    }
}
