package com.drivemate.showcase.core.util

enum class AuthErrorCode {
    InvalidInput
}

class AuthError(
    val code: AuthErrorCode,
    cause: Throwable? = null
) : Throwable(code.name, cause)
