package com.drivemate.showcase.feature.expat.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.drivemate.showcase.feature.expat.ExpatHomeViewModel

/**
 * Mengisi [ExpatNotificationsViewModel] dari sumber yang sudah dimuat Beranda (tanpa panggilan jaringan baru).
 * Dipasang di sesuatu yang SELALU tampil di rute tab Expat (bar navigasi), bukan hanya di layar Notifikasi,
 * supaya badge lonceng dan tab Notifikasi benar sebelum Expat membuka tab itu.
 */
@Composable
fun ExpatNotificationInputsEffect(
    homeVm: ExpatHomeViewModel,
    notificationsVm: ExpatNotificationsViewModel,
    chatUnread: Int
) {
    val drivers by homeVm.drivers.collectAsState()
    val homeError by homeVm.error.collectAsState()
    LaunchedEffect(drivers, chatUnread, homeError) {
        notificationsVm.setInputs(
            ExpatNotificationInputs(
                pendingReviews = drivers.map {
                    ExpatPendingReview(
                        driverUid = it.uid,
                        driverName = it.displayName,
                        pendingCount = it.pendingCount
                    )
                },
                chatUnread = chatUnread,
                reviewFailed = homeError != null
            )
        )
    }
}
