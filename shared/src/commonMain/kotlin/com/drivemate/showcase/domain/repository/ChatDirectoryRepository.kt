package com.drivemate.showcase.domain.repository

import com.drivemate.showcase.core.model.ChatContact
import com.drivemate.showcase.core.util.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * Sumber kontak chat dari `user_directory` (bebas antar peran). Terpisah dari [ChatRepository]
 * dan [UserDirectoryRepository] karena keduanya punya implementasi iOS; yang ini Android saja.
 */
interface ChatDirectoryRepository {
    /** Kontak aktif selain diri sendiri, peran admin|driver|expat, urut nama. */
    fun observeDirectory(): Flow<AppResult<List<ChatContact>>>
}
