package com.drivemate.showcase.bridge

import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.usecase.broadcast.DeleteAnnouncementUseCase
import com.drivemate.showcase.domain.usecase.broadcast.ObserveAnnouncementsUseCase
import com.drivemate.showcase.domain.usecase.broadcast.SendAnnouncementUseCase
import com.drivemate.showcase.domain.usecase.broadcast.UpdateAnnouncementUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal class IOSAnnouncementBridge(
    private val scope: CoroutineScope,
    private val observeAnnouncements: ObserveAnnouncementsUseCase,
    private val sendAnnouncement: SendAnnouncementUseCase,
    private val updateAnnouncementUseCase: UpdateAnnouncementUseCase,
    private val deleteAnnouncementUseCase: DeleteAnnouncementUseCase
) {
    private var announcementsJob: Job? = null

    fun startObserving(
        announcementsChanged: (List<IOSAnnouncementSnapshot>, String?) -> Unit
    ) {
        announcementsJob?.cancel()
        announcementsJob = scope.launch {
            observeAnnouncements().collect { result ->
                when (result) {
                    is AppResult.Success -> announcementsChanged(result.data.map { it.toIosSnapshot() }, null)
                    is AppResult.Failure -> announcementsChanged(
                        emptyList(),
                        result.throwable.message ?: "Failed to load announcements."
                    )
                }
            }
        }
    }

    fun stopObserving() {
        announcementsJob?.cancel()
        announcementsJob = null
    }

    fun createAnnouncement(
        title: String,
        body: String,
        audienceRaw: String,
        completion: (Boolean, String?) -> Unit
    ) {
        scope.launch {
            when (val result = sendAnnouncement.invoke(title, body)) {
                is AppResult.Success -> completion(true, null)
                is AppResult.Failure -> completion(false, result.throwable.message)
            }
        }
    }

    fun updateAnnouncement(
        id: String,
        title: String,
        body: String,
        completion: (Boolean, String?) -> Unit
    ) {
        scope.launch {
            when (val result = updateAnnouncementUseCase.invoke(id, title, body)) {
                is AppResult.Success -> completion(true, null)
                is AppResult.Failure -> completion(false, result.throwable.message)
            }
        }
    }

    fun deleteAnnouncement(
        id: String,
        completion: (Boolean, String?) -> Unit
    ) {
        scope.launch {
            when (val result = deleteAnnouncementUseCase.invoke(id)) {
                is AppResult.Success -> completion(true, null)
                is AppResult.Failure -> completion(false, result.throwable.message)
            }
        }
    }
}
