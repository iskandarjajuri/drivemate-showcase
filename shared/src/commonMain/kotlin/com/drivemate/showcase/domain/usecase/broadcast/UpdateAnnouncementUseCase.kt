package com.drivemate.showcase.domain.usecase.broadcast

import com.drivemate.showcase.domain.repository.AnnouncementRepository

class UpdateAnnouncementUseCase(
    private val repository: AnnouncementRepository
) {
    suspend operator fun invoke(
        id: String,
        title: String,
        body: String
    ) = repository.updateAnnouncement(id, title, body)
}
