package com.drivemate.showcase.data.repository

import com.drivemate.showcase.bridge.IOSPlatformBridgeRegistry
import com.drivemate.showcase.core.model.ChatContact
import com.drivemate.showcase.core.model.DriverProfile
import com.drivemate.showcase.core.model.Role
import com.drivemate.showcase.core.model.SopStatus
import com.drivemate.showcase.core.model.toFirestore
import com.drivemate.showcase.core.model.toRole
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.UserDirectoryRepository
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class IOSUserDirectoryRepository : UserDirectoryRepository {

    private val bridge
        get() = IOSPlatformBridgeRegistry.userDirectoryBridge
            ?: error("IOSUserDirectoryPlatformBridge is not installed")

    override suspend fun getUserDisplayName(uid: String): AppResult<String?> =
        suspendCancellableCoroutine { cont ->
            bridge.getUserDisplayName(uid) { name, error ->
                if (!cont.isCompleted) cont.resume(
                    if (error != null) AppResult.failure(IllegalStateException(error))
                    else AppResult.success(name)
                )
            }
        }

    override suspend fun getAllDriverDisplayNames(): AppResult<Map<String, String>> =
        suspendCancellableCoroutine { cont ->
            bridge.getDriverList { payloads, error ->
                if (!cont.isCompleted) cont.resume(
                    when {
                        error != null -> AppResult.failure(IllegalStateException(error))
                        payloads != null -> AppResult.success(
                            payloads.associate { it.uid to it.displayName }
                        )
                        else -> AppResult.success(emptyMap())
                    }
                )
            }
        }

    override suspend fun getDriverSopStatuses(): AppResult<List<SopStatus>> =
        suspendCancellableCoroutine { cont ->
            bridge.getDriverList { payloads, error ->
                if (!cont.isCompleted) cont.resume(
                    when {
                        error != null -> AppResult.failure(IllegalStateException(error))
                        payloads != null -> AppResult.success(
                            payloads.map { p ->
                                SopStatus(
                                    uid = p.uid,
                                    name = p.displayName,
                                    sopAccepted = p.sopAccepted,
                                    sopAcceptedAtMillis = p.sopAcceptedAtMillis
                                )
                            }
                        )
                        else -> AppResult.success(emptyList())
                    }
                )
            }
        }

    override suspend fun getDriverSopStatus(uid: String): AppResult<SopStatus?> =
        suspendCancellableCoroutine { cont ->
            bridge.getSopStatus(uid) { sopAccepted, sopAcceptedAtMillis, error ->
                if (!cont.isCompleted) cont.resume(
                    if (error != null) AppResult.failure(IllegalStateException(error))
                    else AppResult.success(
                        SopStatus(
                            uid = uid,
                            name = null,
                            sopAccepted = sopAccepted,
                            sopAcceptedAtMillis = sopAcceptedAtMillis
                        )
                    )
                )
            }
        }

    override suspend fun acceptSop(uid: String, acceptedAtMillis: Long): AppResult<Unit> =
        suspendCancellableCoroutine { cont ->
            bridge.acceptSop(uid, acceptedAtMillis) { error ->
                if (!cont.isCompleted) cont.resume(
                    if (error != null) AppResult.failure(IllegalStateException(error))
                    else AppResult.success(Unit)
                )
            }
        }

    override suspend fun getManagedDrivers(managerUid: String): AppResult<List<DriverProfile>> {
        if (managerUid.isBlank()) {
            return AppResult.failure(
                IllegalArgumentException("getManagedDrivers called with blank managerUid")
            )
        }
        return suspendCancellableCoroutine { cont ->
            bridge.getManagedDrivers(managerUid) { payloads, error ->
                if (!cont.isCompleted) cont.resume(
                    when {
                        error != null -> AppResult.failure(IllegalStateException(error))
                        payloads != null -> AppResult.success(
                            payloads.map { p -> DriverProfile(uid = p.uid, name = p.displayName, email = p.email) }
                        )
                        else -> AppResult.success(emptyList())
                    }
                )
            }
        }
    }

    /**
     * Mirrors Android's `FirebaseUserDirectoryRepository.getUsersByRoles` — same
     * `users` / `type` query, same dedup-by-uid, same sort (displayName, then role).
     *
     * Was `UnsupportedOperationException`, which forced every iOS chat screen to fall
     * back to the driver roster. That produced driver↔driver and expat↔driver threads
     * that `firestore.rules:194-203` rejects, because `validThreadParticipantPair`
     * requires exactly one participant to be an admin.
     */
    override suspend fun getUsersByRoles(roles: Set<Role>): AppResult<List<ChatContact>> {
        if (roles.isEmpty()) return AppResult.success(emptyList())
        return suspendCancellableCoroutine { cont ->
            bridge.getUsersByRoles(roles.map { it.toFirestore() }) { payloads, error ->
                if (cont.isCompleted) return@getUsersByRoles
                cont.resume(
                    when {
                        error != null -> AppResult.failure(IllegalStateException(error))
                        payloads == null -> AppResult.success(emptyList())
                        else -> AppResult.success(
                            payloads
                                .mapNotNull { p ->
                                    // An unknown `type` must not take the whole list down.
                                    val role = runCatching { p.roleRaw.toRole() }.getOrNull()
                                        ?: return@mapNotNull null
                                    ChatContact(
                                        uid = p.uid,
                                        displayName = p.displayName,
                                        role = role,
                                        email = p.email
                                    )
                                }
                                .distinctBy { it.uid }
                                .sortedWith(
                                    compareBy<ChatContact> { it.displayName.lowercase() }
                                        .thenBy { it.role.name }
                                )
                        )
                    }
                )
            }
        }
    }
}
