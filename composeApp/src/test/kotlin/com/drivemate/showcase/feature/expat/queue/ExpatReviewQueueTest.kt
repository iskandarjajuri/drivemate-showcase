package com.drivemate.showcase.feature.expat.queue

import com.drivemate.showcase.core.model.ApprovalStatus
import com.drivemate.showcase.feature.expat.ManagedDriverSummaryUi
import kotlin.test.Test
import kotlin.test.assertEquals

class ExpatReviewQueueTest {
    private fun item(id: String, name: String, day: Int, status: ApprovalStatus) =
        ExpatQueueItem(id, "uid-$name", name, day, status)

    private fun driver(name: String) = ManagedDriverSummaryUi("uid-$name", name, null, 0, 0, 0, 0, null)

    private val items = listOf(
        item("a", "Budi", 20, ApprovalStatus.PENDING),
        item("b", "Ani", 10, ApprovalStatus.PENDING),
        item("c", "Cici", 15, ApprovalStatus.REJECTED),
        item("d", "Ani", 12, ApprovalStatus.APPROVED),
        item("e", "Andi", 10, ApprovalStatus.PENDING)
    )

    @Test
    fun pending_tertuaDulu_seriUrutNama() {
        val ids = filterExpatQueue(items, ExpatQueueFilter.PENDING).map { it.workdayId }
        assertEquals(listOf("e", "b", "a"), ids)
    }

    @Test
    fun rejected_hanyaDitolak() {
        assertEquals(listOf("c"), filterExpatQueue(items, ExpatQueueFilter.REJECTED).map { it.workdayId })
    }

    @Test
    fun all_terbaruDulu() {
        val ids = filterExpatQueue(items, ExpatQueueFilter.ALL).map { it.workdayId }
        assertEquals(listOf("a", "c", "d", "e", "b"), ids)
    }

    @Test
    fun hitungPerFilter() {
        val counts = countExpatQueue(items)
        assertEquals(3, counts[ExpatQueueFilter.PENDING])
        assertEquals(5, counts[ExpatQueueFilter.ALL])
        assertEquals(1, counts[ExpatQueueFilter.REJECTED])
    }

    @Test
    fun antreanKosong_semuaNol() {
        assertEquals(emptyList(), filterExpatQueue(emptyList(), ExpatQueueFilter.PENDING))
        assertEquals(0, countExpatQueue(emptyList())[ExpatQueueFilter.ALL])
    }

    @Test
    fun pencarianDriver_tanpaMembedakanHurufDanTrim() {
        val drivers = listOf(driver("Budi Santoso"), driver("Ani"), driver("Andi"))
        assertEquals(listOf("Budi Santoso"), filterExpatDrivers(drivers, "  budi ").map { it.displayName })
        assertEquals(listOf("Andi"), filterExpatDrivers(drivers, "AND").map { it.displayName })
    }

    @Test
    fun pencarianKosong_mengembalikanSemua() {
        val drivers = listOf(driver("Ani"), driver("Budi"))
        assertEquals(drivers, filterExpatDrivers(drivers, "   "))
        assertEquals(emptyList(), filterExpatDrivers(drivers, "zzz"))
    }
}
