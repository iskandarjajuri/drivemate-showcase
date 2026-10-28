package com.drivemate.showcase.domain.usecase.broadcast

import com.drivemate.showcase.domain.repository.AnnouncementRepository

class SendAnnouncementUseCase(
    private val repository: AnnouncementRepository
) {
    suspend operator fun invoke(
        title: String,
        body: String
    ) = repository.publishAnnouncement(title = title, body = body)
}
