package com.drivemate.showcase.domain.error

/**
 * Galat foto profil. Dipisah per penyebab supaya UI bisa menjawab dengan kalimat yang
 * benar (mis. "hubungi Admin" untuk [PermissionDenied]) tanpa membaca pesan mentah SDK.
 */
sealed class UserPhotoError(
    message: String,
    cause: Throwable? = null,
) : Throwable(message, cause) {
    data object NotSignedIn : UserPhotoError("not_signed_in")

    /** Foto kosong (0 byte). */
    data object Empty : UserPhotoError("empty")

    /** Melebihi batas ukuran; rules menolak hal yang sama. */
    data object TooLarge : UserPhotoError("too_large")

    /** Ditolak rules — termasuk bila rules foto belum di-deploy. */
    data object PermissionDenied : UserPhotoError("permission_denied")

    data object Network : UserPhotoError("network")

    class Unknown(cause: Throwable? = null) : UserPhotoError("unknown", cause)
}
