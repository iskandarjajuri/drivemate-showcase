package com.drivemate.showcase.core.model

data class ChatContact(
    val uid: String,
    val displayName: String,
    val role: Role,
    val email: String?,
    /** Bahasa chat eksplisit dari `user_directory.lang` ("ja"/"id"); null bila belum diatur. */
    val lang: String? = null
)
