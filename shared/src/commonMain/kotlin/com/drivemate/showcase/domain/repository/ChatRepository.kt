package com.drivemate.showcase.domain.repository

import com.drivemate.showcase.core.model.ChatMessage
import com.drivemate.showcase.core.util.AppResult
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeMessages(partnerUid: String): Flow<AppResult<List<ChatMessage>>>
    suspend fun sendMessage(partnerUid: String, body: String): AppResult<Unit>
}
