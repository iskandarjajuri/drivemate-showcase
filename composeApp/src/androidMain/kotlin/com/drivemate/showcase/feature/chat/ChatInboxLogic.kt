package com.drivemate.showcase.feature.chat

import com.drivemate.showcase.core.model.ChatContact
import com.drivemate.showcase.core.model.ChatThreadSummary
import com.drivemate.showcase.core.model.Role

/** Satu baris daftar thread: ringkasan + nama dari direktori. */
data class ChatThreadRow(val summary: ChatThreadSummary, val name: String, val role: Role?)

/** Gabungkan thread dengan direktori; nama tak dikenal jatuh ke [unknownName]. */
fun buildThreadRows(
    threads: List<ChatThreadSummary>,
    directory: List<ChatContact>,
    unknownName: String
): List<ChatThreadRow> {
    val byUid = directory.associateBy { it.uid }
    return threads.map { t ->
        val c = byUid[t.partnerUid]
        ChatThreadRow(t, c?.displayName ?: unknownName, c?.role)
    }
}

/** Saring kontak berdasarkan nama (tanpa peka huruf besar, abaikan spasi tepi). */
fun filterContacts(contacts: List<ChatContact>, query: String): List<ChatContact> {
    val q = query.trim().lowercase()
    return if (q.isEmpty()) contacts else contacts.filter { it.displayName.lowercase().contains(q) }
}

/** Urutan kelompok di layar kontak: Admin, Expat, Driver. */
val ChatContactGroupOrder: List<Role> = listOf(Role.ADMIN, Role.EXPAT, Role.DRIVER)

/** Kelompokkan per peran sesuai [ChatContactGroupOrder]; kelompok kosong dibuang. Urutan nama dijaga. */
fun groupContacts(contacts: List<ChatContact>): List<Pair<Role, List<ChatContact>>> =
    ChatContactGroupOrder.mapNotNull { role ->
        contacts.filter { it.role == role }.takeIf { it.isNotEmpty() }?.let { role to it }
    }
