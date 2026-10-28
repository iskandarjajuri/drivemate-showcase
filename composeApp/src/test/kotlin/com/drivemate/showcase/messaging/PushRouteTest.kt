package com.drivemate.showcase.messaging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PushRouteTest {
    private val chat = mapOf(
        "type" to "chat", "threadId" to "a__b", "messageId" to "m1",
        "senderUid" to "a", "senderName" to "Driver Satu"
    )

    @Test
    fun dataChat_menjadiRuteThread_denganNamaPengirim() {
        val r = parsePushRoute(chat)
        assertEquals(PushRoute.ChatThread(threadId = "a__b", senderUid = "a", senderName = "Driver Satu"), r)
    }

    @Test
    fun namaKosong_jatuhKeUid_agarJudulLayarTidakKosong() {
        val r = parsePushRoute(chat + ("senderName" to "  ")) as PushRoute.ChatThread
        assertEquals("a", r.senderName)
    }

    @Test
    fun dataTidakLengkapAtauAsing_null() {
        assertNull(parsePushRoute(emptyMap()))
        assertNull(parsePushRoute(chat - "senderUid"))
        assertNull(parsePushRoute(chat - "threadId"))
        assertNull(parsePushRoute(chat + ("type" to "lain")))
        assertNull(parsePushRoute(mapOf("google.message_id" to "x")))
    }

    @Test
    fun uidDanThreadDenganKarakterBerbahaya_ditolak_temuanKeamanan5() {
        // `/` memecah rute navigasi (crash), spasi/`..` bukan id Firebase yang sah.
        assertNull(parsePushRoute(chat + ("senderUid" to "a/b")))
        assertNull(parsePushRoute(chat + ("senderUid" to "../x")))
        assertNull(parsePushRoute(chat + ("senderUid" to "a b")))
        assertNull(parsePushRoute(chat + ("senderUid" to "x".repeat(129))))
        assertNull(parsePushRoute(chat + ("threadId" to "a/b__c")))
        assertNull(parsePushRoute(chat + ("threadId" to "t".repeat(301))))
    }

    @Test
    fun namaPengirim_dibersihkanDariKarakterKontrol_dan_dipotong() {
        val r = parsePushRoute(chat + ("senderName" to "Pak\nManager\u0000 HR".padEnd(200, 'x'))) as PushRoute.ChatThread
        assertEquals(60, r.senderName.length)
        assertFalse(r.senderName.any { it.isISOControl() })
    }

    @Test
    fun namaPengirim_tanpaKarakterBidi_pemalsuanTampilan() {
        val r = parsePushRoute(chat + ("senderName" to "Budi\u202Eevil\u2066x\u200F")) as PushRoute.ChatThread
        assertEquals("Budievilx", r.senderName)
    }

    @Test
    fun workDenganRefIdBerbahaya_ditolak_atauKosongkan() {
        val r = parsePushRoute(mapOf("type" to "work", "kind" to "plan", "refId" to "../../etc")) as? PushRoute.Work
        assertEquals("", r?.refId)
        val ok = parsePushRoute(mapOf("type" to "work", "kind" to "plan", "refId" to "uid1_20373")) as PushRoute.Work
        assertEquals("uid1_20373", ok.refId)
    }

    @Test
    fun nilaiNull_diperlakukanHilang() {
        assertNull(parsePushRoute(chat + ("senderUid" to null)))
    }

    @Test
    fun notifikasiForeground_disembunyikanHanyaBilaThreadSamaSedangDibuka() {
        assertFalse(shouldShowForegroundChat(openThreadId = "a__b", pushThreadId = "a__b"))
        assertTrue(shouldShowForegroundChat(openThreadId = "a__c", pushThreadId = "a__b"))
        assertTrue(shouldShowForegroundChat(openThreadId = null, pushThreadId = "a__b"))
    }

    @Test
    fun idNotifikasi_stabilPerThread_berbedaAntarThread() {
        assertEquals(chatNotificationId("a__b"), chatNotificationId("a__b"))
        assertNotEquals(chatNotificationId("a__b"), chatNotificationId("a__c"))
    }

    @Test
    fun busMenyimpanRuteSampaiDikonsumSekali() {
        val bus = PushRouteBus()
        assertNull(bus.pending.value)
        bus.offer(PushRoute.ChatThread("a__b", "a", "A"))
        assertEquals("a__b", (bus.pending.value as PushRoute.ChatThread).threadId)
        bus.consume()
        assertNull(bus.pending.value)
        bus.offer(null) // intent biasa tanpa data push tidak menghapus apa pun yang tertunda
        assertNull(bus.pending.value)
    }

    @Test
    fun busTidakMenimpaRuteTertundaDenganNull() {
        val bus = PushRouteBus()
        bus.offer(PushRoute.ChatThread("a__b", "a", "A"))
        bus.offer(null)
        assertEquals("a__b", (bus.pending.value as PushRoute.ChatThread).threadId)
    }

    private val work = mapOf("type" to "work", "kind" to "plan", "refId" to "d1_20373")

    @Test
    fun dataWork_menjadiRuteKerja_denganTujuanPerJenis() {
        assertEquals(PushRoute.Work("plan", "d1_20373"), parsePushRoute(work))
        assertEquals(WorkDestination.HOME, PushRoute.Work("plan", "x").destination())
        assertEquals(WorkDestination.ANNOUNCEMENTS, PushRoute.Work("announcement", "x").destination())
        assertEquals(WorkDestination.HISTORY, PushRoute.Work("decision", "x").destination())
        assertEquals(WorkDestination.TASKS, PushRoute.Work("task", "x").destination())
    }

    @Test
    fun dataWork_jenisAsingAtauKosong_null_refIdBolehKosong() {
        assertNull(parsePushRoute(work + ("kind" to "lain")))
        assertNull(parsePushRoute(work - "kind"))
        assertEquals(PushRoute.Work("task", ""), parsePushRoute(work + ("kind" to "task") - "refId"))
    }

    @Test
    fun idNotifikasiKerja_stabilPerJenisDanReferensi() {
        assertEquals(workNotificationId("plan", "a"), workNotificationId("plan", "a"))
        assertNotEquals(workNotificationId("plan", "a"), workNotificationId("decision", "a"))
        assertNotEquals(workNotificationId("plan", "a"), workNotificationId("plan", "b"))
    }
}
