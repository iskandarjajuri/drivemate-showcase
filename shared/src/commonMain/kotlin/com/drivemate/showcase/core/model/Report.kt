package com.drivemate.showcase.core.model

/**
 * Cross‑platform entity for reports stored in Firestore collection "reports".
 *
 * Fields mirror Firestore payload used by FirebaseReportRepository.
 *
 * @property id               Firestore document id (AUTO_ID).
 * @property driverUid         Driver identifier (email/UID).
 * @property dateEpochDay     Epoch day (days since 1970‑01‑01).
 * @property createdAtMillis  Creation time in epoch millis.
 * @property updatedAtMillis  Last update time in epoch millis.
 * @property notes            Optional free text.
 * @property status           Optional workflow status ("SUBMITTED", "APPROVED", "REJECTED", etc.).
 */
data class Report(
    val id: String = "",
    val driverUid: String = "",
    val dateEpochDay: Int = 0,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
    val notes: String? = null,
    val status: String? = null
)