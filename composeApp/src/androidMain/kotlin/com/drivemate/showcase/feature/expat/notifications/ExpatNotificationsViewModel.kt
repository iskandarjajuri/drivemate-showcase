package com.drivemate.showcase.feature.expat.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivemate.showcase.core.model.Announcement
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.AuthRepository
import com.drivemate.showcase.domain.usecase.broadcast.ObserveAnnouncementsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExpatNotificationInputs(
    val pendingReviews: List<ExpatPendingReview> = emptyList(),
    val chatUnread: Int = 0,
    /** Sumber tinjauan gagal dimuat (Beranda galat); layar tetap tampil dengan sumber lain. */
    val reviewFailed: Boolean = false
)

data class ExpatNotificationsUiState(
    val items: List<ExpatNotification> = emptyList(),
    val unreadCount: Int = 0,
    /** Ada sumber yang gagal: daftar mungkin tidak lengkap. */
    val partialError: Boolean = false
)

/**
 * Menggabungkan sumber yang sudah ada (ringkasan tinjauan dari Beranda, pengumuman, jumlah chat belum
 * dibaca); penanda baca lokal di [ExpatNotificationReadStore]. Satu sumber gagal tidak mematikan layar.
 */
class ExpatNotificationsViewModel(
    observeAnnouncements: ObserveAnnouncementsUseCase,
    private val authRepo: AuthRepository,
    private val store: ExpatNotificationReadStore,
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val inputs = MutableStateFlow(ExpatNotificationInputs())
    private val announcements = MutableStateFlow<List<Announcement>>(emptyList())
    private val announcementsFailed = MutableStateFlow(false)
    private val uid = MutableStateFlow<String?>(null)
    private val seen = MutableStateFlow(0L)

    init {
        viewModelScope.launch {
            val current = authRepo.currentUser()
            if (current is AppResult.Success) {
                val id = current.data?.uid
                uid.value = id
                if (id != null) seen.value = store.lastSeenAnnouncementAt(id)
            }
        }
        viewModelScope.launch {
            observeAnnouncements().collect { result ->
                when (result) {
                    is AppResult.Success -> {
                        announcements.value = result.data
                        announcementsFailed.value = false
                    }
                    is AppResult.Failure -> announcementsFailed.value = true
                }
            }
        }
    }

    val state: StateFlow<ExpatNotificationsUiState> =
        combine(inputs, announcements, seen, announcementsFailed) { inp, anns, seenAnn, annFailed ->
            val items = buildExpatNotifications(
                pendingReviews = inp.pendingReviews,
                announcements = anns,
                lastSeenAnnouncementAt = seenAnn,
                nowMillis = nowMillis(),
                chatUnread = inp.chatUnread
            )
            ExpatNotificationsUiState(
                items = items,
                unreadCount = items.unreadCount(),
                partialError = annFailed || inp.reviewFailed
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ExpatNotificationsUiState())

    fun setInputs(newInputs: ExpatNotificationInputs) {
        inputs.value = newInputs
    }

    /** Menandai pengumuman dibaca. Tinjauan menunggu & pesan tetap (aksi/angka dari server). */
    fun markAllRead() {
        val id = uid.value ?: return
        val now = nowMillis()
        store.markAllSeen(id, now)
        seen.update { now }
    }
}
