package com.drivemate.showcase.domain.profile

import com.drivemate.showcase.domain.error.UserPhotoError

/**
 * Foto profil driver disimpan sebagai satu dokumen kecil `user_photos/{uid}`
 * (bukan Firebase Storage; lihat keputusan PR-E).
 *
 * Nama field ADALAH kontrak dengan `firestore.rules` dan jangan diubah sepihak.
 */
object UserPhotoFields {
    const val COLLECTION = "user_photos"
    const val JPEG = "jpeg"
    const val UPDATED_AT_MILLIS = "updatedAtMillis"
}

/**
 * Batas keras ukuran JPEG. Ditegakkan tiga kali: pemroses gambar di klien, use case
 * simpan, dan `firestore.rules` (`jpeg.size() <= 150000`). Mengubah angka ini berarti
 * mengubah ketiganya.
 */
const val PROFILE_PHOTO_MAX_BYTES: Int = 150_000

/** Sisi foto hasil olahan (piksel). Persegi. */
const val PROFILE_PHOTO_SIDE_PX: Int = 256

/** `null` bila ukuran boleh disimpan; selain itu penyebab penolakannya. */
fun validateProfilePhotoSize(sizeBytes: Int): UserPhotoError? = when {
    sizeBytes <= 0 -> UserPhotoError.Empty
    sizeBytes > PROFILE_PHOTO_MAX_BYTES -> UserPhotoError.TooLarge
    else -> null
}
