package com.drivemate.showcase.core.model

enum class Role { ADMIN, DRIVER, EXPAT }

// --- Helper agar konsisten dengan Firestore (lowercase string di rules)
fun Role.toFirestore(): String = when (this) {
    Role.ADMIN -> "admin"
    Role.DRIVER -> "driver"
    Role.EXPAT -> "expat"
}

fun String.toRole(): Role = when (lowercase()) {
    "admin" -> Role.ADMIN
    "driver" -> Role.DRIVER
    "expat", "expatriate", "expatriat" -> Role.EXPAT
    else -> throw IllegalArgumentException()
}
