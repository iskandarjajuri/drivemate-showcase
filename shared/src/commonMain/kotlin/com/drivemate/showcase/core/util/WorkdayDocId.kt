package com.drivemate.showcase.core.util

/**
 * Canonical Firestore doc id format for workdays documents.
 *
 * Format:
 * - {driverUid}_{dateEpochDay}
 */
fun workdayDocId(driverUid: String, dateEpochDay: Int): String = "${driverUid}_$dateEpochDay"
