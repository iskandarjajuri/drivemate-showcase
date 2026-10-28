package com.drivemate.showcase.feature.chat

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivemate.showcase.R
import com.drivemate.showcase.core.model.ChatContact
import com.drivemate.showcase.core.model.ChatMessage
import com.drivemate.showcase.core.util.buildOneToOneChatThreadId
import com.drivemate.showcase.core.util.effectiveChatLang
import com.drivemate.showcase.core.util.visibleMessages
import com.drivemate.showcase.core.model.ChatThreadSummary
import com.drivemate.showcase.domain.repository.ChatDirectoryRepository
import com.drivemate.showcase.domain.repository.ChatThreadRepository
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.AuthRepository
import com.drivemate.showcase.domain.usecase.chat.ObserveChatRoomUseCase
import com.drivemate.showcase.domain.usecase.chat.SendMessageUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class ChatInboxUiState(
    val isLoading: Boolean = true,
    val myUid: String? = null,
    val threads: List<ChatThreadRow> = emptyList(),
    val contacts: List<ChatContact> = emptyList(),
    val contactsLoading: Boolean = true,
    @param:StringRes val contactsErrorRes: Int? = null,
    val query: String = "",
    val showContacts: Boolean = false,
    @param:StringRes val errorRes: Int? = null
)

/**
 * Daftar thread + kontak dari direktori; sama untuk SEMUA peran. Tiap sumber punya status
 * sendiri ([Source]) dan batas waktu, sehingga satu sumber yang bisu/gagal tidak menahan yang lain.
 */
class ChatInboxViewModel(
    private val threadRepository: ChatThreadRepository,
    private val directoryRepository: ChatDirectoryRepository,
    private val authRepository: AuthRepository,
    private val unknownName: String,
    private val loadTimeoutMillis: Long = CHAT_INBOX_LOAD_TIMEOUT_MILLIS
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatInboxUiState())
    val uiState: StateFlow<ChatInboxUiState> = _uiState.asStateFlow()

    private var job: Job? = null

    init {
        viewModelScope.launch {
            val uid = (authRepository.currentUser() as? AppResult.Success)?.data?.uid
            _uiState.value = _uiState.value.copy(myUid = uid)
        }
        refresh()
    }

    fun refresh() {
        job?.cancel()
        _uiState.value = _uiState.value.copy(
            isLoading = true, errorRes = null, contactsLoading = true, contactsErrorRes = null
        )
        job = viewModelScope.launch {
            combine(
                threadRepository.observeThreads().toSource(loadTimeoutMillis),
                directoryRepository.observeDirectory().toSource(loadTimeoutMillis)
            ) { threads, directory -> threads to directory }
                .collect { (threads, directory) -> apply(threads, directory) }
        }
    }

    private fun apply(
        threads: Source<List<ChatThreadSummary>>,
        directory: Source<List<ChatContact>>
    ) {
        val contacts = (directory as? Source.Success)?.value.orEmpty()
        _uiState.value = _uiState.value.copy(
            isLoading = threads is Source.Loading,
            errorRes = if (threads is Source.Failure) R.string.chat_inbox_error_load_failed else null,
            threads = (threads as? Source.Success)?.let { buildThreadRows(it.value, contacts, unknownName) }
                ?: emptyList(),
            contacts = contacts,
            contactsLoading = directory is Source.Loading,
            contactsErrorRes = if (directory is Source.Failure) R.string.chat_contacts_error_load_failed else null
        )
    }

    fun onQueryChanged(value: String) {
        _uiState.value = _uiState.value.copy(query = value)
    }

    fun setShowContacts(show: Boolean) {
        _uiState.value = _uiState.value.copy(showContacts = show, query = if (show) _uiState.value.query else "")
    }
}

data class ChatThreadUiState(
    val partnerUid: String,
    val partnerName: String,
    val currentUserUid: String? = null,
    /** Bahasa baca efektif pengguna ("ja"/"id"; dari peran, cermin server); BUKAN locale ponsel. */
    val viewerLang: String? = null,
    /** Bahasa efektif lawan bicara dari direktori; null bila direktori belum/gagal dimuat. */
    val partnerLang: String? = null,
    val isLoading: Boolean = true,
    val messages: List<ChatMessage> = emptyList(),
    val partnerReadAtMillis: Long? = null,
    val draftMessage: String = "",
    val isSending: Boolean = false,
    @param:StringRes val errorRes: Int? = null,
    @param:StringRes val feedbackRes: Int? = null
)

class ChatThreadViewModel(
    private val partnerUid: String,
    private val partnerName: String,
    private val observeChatRoom: ObserveChatRoomUseCase,
    private val sendMessage: SendMessageUseCase,
    private val authRepository: AuthRepository,
    private val threadRepository: ChatThreadRepository,
    private val directoryRepository: ChatDirectoryRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ChatThreadUiState(
            partnerUid = partnerUid,
            partnerName = partnerName
        )
    )
    val uiState: StateFlow<ChatThreadUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null
    private var threadId: String? = null
    private var rawMessages: List<ChatMessage> = emptyList()
    private var hiddenIds: Set<String> = emptySet()
    private var lastMarkedAt = 0L

    init {
        resolveCurrentUser()
        observePartnerLang()
        refresh()
    }

    /** Kegagalan direktori hanya mengosongkan petunjuk bahasa; thread tetap berjalan. */
    private fun observePartnerLang() {
        viewModelScope.launch {
            directoryRepository.observeDirectory().collect { result ->
                if (result is AppResult.Success) {
                    val partner = result.data.firstOrNull { it.uid == partnerUid }
                    _uiState.value = _uiState.value.copy(
                        partnerLang = partner?.let { effectiveChatLang(it.lang, it.role) }
                    )
                }
            }
        }
    }

    fun refresh() {
        observeJob?.cancel()
        _uiState.value = _uiState.value.copy(isLoading = true, errorRes = null)
        observeJob = viewModelScope.launch {
            observeChatRoom(partnerUid).collect { result ->
                _uiState.value = when (result) {
                    is AppResult.Success -> {
                        rawMessages = result.data
                        _uiState.value.copy(
                            isLoading = false,
                            messages = visibleMessages(rawMessages, hiddenIds),
                            errorRes = null
                        )
                    }
                    is AppResult.Failure -> _uiState.value.copy(
                        isLoading = false,
                        errorRes = R.string.chat_thread_error_load_failed
                    )
                }
            }
        }
    }

    /** Dipanggil layar saat thread terlihat atau ada pesan masuk baru: tandai terbaca. */
    fun markReadIfNeeded(latestIncomingAtMillis: Long) {
        val id = threadId ?: return
        if (latestIncomingAtMillis <= lastMarkedAt) return
        lastMarkedAt = latestIncomingAtMillis
        viewModelScope.launch { threadRepository.markRead(id, nowMillis()) }
    }

    fun onDraftChanged(value: String) {
        _uiState.value = _uiState.value.copy(
            draftMessage = value,
            feedbackRes = null
        )
    }

    fun dismissFeedback() {
        _uiState.value = _uiState.value.copy(feedbackRes = null)
    }

    /** "Hapus untuk semua": hanya pesan sendiri (dijaga rules juga). */
    fun deleteForEveryone(message: ChatMessage) {
        val id = threadId ?: return
        if (message.senderUid != _uiState.value.currentUserUid) return
        viewModelScope.launch {
            if (threadRepository.deleteForEveryone(id, message.id, nowMillis()) is AppResult.Failure) {
                _uiState.value = _uiState.value.copy(feedbackRes = R.string.chat_error_action_failed)
            }
        }
    }

    /** "Hapus untuk saya": sembunyikan dari tampilan sendiri. */
    fun hideForMe(message: ChatMessage) {
        val id = threadId ?: return
        viewModelScope.launch {
            if (threadRepository.hideForMe(id, message.id) is AppResult.Failure) {
                _uiState.value = _uiState.value.copy(feedbackRes = R.string.chat_error_action_failed)
            }
        }
    }

    fun sendCurrentMessage() {
        val body = _uiState.value.draftMessage.trim()
        if (body.isBlank()) {
            _uiState.value = _uiState.value.copy(
                feedbackRes = R.string.chat_validation_body_required
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            isSending = true,
            feedbackRes = null
        )
        viewModelScope.launch {
            when (sendMessage(partnerUid, body)) {
                is AppResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        draftMessage = "",
                        isSending = false,
                        feedbackRes = null
                    )
                }
                is AppResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isSending = false,
                        feedbackRes = R.string.chat_error_send_failed
                    )
                }
            }
        }
    }

    private fun resolveCurrentUser() {
        viewModelScope.launch {
            val user = (authRepository.currentUser() as? AppResult.Success)?.data
            val currentUid = user?.uid
            _uiState.value = _uiState.value.copy(
                currentUserUid = currentUid,
                viewerLang = effectiveChatLang(null, user?.role)
            )
            if (currentUid != null) startThreadObservers(currentUid)
        }
    }

    private fun startThreadObservers(myUid: String) {
        val id = runCatching { buildOneToOneChatThreadId(myUid, partnerUid) }.getOrNull() ?: return
        threadId = id
        viewModelScope.launch {
            threadRepository.observePartnerReadAt(id, partnerUid).collect { readAt ->
                _uiState.value = _uiState.value.copy(partnerReadAtMillis = readAt)
            }
        }
        viewModelScope.launch {
            threadRepository.observeHiddenIds(id).collect { ids ->
                hiddenIds = ids
                _uiState.value = _uiState.value.copy(messages = visibleMessages(rawMessages, hiddenIds))
            }
        }
    }
}
