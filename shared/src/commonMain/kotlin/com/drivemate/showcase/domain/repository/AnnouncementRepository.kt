package com.drivemate.showcase.domain.repository

import com.drivemate.showcase.core.model.Announcement
import com.drivemate.showcase.core.util.AppResult
import kotlinx.coroutines.flow.Flow

interface AnnouncementRepository {
    fun observeAnnouncements(limit: Int = 50): Flow<AppResult<List<Announcement>>>

    suspend fun publishAnnouncement(
        title: String,
        body: String
    ): AppResult<Unit>

    suspend fun updateAnnouncement(
        id: String,
        title: String,
        body: String
    ): AppResult<Unit>

    suspend fun deleteAnnouncement(id: String): AppResult<Unit>
}
