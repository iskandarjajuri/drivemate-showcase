package com.drivemate.showcase.navigation

import com.drivemate.showcase.feature.driver.notifications.formatNotificationBadge

/** Variasi deskripsi aksesibilitas tab Tinjau. */
internal enum class ExpatReviewDescriptionKind {
    /** Tanpa data tepercaya (memuat, gagal, atau kosong): hanya nama tab. */
    LABEL_ONLY,

    /** "Tinjau, 16 menunggu, 19 dari 35 ditinjau". */
    PROGRESS,

    /** Semua sudah ditinjau. */
    COMPLETE
}

/**
 * Keadaan cincin progres di tab Tinjau. Semuanya turunan dari angka yang SUDAH dimuat Beranda
 * (menunggu + ditinjau); tidak ada query baru.
 *
 * [dataReady] = false (memuat, gagal, atau belum ada driver) TIDAK boleh tampil sebagai "semua selesai"
 * (pelajaran 6e44f07): progres 0, tidak selesai, deskripsi hanya nama tab.
 */
internal data class ExpatReviewRingState(
    val pending: Int,
    val reviewed: Int,
    val dataReady: Boolean
) {
    private val safePending = pending.coerceAtLeast(0)
    private val safeReviewed = reviewed.coerceAtLeast(0)

    /** Menunggu + ditinjau. */
    val total: Int = safePending + safeReviewed

    /** ditinjau / (menunggu + ditinjau), 0 bila belum ada data tepercaya. */
    val progress: Float =
        if (!dataReady || total == 0) 0f else (safeReviewed.toFloat() / total).coerceIn(0f, 1f)

    /** Selesai: data tepercaya, ada yang ditinjau, dan tidak ada yang menunggu. Total 0 bukan "selesai". */
    val isComplete: Boolean = dataReady && total > 0 && safePending == 0

    /** Badge sisa menunggu ("9+" di atas 9); hilang saat selesai atau tak ada yang menunggu. */
    val badgeText: String? = if (isComplete) null else formatNotificationBadge(safePending)

    val descriptionKind: ExpatReviewDescriptionKind = when {
        !dataReady || total == 0 -> ExpatReviewDescriptionKind.LABEL_ONLY
        isComplete -> ExpatReviewDescriptionKind.COMPLETE
        else -> ExpatReviewDescriptionKind.PROGRESS
    }

    val pendingCount: Int get() = safePending
    val reviewedCount: Int get() = safeReviewed
}
