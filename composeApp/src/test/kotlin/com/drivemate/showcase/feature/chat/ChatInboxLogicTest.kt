package com.drivemate.showcase.feature.chat

import com.drivemate.showcase.core.model.ChatContact
import com.drivemate.showcase.core.model.ChatThreadSummary
import com.drivemate.showcase.core.model.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatInboxLogicTest {
    private val contacts = listOf(
        ChatContact("1", "Andi", Role.ADMIN, null),
        ChatContact("2", "Budi Santoso", Role.DRIVER, null),
        ChatContact("3", "Citra", Role.EXPAT, null),
        ChatContact("4", "budi kecil", Role.DRIVER, null)
    )

    private fun thread(partner: String) = ChatThreadSummary("t-$partner", partner, "x", false, null, 1L, 0)

    @Test
    fun cari_tanpaPekaHurufDanAbaikanSpasiTepi() {
        assertEquals(listOf("2", "4"), filterContacts(contacts, "  BUDI ").map { it.uid })
        assertEquals(contacts, filterContacts(contacts, "  "))
        assertTrue(filterContacts(contacts, "zzz").isEmpty())
    }

    @Test
    fun kelompok_adminExpatDriver_kosongDibuang() {
        assertEquals(listOf(Role.ADMIN, Role.EXPAT, Role.DRIVER), groupContacts(contacts).map { it.first })
        assertEquals(listOf(Role.DRIVER), groupContacts(contacts.filter { it.role == Role.DRIVER }).map { it.first })
        assertTrue(groupContacts(emptyList()).isEmpty())
    }

    @Test
    fun barisThread_namaDariDirektoriAtauFallback() {
        val rows = buildThreadRows(listOf(thread("2"), thread("hilang")), contacts, "Tidak dikenal")
        assertEquals("Budi Santoso", rows[0].name)
        assertEquals(Role.DRIVER, rows[0].role)
        assertEquals("Tidak dikenal", rows[1].name)
        assertNull(rows[1].role)
    }
}
