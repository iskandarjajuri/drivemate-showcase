package com.drivemate.showcase

import com.drivemate.showcase.core.model.ChatContact
import com.drivemate.showcase.core.model.ChatMessage
import com.drivemate.showcase.core.model.Role
import com.drivemate.showcase.core.util.CHAT_HIDDEN_IDS_MAX
import com.drivemate.showcase.core.util.addHiddenId
import com.drivemate.showcase.core.model.ChatThreadSummary
import com.drivemate.showcase.core.util.chatUnreadTotal
import com.drivemate.showcase.core.util.effectiveChatLang
import com.drivemate.showcase.core.util.isMessageRead
import com.drivemate.showcase.core.util.normalizeChatRole
import com.drivemate.showcase.core.util.parseDirectoryContact
import com.drivemate.showcase.core.util.parseThreadSummary
import com.drivemate.showcase.core.util.sortContacts
import com.drivemate.showcase.core.util.sortThreads
import com.drivemate.showcase.core.util.visibleMessages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatLogicTest {
    @Test
    fun role_nilaiNyataDanLama() {
        assertEquals(Role.ADMIN, normalizeChatRole("Admin"))
        assertEquals(Role.DRIVER, normalizeChatRole(" driver "))
        assertEquals(Role.EXPAT, normalizeChatRole("expat"))
        assertEquals(Role.EXPAT, normalizeChatRole("expatriate"))
        assertEquals(Role.EXPAT, normalizeChatRole("expatriat"))
        assertNull(normalizeChatRole("unknown")); assertNull(normalizeChatRole(null)); assertNull(normalizeChatRole(3))
    }

    @Test
    fun kontak_filterDiriSendiriTidakAktifDanUnknown() {
        val ok = mapOf("displayName" to " Budi ", "role" to "driver", "active" to true)
        assertEquals("Budi", parseDirectoryContact("u1", ok, "me")?.displayName)
        assertNull(parseDirectoryContact("me", ok, "me"))
        assertNull(parseDirectoryContact("u1", ok + ("active" to false), "me"))
        assertNull(parseDirectoryContact("u1", ok + ("role" to "unknown"), "me"))
        assertNull(parseDirectoryContact("u1", null, "me"))
        // active hilang = aktif; nama kosong -> uid
        assertEquals("u2", parseDirectoryContact("u2", mapOf("role" to "admin"), "me")?.displayName)
    }

    @Test
    fun kontak_urutNamaTanpaPekaHuruf() {
        val list = listOf(
            ChatContact("3", "citra", Role.DRIVER, null),
            ChatContact("1", "Andi", Role.ADMIN, null),
            ChatContact("2", "budi", Role.EXPAT, null)
        )
        assertEquals(listOf("Andi", "budi", "citra"), sortContacts(list).map { it.displayName })
    }

    @Test
    fun thread_dipetakanDanUnreadMilikSendiri() {
        val data = mapOf(
            "participantUids" to listOf("me", "you"),
            "lastMessage" to mapOf("senderUid" to "you", "preview" to "Halo", "createdAtMillis" to 5L, "deleted" to false),
            "lastMessageAtMillis" to 9L,
            "unread" to mapOf("me" to 3L, "you" to 0L)
        )
        val s = parseThreadSummary("me__you", data, "me")!!
        assertEquals("you", s.partnerUid); assertEquals("Halo", s.lastMessagePreview)
        assertEquals(3, s.unreadCount); assertEquals(9L, s.lastMessageAtMillis); assertFalse(s.lastMessageDeleted)
        assertNull(parseThreadSummary("x", data, "orang-lain"))
        assertNull(parseThreadSummary("x", mapOf("participantUids" to "bukan list"), "me"))
    }

    @Test
    fun thread_tombstoneDanUrutan() {
        val deleted = parseThreadSummary("a", mapOf(
            "participantUids" to listOf("me", "p"),
            "lastMessage" to mapOf("deleted" to true, "preview" to "", "createdAtMillis" to 7L)
        ), "me")!!
        assertTrue(deleted.lastMessageDeleted); assertEquals(7L, deleted.lastMessageAtMillis); assertEquals(0, deleted.unreadCount)
        val newer = deleted.copy(threadId = "b", lastMessageAtMillis = 20L)
        assertEquals(listOf("b", "a"), sortThreads(listOf(deleted, newer)).map { it.threadId })
    }

    @Test
    fun statusBaca_batasSamaDenganDibaca() {
        assertFalse(isMessageRead(100L, null))
        assertFalse(isMessageRead(100L, 99L))
        assertTrue(isMessageRead(100L, 100L))
        assertTrue(isMessageRead(100L, 500L))
    }

    @Test
    fun pesanTersembunyi_dibuangTombstoneTetap() {
        val m = { id: String, del: Boolean -> ChatMessage(id, "t", "u", null, if (del) "" else "x", 1L, deleted = del) }
        val result = visibleMessages(listOf(m("1", false), m("2", true), m("3", false)), setOf("1"))
        assertEquals(listOf("2", "3"), result.map { it.id })
    }

    @Test
    fun hiddenIds_tertuaDibuangSaatPenuh_tanpaGanda() {
        val full = (1..CHAT_HIDDEN_IDS_MAX).map { "m$it" }
        val next = addHiddenId(full, "baru")
        assertEquals(CHAT_HIDDEN_IDS_MAX, next.size)
        assertEquals("m2", next.first()); assertEquals("baru", next.last())
        assertEquals(full, addHiddenId(full, "m10"))
        assertEquals(listOf("a", "b"), addHiddenId(listOf("a"), "b"))
    }

    @Test
    fun bahasaEfektif_langEksplisitMenang_laluFallbackPeran_samaDenganServer() {
        assertEquals("id", effectiveChatLang("id", Role.EXPAT))
        assertEquals("ja", effectiveChatLang("ja", Role.DRIVER))
        assertEquals("ja", effectiveChatLang(null, Role.EXPAT))
        assertEquals("id", effectiveChatLang(null, Role.DRIVER))
        assertEquals("id", effectiveChatLang(null, Role.ADMIN))
        // lang tak dikenal diabaikan -> fallback peran
        assertEquals("ja", effectiveChatLang("en", Role.EXPAT))
        assertEquals("id", effectiveChatLang("", Role.DRIVER))
        // tak ada lang dan tak ada peran -> tak diketahui (tak menebak)
        assertNull(effectiveChatLang(null, null))
    }

    @Test
    fun kontak_membawaLangBilaSah() {
        val base = mapOf("displayName" to "Budi", "role" to "expat")
        assertEquals("ja", parseDirectoryContact("u1", base + ("lang" to "ja"), "me")?.lang)
        assertNull(parseDirectoryContact("u1", base + ("lang" to "en"), "me")?.lang)
        assertNull(parseDirectoryContact("u1", base, "me")?.lang)
    }

    private fun th(id: String, unread: Int, lastDeleted: Boolean = false) =
        ChatThreadSummary(id, "p$id", "x", lastDeleted, "p$id", 1L, unread)

    @Test
    fun belumDibaca_totalSemuaThread() {
        assertEquals(0, chatUnreadTotal(emptyList()))
        assertEquals(0, chatUnreadTotal(listOf(th("a", 0), th("b", 0))))
        assertEquals(5, chatUnreadTotal(listOf(th("a", 2), th("b", 3), th("c", 0))))
    }

    @Test
    fun belumDibaca_pesanTerakhirTerhapus_tidakDihitung_danNegatifDiabaikan() {
        assertEquals(3, chatUnreadTotal(listOf(th("a", 4, lastDeleted = true), th("b", 3))))
        assertEquals(1, chatUnreadTotal(listOf(th("a", -2), th("b", 1))))
    }
}
