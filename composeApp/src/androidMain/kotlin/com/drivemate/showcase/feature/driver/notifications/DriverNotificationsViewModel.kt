package com.drivemate.showcase.feature.driver.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivemate.showcase.core.model.Announcement
import com.drivemate.showcase.core.model.WorkDay
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.usecase.broadcast.ObserveAnnouncementsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DriverNotificationInputs(
    val uid: String? = null,
    val pendingTasks: List<DriverPendingTask> = emptyList(),
    val workDays: List<WorkDay> = emptyList(),
    val chatUnread: Int = 0
)

data class DriverNotificationsUiState(
    val items: List<DriverNotification> = emptyList(),
    val unreadCount: Int = 0
)

/**
 * SATU sumber untuk lencana lonceng Home dan layar Notifikasi: instans yang sama
 * (di-scope ke entri DriverHome). Menggabungkan sumber yang sudah ada; penanda baca
 * lokal di [DriverNotificationReadStore]. Pemanggil mengisi [setInputs] dari state
 * DriverViewModel (tugas tertunda + hari kerja terbaru).
 */
class DriverNotificationsViewModel(
    observeAnnouncements: ObserveAnnouncementsUseCase,
    private val store: DriverNotificationReadStore,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val approvalLabel: (WorkDay) -> String = { it.dateEpochDay.toString() }
) : ViewModel() {

    private val inputs = MutableStateFlow(DriverNotificationInputs())
    private val announcements = MutableStateFlow<List<Announcement>>(emptyList())
    private val seen = MutableStateFlow(0L to 0L)

    init {
        viewModelScope.launch {
            observeAnnouncements().collect { result ->
                if (result is AppResult.Success) announcements.value = result.data
            }
        }
    }

    val state: StateFlow<DriverNotificationsUiState> =
        combine(inputs, announcements, seen) { inp, anns, (seenAnn, seenAppr) ->
            val items = buildDriverNotifications(
                pendingTasks = inp.pendingTasks,
                announcements = anns,
                workDays = inp.workDays,
                lastSeenAnnouncementAt = seenAnn,
                lastSeenApprovalAt = seenAppr,
                nowMillis = nowMillis(),
                chatUnread = inp.chatUnread,
                approvalLabel = approvalLabel
            )
            DriverNotificationsUiState(items = items, unreadCount = items.unreadCount())
        }.stateIn(viewModelScope, SharingStarted.Eagerly, DriverNotificationsUiState())

    fun setInputs(newInputs: DriverNotificationInputs) {
        val uidChanged = newInputs.uid != inputs.value.uid
        inputs.value = newInputs
        if (uidChanged && newInputs.uid != null) {
            seen.value = store.lastSeenAnnouncementAt(newInputs.uid) to store.lastSeenApprovalAt(newInputs.uid)
        }
    }

    /** Menandai pengumuman & persetujuan dibaca. Tugas tertunda tetap (itu aksi, bukan info). */
    fun markAllRead() {
        val uid = inputs.value.uid ?: return
        val now = nowMillis()
        store.markAllSeen(uid, now)
        seen.update { now to now }
    }
}
