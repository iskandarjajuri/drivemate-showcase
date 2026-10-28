package com.drivemate.showcase.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.model.ChatThreadSummary
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.addHiddenId
import com.drivemate.showcase.core.util.parseThreadSummary
import com.drivemate.showcase.core.util.sortThreads
import com.drivemate.showcase.data.firestore.FirestoreService
import com.drivemate.showcase.domain.repository.ChatThreadRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Thread, status baca, dan hapus pesan di atas Firebase SDK langsung (butuh `array-contains` dan
 * listener dokumen yang tidak ada di [FirestoreService]). Tidak mengubah interface commonMain
 * yang diimplementasi iOS.
 */
class FirebaseChatThreadRepository(
    private val db: FirebaseFirestore,
    private val fs: FirestoreService
) : ChatThreadRepository {

    override fun observeThreads(): Flow<AppResult<List<ChatThreadSummary>>> = callbackFlow {
        val myUid = runCatching { fs.currentUserUidOrThrow() }.getOrElse { t ->
            trySend(AppResult.failure(t))
            close(t)
            return@callbackFlow
        }
        // Tanpa orderBy supaya tidak butuh indeks komposit; diurutkan di klien.
        val registration = db.collection("chat_threads")
            .whereArrayContains("participantUids", myUid)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    DMLog.e(TAG, "observeThreads gagal: koleksi=chat_threads kode=${(error as? FirebaseFirestoreException)?.code ?: error.javaClass.simpleName}")
                    trySend(AppResult.failure(error))
                    return@addSnapshotListener
                }
                val threads = snap?.documents.orEmpty()
                    .mapNotNull { parseThreadSummary(it.id, it.data, myUid) }
                trySend(AppResult.success(sortThreads(threads)))
            }
        awaitClose { registration.remove() }
    }

    override suspend fun markRead(threadId: String, nowMillis: Long): AppResult<Unit> = guarded {
        val myUid = fs.currentUserUidOrThrow()
        // merge: `hiddenMessageIds` tidak ikut tertimpa.
        readsDoc(threadId, myUid).set(mapOf("lastReadAtMillis" to nowMillis), SetOptions.merge()).await()
    }

    override fun observePartnerReadAt(threadId: String, partnerUid: String): Flow<Long?> = callbackFlow {
        val registration = readsDoc(threadId, partnerUid).addSnapshotListener { snap, error ->
            if (error != null) {
                trySend(null)
                return@addSnapshotListener
            }
            trySend((snap?.get("lastReadAtMillis") as? Number)?.toLong())
        }
        awaitClose { registration.remove() }
    }

    override fun observeHiddenIds(threadId: String): Flow<Set<String>> = callbackFlow {
        val myUid = runCatching { fs.currentUserUidOrThrow() }.getOrElse {
            trySend(emptySet())
            close(it)
            return@callbackFlow
        }
        val registration = readsDoc(threadId, myUid).addSnapshotListener { snap, error ->
            if (error != null) {
                trySend(emptySet())
                return@addSnapshotListener
            }
            val ids = (snap?.get("hiddenMessageIds") as? List<*>)?.filterIsInstance<String>().orEmpty()
            trySend(ids.toSet())
        }
        awaitClose { registration.remove() }
    }

    override suspend fun deleteForEveryone(threadId: String, messageId: String, nowMillis: Long): AppResult<Unit> =
        guarded {
            // HANYA dua field ini yang boleh diubah klien (rules); server membersihkan isi.
            db.collection("chat_threads").document(threadId).collection("messages").document(messageId)
                .update(mapOf("deleted" to true, "deletedAtMillis" to nowMillis))
                .await()
        }

    override suspend fun hideForMe(threadId: String, messageId: String): AppResult<Unit> = guarded {
        val myUid = fs.currentUserUidOrThrow()
        val ref = readsDoc(threadId, myUid)
        val current = (ref.get().await().get("hiddenMessageIds") as? List<*>)
            ?.filterIsInstance<String>().orEmpty()
        val next = addHiddenId(current, messageId)
        if (next != current) {
            ref.set(mapOf("hiddenMessageIds" to next), SetOptions.merge()).await()
        }
    }

    private fun readsDoc(threadId: String, uid: String) =
        db.collection("chat_threads").document(threadId).collection("reads").document(uid)

    private suspend fun guarded(block: suspend () -> Unit): AppResult<Unit> = try {
        block()
        AppResult.success(Unit)
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        DMLog.e(TAG, "operasi chat gagal: ${t.javaClass.simpleName}")
        AppResult.failure(t)
    }

    private companion object {
        const val TAG = "DM/ChatThread"
    }
}
