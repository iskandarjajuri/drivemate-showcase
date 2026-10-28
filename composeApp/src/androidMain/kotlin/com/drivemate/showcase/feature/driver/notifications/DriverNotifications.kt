package com.drivemate.showcase.feature.driver.notifications

import com.drivemate.showcase.core.model.Announcement
import com.drivemate.showcase.core.model.ApprovalStatus
import com.drivemate.showcase.core.model.WorkDay
import com.drivemate.showcase.core.model.approvalStatus

enum class DriverNotificationKind { TASK, ANNOUNCEMENT, APPROVAL, MESSAGE }

/** Tugas harian/sekali-jalan yang masih menunggu driver. */
enum class DriverPendingTask { SOP, CHECKLIST, SAFETY }

/**
 * Satu baris notifikasi. [text] = judul pengumuman (ANNOUNCEMENT) atau label tanggal
 * hari kerja (APPROVAL); untuk TASK diisi dari [task] di lapisan UI (string resource).
 */
data class DriverNotification(
    val id: String,
    val kind: DriverNotificationKind,
    val text: String,
    val atMillis: Long,
    val unread: Boolean,
    val task: DriverPendingTask? = null,
    val rejected: Boolean = false,
    /** Hanya untuk MESSAGE: jumlah pesan chat belum dibaca. */
    val count: Int = 0
)

/** Jendela keterbaruan: pengumuman/persetujuan lebih lama dari ini tidak dihitung "belum dibaca". */
const val NOTIFICATION_UNREAD_WINDOW_MILLIS: Long = 7L * 24 * 60 * 60 * 1000

private const val MAX_ANNOUNCEMENTS = 20
private const val MAX_APPROVALS = 10

/**
 * Menggabungkan sumber yang SUDAH ADA menjadi satu daftar (terbaru dulu):
 * tugas tertunda, pengumuman, dan keputusan persetujuan hari kerja. Pesan chat tidak
 * disertakan sebagai satu baris MESSAGE bila ada pesan belum dibaca ([chatUnread] dari server).
 *
 * Tugas selalu "belum dibaca" selama tertunda (aksi, bukan info). Pengumuman/persetujuan
 * belum dibaca bila lebih baru dari penanda baca terakhir DAN dalam jendela 7 hari.
 */
fun buildDriverNotifications(
    pendingTasks: List<DriverPendingTask>,
    announcements: List<Announcement>,
    workDays: List<WorkDay>,
    lastSeenAnnouncementAt: Long,
    lastSeenApprovalAt: Long,
    nowMillis: Long,
    chatUnread: Int = 0,
    approvalLabel: (WorkDay) -> String
): List<DriverNotification> {
    val windowStart = nowMillis - NOTIFICATION_UNREAD_WINDOW_MILLIS
    val tasks = pendingTasks.map {
        DriverNotification(
            id = "task:${it.name}",
            kind = DriverNotificationKind.TASK,
            text = "",
            atMillis = nowMillis,
            unread = true,
            task = it
        )
    }
    val infos = announcements
        .sortedByDescending { it.createdAtMillis }
        .take(MAX_ANNOUNCEMENTS)
        .map {
            DriverNotification(
                id = "ann:${it.id}",
                kind = DriverNotificationKind.ANNOUNCEMENT,
                text = it.title,
                atMillis = it.createdAtMillis,
                unread = it.createdAtMillis > lastSeenAnnouncementAt && it.createdAtMillis >= windowStart
            )
        }
    val approvals = workDays.mapNotNull { day ->
        val status = day.approvalStatus()
        val at = when (status) {
            ApprovalStatus.APPROVED -> day.approvedAtMillis
            ApprovalStatus.REJECTED -> day.rejectedAtMillis
            ApprovalStatus.PENDING -> null
        } ?: return@mapNotNull null
        DriverNotification(
            id = "appr:${day.id}",
            kind = DriverNotificationKind.APPROVAL,
            text = approvalLabel(day),
            atMillis = at,
            unread = at > lastSeenApprovalAt && at >= windowStart,
            rejected = status == ApprovalStatus.REJECTED
        )
    }.sortedByDescending { it.atMillis }.take(MAX_APPROVALS)

    // Pesan chat belum dibaca: satu baris ringkas; hilang saat semua thread dibaca (angka dari server).
    val messages = if (chatUnread > 0) {
        listOf(
            DriverNotification(
                id = "msg:chat", kind = DriverNotificationKind.MESSAGE, text = "",
                atMillis = nowMillis, unread = true, count = chatUnread
            )
        )
    } else emptyList()

    return (tasks + messages + infos + approvals).sortedByDescending { it.atMillis }
}

fun List<DriverNotification>.unreadCount(): Int = count { it.unread }

/**
 * Pembaruan pekerjaan yang belum dilihat (pengumuman + keputusan persetujuan) untuk badge tab Beranda.
 * Tugas tertunda (badge Tasks sendiri) dan pesan chat (badge Pesan sendiri) tidak ikut dihitung.
 */
fun List<DriverNotification>.unseenWorkUpdates(): Int =
    count { it.unread && (it.kind == DriverNotificationKind.ANNOUNCEMENT || it.kind == DriverNotificationKind.APPROVAL) }

/** Teks lencana: null bila 0, "9+" bila > 9. */
fun formatNotificationBadge(count: Int): String? = when {
    count <= 0 -> null
    count > 9 -> "9+"
    else -> count.toString()
}

/** Kelompok tampilan: hari ini (>= awal hari) atau sebelumnya. */
fun DriverNotification.isToday(startOfTodayMillis: Long): Boolean = atMillis >= startOfTodayMillis

enum class DriverNotificationFilter { ALL, TASKS, INFO }

fun List<DriverNotification>.filteredBy(filter: DriverNotificationFilter): List<DriverNotification> = when (filter) {
    DriverNotificationFilter.ALL -> this
    DriverNotificationFilter.TASKS -> filter { it.kind == DriverNotificationKind.TASK }
    DriverNotificationFilter.INFO -> filter { it.kind != DriverNotificationKind.TASK }
}

/** Tugas tertunda dari sinyal Home; nilai null (belum diketahui) TIDAK dihitung tertunda. */
fun pendingDriverTasks(sopAccepted: Boolean?, checklistSubmitted: Boolean?, safetySubmitted: Boolean?): List<DriverPendingTask> =
    buildList {
        if (sopAccepted == false) add(DriverPendingTask.SOP)
        if (checklistSubmitted == false) add(DriverPendingTask.CHECKLIST)
        if (safetySubmitted == false) add(DriverPendingTask.SAFETY)
    }
