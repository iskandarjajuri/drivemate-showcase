package com.drivemate.showcase.core.model

/**
 * Hasil eksekusi bulk approve/reject WorkDay dengan rincian per dokumen.
 */
data class BulkActionResult(
    val action: BulkActionType,
    val attemptedCount: Int,
    val successCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
    val items: List<BulkActionItemResult>
) {
    val hasSuccess: Boolean get() = successCount > 0
    val hasIssues: Boolean get() = skippedCount > 0 || failedCount > 0
}

/**
 * Hasil per dokumen untuk operasi bulk.
 */
data class BulkActionItemResult(
    val docId: String,
    val driverUid: String?,
    val dateEpochDay: Int?,
    val outcome: BulkActionOutcome,
    val reasonCode: ReasonCode,
    val reasonMessage: String?
)

enum class BulkActionType { APPROVE, REJECT }

enum class BulkActionOutcome { SUCCESS, SKIPPED, FAILED }

enum class ReasonCode {
    NONE,
    LEGACY_MISSING_FIELDS,
    INVALID_DOC_ID_FORMAT,
    INVALID_SCHEMA,
    PERMISSION_DENIED,
    NOT_MANAGED,
    EXCEPTION
}
