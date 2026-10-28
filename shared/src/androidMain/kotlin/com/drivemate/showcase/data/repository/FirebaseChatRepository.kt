package com.drivemate.showcase.data.repository

import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.model.ChatMessage
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.core.util.parseChatTranslation
import com.drivemate.showcase.core.util.buildOneToOneChatThreadId
import com.drivemate.showcase.data.firestore.FirestoreService
import com.drivemate.showcase.data.firestore.FsDoc
import com.drivemate.showcase.domain.repository.ChatRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class FirebaseChatRepository(
    private val displayNameFallback: String,
    private val fs: FirestoreService
) : ChatRepository {

    private companion object {
        const val TAG = "DM/Chat"
        const val CHAT_MESSAGE_LISTENER_LIMIT = 100
    }

    override fun observeMessages(partnerUid: String): Flow<AppResult<List<ChatMessage>>> = callbackFlow {
        val currentUid = runCatching { fs.currentUserUidOrThrow() }
            .getOrElse { throwable ->
                trySend(AppResult.failure(throwable)).isSuccess
                close(throwable)
                return@callbackFlow
            }
        val threadId = buildOneToOneChatThreadId(currentUid, partnerUid)
        val registration = fs.listenCollectionOrderBy(
            collection = messagesCollection(threadId),
            orderByField = "createdAtMillis",
            orderDesc = true,
            // TODO_VERIFY: older chat backfill still needs pagination before raising this listener cap.
            limit = CHAT_MESSAGE_LISTENER_LIMIT,
            onChange = { docs ->
                val messages = docs
                    .asReversed()
                    .mapNotNull(::mapDocToChatMessage)
                trySend(AppResult.success(messages)).isSuccess
            },
            onError = { throwable ->
                DMLog.e(TAG, "observeMessages failed: ${throwable.message}", throwable)
                trySend(AppResult.failure(throwable)).isSuccess
            }
        )
        awaitClose { registration.remove() }
    }

    override suspend fun sendMessage(partnerUid: String, body: String): AppResult<Unit> {
        val trimmedBody = body.trim()
        if (trimmedBody.isBlank()) {
            return AppResult.failure(IllegalArgumentException("chat body is blank"))
        }

        return try {
            val currentUid = fs.currentUserUidOrThrow()
            val threadId = buildOneToOneChatThreadId(currentUid, partnerUid)
            val userDoc = fs.getDocumentMap("users", currentUid)
            val email = runCatching { fs.currentUserEmailOrThrow() }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: (userDoc?.get("email") as? String)?.takeIf { it.isNotBlank() }
            val senderName = (userDoc?.get("name") as? String)?.takeIf { it.isNotBlank() }
                ?: email?.substringBefore("@")?.takeIf { it.isNotBlank() }
                ?: displayNameFallback

            fs.addDocument(
                collection = messagesCollection(threadId),
                data = mapOf(
                    "threadId" to threadId,
                    "participantUids" to listOf(currentUid, partnerUid).sorted(),
                    "senderUid" to currentUid,
                    "senderName" to senderName,
                    "body" to trimmedBody,
                    "createdAtMillis" to TimeUtil.nowEpochMillis()
                )
            )
            AppResult.success(Unit)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            DMLog.e(TAG, "sendMessage failed: ${t.message}", t)
            AppResult.failure(t)
        }
    }

    private fun messagesCollection(threadId: String): String = "chat_threads/$threadId/messages"

    private fun mapDocToChatMessage(doc: FsDoc): ChatMessage? {
        val data = doc.data ?: return null
        val threadId = (data["threadId"] as? String)?.takeIf { it.isNotBlank() } ?: return null
        val senderUid = (data["senderUid"] as? String)?.takeIf { it.isNotBlank() } ?: return null
        val body = (data["body"] as? String)?.trim().orEmpty()
        val createdAtMillis = (data["createdAtMillis"] as? Number)?.toLong() ?: return null
        val deleted = data["deleted"] as? Boolean ?: false
        // Tombstone (deleted) isinya sengaja kosong dan TETAP dipetakan.
        if (body.isBlank() && !deleted) return null
        val translation = parseChatTranslation(data)
        return ChatMessage(
            id = doc.id,
            threadId = threadId,
            senderUid = senderUid,
            senderName = (data["senderName"] as? String)?.takeIf { it.isNotBlank() },
            body = body,
            createdAtMillis = createdAtMillis,
            srcLang = translation.srcLang,
            register = translation.register,
            translations = translation.translations,
            translationStatus = translation.translationStatus,
            deleted = deleted
        )
    }
}
