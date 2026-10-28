package com.drivemate.showcase.core.model

enum class AssignmentReason {
    SICK,
    LEAVE,
    OVERTIME_REST,
    OTHER;

    fun toRaw(): String = name

    companion object {
        fun fromRaw(raw: String?): AssignmentReason? {
            val normalized = raw?.trim()?.uppercase() ?: return null
            return entries.firstOrNull { it.name == normalized }
        }
    }
}

data class DriverAssignmentOverride(
    val driverUid: String,
    val expatUid: String,
    val assignedBy: String,
    val reason: AssignmentReason,
    val epochDay: Int,
    val createdAt: Long?
)
