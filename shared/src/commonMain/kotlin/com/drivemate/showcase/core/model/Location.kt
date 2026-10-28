package com.drivemate.showcase.core.model

/**
 * Lokasi terkini seorang driver.
 * Digunakan Admin untuk live tracking dan Driver untuk update background.
 */
data class Location(
    val driverUid: String,
    val latitude: Double?,
    val longitude: Double?,
    val speedMps: Double? = null,
    val speedKph: Double? = null,
    val headingDeg: Double? = null,
    val accuracyM: Double? = null,
    val updatedAtMillis: Long,
    val locationName: String? = null
)
