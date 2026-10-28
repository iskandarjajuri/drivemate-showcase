package com.drivemate.showcase.feature.expat.queue

import com.drivemate.showcase.core.model.ApprovalStatus
import com.drivemate.showcase.feature.expat.ManagedDriverSummaryUi
import kotlin.test.Test
import kotlin.test.assertEquals

class ExpatListLogicTest {

    private fun item(name: String, day: Int, status: ApprovalStatus = ApprovalStatus.PENDING) =
        ExpatQueueItem(workdayId = "w$day$name", driverUid = "u$name", driverName = name, dateEpochDay = day, status = status)

    private fun driver(name: String, pending: Int = 0, rejected: Int = 0, approved: Int = 0) = ManagedDriverSummaryUi(
        uid = "u$name", displayName = name, email = null, totalDays = pending + rejected + approved,
        approvedCount = approved, rejectedCount = rejected, pendingCount = pending, lastEpochDay = null
    )

    @Test
    fun `antrean dikelompokkan per tanggal dengan yang tertua lebih dulu`() {
        val groups = groupQueueByDate(listOf(item("Budi", 20_003), item("Ana", 20_001), item("Cici", 20_003), item("Dedi", 20_002)))
        assertEquals(listOf(20_001, 20_002, 20_003), groups.map { it.dateEpochDay })
        assertEquals(listOf("Budi", "Cici"), groups.last().items.map { it.driverName })
    }

    @Test
    fun `dalam satu tanggal terurut nama tanpa membedakan huruf besar kecil`() {
        val groups = groupQueueByDate(listOf(item("budi", 1), item("Ana", 1), item("Cici", 1)))
        assertEquals(listOf("Ana", "budi", "Cici"), groups.single().items.map { it.driverName })
    }

    @Test
    fun `antrean kosong menghasilkan nol kelompok`() {
        assertEquals(emptyList(), groupQueueByDate(emptyList()))
    }

    @Test
    fun `driver diurutkan menunggu terbanyak lebih dulu lalu nama`() {
        val sorted = sortDriversByPending(listOf(driver("Zed", pending = 1), driver("Ana", pending = 0), driver("Budi", pending = 5), driver("Cici", pending = 1)))
        assertEquals(listOf("Budi", "Cici", "Zed", "Ana"), sorted.map { it.displayName })
    }

    @Test
    fun `pencarian lalu urutan menunggu`() {
        val list = listOf(driver("Driver Satu", pending = 2), driver("Driver Dua", pending = 5), driver("Lain", pending = 9))
        assertEquals(listOf("Driver Dua", "Driver Satu"), expatDriversForDisplay(list, "driver").map { it.displayName })
    }

    @Test
    fun `pil status driver 0 per 0 dan semua beres`() {
        assertEquals(listOf(ExpatDriverPillKind.ALL_CLEAR), driverPills(driver("A")))
        assertEquals(listOf(ExpatDriverPillKind.ALL_CLEAR), driverPills(driver("A", approved = 7)))
    }

    @Test
    fun `pil status driver menunggu dan ditolak tampil berurutan tanpa semua beres`() {
        assertEquals(listOf(ExpatDriverPillKind.PENDING), driverPills(driver("A", pending = 3)))
        assertEquals(listOf(ExpatDriverPillKind.REJECTED), driverPills(driver("A", rejected = 2)))
        assertEquals(listOf(ExpatDriverPillKind.PENDING, ExpatDriverPillKind.REJECTED), driverPills(driver("A", pending = 4, rejected = 2)))
    }

    @Test
    fun `inisial avatar`() {
        assertEquals("DS", queueAvatarInitials("Driver Uji Satu"))
        assertEquals("D", queueAvatarInitials("driver"))
        assertEquals("?", queueAvatarInitials(""))
    }
}
