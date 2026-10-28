package com.drivemate.showcase.feature.chat

import com.drivemate.showcase.core.model.ChatMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ChatMessageGroupingTest {
    private val zone = "Asia/Jakarta"
    // 2026-10-10 10:00 WIB
    private val now = 1791601200000L
    private val minute = 60_000L

    private fun msg(id: String, sender: String, at: Long) =
        ChatMessage(id = id, threadId = "t", senderUid = sender, senderName = null, body = id, createdAtMillis = at)

    private fun bubbles(items: List<ChatListItem>) = items.filterIsInstance<ChatListItem.Bubble>()

    @Test
    fun kosong_tanpaItem() = assertTrue(buildChatListItems(emptyList(), "me", zone, now).isEmpty())

    @Test
    fun pesanBerurutanSamaPengirim_berkelompokDanWaktuDiAkhir() {
        val items = buildChatListItems(
            listOf(msg("a", "me", now), msg("b", "me", now + minute), msg("c", "me", now + 2 * minute)),
            "me", zone, now + 3 * minute
        )
        assertIs<ChatListItem.DateSeparator>(items.first())
        val b = bubbles(items)
        assertEquals(listOf(BubblePosition.First, BubblePosition.Middle, BubblePosition.Last), b.map { it.position })
        assertEquals(listOf(false, false, true), b.map { it.showTime })
        assertTrue(b.all { it.isMine })
    }

    @Test
    fun gantiPengirim_memutusKelompok() {
        val b = bubbles(buildChatListItems(listOf(msg("a", "me", now), msg("b", "you", now + minute)), "me", zone, now))
        assertEquals(listOf(BubblePosition.Single, BubblePosition.Single), b.map { it.position })
        assertEquals(listOf(true, true), b.map { it.showTime })
        assertFalse(b[1].isMine)
    }

    @Test
    fun jedaLebihDariLimaMenit_memutusKelompok() {
        val b = bubbles(buildChatListItems(listOf(msg("a", "me", now), msg("b", "me", now + 6 * minute)), "me", zone, now))
        assertEquals(listOf(BubblePosition.Single, BubblePosition.Single), b.map { it.position })
    }

    @Test
    fun pemisahTanggal_hariIniKemarinDanLain() {
        val day = 24 * 60 * minute
        val items = buildChatListItems(
            listOf(msg("a", "me", now - 3 * day), msg("b", "me", now - day), msg("c", "me", now)),
            "me", zone, now
        )
        val kinds = items.filterIsInstance<ChatListItem.DateSeparator>().map { it.kind }
        assertEquals(listOf(ChatDayKind.Date, ChatDayKind.Yesterday, ChatDayKind.Today), kinds)
    }

    @Test
    fun hariBerbeda_memutusKelompokMeskiJedaPendek() {
        // 23:59 WIB lalu 00:01 WIB hari berikutnya
        val late = 1791651540000L // 23:59
        val b = bubbles(buildChatListItems(listOf(msg("a", "me", late), msg("b", "me", late + 2 * minute)), "me", zone, late + 3 * minute))
        assertEquals(listOf(BubblePosition.Single, BubblePosition.Single), b.map { it.position })
    }
}
