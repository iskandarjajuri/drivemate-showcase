package com.drivemate.showcase

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform