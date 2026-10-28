package com.drivemate.showcase.feature.expat.queue

import com.drivemate.showcase.feature.expat.ManagedDriverSummaryUi

/** Satu kelompok tanggal pada antrean Tinjau. */
internal data class ExpatQueueDayGroup(val dateEpochDay: Int, val items: List<ExpatQueueItem>)

/**
 * Kelompokkan antrean per tanggal, tanggal TERTUA lebih dulu (yang paling lama menunggu paling atas); di dalam satu
 * tanggal terurut nama driver tanpa membedakan huruf besar/kecil.
 */
internal fun groupQueueByDate(items: List<ExpatQueueItem>): List<ExpatQueueDayGroup> =
    items.groupBy { it.dateEpochDay }
        .toSortedMap()
        .map { (day, list) -> ExpatQueueDayGroup(day, list.sortedBy { it.driverName.lowercase() }) }

/** Urutan daftar Driver: menunggu terbanyak lebih dulu, lalu nama. Driver tanpa antrean ada di bawah. */
internal fun sortDriversByPending(drivers: List<ManagedDriverSummaryUi>): List<ManagedDriverSummaryUi> =
    drivers.sortedWith(
        compareByDescending<ManagedDriverSummaryUi> { it.pendingCount }
            .thenBy { it.displayName.lowercase() }
    )

/** Daftar Driver yang ditampilkan: hasil pencarian lokal yang diurutkan menurut [sortDriversByPending]. */
internal fun expatDriversForDisplay(drivers: List<ManagedDriverSummaryUi>, query: String): List<ManagedDriverSummaryUi> =
    sortDriversByPending(filterExpatDrivers(drivers, query))

/** Keadaan pil status kartu Driver. 0 menunggu dan 0 ditolak (termasuk 0/0) = "semua beres". */
internal enum class ExpatDriverPillKind { PENDING, REJECTED, ALL_CLEAR }

/** Pil yang tampil untuk satu driver, berurutan; selalu ada minimal satu (tidak ada kartu tanpa status). */
internal fun driverPills(driver: ManagedDriverSummaryUi): List<ExpatDriverPillKind> = buildList {
    if (driver.pendingCount > 0) add(ExpatDriverPillKind.PENDING)
    if (driver.rejectedCount > 0) add(ExpatDriverPillKind.REJECTED)
    if (isEmpty()) add(ExpatDriverPillKind.ALL_CLEAR)
}

/** Inisial avatar: huruf pertama kata pertama dan terakhir; satu kata = satu huruf; kosong = "?". */
internal fun queueAvatarInitials(name: String?): String {
    val words = name.orEmpty().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return "?"
    fun first(word: String): String = String(Character.toChars(word.codePointAt(0))).uppercase()
    return if (words.size == 1) first(words.first()) else first(words.first()) + first(words.last())
}
