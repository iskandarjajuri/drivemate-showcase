package com.drivemate.showcase.feature.expat.review

/**
 * Ringkasan seleksi aksi massal Expat untuk dialog konfirmasi: berapa hari kerja, dari berapa driver, dan rentang
 * tanggalnya. Murni tampilan; tidak mengubah perilaku approve/reject di ViewModel.
 */
data class ExpatBulkSummary(
    val workdayCount: Int,
    val driverCount: Int,
    val firstEpochDay: Int?,
    val lastEpochDay: Int?
)

/** [selection] = pasangan (driverUid, epochDay) dari hari kerja yang dipilih. */
fun summarizeBulkSelection(selection: List<Pair<String, Int>>): ExpatBulkSummary = ExpatBulkSummary(
    workdayCount = selection.size,
    driverCount = selection.map { it.first }.distinct().size,
    firstEpochDay = selection.minOfOrNull { it.second },
    lastEpochDay = selection.maxOfOrNull { it.second }
)
