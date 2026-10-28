package com.drivemate.showcase.data.repository

import com.drivemate.showcase.bridge.IOSPlatformBridgeRegistry
import com.drivemate.showcase.core.model.ChatMessage
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.buildOneToOneChatThreadId
import com.drivemate.showcase.domain.repository.ChatRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class IOSChatRepository : ChatRepository {

    override fun observeMessages(partnerUid: String): Flow<AppResult<List<ChatMessage>>> = callbackFlow {
        val authBridge = IOSPlatformBridgeRegistry.authBridge
            ?: run {
                trySend(AppResult.failure(IllegalStateException("Auth bridge not installed"))).isSuccess
                close()
                return@callbackFlow
            }
        val currentUid = authBridge.currentUser()?.uid
            ?: run {
                trySend(AppResult.failure(IllegalStateException("Not authenticated"))).isSuccess
                close()
                return@callbackFlow
            }
        val chatBridge = IOSPlatformBridgeRegistry.chatBridge
            ?: run {
                trySend(AppResult.failure(IllegalStateException("Chat bridge not installed"))).isSuccess
                close()
                return@callbackFlow
            }
        val threadId = buildOneToOneChatThreadId(currentUid, partnerUid)
        val registration = chatBridge.observeMessages(threadId) { payloads, error ->
            if (error != null) {
                trySend(AppResult.failure(RuntimeException(error))).isSuccess
            } else {
                val messages = payloads?.mapNotNull { p ->
                    val body = p.body.trim()
                    if (body.isBlank()) null
                    else ChatMessage(
                        id = p.id,
                        threadId = p.threadId,
                        senderUid = p.senderUid,
                        senderName = p.senderName?.takeIf { it.isNotBlank() },
                        body = body,
                        createdAtMillis = p.createdAtMillis
                    )
                } ?: emptyList()
                trySend(AppResult.success(messages)).isSuccess
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun sendMessage(partnerUid: String, body: String): AppResult<Unit> {
        val trimmedBody = body.trim()
        if (trimmedBody.isBlank()) {
            return AppResult.failure(IllegalArgumentException("chat body is blank"))
        }
        val authBridge = IOSPlatformBridgeRegistry.authBridge
            ?: return AppResult.failure(IllegalStateException("Auth bridge not installed"))
        val currentUser = authBridge.currentUser()
            ?: return AppResult.failure(IllegalStateException("Not authenticated"))
        val currentUid = currentUser.uid
        val senderName = currentUser.email?.substringBefore("@")?.trim()?.takeIf { it.isNotBlank() }
        val chatBridge = IOSPlatformBridgeRegistry.chatBridge
            ?: return AppResult.failure(IllegalStateException("Chat bridge not installed"))
        val threadId = buildOneToOneChatThreadId(currentUid, partnerUid)

        return suspendCancellableCoroutine { cont ->
            chatBridge.sendMessage(threadId, currentUid, senderName, trimmedBody) { error ->
                if (!cont.isCompleted) cont.resume(
                    if (error != null) AppResult.failure(RuntimeException(error))
                    else AppResult.success(Unit)
                )
            }
        }
    }
}
