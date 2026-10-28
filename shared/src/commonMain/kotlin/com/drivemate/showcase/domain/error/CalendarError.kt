package com.drivemate.showcase.domain.error

sealed class CalendarError(
    message: String,
    cause: Throwable? = null
) : Throwable(message, cause) {
    data object NotFound : CalendarError("not_found")
    data object PermissionDenied : CalendarError("permission_denied")
    class Unknown(cause: Throwable? = null) : CalendarError("unknown", cause)
}
