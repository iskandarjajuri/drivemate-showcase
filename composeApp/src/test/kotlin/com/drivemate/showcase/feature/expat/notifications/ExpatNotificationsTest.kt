package com.drivemate.showcase.feature.expat.notifications

import com.drivemate.showcase.core.model.Announcement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExpatNotificationsTest {
    private val now = 1_000_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun ann(id: String, at: Long) = Announcement(id, "Judul $id", "", at, "a", null, null)

    private fun review(uid: String, name: String, pending: Int) = ExpatPendingReview(uid, name, pending)

    private fun build(
        reviews: List<ExpatPendingReview> = emptyList(),
        anns: List<Announcement> = emptyList(),
        seenAnn: Long = 0,
        chatUnread: Int = 0
    ) = buildExpatNotifications(reviews, anns, seenAnn, now, chatUnread)

    @Test
    fun tinjauanMenunggu_satuBarisPerDriver_selaluBelumDibaca() {
        val list = build(
            reviews = listOf(review("d2", "Budi", 2), review("d1", "Ani", 5), review("d3", "Cici", 0)),
            seenAnn = now
        )
        val rows = list.filter { it.kind == ExpatNotificationKind.REVIEW }
        assertEquals(listOf("review:d1", "review:d2"), rows.map { it.id })
        assertEquals(listOf(5, 2), rows.map { it.count })
        assertEquals("d1", rows.first().driverUid)
        assertEquals(2, list.unreadCount())
    }

    @Test
    fun pesanBelumDibaca_satuBarisDenganJumlah_dan_hilangBilaNol() {
        val msg = build(chatUnread = 3).single { it.kind == ExpatNotificationKind.MESSAGE }
        assertEquals(3, msg.count)
        assertTrue(msg.unread)
        assertTrue(build(chatUnread = 0).none { it.kind == ExpatNotificationKind.MESSAGE })
    }

    @Test
    fun pengumuman_lebihBaruDariPenanda_belumDibaca() {
        val list = build(
            anns = listOf(ann("1", now - day), ann("2", now - 2 * day)),
            seenAnn = now - day - 1 - day / 2
        )
        assertEquals(setOf("ann:1"), list.filter { it.unread }.map { it.id }.toSet())
    }

    @Test
    fun pengumuman_lamaDiLuarJendelaTujuhHari_tidakDihitung() {
        val list = build(anns = listOf(ann("old", now - 8 * day)), seenAnn = 0)
        assertEquals(1, list.size)
        assertEquals(0, list.unreadCount())
    }

    @Test
    fun pengumuman_setelahDitandaiDibaca_nol() {
        val list = build(anns = listOf(ann("a", now - day)), seenAnn = now)
        assertEquals(0, list.unreadCount())
    }

    @Test
    fun urutan_terbaruDulu_pengumumanLamaDiBawah() {
        val list = build(
            reviews = listOf(review("d1", "Ani", 1)),
            anns = listOf(ann("1", now - day))
        )
        assertEquals(listOf("review:d1", "ann:1"), list.map { it.id })
    }

    @Test
    fun tanpaSumber_daftarKosong() {
        assertTrue(build().isEmpty())
        assertEquals(0, build().unreadCount())
    }

    @Test
    fun hariIni_batasAwalHari() {
        val n = ExpatNotification("x", ExpatNotificationKind.ANNOUNCEMENT, "", 100, true)
        assertTrue(n.isToday(100))
        assertFalse(n.isToday(101))
        assertNull(n.driverUid)
    }
}
