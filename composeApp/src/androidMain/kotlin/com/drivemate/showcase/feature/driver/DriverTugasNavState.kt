package com.drivemate.showcase.feature.driver

/** Progress mapping for the Tugas tab's ring + remaining-count badge in the floating nav bar. */
data class DriverTugasNavState(
    val doneCount: Int,
    val totalCount: Int
) {
    val isComplete: Boolean get() = doneCount >= totalCount
    val remainingCount: Int get() = (totalCount - doneCount).coerceAtLeast(0)
    val progress: Float get() = if (totalCount <= 0) 0f else doneCount.toFloat() / totalCount
}

fun DriverDailyStatus.toTugasNavState(): DriverTugasNavState =
    DriverTugasNavState(doneCount = doneCount, totalCount = TUGAS_TOTAL_COUNT)

private const val TUGAS_TOTAL_COUNT = 2
