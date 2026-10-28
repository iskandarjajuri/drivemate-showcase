package com.drivemate.showcase.feature.expat.notifications

import com.drivemate.showcase.core.model.Announcement
import com.drivemate.showcase.feature.driver.notifications.NOTIFICATION_UNREAD_WINDOW_MILLIS

enum class ExpatNotificationKind { REVIEW, ANNOUNCEMENT, MESSAGE }

/** Driver yang punya hari kerja menunggu tinjauan (turunan dari ringkasan yang sudah dimuat Beranda). */
data class ExpatPendingReview(
    val driverUid: String,
    val driverName: String,
    val pendingCount: Int
)

/**
 * Satu baris notifikasi. [text] = judul pengumuman (ANNOUNCEMENT) atau nama driver (REVIEW);
 * [count] = jumlah hari kerja menunggu (REVIEW) atau pesan belum dibaca (MESSAGE).
 */
data class ExpatNotification(
    val id: String,
    val kind: ExpatNotificationKind,
    val text: String,
    val atMillis: Long,
    val unread: Boolean,
    val count: Int = 0,
    /** Hanya untuk REVIEW: tujuan ke tinjauan bulanan driver terkait. */
    val driverUid: String? = null
)

private const val MAX_ANNOUNCEMENTS = 20

/**
 * Menggabungkan sumber yang SUDAH ADA menjadi satu daftar (terbaru dulu), meniru
 * `buildDriverNotifications`: tinjauan menunggu, pengumuman, dan pesan chat belum dibaca.
 *
 * Tinjauan menunggu dan pesan belum dibaca selalu "belum dibaca" selama masih ada (aksi/angka dari
 * server). Pengumuman belum dibaca bila lebih baru dari penanda baca lokal DAN dalam jendela 7 hari.
 */
fun buildExpatNotifications(
    pendingReviews: List<ExpatPendingReview>,
    announcements: List<Announcement>,
    lastSeenAnnouncementAt: Long,
    nowMillis: Long,
    chatUnread: Int = 0
): List<ExpatNotification> {
    val windowStart = nowMillis - NOTIFICATION_UNREAD_WINDOW_MILLIS
    val reviews = pendingReviews
        .filter { it.pendingCount > 0 }
        .sortedBy { it.driverName }
        .map {
            ExpatNotification(
                id = "review:${it.driverUid}",
                kind = ExpatNotificationKind.REVIEW,
                text = it.driverName,
                atMillis = nowMillis,
                unread = true,
                count = it.pendingCount,
                driverUid = it.driverUid
            )
        }
    val infos = announcements
        .sortedByDescending { it.createdAtMillis }
        .take(MAX_ANNOUNCEMENTS)
        .map {
            ExpatNotification(
                id = "ann:${it.id}",
                kind = ExpatNotificationKind.ANNOUNCEMENT,
                text = it.title,
                atMillis = it.createdAtMillis,
                unread = it.createdAtMillis > lastSeenAnnouncementAt && it.createdAtMillis >= windowStart
            )
        }
    val messages = if (chatUnread > 0) {
        listOf(
            ExpatNotification(
                id = "msg:chat", kind = ExpatNotificationKind.MESSAGE, text = "",
                atMillis = nowMillis, unread = true, count = chatUnread
            )
        )
    } else emptyList()

    // sortedByDescending stabil: pada waktu sama, urutan tinjauan -> pesan -> pengumuman terjaga.
    return (reviews + messages + infos).sortedByDescending { it.atMillis }
}

fun List<ExpatNotification>.unreadCount(): Int = count { it.unread }

/** Kelompok tampilan: hari ini (>= awal hari) atau sebelumnya. */
fun ExpatNotification.isToday(startOfTodayMillis: Long): Boolean = atMillis >= startOfTodayMillis
