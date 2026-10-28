package com.drivemate.showcase.data.repository

import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.model.Announcement
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.data.firestore.FirestoreService
import com.drivemate.showcase.data.firestore.FsDoc
import com.drivemate.showcase.domain.repository.AnnouncementRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class FirebaseAnnouncementRepository(
    private val fs: FirestoreService
) : AnnouncementRepository {

    private companion object {
        const val TAG = "DM/Announcements"
        const val COLLECTION = "announcements"
    }

    override fun observeAnnouncements(limit: Int): Flow<AppResult<List<Announcement>>> = callbackFlow {
        DMLog.i(TAG, "observeAnnouncements attach limit=$limit")
        val registration = fs.listenCollectionOrderBy(
            collection = COLLECTION,
            orderByField = "createdAtMillis",
            orderDesc = true,
            limit = limit,
            onChange = { docs ->
                val announcements = docs.mapNotNull(::mapDocToAnnouncement)
                trySend(AppResult.success(announcements)).isSuccess
            },
            onError = { throwable ->
                DMLog.e(TAG, "observeAnnouncements failed: ${throwable.message}", throwable)
                trySend(AppResult.failure(throwable)).isSuccess
            }
        )
        awaitClose {
            DMLog.i(TAG, "observeAnnouncements detach")
            registration.remove()
        }
    }

    override suspend fun publishAnnouncement(
        title: String,
        body: String
    ): AppResult<Unit> {
        val trimmedTitle = title.trim()
        val trimmedBody = body.trim()
        if (trimmedTitle.isBlank()) {
            return AppResult.failure(IllegalArgumentException("announcement title is blank"))
        }
        if (trimmedBody.isBlank()) {
            return AppResult.failure(IllegalArgumentException("announcement body is blank"))
        }

        return try {
            val uid = fs.currentUserUidOrThrow()
            val userDoc = fs.getDocumentMap("users", uid)
            val authEmail = runCatching { fs.currentUserEmailOrThrow() }.getOrNull()
            val createdByEmail = authEmail?.takeIf { it.isNotBlank() }
                ?: (userDoc?.get("email") as? String)?.takeIf { it.isNotBlank() }
            val createdByName = (userDoc?.get("name") as? String)?.takeIf { it.isNotBlank() }
                ?: createdByEmail?.substringBefore("@")?.takeIf { it.isNotBlank() }
            val payload = mutableMapOf<String, Any?>(
                "title" to trimmedTitle,
                "body" to trimmedBody,
                "createdAtMillis" to TimeUtil.nowEpochMillis(),
                "createdByUid" to uid
            )
            createdByName?.let { payload["createdByName"] = it }
            createdByEmail?.let { payload["createdByEmail"] = it }
            fs.addDocument(COLLECTION, payload)
            DMLog.i(TAG, "publishAnnouncement success uid=$uid")
            AppResult.success(Unit)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            DMLog.e(TAG, "publishAnnouncement failed: ${t.message}", t)
            AppResult.failure(t)
        }
    }

    override suspend fun updateAnnouncement(
        id: String,
        title: String,
        body: String
    ): AppResult<Unit> {
        val trimmedTitle = title.trim()
        val trimmedBody = body.trim()
        if (trimmedTitle.isBlank()) {
            return AppResult.failure(IllegalArgumentException("announcement title is blank"))
        }
        if (trimmedBody.isBlank()) {
            return AppResult.failure(IllegalArgumentException("announcement body is blank"))
        }
        return try {
            fs.setDocument(
                "$COLLECTION/$id",
                mapOf("title" to trimmedTitle, "body" to trimmedBody),
                merge = true
            )
            DMLog.i(TAG, "updateAnnouncement success id=$id")
            AppResult.success(Unit)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            DMLog.e(TAG, "updateAnnouncement failed: ${t.message}", t)
            AppResult.failure(t)
        }
    }

    override suspend fun deleteAnnouncement(id: String): AppResult<Unit> {
        return try {
            fs.deleteDocument("$COLLECTION/$id")
            DMLog.i(TAG, "deleteAnnouncement success id=$id")
            AppResult.success(Unit)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            DMLog.e(TAG, "deleteAnnouncement failed: ${t.message}", t)
            AppResult.failure(t)
        }
    }

    private fun mapDocToAnnouncement(doc: FsDoc): Announcement? {
        val data = doc.data ?: return null
        val title = (data["title"] as? String)?.trim().orEmpty()
        val body = (data["body"] as? String)?.trim().orEmpty()
        val createdByUid = (data["createdByUid"] as? String)?.trim().orEmpty()
        val createdAtMillis = (data["createdAtMillis"] as? Number)?.toLong() ?: return null
        if (title.isBlank() || body.isBlank() || createdByUid.isBlank()) return null
        return Announcement(
            id = doc.id,
            title = title,
            body = body,
            createdAtMillis = createdAtMillis,
            createdByUid = createdByUid,
            createdByName = (data["createdByName"] as? String)?.takeIf { it.isNotBlank() },
            createdByEmail = (data["createdByEmail"] as? String)?.takeIf { it.isNotBlank() }
        )
    }
}
