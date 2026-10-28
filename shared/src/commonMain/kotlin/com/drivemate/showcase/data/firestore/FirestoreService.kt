package com.drivemate.showcase.data.firestore

import com.drivemate.showcase.core.model.WorkDay
import com.drivemate.showcase.core.util.AppResult

interface FirestoreService {
    suspend fun getUserTypeByUid(uid: String): AppResult<String>
    suspend fun pingServer(uid: String): AppResult<Unit> // opsional tapi membantu debug
    suspend fun getWorkDays(driverUid: String): AppResult<List<WorkDay>>
    suspend fun getAttendances(driverUid: String): AppResult<List<Map<String, Any>>>
    suspend fun getUserTypeCurrent(): AppResult<String> =
        getUserTypeByUid(currentUserUidOrThrow())
    // write
    suspend fun setDocument(path: String, data: Map<String, Any?>, merge: Boolean)
    suspend fun addDocument(collection: String, data: Map<String, Any?>)
    suspend fun deleteDocument(path: String)

    // read/query
    suspend fun query(
        collection: String,
        where: List<Pair<String, Any>>,
        orderBy: String? = null,
        limit: Int? = null
    ): List<FsDoc>

    // 🔹 NEW: get 1 dokumen sebagai Map (KMM-safe)
    suspend fun getDocumentMap(collection: String, documentId: String): Map<String, Any?>?

    // realtime
    fun listenCollection(
        collection: String,
        onChange: (List<FsDoc>) -> Unit
    ): ListenerRegistration

    /**
     * Realtime listener dengan orderBy (+ optional limit).
     */
    fun listenCollectionOrderBy(
        collection: String,
        orderByField: String,
        orderDesc: Boolean = true,
        limit: Int? = null,
        onChange: (List<FsDoc>) -> Unit,
        onError: ((Throwable) -> Unit)? = null
    ): ListenerRegistration

    /**
     * Realtime listener dengan filter whereEqualTo + orderBy (+ optional limit).
     * Digunakan untuk kasus seperti:
     * - Riwayat lokasi 1 driver: where driverUid == X, orderBy updatedAtMillis DESC, limit 1
     *
     * @param collection Nama koleksi (mis. "locations")
     * @param whereField Field untuk whereEqualTo (mis. "driverUid")
     * @param equalTo Nilai untuk whereEqualTo
     * @param orderByField Field untuk pengurutan (mis. "updatedAtMillis")
     * @param orderDesc Urutan menurun (DESC) jika true; default true
     * @param limit Batas jumlah dokumen (opsional)
     * @param onChange Callback pada update snapshot; berisi daftar dokumen (FsDoc)
     * @return Handle listener yang bisa di-remove di layer platform
     */
    fun listenCollectionWhereOrderBy(
        collection: String,
        whereField: String,
        equalTo: Any,
        orderByField: String,
        orderDesc: Boolean = true,
        limit: Int? = null,
        onChange: (List<FsDoc>) -> Unit
    ): ListenerRegistration

    fun currentUserEmailOrThrow(): String
    fun currentUserUidOrThrow(): String
}
