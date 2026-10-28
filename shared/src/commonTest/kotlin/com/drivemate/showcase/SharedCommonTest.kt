package com.drivemate.showcase

import com.drivemate.showcase.core.model.normalizeApprovalStatus
import com.drivemate.showcase.core.model.resolveApprovalStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedCommonTest {

    @Test
    fun example() {
        assertEquals(3, 1 + 2)
    }

    @Test
    fun approvalStatusDefaultsToPendingOnUnknown() {
        assertEquals(
            null,
            resolveApprovalStatus(
                statusRaw = "UNKNOWN",
                approvedByExpat = null,
                approvedByUid = null,
                rejectedByUid = null,
                rejectedAtMillis = null,
                rejectReason = null,
                rejectionNote = null
            )
        )
    }

    @Test
    fun approvalStatusHonorsExplicitReject() {
        assertEquals(
            false,
            resolveApprovalStatus(
                statusRaw = null,
                approvedByExpat = false,
                approvedByUid = null,
                rejectedByUid = "expatUid",
                rejectedAtMillis = 123L,
                rejectReason = "reason",
                rejectionNote = null
            )
        )
    }

    @Test
    fun approvalStatusFalseWithoutMetadataIsPending() {
        assertEquals(
            null,
            resolveApprovalStatus(
                statusRaw = null,
                approvedByExpat = false,
                approvedByUid = null,
                rejectedByUid = null,
                rejectedAtMillis = null,
                rejectReason = null,
                rejectionNote = null
            )
        )
    }

    @Test
    fun approvalStatusPendingAfterRegenWhenPreviouslyApproved() {
        // Regen resets status to "pending" and clears decision metadata.
        assertEquals(
            null,
            resolveApprovalStatus(
                statusRaw = "pending",
                approvedByExpat = null,
                approvedByUid = null,
                rejectedByUid = null,
                rejectedAtMillis = null,
                rejectReason = null,
                rejectionNote = null
            )
        )
    }

    @Test
    fun approvalStatusPendingAfterRegenWhenPreviouslyPending() {
        // Regen keeps pending status when computed values change.
        assertEquals(
            null,
            resolveApprovalStatus(
                statusRaw = "pending",
                approvedByExpat = null,
                approvedByUid = null,
                rejectedByUid = null,
                rejectedAtMillis = null,
                rejectReason = null,
                rejectionNote = null
            )
        )
    }

    @Test
    fun approvalStatusDefaultsToApprovedWhenLegacyExpatTrue() {
        assertEquals(
            true,
            resolveApprovalStatus(
                statusRaw = null,
                approvedByExpat = true,
                approvedByUid = null,
                rejectedByUid = null,
                rejectedAtMillis = null,
                rejectReason = null,
                rejectionNote = null
            )
        )
    }

    @Test
    fun approvalStatusNormalizesMixedCaseStatus() {
        val normalized = normalizeApprovalStatus(
            statusRaw = "ApPrOvEd",
            approvedByExpat = null,
            approvedByUid = null,
            rejectedByUid = null,
            rejectedAtMillis = null,
            rejectReason = null,
            rejectionNote = null
        )
        assertEquals("approved", normalized.status)
    }

    @Test
    fun approvalStatusUsesApprovedByUidWhenStatusMissing() {
        val normalized = normalizeApprovalStatus(
            statusRaw = null,
            approvedByExpat = null,
            approvedByUid = "approver",
            rejectedByUid = null,
            rejectedAtMillis = null,
            rejectReason = null,
            rejectionNote = null
        )
        assertEquals("approved", normalized.status)
    }

    @Test
    fun approvalStatusRejectOverridesApproveWhenBothPresent() {
        assertEquals(
            false,
            resolveApprovalStatus(
                statusRaw = null,
                approvedByExpat = true,
                approvedByUid = "approver",
                rejectedByUid = "rejecter",
                rejectedAtMillis = 123L,
                rejectReason = "reason",
                rejectionNote = null
            )
        )
    }
}
