package com.drivemate.showcase.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.model.Role
import com.drivemate.showcase.core.model.User
import com.drivemate.showcase.core.model.UserSession
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.data.RoleCache
import com.drivemate.showcase.data.firestore.FirestoreService
import com.drivemate.showcase.domain.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.util.Locale

private const val TAG = "DM/AuthRepo"

class FirebaseAuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val fs: FirestoreService,
    private val roleCache: RoleCache
) : AuthRepository {

    // Scope untuk StateFlow di repository (hidup sepanjang instance repo)
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Seed session awal dari FirebaseAuth (UID-first)
    private fun seedSessionFromFirebase(): UserSession? {
        val u = auth.currentUser ?: return null
        return UserSession(uid = u.uid, email = u.email, role = null) // role resolved via Firestore later
    }

    // Resolve role ketat berdasarkan users/{uid}.type
    private suspend fun resolveRoleByUid(uid: String): Role? {
        roleCache.get(uid)?.let { return it }

        val deferred = roleResolveMutex.withLock {
            inFlightRole[uid]?.let { return@withLock it }
            repoScope.async(Dispatchers.IO) {
                try {
                    when (val res = fs.getUserTypeByUid(uid)) {
                        is AppResult.Success -> {
                            val role = res.data.toRoleOrNull()
                            if (role == null) {
                                DMLog.e(TAG, "resolveRoleByUid($uid) invalid role='${res.data}'")
                            }
                            role
                        }
                        is AppResult.Failure -> {
                            val cause = res.throwable
                            if (cause is CancellationException) throw cause
                            DMLog.e(TAG, "resolveRoleByUid($uid) failed: ${cause.message}", cause)
                            null
                        }
                    }
                } catch (c: CancellationException) {
                    DMLog.d(TAG, "resolveRoleByUid($uid) cancelled (lifecycle)")
                    throw c
                }
            }.also { inFlightRole[uid] = it }
        }

        return try {
            deferred.await()?.also { roleCache.save(uid, it) }
        } finally {
            roleResolveMutex.withLock {
                if (inFlightRole[uid] === deferred) inFlightRole.remove(uid)
            }
        }
    }

    // Expose hot flow of UserSession (UID-first, server role resolution)
    override val userSessionFlow: Flow<UserSession?> =
        callbackFlow {
            // initial emission (non-blocking)
            trySend(seedSessionFromFirebase())

            val listener = FirebaseAuth.AuthStateListener { fa ->
                val u = fa.currentUser
                if (u == null) {
                    DMLog.d(TAG, "userSessionFlow → signed out")
                    trySend(null)
                } else {
                    // Emit base session immediately (role=null)
                    val base = UserSession(uid = u.uid, email = u.email, role = null)
                    DMLog.d(TAG, "userSessionFlow → auth change uid=${base.uid}, email=${base.email}")
                    trySend(base)

                    // Resolve role strictly from server (users/{uid}.type), then emit again
                    repoScope.launch(Dispatchers.IO) {
                        try {
                            val role = resolveRoleByUid(u.uid)
                            if (role != null) {
                                roleCache.save(u.uid, role)
                                trySend(base.copy(role = role))
                            } else {
                                DMLog.d(TAG, "userSessionFlow: role unresolved for uid=${u.uid}")
                            }
                        } catch (c: CancellationException) {
                            DMLog.d(TAG, "userSessionFlow: role resolve cancelled (lifecycle/navigation)")
                        }
                    }
                }
            }
            auth.addAuthStateListener(listener)
            awaitClose { auth.removeAuthStateListener(listener) }
        }.stateIn(
            scope = repoScope,
            started = SharingStarted.Eagerly,
            initialValue = seedSessionFromFirebase()
        )

    // StateFlow auth yang hot + punya initial value (placeholder berbasis UID)
    private val authStateFlow: StateFlow<User?> =
        callbackFlow {
            DMLog.i(TAG, "Mulai observe authState()")
            // initial non-blocking based on current Firebase user
            val seed = auth.currentUser?.let { u ->
                val email = u.email ?: ""
                // placeholder role; JANGAN dipakai untuk keputusan keamanan/navigasi
                User(uid = u.uid, email = email, role = Role.DRIVER)
            }
            trySend(seed)

            val listener = FirebaseAuth.AuthStateListener { fa ->
                val u = fa.currentUser
                val user = u?.let {
                    val email = it.email ?: ""
                    User(uid = it.uid, email = email, role = Role.DRIVER) // placeholder
                }
                DMLog.d(TAG, "authState berubah (UID) → ${user?.uid ?: "null"}")
                trySend(user)
            }
            auth.addAuthStateListener(listener)
            awaitClose {
                DMLog.i(TAG, "Stop observe authState()")
                auth.removeAuthStateListener(listener)
            }
        }.stateIn(
            scope = repoScope,
            started = SharingStarted.Eagerly,
            initialValue = auth.currentUser?.let { u ->
                val email = u.email ?: ""
                User(uid = u.uid, email = email, role = Role.DRIVER)
            }
        )

    private val roleResolveMutex = Mutex()
    private val inFlightRole = mutableMapOf<String, kotlinx.coroutines.Deferred<Role?>>()

    /**
     * Health-check Firestore sebelum operasi kritis login.
     * Fail-fast jika offline supaya UI tidak menggantung di role=null.
     */
    private suspend fun ensureBackendOnlineOrFail(): AppResult<Unit> {
        DMLog.d(TAG, "login: ensureBackendOnlineOrFail()")

        val uid = auth.currentUser?.uid ?: ""
        return when (val ping = fs.pingServer(uid)) {
            is AppResult.Success -> {
                DMLog.i(TAG, "login: backend online")
                AppResult.Success(Unit)
            }
            is AppResult.Failure -> {
                val cause = ping.throwable
                if (cause is CancellationException) throw cause
                DMLog.e(TAG, "login: backend offline/unreachable: ${cause.message}", cause)
                AppResult.Failure(
                    IllegalStateException(
                        "Tidak bisa mengakses server (offline). Coba lagi.",
                        cause
                    )
                )
            }
        }
    }

    override suspend fun login(email: String, password: String): AppResult<Unit> {
        return try {
            DMLog.i(TAG, "login(email=$email)")
            auth.signInWithEmailAndPassword(email, password).await()
            val uid = auth.currentUser?.uid
            DMLog.i(TAG, "login: auth ok uid=${uid ?: "-"}")

            // Pastikan backend online sebelum resolve role
            when (val health = ensureBackendOnlineOrFail()) {
                is AppResult.Failure -> {
                    runCatching { auth.signOut() }
                    DMLog.e(TAG, "login: fail → backend offline: ${health.throwable.message}", health.throwable)
                    return AppResult.Failure(health.throwable)
                }
                is AppResult.Success -> Unit
            }

            val u = auth.currentUser
            if (u == null) {
                runCatching { auth.signOut() }
                val err = IllegalStateException("Login gagal: user hilang setelah sign-in")
                DMLog.e(TAG, "login: fail → ${err.message}")
                return AppResult.Failure(err)
            }

            val role = resolveRoleByUid(u.uid)
            if (role == null) {
                runCatching { auth.signOut() }
                val err = IllegalStateException("Role user tidak valid atau belum di-set. Hubungi admin.")
                DMLog.e(TAG, "login: fail → role unresolved for uid=${u.uid}")
                return AppResult.Failure(err)
            }

            roleCache.save(u.uid, role)
            DMLog.i(TAG, "login: role resolved=$role")

            AppResult.Success(Unit)
        } catch (c: CancellationException) {
            DMLog.d(TAG, "login dibatalkan (lifecycle/navigation)")
            throw c
        } catch (t: Throwable) {
            DMLog.e(TAG, "login gagal: ${t.message}", t)
            AppResult.Failure(t)
        }
    }

    override suspend fun logout(): AppResult<Unit> = try {
        DMLog.i(TAG, "logout() dipanggil")
        auth.signOut()
        AppResult.Success(Unit)
    } catch (t: Throwable) {
        DMLog.e(TAG, "logout gagal: ${t.message}", t)
        AppResult.Failure(t)
    }

    override suspend fun currentUser(): AppResult<User?> {
        return try {
            val u = auth.currentUser ?: return AppResult.Success(null)
            val email = u.email ?: ""
            val role = roleCache.get(u.uid)

            val user = User(uid = u.uid, email = email, role = role ?: Role.DRIVER)
            if (role == null) {
                DMLog.d(TAG, "currentUser: role unresolved for uid=${u.uid} → return placeholder role until session flow resolves")
            } else {
                DMLog.d(TAG, "currentUser (cached role) = ${user.uid} (${user.role})")
            }
            AppResult.Success(user)
        } catch (c: CancellationException) {
            DMLog.d(TAG, "currentUser cancelled (lifecycle/navigation)")
            throw c
        } catch (t: Throwable) {
            DMLog.e(TAG, "currentUser gagal: ${t.message}", t)
            AppResult.Failure(t)
        }
    }

    override fun authState(): Flow<User?> = authStateFlow

    override suspend fun sendReset(email: String): AppResult<Unit> = try {
        DMLog.i(TAG, "Kirim reset password ke $email")
        auth.sendPasswordResetEmail(email).await()
        AppResult.Success(Unit)
    } catch (t: Throwable) {
        DMLog.e(TAG, "sendReset gagal: ${t.message}", t)
        AppResult.Failure(t)
    }
}

private fun String.toRoleOrNull(): Role? {
    val normalized = lowercase(Locale.ROOT)
    return when (normalized) {
        "admin" -> Role.ADMIN
        "driver" -> Role.DRIVER
        "expat" -> Role.EXPAT
        "expatriate", "expatriat" -> {
            DMLog.d(TAG, "Legacy role alias detected: $this -> expat")
            Role.EXPAT
        }
        else -> null
    }
}
