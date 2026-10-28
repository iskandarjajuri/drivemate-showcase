package com.drivemate.showcase.feature.settings.photo

import android.content.Context
import java.io.File

/**
 * Salinan lokal foto profil di penyimpanan internal aplikasi (`filesDir/profile_photo/<uid>.jpg`),
 * tanpa izin penyimpanan. Foto tampil dari sini lebih dulu; unggah ke Firestore menyusul sebagai
 * upaya terbaik. Berkas `<uid>.pending` menandai foto yang belum tersinkron.
 *
 * Dikunci per uid dan DIHAPUS SELURUHNYA saat sign-out ([clearAll]) supaya foto tidak bocor
 * antar-akun. BLOKIR (I/O): panggil dari dispatcher latar.
 */
class ProfilePhotoLocalStore(private val dir: File) {

    constructor(context: Context) : this(File(context.filesDir, DIR_NAME))

    fun read(uid: String): ByteArray? = runCatching {
        photoFile(uid).takeIf { it.isFile && it.length() > 0 }?.readBytes()
    }.getOrNull()

    fun isPending(uid: String): Boolean = pendingFile(uid).exists()

    /** Menulis atomik (berkas sementara lalu rename) supaya tidak pernah meninggalkan JPEG separuh. */
    fun write(uid: String, jpeg: ByteArray, pending: Boolean) {
        dir.mkdirs()
        val tmp = File(dir, "${safe(uid)}.tmp")
        tmp.writeBytes(jpeg)
        if (!tmp.renameTo(photoFile(uid))) {
            photoFile(uid).writeBytes(jpeg)
            tmp.delete()
        }
        if (pending) pendingFile(uid).writeText("1") else pendingFile(uid).delete()
    }

    fun markSynced(uid: String) {
        pendingFile(uid).delete()
    }

    fun delete(uid: String) {
        photoFile(uid).delete()
        pendingFile(uid).delete()
    }

    fun clearAll() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun photoFile(uid: String) = File(dir, "${safe(uid)}.jpg")
    private fun pendingFile(uid: String) = File(dir, "${safe(uid)}.pending")
    private fun safe(uid: String) = uid.replace(Regex("[^A-Za-z0-9_-]"), "_")

    private companion object {
        const val DIR_NAME = "profile_photo"
    }
}

/** Status sinkron foto ke Firestore, ditampilkan sebagai chip di Settings. */
enum class PhotoSyncState { None, Synced, Syncing, Unsynced }
