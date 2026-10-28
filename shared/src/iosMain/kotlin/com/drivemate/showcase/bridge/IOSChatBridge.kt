package com.drivemate.showcase.bridge

import com.drivemate.showcase.core.model.Role
import com.drivemate.showcase.core.model.UserSession
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.domain.usecase.chat.ObserveChatRoomUseCase
import com.drivemate.showcase.domain.usecase.chat.SendMessageUseCase
import com.drivemate.showcase.domain.usecase.user.GetChatContactsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class IOSChatBridge(
    private val scope: CoroutineScope,
    private val observeChatRoom: ObserveChatRoomUseCase,
    private val sendMessage: SendMessageUseCase,
    private val getChatContacts: GetChatContactsUseCase,
    private val currentSession: () -> UserSession?
) {
    private var observerJob: Job? = null

    /**
     * The counterparty roles each role is allowed to chat with — the same sets Android
     * passes to `ChatContactsViewModel` (AppNavGraph.kt:275, :757, :846).
     *
     * These are not a UI preference. `firestore.rules:194-203`
     * (`validThreadParticipantPair`) requires exactly one participant to be an admin,
     * so a driver↔driver or expat↔driver thread is rejected by the backend. iOS
     * previously fed every role the *driver roster*, which meant a driver and an expat
     * were only ever offered threads Firestore would deny.
     */
    private fun counterpartyRoles(role: Role): Set<Role> = when (role) {
        Role.DRIVER -> setOf(Role.ADMIN)
        Role.EXPAT -> setOf(Role.ADMIN)
        Role.ADMIN -> setOf(Role.DRIVER, Role.EXPAT)
    }

    /**
     * Chat contacts for the signed-in user's role, resolved through the same
     * `GetChatContactsUseCase` Android uses.
     *
     * Reports failures instead of swallowing them — see [startObservingChatThread].
     */
    fun loadChatContacts(
        completion: (List<IOSChatContactSnapshot>?, String?) -> Unit
    ) {
        val session = currentSession() ?: run {
            completion(null, "Not authenticated")
            return
        }
        val role = session.role ?: run {
            completion(null, "Role not resolved")
            return
        }
        scope.launch {
            when (val result = getChatContacts(counterpartyRoles(role))) {
                is AppResult.Success -> completion(
                    result.data.map { c ->
                        IOSChatContactSnapshot(
                            uid = c.uid,
                            displayName = c.displayName,
                            roleRaw = c.role.name.lowercase(),
                            email = c.email
                        )
                    },
                    null
                )
                is AppResult.Failure -> completion(
                    null,
                    result.throwable.message ?: "Failed to load chat contacts."
                )
            }
        }
    }

    /**
     * [onChange] now carries an error string. It used to be
     * `is AppResult.Failure -> onChange(emptyList())` — a listener failure (including
     * the PERMISSION_DENIED that rules-invalid threads produce) rendered as an *empty
     * conversation*, indistinguishable from "no messages yet", with no retry. Failing
     * silently on a permissions error is how the contact-role bug stayed invisible.
     */
    fun startObservingChatThread(
        partnerUid: String,
        onChange: (List<IOSChatMessageSnapshot>?, String?) -> Unit
    ) {
        observerJob?.cancel()
        val session = currentSession() ?: run {
            onChange(null, "Not authenticated")
            return
        }
        val myUid = session.uid
        observerJob = scope.launch {
            observeChatRoom(partnerUid).collect { result ->
                when (result) {
                    is AppResult.Success -> onChange(
                        result.data.map { msg ->
                            IOSChatMessageSnapshot(
                                id = msg.id,
                                senderUid = msg.senderUid,
                                text = msg.body,
                                dateLabel = TimeUtil.formatYmdFromEpochDay(
                                    TimeUtil.epochDayOf(msg.createdAtMillis, IOSDriverTimezone).toInt(),
                                    IOSDriverTimezone
                                ),
                                timeLabel = TimeUtil.formatHm(msg.createdAtMillis, IOSDriverTimezone),
                                isMine = msg.senderUid == myUid
                            )
                        },
                        null
                    )
                    is AppResult.Failure -> onChange(
                        null,
                        result.throwable.message ?: "Failed to load conversation."
                    )
                }
            }
        }
    }

    fun stopObservingChatThread() {
        observerJob?.cancel()
        observerJob = null
    }

    fun sendMessage(
        partnerUid: String,
        text: String,
        completion: (Boolean, String?) -> Unit
    ) {
        scope.launch {
            when (val result = sendMessage(partnerUid, text)) {
                is AppResult.Success -> completion(true, null)
                is AppResult.Failure -> completion(false, result.throwable.message)
            }
        }
    }
}
