package com.drivemate.showcase.core.model

enum class TrackingIssue {
    PermissionMissing,
    ProviderDisabled
}

data class TrackingStatus(
    val active: Boolean = false,
    val issue: TrackingIssue? = null,
    val batteryOptimizationEnabled: Boolean = false,
    val hideLocationWhenOffDuty: Boolean = false,
    /**
     * Driver sedang melaju di atas batas kecepatan SEKARANG (R3).
     *
     * Ikut kanal `TrackingStatusRepository` yang sudah ada — itu satu-satunya seam
     * yang memang sudah menghubungkan `LocationTrackingService` ke UI, jadi tidak
     * perlu StateFlow singleton paralel maupun round-trip ke Firestore hanya untuk
     * memberi tahu driver apa yang sudah diketahui perangkatnya sendiri.
     * Field ber-default, jadi konstruksi lama di kedua platform tetap kompilasi.
     */
    val overSpeedLimit: Boolean = false,
    /** Kecepatan terakhir terbaca (km/jam), untuk isi banner. */
    val currentSpeedKph: Double? = null,
    /** Batas yang berlaku (km/jam), untuk isi banner. */
    val speedLimitKph: Double? = null
)
