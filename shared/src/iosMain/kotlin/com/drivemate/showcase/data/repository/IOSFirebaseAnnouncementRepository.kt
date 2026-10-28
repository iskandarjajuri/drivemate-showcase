package com.drivemate.showcase.data.repository

import com.drivemate.showcase.bridge.IOSAnnouncementPayload
import com.drivemate.showcase.bridge.IOSAnnouncementPlatformBridge
import com.drivemate.showcase.bridge.IOSPlatformBridgeRegistry
import com.drivemate.showcase.core.model.Announcement
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.AnnouncementRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class IOSFirebaseAnnouncementRepository : AnnouncementRepository {
    private val announcementBridge: IOSAnnouncementPlatformBridge
        get() = IOSPlatformBridgeRegistry.announcementBridge
            ?: error("IOSAnnouncementPlatformBridge is not installed")

    override fun observeAnnouncements(limit: Int): Flow<AppResult<List<Announcement>>> = callbackFlow {
        val registration = announcementBridge.observeAnnouncements(limit) { payloads, errorMessage ->
            if (errorMessage != null) {
                trySend(AppResult.failure(IllegalStateException(errorMessage)))
                return@observeAnnouncements
            }
            trySend(
                AppResult.success(
                    (payloads ?: emptyList()).mapNotNull { it.toAnnouncement() }
                )
            )
        }
        awaitClose { registration.remove() }
    }

    override suspend fun publishAnnouncement(title: String, body: String): AppResult<Unit> =
        suspendCancellableCoroutine { cont ->
            announcementBridge.createAnnouncement(title, body) { errorMessage ->
                if (!cont.isCompleted) {
                    cont.resume(
                        if (errorMessage == null) AppResult.success(Unit)
                        else AppResult.failure(IllegalStateException(errorMessage))
                    )
                }
            }
        }

    override suspend fun updateAnnouncement(
        id: String,
        title: String,
        body: String
    ): AppResult<Unit> = suspendCancellableCoroutine { cont ->
        announcementBridge.updateAnnouncement(id, title, body) { errorMessage ->
            if (!cont.isCompleted) {
                cont.resume(
                    if (errorMessage == null) AppResult.success(Unit)
                    else AppResult.failure(IllegalStateException(errorMessage))
                )
            }
        }
    }

    override suspend fun deleteAnnouncement(id: String): AppResult<Unit> =
        suspendCancellableCoroutine { cont ->
            announcementBridge.deleteAnnouncement(id) { errorMessage ->
                if (!cont.isCompleted) {
                    cont.resume(
                        if (errorMessage == null) AppResult.success(Unit)
                        else AppResult.failure(IllegalStateException(errorMessage))
                    )
                }
            }
        }

    private fun IOSAnnouncementPayload.toAnnouncement(): Announcement? {
        val safeTitle = title.trim()
        val safeBody = body.trim()
        if (safeTitle.isBlank() || safeBody.isBlank()) return null
        return Announcement(
            id = id,
            title = safeTitle,
            body = safeBody,
            createdAtMillis = createdAtMillis,
            createdByUid = "",
            createdByName = createdByName?.takeIf { it.isNotBlank() },
            createdByEmail = createdByEmail?.takeIf { it.isNotBlank() }
        )
    }
}
