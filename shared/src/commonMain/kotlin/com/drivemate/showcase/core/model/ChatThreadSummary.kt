package com.drivemate.showcase.core.model

/** Ringkasan satu thread 1:1 untuk daftar Pesan (dari dokumen thread yang dipelihara function). */
data class ChatThreadSummary(
    val threadId: String,
    val partnerUid: String,
    val lastMessagePreview: String,
    val lastMessageDeleted: Boolean,
    val lastMessageSenderUid: String?,
    val lastMessageAtMillis: Long,
    val unreadCount: Int
)
