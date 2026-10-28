package com.drivemate.showcase.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.chatUnreadTotal
import com.drivemate.showcase.domain.repository.ChatThreadRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Total pesan belum dibaca untuk badge nav bar. Memakai ulang `observeThreads` (query yang sama
 * dengan inbox; SDK Firestore menggabungkan target identik, jadi tidak ada beban listener ganda).
 * Naik realtime saat pesan masuk, turun saat penanda baca server menolkan `unread`. Galat/offline
 * mempertahankan angka terakhir (bukan 0 palsu).
 */
class ChatUnreadViewModel(
    threadRepository: ChatThreadRepository
) : ViewModel() {
    private val _unread = MutableStateFlow(0)
    val unread: StateFlow<Int> = _unread.asStateFlow()

    init {
        viewModelScope.launch {
            threadRepository.observeThreads().collect { result ->
                if (result is AppResult.Success) _unread.value = chatUnreadTotal(result.data)
            }
        }
    }
}
