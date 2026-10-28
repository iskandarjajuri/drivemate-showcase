package com.drivemate.showcase.domain.usecase.broadcast

import com.drivemate.showcase.domain.repository.AnnouncementRepository

class DeleteAnnouncementUseCase(
    private val repository: AnnouncementRepository
) {
    suspend operator fun invoke(id: String) = repository.deleteAnnouncement(id)
}
