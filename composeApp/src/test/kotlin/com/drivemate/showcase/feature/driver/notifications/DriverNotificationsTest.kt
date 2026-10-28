package com.drivemate.showcase.feature.driver.notifications

import com.drivemate.showcase.core.model.Announcement
import com.drivemate.showcase.core.model.WorkDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DriverNotificationsTest {
    private val now = 1_000_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun ann(id: String, at: Long) = Announcement(id, "Judul $id", "", at, "a", null, null)

    private fun wd(id: String, status: String?, approvedAt: Long? = null, rejectedAt: Long? = null) = WorkDay(
        id = id, driverUid = "d", dateEpochDay = 1, clockInMillis = 0, clockOutMillis = 0, totalMinutes = 0,
        status = status, approvedByUid = if (status == "approved") "x" else null,
        approvedAtMillis = approvedAt, rejectedByUid = if (status == "rejected") "x" else null,
        rejectedAtMillis = rejectedAt
    )

    private fun build(
        tasks: List<DriverPendingTask> = emptyList(),
        anns: List<Announcement> = emptyList(),
        days: List<WorkDay> = emptyList(),
        seenAnn: Long = 0,
        seenAppr: Long = 0,
        chatUnread: Int = 0
    ) = buildDriverNotifications(tasks, anns, days, seenAnn, seenAppr, now, chatUnread) { "Hari ${it.id}" }

    @Test
    fun pesanBelumDibaca_munculSebagaiSatuBarisMessage_denganJumlah() {
        val list = build(chatUnread = 3)
        val msg = list.single { it.kind == DriverNotificationKind.MESSAGE }
        assertEquals(3, msg.count)
        assertTrue(msg.unread)
        assertEquals(1, list.unreadCount())
    }

    @Test
    fun tanpaPesanBelumDibaca_tidakAdaBarisMessage() {
        assertTrue(build(chatUnread = 0).none { it.kind == DriverNotificationKind.MESSAGE })
    }

    @Test
    fun filterInfo_menyertakanPesan_filterTugas_tidak() {
        val list = build(tasks = listOf(DriverPendingTask.SOP), chatUnread = 2)
        assertEquals(1, list.filteredBy(DriverNotificationFilter.TASKS).size)
        assertTrue(list.filteredBy(DriverNotificationFilter.INFO).any { it.kind == DriverNotificationKind.MESSAGE })
    }

    @Test
    fun tugasTertunda_selalu_belumDibaca() {
        val list = build(tasks = listOf(DriverPendingTask.CHECKLIST, DriverPendingTask.SAFETY), seenAnn = now, seenAppr = now)
        assertEquals(2, list.unreadCount())
    }

    @Test
    fun pengumuman_lebihBaruDariPenanda_belumDibaca() {
        val list = build(anns = listOf(ann("1", now - day), ann("2", now - 2 * day)), seenAnn = now - day - 1 - day / 2)
        assertEquals(setOf("ann:1"), list.filter { it.unread }.map { it.id }.toSet())
    }

    @Test
    fun pengumuman_lamaDiLuarJendela_tidakDihitung() {
        val list = build(anns = listOf(ann("old", now - 8 * day)), seenAnn = 0)
        assertEquals(0, list.unreadCount())
        assertEquals(1, list.size)
    }

    @Test
    fun persetujuan_diterimaDanDitolak_denganWaktuKeputusan() {
        val list = build(
            days = listOf(
                wd("a", "approved", approvedAt = now - day),
                wd("r", "rejected", rejectedAt = now - 2 * day),
                wd("p", null)
            )
        )
        assertEquals(2, list.size)
        assertFalse(list.first { it.id == "appr:a" }.rejected)
        assertTrue(list.first { it.id == "appr:r" }.rejected)
        assertEquals(2, list.unreadCount())
    }

    @Test
    fun urutan_terbaruDulu() {
        val list = build(tasks = listOf(DriverPendingTask.SOP), anns = listOf(ann("1", now - day)))
        assertEquals(listOf("task:SOP", "ann:1"), list.map { it.id })
    }

    @Test
    fun lencana_sembilanPlus() {
        assertNull(formatNotificationBadge(0))
        assertEquals("1", formatNotificationBadge(1))
        assertEquals("9", formatNotificationBadge(9))
        assertEquals("9+", formatNotificationBadge(10))
        assertEquals("9+", formatNotificationBadge(250))
    }

    @Test
    fun filter_tugasDanInfo() {
        val list = build(tasks = listOf(DriverPendingTask.SOP), anns = listOf(ann("1", now)))
        assertEquals(1, list.filteredBy(DriverNotificationFilter.TASKS).size)
        assertEquals(1, list.filteredBy(DriverNotificationFilter.INFO).size)
        assertEquals(2, list.filteredBy(DriverNotificationFilter.ALL).size)
    }

    @Test
    fun hariIni_batasAwalHari() {
        val n = DriverNotification("x", DriverNotificationKind.ANNOUNCEMENT, "", 100, true)
        assertTrue(n.isToday(100))
        assertFalse(n.isToday(101))
    }

    @Test
    fun pembaruanBelumDilihat_hanyaPengumumanDanPersetujuanBelumDibaca_tanpaTugasDanPesan() {
        val list = build(
            tasks = listOf(DriverPendingTask.SOP),
            anns = listOf(ann("baru", now - day), ann("lama", now - 2 * day)),
            days = listOf(wd("a", "approved", approvedAt = now - day / 2)),
            seenAnn = now - day - 1 - day / 2,
            seenAppr = 0,
            chatUnread = 4
        )
        // tugas (aksi, badge Tasks sendiri) dan pesan (badge Pesan sendiri) tidak dihitung; 1 pengumuman + 1 persetujuan
        assertEquals(2, list.unseenWorkUpdates())
    }

    @Test
    fun pembaruanBelumDilihat_nolSetelahDitandaiDibaca() {
        val list = build(
            anns = listOf(ann("a", now - day)),
            days = listOf(wd("a", "approved", approvedAt = now - day)),
            seenAnn = now, seenAppr = now
        )
        assertEquals(0, list.unseenWorkUpdates())
    }
}
