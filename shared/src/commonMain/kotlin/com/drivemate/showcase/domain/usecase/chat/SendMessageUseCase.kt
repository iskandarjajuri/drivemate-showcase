package com.drivemate.showcase.domain.usecase.chat

import com.drivemate.showcase.domain.repository.ChatRepository

class SendMessageUseCase(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(partnerUid: String, body: String) =
        chatRepository.sendMessage(partnerUid, body)
}
