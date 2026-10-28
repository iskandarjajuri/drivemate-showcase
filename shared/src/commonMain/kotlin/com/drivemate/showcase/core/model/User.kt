package com.drivemate.showcase.core.model

data class User(
    val uid: String,   // <— sebelumnya 'id'
    val email: String,
    val role: Role
)