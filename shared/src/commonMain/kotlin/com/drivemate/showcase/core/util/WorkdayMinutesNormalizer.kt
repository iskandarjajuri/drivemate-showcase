package com.drivemate.showcase.core.util

private const val DEFAULT_WORKDAY_MINUTES = 480L

fun normalizePersistedTotalMinutes(totalMinutesRaw: Long?, overtimeMinutesRaw: Long?): Int {
    val overtimeNonNegative = (overtimeMinutesRaw ?: 0L).coerceAtLeast(0L)
    val fallbackTotal = (DEFAULT_WORKDAY_MINUTES + overtimeNonNegative)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()

    if (totalMinutesRaw == null) return fallbackTotal

    val totalInt = totalMinutesRaw.toInt()
    return if (totalInt < 0) fallbackTotal else totalInt
}
