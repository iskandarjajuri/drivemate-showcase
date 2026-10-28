package com.drivemate.showcase.feature.driver

/**
 * TODO(shared parity): move to shared/commonMain once iOS needs the same
 * "next task" ordering -- kept in composeApp for now per this PR's scope.
 */
enum class DriverTask { SOP, TAP_IN, VEHICLE_CHECKLIST, SAFETY_DECLARATION }

data class DriverDailyStatus(
    val sopAccepted: Boolean,
    val tappedIn: Boolean,
    val checklistDone: Boolean,
    val safetyDone: Boolean
) {
    /** Of the two daily-flow items (checklist, safety) -- SOP/tap-in are one-time, not "daily flow". */
    val doneCount: Int get() = listOf(checklistDone, safetyDone).count { it }

    fun nextTask(): DriverTask? = when {
        !sopAccepted -> DriverTask.SOP
        !tappedIn -> DriverTask.TAP_IN
        !checklistDone -> DriverTask.VEHICLE_CHECKLIST
        !safetyDone -> DriverTask.SAFETY_DECLARATION
        else -> null
    }
}

/** Satu baris di layar Tugas hari ini. */
data class DriverTugasItem(val task: DriverTask, val done: Boolean)

/**
 * Daftar tugas yang tampil di layar Tugas: SOP hanya selama belum diterima, lalu Checklist dan
 * Deklarasi Keselamatan. Logika bisnis tetap di [DriverDailyStatus]; ini hanya pemetaan tampilan.
 */
fun DriverDailyStatus.tugasItems(): List<DriverTugasItem> = buildList {
    if (!sopAccepted) add(DriverTugasItem(DriverTask.SOP, done = false))
    add(DriverTugasItem(DriverTask.VEHICLE_CHECKLIST, checklistDone))
    add(DriverTugasItem(DriverTask.SAFETY_DECLARATION, safetyDone))
}

/** Jumlah tugas wajib yang belum selesai (termasuk SOP bila belum diterima). */
fun DriverDailyStatus.requiredRemaining(): Int = tugasItems().count { !it.done }
