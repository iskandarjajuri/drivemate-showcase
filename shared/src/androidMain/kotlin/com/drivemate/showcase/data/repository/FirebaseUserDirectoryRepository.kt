package com.drivemate.showcase.data.repository

import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.model.ChatContact
import com.drivemate.showcase.core.model.DriverProfile
import com.drivemate.showcase.core.model.Role
import com.drivemate.showcase.core.model.SopStatus
import com.drivemate.showcase.core.model.toFirestore
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.data.FirestoreConstants
import com.drivemate.showcase.data.firestore.FirestoreService
import com.drivemate.showcase.domain.repository.UserDirectoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout

class FirebaseUserDirectoryRepository(
    private val driverNameFallback: String,
    private val fs: FirestoreService
) : UserDirectoryRepository {

    private companion object { const val TAG = "DM/UserDirectory" }

    override suspend fun getUserDisplayName(uid: String): AppResult<String?> = try {
        val doc = withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
            fs.getDocumentMap("users", uid)
        }
        val name = (doc?.get("name") as? String)?.takeIf { it.isNotBlank() }
        val email = (doc?.get("email") as? String)
        val display = name
            ?: email?.substringBefore("@").takeIf { !it.isNullOrBlank() }
            ?: driverNameFallback
        AppResult.success(display)
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        DMLog.e(TAG, "getUserDisplayName failed: ${t.message}", t)
        AppResult.failure(t)
    }

    override suspend fun getAllDriverDisplayNames(): AppResult<Map<String, String>> = try {
        // TODO_VERIFY: this is intentionally unbounded until user directory pagination is designed.
        val docs = withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
            fs.query(
                collection = "users",
                where = listOf("type" to "driver"),
                orderBy = null,
                limit = null
            )
        }
        val map = buildMap {
            docs.forEach { d ->
                val m = d.data ?: return@forEach
                val uid = d.id
                val name = (m["name"] as? String)?.takeIf { it.isNotBlank() }
                val email = (m["email"] as? String)
                val display = name
                    ?: email?.substringBefore("@")?.takeIf { it.isNotBlank() }
                    ?: driverNameFallback
                put(uid, display)
            }
        }
        AppResult.success(map)
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        DMLog.e(TAG, "getAllDriverDisplayNames failed: ${t.message}", t)
        AppResult.failure(t)
    }

    override suspend fun getDriverSopStatuses(): AppResult<List<SopStatus>> = try {
        // TODO_VERIFY: this is intentionally unbounded until SOP status pagination is designed.
        val docs = withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
            fs.query(
                collection = "users",
                where = listOf("type" to "driver"),
                orderBy = null,
                limit = null
            )
        }

        val list = docs.mapNotNull { d ->
            val m = d.data ?: return@mapNotNull null
            val uid = d.id
            val name = (m["name"] as? String)?.takeIf { it.isNotBlank() }
            val email = (m["email"] as? String)
            val display = name
                ?: email?.substringBefore("@").takeIf { !it.isNullOrBlank() }
                ?: driverNameFallback
            val sopAccepted = (m["sopAccepted"] as? Boolean) ?: false
            val sopAcceptedAt = (m["sopAcceptedAtMillis"] as? Number)?.toLong()

            SopStatus(
                uid = uid,
                name = display,
                sopAccepted = sopAccepted,
                sopAcceptedAtMillis = sopAcceptedAt
            )
        }.sortedBy { it.name ?: "" }

        AppResult.success(list)
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        DMLog.e(TAG, "getDriverSopStatuses failed: ${t.message}", t)
        AppResult.failure(t)
    }

    override suspend fun getDriverSopStatus(uid: String): AppResult<SopStatus?> = try {
        val m = withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
            fs.getDocumentMap("users", uid)
        } ?: return AppResult.success(null)
        val name = (m["name"] as? String)?.takeIf { it.isNotBlank() }
        val email = (m["email"] as? String)
        val display = name
            ?: email?.substringBefore("@").takeIf { !it.isNullOrBlank() }
            ?: driverNameFallback
        val sopAccepted = (m["sopAccepted"] as? Boolean) ?: false
        val sopAcceptedAt = (m["sopAcceptedAtMillis"] as? Number)?.toLong()
        AppResult.success(
            SopStatus(
                uid = uid,
                name = display,
                sopAccepted = sopAccepted,
                sopAcceptedAtMillis = sopAcceptedAt
            )
        )
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        DMLog.e(TAG, "getDriverSopStatus failed: ${t.message}", t)
        AppResult.failure(t)
    }

    override suspend fun acceptSop(uid: String, acceptedAtMillis: Long): AppResult<Unit> = try {
        withTimeout(FirestoreConstants.WRITE_TIMEOUT_MS) {
            fs.setDocument(
                path = "users/$uid",
                data = mapOf(
                    "sopAccepted" to true,
                    "sopAcceptedAtMillis" to acceptedAtMillis
                ),
                merge = true
            )
        }
        AppResult.success(Unit)
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        DMLog.e(TAG, "acceptSop failed: ${t.message}", t)
        AppResult.failure(t)
    }

    override suspend fun getManagedDrivers(managerUid: String): AppResult<List<DriverProfile>> = try {
        if (managerUid.isBlank()) {
            val message = "getManagedDrivers called with blank managerUid"
            DMLog.w(TAG, message)
            assert(false) { message }
            return AppResult.failure(IllegalArgumentException(message))
        }
        // TODO_VERIFY: this is intentionally unbounded until managed-driver pagination is designed.
        val docs = withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
            fs.query(
                collection = "users",
                where = listOf(
                    "type" to "driver",
                    "managerUid" to managerUid
                ),
                orderBy = null,
                limit = null
            )
        }
        val list = docs.mapNotNull { d ->
            val m = d.data ?: return@mapNotNull null
            val uid = d.id
            val name = (m["name"] as? String)?.takeIf { it.isNotBlank() }
            val email = (m["email"] as? String)
            DriverProfile(
                uid = uid,
                name = name,
                email = email
            )
        }
        AppResult.success(list)
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        DMLog.e(TAG, "getManagedDrivers failed: ${t.message}", t)
        AppResult.failure(t)
    }

    override suspend fun getUsersByRoles(roles: Set<Role>): AppResult<List<ChatContact>> = try {
        if (roles.isEmpty()) return AppResult.success(emptyList())
        val contacts = roles.flatMap { role ->
            // TODO_VERIFY: this is intentionally unbounded until chat contact pagination is designed.
            withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
                fs.query(
                    collection = "users",
                    where = listOf("type" to role.toFirestore()),
                    orderBy = null,
                    limit = null
                )
            }.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                val name = (data["name"] as? String)?.takeIf { it.isNotBlank() }
                val email = (data["email"] as? String)?.takeIf { it.isNotBlank() }
                val displayName = name
                    ?: email?.substringBefore("@")?.takeIf { it.isNotBlank() }
                    ?: driverNameFallback
                ChatContact(
                    uid = doc.id,
                    displayName = displayName,
                    role = role,
                    email = email
                )
            }
        }
            .distinctBy { it.uid }
            .sortedWith(
                compareBy<ChatContact> { it.displayName.lowercase() }
                    .thenBy { it.role.name }
            )
        AppResult.success(contacts)
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        DMLog.e(TAG, "getUsersByRoles failed: ${t.message}", t)
        AppResult.failure(t)
    }
}
