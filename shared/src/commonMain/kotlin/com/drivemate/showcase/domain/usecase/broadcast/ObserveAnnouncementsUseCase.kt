package com.drivemate.showcase.domain.usecase.broadcast

import com.drivemate.showcase.domain.repository.AnnouncementRepository

class ObserveAnnouncementsUseCase(
    private val repository: AnnouncementRepository
) {
    operator fun invoke(limit: Int = 50) = repository.observeAnnouncements(limit)
}
