package com.drivemate.showcase.core.model

data class Announcement(
    val id: String,
    val title: String,
    val body: String,
    val createdAtMillis: Long,
    val createdByUid: String,
    val createdByName: String?,
    val createdByEmail: String?
)
