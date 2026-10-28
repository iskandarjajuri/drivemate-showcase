package com.drivemate.showcase.core.model

/**
 * Snapshot sesi user yang sedang aktif, lintas platform.
 * Jangan refer ke class Firebase di sini.
 */
data class UserSession(
    val uid: String,
    val email: String?,
    val role: Role?
)