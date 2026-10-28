package com.drivemate.showcase.data.repository

import com.drivemate.showcase.bridge.IOSAuthPlatformBridge
import com.drivemate.showcase.bridge.IOSAuthUserPayload
import com.drivemate.showcase.bridge.IOSPlatformBridgeRegistry
import com.drivemate.showcase.bridge.IOSRoleResolutionState
import com.drivemate.showcase.core.model.Role
import com.drivemate.showcase.core.model.User
import com.drivemate.showcase.core.model.UserSession
import com.drivemate.showcase.core.model.toRole
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class IOSFirebaseAuthRepository : AuthRepository {
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val roleCache = mutableMapOf<String, Role>()
    private val _userSession = MutableStateFlow(seedSession())
    private val _roleResolutionState = MutableStateFlow(
        if (_userSession.value == null) IOSRoleResolutionState.IDLE else IOSRoleResolutionState.RESOLVING
    )
    private var roleResolutionJob: Job? = null

    private val authBridge: IOSAuthPlatformBridge
        get() = IOSPlatformBridgeRegistry.authBridge
            ?: error("IOSAuthPlatformBridge is not installed")

    internal val roleResolutionState: StateFlow<IOSRoleResolutionState> = _roleResolutionState.asStateFlow()

    private fun seedSession(): UserSession? =
        authBridge.currentUser()?.toSession(role = null)

    private fun clearCachedRole(uid: String) {
        roleCache.remove(uid)
    }

    init {
        authBridge.observeAuthState { payload ->
            handleAuthPayload(payload)
        }
    }

    private fun handleAuthPayload(payload: IOSAuthUserPayload?) {
        roleResolutionJob?.cancel()
        if (payload == null) {
            _userSession.value = null
            _roleResolutionState.value = IOSRoleResolutionState.IDLE
            return
        }

        val base = payload.toSession(role = null)
        _userSession.value = base
        _roleResolutionState.value = IOSRoleResolutionState.RESOLVING

        roleResolutionJob = repoScope.launch {
            when (val outcome = resolveRoleByUid(payload.uid)) {
                is IOSRoleResolutionOutcome.Resolved -> {
                    if (!isCurrentAuthUser(payload.uid)) return@launch
                    _userSession.value = base.copy(role = outcome.role)
                    _roleResolutionState.value = IOSRoleResolutionState.RESOLVED
                }

                IOSRoleResolutionOutcome.TransientFailure -> {
                    if (!isCurrentAuthUser(payload.uid)) return@launch
                    _roleResolutionState.value = IOSRoleResolutionState.TRANSIENT_FAILURE
                }

                IOSRoleResolutionOutcome.InvalidAccount -> {
                    if (!isCurrentAuthUser(payload.uid)) return@launch
                    clearCachedRole(payload.uid)
                    _roleResolutionState.value = IOSRoleResolutionState.INVALID_ACCOUNT
                }
            }
        }
    }

    private fun isCurrentAuthUser(uid: String): Boolean =
        authBridge.currentUser()?.uid == uid

    fun currentUserSession(): UserSession? = _userSession.value

    fun retryRoleResolution(uid: String) {
        val current = authBridge.currentUser() ?: run {
            _userSession.value = null
            _roleResolutionState.value = IOSRoleResolutionState.IDLE
            return
        }
        if (current.uid != uid) return
        handleAuthPayload(current)
    }

    private suspend fun resolveRoleByUid(uid: String): IOSRoleResolutionOutcome {
        roleCache[uid]?.let { return IOSRoleResolutionOutcome.Resolved(it) }
        val resolved = suspendCancellableCoroutine<IOSRoleResolutionOutcome> { continuation ->
            authBridge.fetchUserRole(uid) { roleRaw, errorMessage ->
                if (errorMessage != null) {
                    continuation.resume(IOSRoleResolutionOutcome.TransientFailure)
                    return@fetchUserRole
                }

                if (roleRaw == null) {
                    continuation.resume(IOSRoleResolutionOutcome.InvalidAccount)
                    return@fetchUserRole
                }

                val role = roleRaw
                    .trim()
                    .takeIf { it.isNotEmpty() }
                    ?.let { raw -> runCatching { raw.toRole() }.getOrNull() }

                continuation.resume(
                    if (role != null) IOSRoleResolutionOutcome.Resolved(role)
                    else IOSRoleResolutionOutcome.InvalidAccount
                )
            }
        }
        if (resolved is IOSRoleResolutionOutcome.Resolved) {
            roleCache[uid] = resolved.role
        }
        return resolved
    }

    override val userSessionFlow: Flow<UserSession?> = _userSession.asStateFlow()

    override suspend fun login(email: String, password: String): AppResult<Unit> =
        suspendCancellableCoroutine { continuation ->
            authBridge.signIn(email.trim(), password) { errorMessage ->
                if (errorMessage != null) {
                    continuation.resume(AppResult.failure(IllegalStateException(errorMessage)))
                    return@signIn
                }
                continuation.resume(AppResult.success(Unit))
            }
        }

    override suspend fun logout(): AppResult<Unit> =
        suspendCancellableCoroutine { continuation ->
            authBridge.signOut { errorMessage ->
                if (errorMessage != null) {
                    continuation.resume(AppResult.failure(IllegalStateException(errorMessage)))
                } else {
                    continuation.resume(AppResult.success(Unit))
                }
            }
        }

    override suspend fun sendReset(email: String): AppResult<Unit> =
        suspendCancellableCoroutine { continuation ->
            authBridge.sendPasswordReset(email.trim()) { errorMessage ->
                if (errorMessage != null) {
                    continuation.resume(AppResult.failure(IllegalStateException(errorMessage)))
                } else {
                    continuation.resume(AppResult.success(Unit))
                }
            }
        }

    override fun authState(): Flow<User?> =
        userSessionFlow.map { session ->
            session?.role?.let { role ->
                User(
                    uid = session.uid,
                    email = session.email.orEmpty(),
                    role = role
                )
            }
        }

    override suspend fun currentUser(): AppResult<User?> {
        val session = _userSession.value ?: return AppResult.success(null)
        val role = session.role ?: roleCache[session.uid] ?: return AppResult.success(null)
        return AppResult.success(
            User(
                uid = session.uid,
                email = session.email.orEmpty(),
                role = role
            )
        )
    }

    private fun IOSAuthUserPayload.toSession(role: Role?): UserSession =
        UserSession(
            uid = uid,
            email = email,
            role = role
        )
}

private sealed interface IOSRoleResolutionOutcome {
    data class Resolved(val role: Role) : IOSRoleResolutionOutcome
    data object TransientFailure : IOSRoleResolutionOutcome
    data object InvalidAccount : IOSRoleResolutionOutcome
}
