package com.drivemate.showcase.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.model.ChatContact
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.parseDirectoryContact
import com.drivemate.showcase.core.util.sortContacts
import com.drivemate.showcase.data.firestore.FirestoreService
import com.drivemate.showcase.domain.repository.ChatDirectoryRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Kontak chat dari `user_directory` (disalin dari `users` oleh Cloud Function; tanpa email).
 *
 * Memakai Firebase SDK langsung (bukan `FirestoreService.listenCollection`) karena yang terakhir
 * menelan galat listener sehingga flow tak pernah emit saat PERMISSION_DENIED -> layar menunggu
 * selamanya. Di sini galat diteruskan sebagai [AppResult.Failure].
 */
class FirebaseChatDirectoryRepository(
    private val db: FirebaseFirestore,
    private val fs: FirestoreService
) : ChatDirectoryRepository {

    override fun observeDirectory(): Flow<AppResult<List<ChatContact>>> = callbackFlow {
        val myUid = runCatching { fs.currentUserUidOrThrow() }.getOrElse { t ->
            trySend(AppResult.failure(t))
            close()
            return@callbackFlow
        }
        val registration = db.collection("user_directory").addSnapshotListener { snap, error ->
            if (error != null) {
                DMLog.e(TAG, "observeDirectory gagal: ${error.javaClass.simpleName} ${(error as? com.google.firebase.firestore.FirebaseFirestoreException)?.code}")
                trySend(AppResult.failure(error))
                return@addSnapshotListener
            }
            val contacts = snap?.documents.orEmpty().mapNotNull { parseDirectoryContact(it.id, it.data, myUid) }
            trySend(AppResult.success(sortContacts(contacts)))
        }
        awaitClose { registration.remove() }
    }

    private companion object {
        const val TAG = "DM/ChatDirectory"
    }
}
