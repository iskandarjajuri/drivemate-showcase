package com.drivemate.showcase.core.util

/**
 * Canonical Firestore doc id format for attendance_daily documents.
 *
 * Format:
 * - {driverUid}_{epochDay}
 */
fun attendanceDailyDocId(driverUid: String, epochDay: Long): String = "${driverUid}_$epochDay"
