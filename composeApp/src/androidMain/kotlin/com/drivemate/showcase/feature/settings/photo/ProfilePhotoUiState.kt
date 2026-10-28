package com.drivemate.showcase.feature.settings.photo

import androidx.annotation.StringRes
import com.drivemate.showcase.R

/** Penyebab gagalnya ganti foto, dipetakan ke kalimat yang jelas di UI. */
enum class ProfilePhotoErrorCode(@StringRes val messageRes: Int) {
    /** Ditolak rules — termasuk bila rules foto belum di-deploy. */
    NO_PERMISSION(R.string.driver_profile_photo_error_no_permission),
    NETWORK(R.string.driver_profile_photo_error_network),
    NOT_AN_IMAGE(R.string.driver_profile_photo_error_not_image),
    TOO_LARGE(R.string.driver_profile_photo_error_too_large),
    NO_CAMERA(R.string.driver_profile_photo_error_no_camera),
    /** Tahap PENGOLAHAN (decode/potong/kompres) gagal di perangkat. */
    PROCESSING_FAILED(R.string.driver_profile_photo_error_processing),

    /** Tahap UNGGAH gagal karena sebab selain jaringan/izin. */
    UPLOAD_FAILED(R.string.driver_profile_photo_error_upload),
}

/** State alur ganti foto. Berada di ViewModel, jadi selamat dari rotasi layar. */
sealed interface ProfilePhotoUiState {
    data object Idle : ProfilePhotoUiState

    /** Foto pilihan sedang dipotong dan dikecilkan. */
    data object Processing : ProfilePhotoUiState

    /** Hasil olahan siap dipratinjau; belum dikirim. [jpeg] sudah <= batas ukuran. */
    class Preview(val jpeg: ByteArray) : ProfilePhotoUiState

    data object Uploading : ProfilePhotoUiState

    data class Error(val code: ProfilePhotoErrorCode) : ProfilePhotoUiState
}
