package com.drivemate.showcase.domain.usecase.chat

import com.drivemate.showcase.domain.repository.ChatRepository

class ObserveChatRoomUseCase(
    private val chatRepository: ChatRepository
) {
    operator fun invoke(partnerUid: String) = chatRepository.observeMessages(partnerUid)
}
