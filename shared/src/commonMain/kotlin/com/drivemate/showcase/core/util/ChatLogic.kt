package com.drivemate.showcase.core.util

import com.drivemate.showcase.core.model.ChatContact
import com.drivemate.showcase.core.model.ChatMessage
import com.drivemate.showcase.core.model.ChatThreadSummary
import com.drivemate.showcase.core.model.Role

/** Batas `hiddenMessageIds` yang ditegakkan rules. */
const val CHAT_HIDDEN_IDS_MAX = 500

/** Peran direktori -> [Role]; nilai lama (expatriate/expatriat) dijaga; "unknown"/lain -> null. */
fun normalizeChatRole(raw: Any?): Role? = when ((raw as? String)?.trim()?.lowercase()) {
    "admin" -> Role.ADMIN
    "driver" -> Role.DRIVER
    "expat", "expatriate", "expatriat" -> Role.EXPAT
    else -> null
}

/**
 * Bahasa chat efektif seseorang; cermin `effectiveLang` di function terjemahan (server):
 * `lang` eksplisit ("ja"/"id") menang, selain itu peran (expat -> "ja", admin/driver -> "id").
 * Null bila tak ada `lang` sah dan peran tak diketahui (tidak menebak).
 */
fun effectiveChatLang(lang: String?, role: Role?): String? = when {
    lang == "ja" || lang == "id" -> lang
    role == Role.EXPAT -> "ja"
    role != null -> "id"
    else -> null
}

/**
 * Total pesan belum dibaca dari semua thread milik pengguna (untuk badge nav bar).
 * `unreadCount` sudah khusus untuk pengguna ini (server menaikkannya hanya untuk penerima, jadi
 * pesan sendiri tak ikut dihitung). Thread yang pesan terakhirnya dihapus-untuk-semua tidak dihitung
 * (server menolkan unread saat itu); nilai negatif diabaikan.
 */
fun chatUnreadTotal(threads: List<ChatThreadSummary>): Int =
    threads.filterNot { it.lastMessageDeleted }.sumOf { it.unreadCount.coerceAtLeast(0) }

/** Dokumen `user_directory/{uid}` -> kontak; null bila tidak sah, tidak aktif, atau diri sendiri. */
fun parseDirectoryContact(docId: String, data: Map<String, Any?>?, myUid: String): ChatContact? {
    if (data == null || docId == myUid) return null
    if (data["active"] == false) return null
    val role = normalizeChatRole(data["role"]) ?: return null
    val name = (data["displayName"] as? String)?.trim().orEmpty()
    val lang = (data["lang"] as? String)?.takeIf { it == "ja" || it == "id" }
    return ChatContact(uid = docId, displayName = name.ifBlank { docId }, role = role, email = null, lang = lang)
}

/** Urut nama (tanpa peka huruf besar), lalu uid agar stabil. */
fun sortContacts(contacts: List<ChatContact>): List<ChatContact> =
    contacts.sortedWith(compareBy({ it.displayName.lowercase() }, { it.uid }))

/** Dokumen thread -> ringkasan; null bila bukan peserta atau tidak lengkap. */
fun parseThreadSummary(docId: String, data: Map<String, Any?>?, myUid: String): ChatThreadSummary? {
    if (data == null) return null
    val participants = (data["participantUids"] as? List<*>)?.filterIsInstance<String>() ?: return null
    if (myUid !in participants) return null
    val partner = participants.firstOrNull { it != myUid } ?: return null
    val last = data["lastMessage"] as? Map<*, *>
    val at = (data["lastMessageAtMillis"] as? Number)?.toLong()
        ?: (last?.get("createdAtMillis") as? Number)?.toLong()
        ?: 0L
    val unread = ((data["unread"] as? Map<*, *>)?.get(myUid) as? Number)?.toInt() ?: 0
    return ChatThreadSummary(
        threadId = docId,
        partnerUid = partner,
        lastMessagePreview = (last?.get("preview") as? String).orEmpty(),
        lastMessageDeleted = last?.get("deleted") as? Boolean ?: false,
        lastMessageSenderUid = last?.get("senderUid") as? String,
        lastMessageAtMillis = at,
        unreadCount = unread.coerceAtLeast(0)
    )
}

/** Thread terbaru dulu. */
fun sortThreads(threads: List<ChatThreadSummary>): List<ChatThreadSummary> =
    threads.sortedByDescending { it.lastMessageAtMillis }

/** Pesan sendiri terbaca (centang ganda) bila lawan bicara membaca pada/setelah pesan dibuat. */
fun isMessageRead(createdAtMillis: Long, partnerReadAtMillis: Long?): Boolean =
    partnerReadAtMillis != null && partnerReadAtMillis >= createdAtMillis

/** Buang pesan yang disembunyikan pengguna. Tombstone (deleted) TETAP tampil. */
fun visibleMessages(messages: List<ChatMessage>, hiddenIds: Set<String>): List<ChatMessage> =
    messages.filter { it.id !in hiddenIds }

/**
 * Tambah id ke daftar tersembunyi (urut kronologis penambahan). Bila melebihi [max], id TERTUA
 * dibuang. Id yang sudah ada tidak digandakan.
 */
fun addHiddenId(existing: List<String>, id: String, max: Int = CHAT_HIDDEN_IDS_MAX): List<String> {
    if (id in existing) return existing
    val next = existing + id
    return if (next.size > max) next.takeLast(max) else next
}
