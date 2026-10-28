package com.drivemate.showcase.feature.settings.photo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Cache memori foto profil yang dipakai bersama Settings dan Home, supaya foto yang baru
 * diganti langsung tampil di Home tanpa mengambil ulang dari Firestore.
 *
 * Dikunci per [Entry.uid]: isi milik akun lain TIDAK PERNAH dikembalikan, jadi pergantian
 * akun di perangkat yang sama tidak bisa menampilkan foto orang sebelumnya.
 */
class ProfilePhotoStore {

    /** [jpeg] `null` = sudah dicek dan memang belum ada foto (berbeda dari belum dimuat). */
    class Entry(val uid: String, val jpeg: ByteArray?)

    private val _entry = MutableStateFlow<Entry?>(null)
    val entry: StateFlow<Entry?> = _entry.asStateFlow()

    fun put(uid: String, jpeg: ByteArray?) {
        _entry.value = Entry(uid, jpeg)
    }

    /** `true` bila foto [uid] sudah dimuat (ada atau tidak ada). */
    fun isLoaded(uid: String): Boolean = _entry.value?.uid == uid

    fun clear() {
        _entry.value = null
    }
}
