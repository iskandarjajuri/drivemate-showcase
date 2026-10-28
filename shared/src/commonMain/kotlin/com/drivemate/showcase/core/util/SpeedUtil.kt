package com.drivemate.showcase.core.util

object TrackingConstants {
    const val STALE_AFTER_MILLIS: Long = 2 * 60 * 1000L
    const val SPEED_IDLE_MAX_MPS: Double = 0.6
    const val SPEED_SLOW_MAX_MPS: Double = 3.0
}

enum class SpeedTier {
    Idle,
    Slow,
    Fast,
    Unknown
}

enum class SpeedStatus {
    Idle,
    MovingSlow,
    MovingFast,
    Stale,
    Unknown
}

fun speedTierFromMps(speedMps: Double?): SpeedTier = when {
    speedMps == null -> SpeedTier.Unknown
    speedMps < TrackingConstants.SPEED_IDLE_MAX_MPS -> SpeedTier.Idle
    speedMps < TrackingConstants.SPEED_SLOW_MAX_MPS -> SpeedTier.Slow
    else -> SpeedTier.Fast
}

fun speedStatusFromMps(
    speedMps: Double?,
    updatedAtMillis: Long,
    nowMillis: Long,
    staleAfterMillis: Long = TrackingConstants.STALE_AFTER_MILLIS
): SpeedStatus = when {
    nowMillis - updatedAtMillis > staleAfterMillis -> SpeedStatus.Stale
    else -> when (speedTierFromMps(speedMps)) {
        SpeedTier.Idle -> SpeedStatus.Idle
        SpeedTier.Slow -> SpeedStatus.MovingSlow
        SpeedTier.Fast -> SpeedStatus.MovingFast
        SpeedTier.Unknown -> SpeedStatus.Unknown
    }
}

fun speedMpsFromKph(speedKph: Double?): Double? = speedKph?.div(3.6)

fun speedKphFromMps(speedMps: Double?): Double? = speedMps?.times(3.6)
