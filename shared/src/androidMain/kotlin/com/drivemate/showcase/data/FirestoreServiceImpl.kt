package com.drivemate.showcase.data

import android.os.Handler
import android.os.Looper
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.Transaction
import com.drivemate.showcase.core.config.FirestoreEmulatorConfig
import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.model.PlanSource
import com.drivemate.showcase.core.model.WorkDay
import com.drivemate.showcase.core.model.parseDeviationFlags
import com.drivemate.showcase.core.model.normalizeApprovalStatus
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.data.firestore.FirestoreService
import com.drivemate.showcase.data.firestore.FsDoc
import com.drivemate.showcase.data.firestore.ListenerRegistration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import com.google.firebase.firestore.ListenerRegistration as FbRegistration

private const val TAG = "DM/FirestoreService"
// Fail-fast guard: the app must be pointed at the project it was built for.
// Replace with your own Firebase project id (the showcase uses an emulator-only demo id).
private const val EXPECTED_PROJECT_ID = "demo-drivemate"

private class FirestoreServerReadTimeoutException(
    operation: String,
    timeoutMs: Long,
    cause: Throwable? = null
) : IllegalStateException(
    "Firestore server read timed out in $operation after ${timeoutMs}ms",
    cause
)

private suspend fun <T> withServerReadTimeout(
    timeoutMs: Long,
    operation: String,
    block: suspend () -> T
): T = try {
    withTimeout(timeoutMs) { block() }
} catch (t: TimeoutCancellationException) {
    throw FirestoreServerReadTimeoutException(operation, timeoutMs, t)
} catch (c: CancellationException) {
    throw c
}

private class FirstSnapshotTimeout(
    private val completed: AtomicBoolean,
    private val handler: Handler,
    private val timeoutRunnable: Runnable
) {
    fun markFirstEmission() {
        if (completed.compareAndSet(false, true)) {
            handler.removeCallbacks(timeoutRunnable)
        }
    }

    fun cancel() {
        if (completed.compareAndSet(false, true)) {
            handler.removeCallbacks(timeoutRunnable)
        }
    }
}

private fun firstSnapshotTimeout(
    operation: String,
    onTimeout: (Throwable) -> Unit
): FirstSnapshotTimeout {
    val completed = AtomicBoolean(false)
    val handler = Handler(Looper.getMainLooper())
    val timeoutRunnable = Runnable {
        if (completed.compareAndSet(false, true)) {
            val error = FirestoreServerReadTimeoutException(
                operation = operation,
                timeoutMs = FirestoreConstants.READ_TIMEOUT_MS
            )
            DMLog.e(TAG, "$operation first snapshot timed out after ${FirestoreConstants.READ_TIMEOUT_MS}ms", error)
            onTimeout(error)
        }
    }
    handler.postDelayed(timeoutRunnable, FirestoreConstants.READ_TIMEOUT_MS)
    return FirstSnapshotTimeout(completed, handler, timeoutRunnable)
}

class FirestoreServiceImpl(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val defaultTimeoutMs: Long,
    private val emulatorConfig: FirestoreEmulatorConfig = FirestoreEmulatorConfig.Disabled
) : FirestoreService {
    private fun isProbablyUid(key: String): Boolean =
        key.length >= 20 && !key.contains("@")

    private fun requireUid(key: String, origin: String) {
        if (!isProbablyUid(key)) {
            val msg = "Driver UID required in $origin (got '$key')"
            DMLog.w(TAG, msg)
            throw IllegalStateException(msg)
        }
    }

    init {
        // Firestore instance & settings are configured by DI.
        DMLog.i(TAG, "Init FirestoreServiceImpl (configured via DI)")
        // Print FirebaseApp options (project id, app id, api key masked, storage)
        runCatching {
            val app = FirebaseApp.getInstance()
            val opt = app.options

            fun mask(s: String?): String =
                if (s.isNullOrBlank()) "(none)"
                else if (s.length <= 6) "•••"
                else s.take(4) + "•••" + s.takeLast(2)

            DMLog.i(
                "DM/Firebase",
                "Options → projectId=${opt.projectId ?: "(none)"}, " +
                        "appId=${mask(opt.applicationId)}, " +
                        "apiKey=${mask(opt.apiKey)}, " +
                        "databaseUrl=${opt.databaseUrl ?: "(none)"}, " +
                        "storage=${opt.storageBucket ?: "(none)"}, " +
                        "gcmSenderId=${opt.gcmSenderId ?: "(none)"}"
            )
        }.onFailure {
            DMLog.w("DM/Firebase", "FirebaseApp options not available (will continue): ${it.message}")
        }
        // Log Firestore effective settings (host / SSL / cache type)
        runCatching {
            val settings = db.firestoreSettings
            // Try to detect cache settings across SDK versions (new & legacy)
            val cacheType = runCatching {
                // Newer SDKs: getLocalCacheSettings() or getCacheSettings()
                val cache = try {
                    settings.javaClass.getMethod("getLocalCacheSettings").invoke(settings)
                } catch (_: NoSuchMethodException) {
                    try {
                        settings.javaClass.getMethod("getCacheSettings").invoke(settings)
                    } catch (_: NoSuchMethodException) {
                        null
                    }
                }
                when (cache?.javaClass?.simpleName) {
                    "PersistentCacheSettings" -> "PersistentCacheSettings"
                    "MemoryCacheSettings" -> "MemoryCacheSettings"
                    else -> {
                        // Legacy SDKs: isPersistenceEnabled()
                        val persisted = try {
                            settings.javaClass.getMethod("isPersistenceEnabled").invoke(settings) as? Boolean
                        } catch (_: Throwable) { null }
                        if (persisted == true) "Persistent (legacy)" else "Memory/Unknown"
                    }
                }
            }.getOrElse { "UnknownCache" }
            DMLog.i(TAG, "Firestore settings → host=${settings.host}, ssl=${settings.isSslEnabled}, cache=$cacheType")
        }.onFailure {
            DMLog.w(TAG, "Unable to read Firestore settings: ${it.message}")
        }
        // --- Effective settings guard (fail-fast)
        // Validates against whichever mode was actually, deliberately intended —
        // production by default, or the dev-only Firestore emulator when
        // FirestoreEmulatorConfig.enabled is true (which itself can only be true in a
        // debug build; see FirestoreEmulatorConfig's doc). Either way this still fails
        // fast on ANY mismatch between intended and actual settings — it's mode-aware,
        // not loosened.
        run {
            val options = FirebaseApp.getInstance().options
            val settings = db.firestoreSettings

            // Project guard — always required, emulator or not. The emulator still
            // targets this project's rules/config; only network transport changes.
            require(options.projectId == EXPECTED_PROJECT_ID) {
                "Wrong Firebase projectId: ${options.projectId} (expected $EXPECTED_PROJECT_ID)"
            }

            if (emulatorConfig.enabled) {
                val expectedHost = "${emulatorConfig.host}:${emulatorConfig.port}"
                require(!settings.isSslEnabled) {
                    "Emulator mode was requested but SSL is enabled — useEmulator() was not applied"
                }
                require(settings.host == expectedHost) {
                    "Emulator mode was requested but host is ${settings.host} (expected $expectedHost)"
                }
                DMLog.w(TAG, "⚠️ Effective settings guard: EMULATOR mode confirmed (host=${settings.host})")
            } else {
                require(settings.isSslEnabled) {
                    "Firestore SSL is disabled (must be true)"
                }
                require(settings.host == "firestore.googleapis.com") {
                    "Suspicious Firestore host: ${settings.host} (expected firestore.googleapis.com)"
                }
            }

            DMLog.i(TAG, "Effective settings guard passed ✅ (projectId=${options.projectId}, host=${settings.host}, ssl=${settings.isSslEnabled})")
        }
    }



    // Retry helper with simple backoff
    private suspend inline fun <T> retry(
        times: Int = 3,
        initialDelayMs: Long = 200,
        factor: Double = 1.5,
        crossinline block: suspend () -> T
    ): T {
        var last: Throwable? = null
        var delayMs = initialDelayMs
        repeat(times) {
            try {
                return block()
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                last = t
                kotlinx.coroutines.delay(delayMs)
                delayMs = (delayMs * factor).toLong()
            }
        }
        throw last ?: IllegalStateException("retry failed with unknown error")
    }

    /**
     * Ensure Firestore is reachable for the **current user context**.
     * - Selalu enable network.
     * - Jika BELUM login → jangan sentuh path yang dilindungi rules (cukup enable network).
     * - Jika SUDAH login → GET ringan ke `/users/{uid}` (diizinkan oleh rules kamu).
     */
    private suspend fun ensureReachableForCurrentUser() {
        retry(times = 3, initialDelayMs = 250) {
            val timeout = minOf(5_000L, FirestoreConstants.READ_TIMEOUT_MS)
            // 1) enable network (no-op kalau sudah)
            withServerReadTimeout(timeout, "enableNetwork") { db.enableNetwork().await() }

            // 2) jika sudah login → sentuh path yang allowed oleh rules
            val uid = auth.currentUser?.uid
            if (!uid.isNullOrBlank()) {
                withServerReadTimeout(timeout, "ensureReachable(users/{uid})") {
                    db.collection("users").document(uid).get(Source.SERVER).await()
                }
            }
            // jika belum login: skip protected read (cukup network enabled)
        }
        DMLog.v(TAG, "ensureReachableForCurrentUser() → backend reachable for current context")
    }

    // ===== READ/QUERY =====
    override suspend fun query(
        collection: String,
        where: List<Pair<String, Any>>,
        orderBy: String?,
        limit: Int?
    ): List<FsDoc> {
        ensureReachableForCurrentUser()
        DMLog.d(TAG, "query() → col=$collection, where=$where, orderBy=$orderBy, limit=$limit")

        var ref: Query = db.collection(collection)
        for ((field, value) in where) {
            val key = field.takeWhile { it !in charArrayOf('>', '<', '=') }
            val op = field.removePrefix(key)
            ref = when (op) {
                ">=" -> ref.whereGreaterThanOrEqualTo(key, value)
                "<=" -> ref.whereLessThanOrEqualTo(key, value)
                ">"  -> ref.whereGreaterThan(key, value)
                "<"  -> ref.whereLessThan(key, value)
                else -> ref.whereEqualTo(key, value)
            }
        }
        if (orderBy != null) {
            val orderField = orderBy.removePrefix("-")
            val direction = if (orderBy.startsWith("-")) {
                Query.Direction.DESCENDING
            } else {
                Query.Direction.ASCENDING
            }
            ref = ref.orderBy(orderField, direction)
        }
        if (limit != null) ref = ref.limit(limit.toLong())

        val docs = withServerReadTimeout(FirestoreConstants.READ_TIMEOUT_MS, "query($collection)") {
            ref.get(Source.SERVER).await().documents
        }
        val out = docs.map { FsDoc(it.id, it.data) }
        DMLog.d(TAG, "query() done (SERVER_ONLY) → ${out.size} docs")
        return out
    }

    // ===== READ: single doc → Map =====
    override suspend fun getDocumentMap(collection: String, documentId: String): Map<String, Any>? {
        ensureReachableForCurrentUser()
        val ref = db.collection(collection).document(documentId)
        DMLog.d(TAG, "getDocumentMap(SERVER_ONLY): $collection/$documentId")
        return ref.getFromServerOnly(FirestoreConstants.READ_TIMEOUT_MS)
    }

    // ===== WRITE =====
    override suspend fun setDocument(path: String, data: Map<String, Any?>, merge: Boolean) {
        ensureReachableForCurrentUser()
        DMLog.i(TAG, "setDocument(path=$path, merge=$merge) keys=${data.keys}")
        val docRef = db.document(path)
        withTimeout(FirestoreConstants.WRITE_TIMEOUT_MS) {
            if (merge) docRef.set(data, SetOptions.merge()).await() else docRef.set(data).await()
        }
        DMLog.d(TAG, "setDocument() success")
    }

    override suspend fun addDocument(collection: String, data: Map<String, Any?>) {
        ensureReachableForCurrentUser()
        DMLog.i(TAG, "addDocument(col=$collection) keys=${data.keys}")
        withTimeout(FirestoreConstants.WRITE_TIMEOUT_MS) {
            db.collection(collection).add(data).await()
        }
        DMLog.d(TAG, "addDocument() success")
    }

    override suspend fun deleteDocument(path: String) {
        ensureReachableForCurrentUser()
        DMLog.i(TAG, "deleteDocument(path=$path)")
        withTimeout(FirestoreConstants.WRITE_TIMEOUT_MS) {
            db.document(path).delete().await()
        }
        DMLog.d(TAG, "deleteDocument() success")
    }

    suspend fun <T> runTransaction(block: (Transaction) -> T): T {
        ensureReachableForCurrentUser()
        return withTimeout(FirestoreConstants.WRITE_TIMEOUT_MS) {
            db.runTransaction { tx -> block(tx) }.await()
        }
    }

    fun documentRef(path: String): DocumentReference = db.document(path)

    // ===== REALTIME =====
    override fun listenCollection(
        collection: String,
        onChange: (List<FsDoc>) -> Unit
    ): ListenerRegistration {
        DMLog.i(TAG, "listenCollection() attach → $collection")
        var fb: FbRegistration? = null
        val firstEmissionTimeout = firstSnapshotTimeout("listenCollection($collection)") { error ->
            // TODO_VERIFY: shared FirestoreService.listenCollection cannot surface listener failures to callers yet.
            DMLog.w(TAG, "listenCollection() removing listener after hidden failure: ${error.message}")
            fb?.remove()
        }
        fb = db.collection(collection).addSnapshotListener { snap, err ->
            if (err != null) {
                firstEmissionTimeout.cancel()
                DMLog.e(
                    TAG,
                    "listenCollection() error not propagated to caller (shared API has no onError): ${err.message}",
                    err
                )
                return@addSnapshotListener
            }
            if (snap == null) {
                DMLog.w(TAG, "listenCollection() snapshot null")
                return@addSnapshotListener
            }
            firstEmissionTimeout.markFirstEmission()
            val docs = snap.documents.map { FsDoc(it.id, it.data) }
            DMLog.d(TAG, "listenCollection() update → ${docs.size} docs (fromCache=${snap.metadata.isFromCache})")
            onChange(docs)
        }
        return ListenerRegistration {
            DMLog.i(TAG, "listenCollection() remove → $collection")
            firstEmissionTimeout.cancel()
            fb.remove()
        }
    }

    override fun listenCollectionOrderBy(
        collection: String,
        orderByField: String,
        orderDesc: Boolean,
        limit: Int?,
        onChange: (List<FsDoc>) -> Unit,
        onError: ((Throwable) -> Unit)?
    ): ListenerRegistration {
        DMLog.i(
            TAG,
            "listenCollectionOrderBy() attach → $collection orderBy=$orderByField desc=$orderDesc limit=$limit"
        )

        var q: Query = db.collection(collection).orderBy(
            orderByField,
            if (orderDesc) Query.Direction.DESCENDING else Query.Direction.ASCENDING
        )
        if (limit != null && limit > 0) q = q.limit(limit.toLong())

        var fb: FbRegistration? = null
        val firstEmissionTimeout = firstSnapshotTimeout("listenCollectionOrderBy($collection)") { error ->
            onError?.invoke(error)
            fb?.remove()
        }
        fb = q.addSnapshotListener { snap, err ->
            if (err != null) {
                firstEmissionTimeout.cancel()
                DMLog.e(TAG, "listenCollectionOrderBy() error: ${err.message}", err)
                onError?.invoke(err)
                return@addSnapshotListener
            }
            if (snap == null) {
                DMLog.w(TAG, "listenCollectionOrderBy() snapshot null")
                return@addSnapshotListener
            }
            firstEmissionTimeout.markFirstEmission()
            val docs = snap.documents.map { FsDoc(it.id, it.data) }
            DMLog.d(
                TAG,
                "listenCollectionOrderBy() update → ${docs.size} docs (fromCache=${snap.metadata.isFromCache})"
            )
            onChange(docs)
        }

        return ListenerRegistration {
            DMLog.i(TAG, "listenCollectionOrderBy() remove → $collection")
            firstEmissionTimeout.cancel()
            fb.remove()
        }
    }

    override fun listenCollectionWhereOrderBy(
        collection: String,
        whereField: String,
        equalTo: Any,
        orderByField: String,
        orderDesc: Boolean,
        limit: Int?,
        onChange: (List<FsDoc>) -> Unit
    ): ListenerRegistration {
        DMLog.i(
            TAG,
            "listenCollectionWhereOrderBy() attach → $collection where $whereField == $equalTo orderBy=$orderByField desc=$orderDesc limit=$limit"
        )

        var q: Query = db.collection(collection)
            .whereEqualTo(whereField, equalTo)
            .orderBy(
                orderByField,
                if (orderDesc) Query.Direction.DESCENDING else Query.Direction.ASCENDING
            )

        if (limit != null && limit > 0) {
            q = q.limit(limit.toLong())
        }

        var fb: FbRegistration? = null
        val firstEmissionTimeout = firstSnapshotTimeout("listenCollectionWhereOrderBy($collection)") { error ->
            // TODO_VERIFY: shared FirestoreService.listenCollectionWhereOrderBy cannot surface listener failures to callers yet.
            DMLog.w(TAG, "listenCollectionWhereOrderBy() removing listener after hidden failure: ${error.message}")
            fb?.remove()
        }
        fb = q.addSnapshotListener { snap, err ->
            if (err != null) {
                firstEmissionTimeout.cancel()
                DMLog.e(
                    TAG,
                    "listenCollectionWhereOrderBy() error not propagated to caller (shared API has no onError): ${err.message}",
                    err
                )
                return@addSnapshotListener
            }
            if (snap == null) {
                DMLog.w(TAG, "listenCollectionWhereOrderBy() snapshot null")
                return@addSnapshotListener
            }
            firstEmissionTimeout.markFirstEmission()
            val docs = snap.documents.map { FsDoc(it.id, it.data) }
            DMLog.d(
                TAG,
                "listenCollectionWhereOrderBy() update → ${docs.size} docs (fromCache=${snap.metadata.isFromCache})"
            )
            onChange(docs)
        }

        return ListenerRegistration {
            DMLog.i(TAG, "listenCollectionWhereOrderBy() remove → $collection")
            firstEmissionTimeout.cancel()
            fb.remove()
        }
    }

    // ===== AUTH UTILS =====
    override fun currentUserEmailOrThrow(): String {
        val email = auth.currentUser?.email
        DMLog.d(TAG, "currentUserEmailOrThrow() → $email")
        return email ?: throw IllegalStateException("No authenticated user found")
    }

    override fun currentUserUidOrThrow(): String {
        val uid = auth.currentUser?.uid
        DMLog.d(TAG, "currentUserUidOrThrow() → $uid")
        return uid ?: throw IllegalStateException("No authenticated user found")
    }

    override suspend fun getUserTypeByUid(uid: String): AppResult<String> {
        return try {
            ensureReachableForCurrentUser()
            DMLog.d(TAG, "getUserTypeByUid(SERVER_ONLY) → $uid")
            val snap = withServerReadTimeout(FirestoreConstants.READ_TIMEOUT_MS, "getUserTypeByUid") {
                db.collection("users").document(uid).get(Source.SERVER).await()
            }
            if (!snap.exists()) {
                AppResult.Failure(IllegalStateException("User `$uid` not found (SERVER)"))
            } else {
                val rawType = snap.getString("type")?.trim()
                if (rawType.isNullOrBlank()) {
                    AppResult.Failure(IllegalStateException("User `$uid` missing `type`"))
                } else {
                    val normalized = when (rawType.lowercase(Locale.ROOT)) {
                        "admin" -> "admin"
                        "driver" -> "driver"
                        "expat" -> "expat"
                        "expatriate", "expatriat" -> {
                            DMLog.d(TAG, "Legacy role alias detected for uid=$uid: $rawType -> expat")
                            "expat"
                        }
                        else -> null
                    }
                    if (normalized == null) {
                        AppResult.Failure(
                            IllegalStateException("User `$uid` has invalid `type`: `$rawType`")
                        )
                    } else {
                        AppResult.Success(normalized)
                    }
                }
            }
        } catch (c: CancellationException) {
            DMLog.d(TAG, "getUserTypeByUid($uid) cancelled (lifecycle)")
            throw c
        } catch (t: Throwable) {
            DMLog.e(TAG, "getUserTypeByUid() failed: ${t.message}", t)
            return AppResult.Failure(t)
        }
    }

    // ===== HELPERS =====
    /**
     * UID-first implementation.
     * NOTE: `email` parameter is ignored; we resolve the current authenticated user's UID
     * and fetch `/users/{uid}` which is allowed by Firestore rules.
     * Callers should migrate to using `getUserTypeByUid(currentUserUidOrThrow())`.
     */


    // ===== HEALTHCHECK (optional) =====
    override suspend fun pingServer(uid: String): AppResult<Unit> {
        return try {
            val timeout = minOf(5_000L, FirestoreConstants.READ_TIMEOUT_MS)
            withServerReadTimeout(timeout, "pingServer(enableNetwork)") { db.enableNetwork().await() }

            // pre-auth: jangan sentuh path yang dilindungi rules
            if (uid.isBlank()) {
                DMLog.d(TAG, "pingServer(): no uid — network enabled, skipping protected read")
                return AppResult.Success(Unit)
            }

            // Sesuai rules: user boleh baca /users/{uid} dirinya sendiri (atau admin baca semua)
            withServerReadTimeout(timeout, "pingServer(users/{uid})") {
                db.collection("users")
                    .document(uid)
                    .get(Source.SERVER)
                    .await()
            }

            DMLog.d(TAG, "pingServer() ok — reachable via users/$uid")
            AppResult.Success(Unit)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            DMLog.e(TAG, "pingServer() failed: ${t.message}", t)
            AppResult.Failure(t)
        }
    }

    override suspend fun getAttendances(driverUid: String): AppResult<List<Map<String, Any>>> {
        return try {
            ensureReachableForCurrentUser()
            requireUid(driverUid, "getAttendances")
            DMLog.d(TAG, "getAttendances($driverUid) [SERVER_ONLY]")
            val q = db.collection("attendances").whereEqualTo("driverUid", driverUid)
            val docs = withServerReadTimeout(FirestoreConstants.READ_TIMEOUT_MS, "getAttendances") {
                q.get(Source.SERVER).await().documents
            }
            val list = docs.mapNotNull { it.data }
            DMLog.d(TAG, "getAttendances() → ${list.size} rows")
            AppResult.Success(list)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            DMLog.e(TAG, "getAttendances() failed: ${t.message}", t)
            AppResult.Failure(t)
        }
    }

    suspend fun getLatestAttendanceForUid(driverUid: String): FsDoc? {
        ensureReachableForCurrentUser()
        requireUid(driverUid, "getLatestAttendanceForUid")
        DMLog.d(TAG, "getLatestAttendanceForUid($driverUid) [SERVER_ONLY]")
        val docs = withServerReadTimeout(FirestoreConstants.READ_TIMEOUT_MS, "getLatestAttendanceForUid") {
            db.collection("attendances")
                .whereEqualTo("driverUid", driverUid)
                .orderBy("timestampMillis", Query.Direction.DESCENDING)
                .limit(1)
                .get(Source.SERVER)
                .await()
                .documents
        }
        return docs.firstOrNull()?.let { FsDoc(it.id, it.data) }
    }

    override suspend fun getWorkDays(driverUid: String): AppResult<List<WorkDay>> {
        return try {
            ensureReachableForCurrentUser()
            requireUid(driverUid, "getWorkDays")
            DMLog.d(TAG, "getWorkDays($driverUid) [SERVER_ONLY]")
            val q = db.collection("workdays").whereEqualTo("driverUid", driverUid)
            val docs = withServerReadTimeout(FirestoreConstants.READ_TIMEOUT_MS, "getWorkDays") {
                q.get(Source.SERVER).await().documents
            }

            val list = docs.mapNotNull { d ->
                val m = d.data ?: return@mapNotNull null
                val id = d.id
                val dateEpochDay = (m["dateEpochDay"] as? Number)?.toInt() ?: return@mapNotNull null
                val clockIn = (m["clockInMillis"] as? Number)?.toLong() ?: 0L
                val clockOut = (m["clockOutMillis"] as? Number)?.toLong() ?: 0L
                val total = (m["totalMinutes"] as? Number)?.toInt() ?: 0
                val overtime = (m["overtimeMinutes"] as? Number)?.toInt() ?: 0
                val driverName = m["driverName"] as? String?
                val statusRaw = m["status"] as? String?
                val approvedRaw = m["approvedByExpat"] as? Boolean?
                val approvedByUid = m["approvedByUid"] as? String?
                val approvedAtMillis = (m["approvedAtMillis"] as? Number)?.toLong()
                val approvalNote = m["approvalNote"] as? String?
                val rejectedByUid = m["rejectedByUid"] as? String?
                val rejectedAtMillis = (m["rejectedAtMillis"] as? Number)?.toLong()
                val rejectReason = m["rejectReason"] as? String?
                val rejectionNote = m["rejectionNote"] as? String?
                val plannedStart = (m["plannedStartMillis"] as? Number)?.toLong()
                val plannedEnd = (m["plannedEndMillis"] as? Number)?.toLong()
                val planSourceRaw = m["planSource"] as? String?
                val deviationFlagsRaw = (m["deviationFlags"] as? List<*>)?.mapNotNull { it as? String }
                val reviewHold = m["reviewHold"] as? Boolean
                val adjustedBy = m["adjustedBy"] as? String?
                val adjustedAt = (m["adjustedAt"] as? Number)?.toLong()
                val adjustedReason = m["adjustedReason"] as? String?
                val normalized = normalizeApprovalStatus(
                    statusRaw = statusRaw,
                    approvedByExpat = approvedRaw,
                    approvedByUid = approvedByUid,
                    rejectedByUid = rejectedByUid,
                    rejectedAtMillis = rejectedAtMillis,
                    rejectReason = rejectReason,
                    rejectionNote = rejectionNote
                )

                WorkDay(
                    id = id,
                    driverUid = driverUid,          // ← pass UID into model field
                    dateEpochDay = dateEpochDay,
                    clockInMillis = clockIn,
                    clockOutMillis = clockOut,
                    totalMinutes = total,
                    overtimeMinutes = overtime,
                    driverName = driverName,
                    approvedByExpat = normalized.approvedByExpat,
                    rejectionNote = rejectionNote,
                    adjustedBy = adjustedBy,
                    adjustedAt = adjustedAt,
                    adjustedReason = adjustedReason,
                    status = normalized.status,
                    approvedByUid = approvedByUid,
                    approvedAtMillis = approvedAtMillis,
                    approvalNote = approvalNote,
                    rejectedByUid = rejectedByUid,
                    rejectedAtMillis = rejectedAtMillis,
                    rejectReason = rejectReason,
                    plannedStartMillis = plannedStart,
                    plannedEndMillis = plannedEnd,
                    planSource = PlanSource.fromRaw(planSourceRaw),
                    deviationFlags = parseDeviationFlags(deviationFlagsRaw),
                    reviewHold = reviewHold ?: false
                )
            }
            DMLog.d(TAG, "getWorkDays() → ${list.size} rows")
            AppResult.Success(list)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            DMLog.e(TAG, "getWorkDays() failed: ${t.message}", t)
            AppResult.Failure(t)
        }
    }
}

// ===== Extensions =====
private suspend fun DocumentReference.getFromServerOnly(timeoutMs: Long): Map<String, Any>? {
    return try {
        withServerReadTimeout(timeoutMs, "getDocumentMap") {
            val snap = get(Source.SERVER).await()
            if (snap.exists()) snap.data else null
        }
    } catch (c: CancellationException) {
        // Always propagate — coroutine lifecycle cancellation must not be swallowed.
        throw c
    } catch (t: Throwable) {
        // Backend failures (permission denied, offline, timeout) must propagate so
        // callers can convert them to AppResult.failure instead of success(null).
        DMLog.e(TAG, "getFromServerOnly() failed: ${t.message}", t)
        throw t
    }
}
