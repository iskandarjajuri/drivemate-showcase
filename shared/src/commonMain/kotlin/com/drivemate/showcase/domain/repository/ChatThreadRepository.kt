package com.drivemate.showcase.domain.repository

import com.drivemate.showcase.core.model.ChatThreadSummary
import com.drivemate.showcase.core.util.AppResult
import kotlinx.coroutines.flow.Flow

/** Thread, status baca, dan hapus pesan (Android saja; lihat [ChatDirectoryRepository]). */
interface ChatThreadRepository {
    /** Thread milik pengguna, terbaru dulu. */
    fun observeThreads(): Flow<AppResult<List<ChatThreadSummary>>>

    /** Tandai thread terbaca sampai [nowMillis] (merge; `hiddenMessageIds` tidak hilang). */
    suspend fun markRead(threadId: String, nowMillis: Long): AppResult<Unit>

    /** Waktu terakhir lawan bicara membaca thread; null bila belum pernah. */
    fun observePartnerReadAt(threadId: String, partnerUid: String): Flow<Long?>

    /** Id pesan yang disembunyikan pengguna ("hapus untuk saya"). */
    fun observeHiddenIds(threadId: String): Flow<Set<String>>

    /** "Hapus untuk semua": hanya pesan sendiri; server membersihkan isi. */
    suspend fun deleteForEveryone(threadId: String, messageId: String, nowMillis: Long): AppResult<Unit>

    /** "Hapus untuk saya": sembunyikan di tampilan sendiri. */
    suspend fun hideForMe(threadId: String, messageId: String): AppResult<Unit>
}
