package com.drivemate.showcase.domain.account

/**
 * Permintaan penghapusan akun — gerbang toko aplikasi (PRD §20, Blok 5).
 *
 * ## Kenapa ini PERMINTAAN, bukan penghapusan
 * Apple pedoman 5.1.1(v) dan Play sama-sama menuntut jalur penghapusan akun **di dalam
 * aplikasi**. Yang tidak mereka tuntut adalah bahwa satu ketukan langsung memusnahkan data.
 * Di DriveMate perbedaan itu menentukan:
 *
 * - Akun driver **dibuat dan dimiliki perusahaan**, bukan oleh drivernya. Ia bukan akun
 *   konsumen yang didaftarkan sendiri.
 * - Catatan yang menempel pada akun itu — `attendances`, `attendance_daily`, `workdays` —
 *   adalah **bukti upah**. Sejak 7 September 2026 DriveMate menjadi dasar perhitungan upah
 *   lembur, jadi menghapusnya atas permintaan satu orang berarti menghapus bukti pembayaran
 *   milik perusahaan, dan kemungkinan besar melanggar kewajiban retensi.
 * - `firestore.rules` sudah dibangun di atas premis itu jauh sebelum berkas ini ada: TIDAK
 *   ADA `allow delete` pada `users/{uid}`, dan driver tidak boleh menghapus `attendances`
 *   maupun `workdays` miliknya sendiri (hanya admin/expat). Menambahkan penghapusan mandiri
 *   berarti membatalkan keputusan itu, bukan melengkapinya.
 *
 * Karena itu lapis ini **hanya mencatat permintaan**. Ia tidak memanggil
 * `FirebaseAuth.deleteUser`, tidak menghapus satu dokumen pun, dan sengaja tidak bisa.
 *
 * ## Yang MASIH menunggu keputusan Iskandar
 * Apa yang terjadi SETELAH permintaan masuk — hapus keras, anonimisasi, atau tolak dengan
 * alasan retensi — adalah kebijakan, bukan kode, dan PRD Q5 memang menundanya. Sampai itu
 * diputuskan, teks di layar harus jujur: permintaannya tercatat dan ditinjau, sebagian
 * catatan mungkin dipertahankan karena kewajiban hukum. Menjanjikan "semua data Anda
 * dihapus" sebelum kebijakannya ada adalah klaim yang tidak bisa ditepati aplikasinya.
 *
 * Fungsi di berkas ini MURNI: nol I/O, nol pembacaan jam sistem.
 */

/** Nama field Firestore untuk `deletion_requests/{uid}`. Dipakai bersama oleh rules dan klien. */
object AccountDeletionFields {
    const val COLLECTION: String = "deletion_requests"
    const val UID: String = "uid"
    const val STATUS: String = "status"
    const val REASON: String = "reason"
    const val REQUESTED_AT: String = "requestedAtMillis"
    const val HANDLED_BY_UID: String = "handledByUid"
    const val HANDLED_AT: String = "handledAtMillis"
    const val HANDLED_NOTE: String = "handledNote"
}

/** Batas panjang alasan yang ditulis pengguna. Sama dengan yang dijaga rules. */
const val ACCOUNT_DELETION_REASON_MAX_LENGTH: Int = 500

/**
 * Status permintaan.
 *
 * Sengaja hanya tiga. Status yang lebih halus ("sedang ditinjau", "menunggu HR") menggoda
 * untuk ditambahkan, tapi setiap status baru adalah janji kepada pengguna tentang proses yang
 * belum diputuskan.
 */
enum class AccountDeletionStatus(val code: String) {
    PENDING("pending"),
    COMPLETED("completed"),
    REJECTED("rejected");

    companion object {
        /**
         * Fail-closed: kode yang tidak dikenal menjadi `null`, bukan diam-diam [PENDING].
         *
         * Dokumen berstatus asing lebih baik tampil sebagai "tidak diketahui" daripada
         * tampil sebagai permintaan yang masih menunggu — yang terakhir membuat admin
         * mengira ada pekerjaan yang belum selesai, atau sebaliknya menutup yang belum.
         */
        fun fromCode(raw: String?): AccountDeletionStatus? =
            entries.firstOrNull { it.code == raw?.trim()?.lowercase() }
    }
}

/** Satu permintaan penghapusan akun. Doc-id-nya adalah [uid] pemohon. */
data class AccountDeletionRequest(
    val uid: String,
    val status: AccountDeletionStatus,
    val requestedAtMillis: Long,
    val reason: String? = null,
    val handledByUid: String? = null,
    val handledAtMillis: Long? = null,
    val handledNote: String? = null,
) {
    /** Permintaan yang masih menunggu tindakan manusia. */
    val isOpen: Boolean get() = status == AccountDeletionStatus.PENDING
}

/** Kenapa sebuah permintaan ditolak sebelum menyentuh jaringan. */
enum class AccountDeletionError {
    /** Tidak ada sesi login — permintaan hanya boleh untuk diri sendiri. */
    NOT_SIGNED_IN,

    /** Sudah ada permintaan yang menunggu; mengirim lagi tidak menambah apa pun. */
    ALREADY_PENDING,

    /** Alasan melebihi [ACCOUNT_DELETION_REASON_MAX_LENGTH] karakter. */
    REASON_TOO_LONG,
}

/** Hasil evaluasi sebelum menulis. */
sealed interface AccountDeletionEvaluation {
    /** Boleh dikirim; [payloadReason] sudah dirapikan (kosong menjadi `null`). */
    data class Allowed(val payloadReason: String?) : AccountDeletionEvaluation

    data class Rejected(val error: AccountDeletionError) : AccountDeletionEvaluation
}

/**
 * Aturan sebelum sebuah permintaan boleh ditulis.
 *
 * Dipisah dari repository supaya bisa diuji tanpa Firestore, dan supaya kedua platform
 * memakai aturan yang sama persis alih-alih menuliskannya ulang di Swift.
 *
 * @param currentUid uid sesi aktif, `null` bila belum login.
 * @param existing permintaan yang sudah ada untuk uid itu, `null` bila belum pernah ada.
 * @param reason teks bebas dari pengguna; boleh kosong.
 */
fun evaluateAccountDeletionRequest(
    currentUid: String?,
    existing: AccountDeletionRequest?,
    reason: String?,
): AccountDeletionEvaluation {
    if (currentUid.isNullOrBlank()) {
        return AccountDeletionEvaluation.Rejected(AccountDeletionError.NOT_SIGNED_IN)
    }
    if (existing != null && existing.isOpen) {
        return AccountDeletionEvaluation.Rejected(AccountDeletionError.ALREADY_PENDING)
    }
    val trimmed = reason?.trim().orEmpty()
    if (trimmed.length > ACCOUNT_DELETION_REASON_MAX_LENGTH) {
        return AccountDeletionEvaluation.Rejected(AccountDeletionError.REASON_TOO_LONG)
    }
    return AccountDeletionEvaluation.Allowed(payloadReason = trimmed.ifEmpty { null })
}

/**
 * Payload untuk `deletion_requests/{uid}`.
 *
 * `reason` DIHILANGKAN seluruhnya saat kosong, bukan ditulis sebagai string kosong — rules
 * memakai `hasOnly` atas daftar kunci, dan kunci bernilai kosong tetap menghabiskan jatah
 * kunci sekaligus membuat pembaca mengira pengguna mengetik sesuatu lalu menghapusnya.
 *
 * Permintaan ulang setelah ditolak menimpa dokumen yang sama (doc-id = uid): riwayat
 * penolakan sebelumnya TIDAK disimpan. Itu keputusan sadar — menyimpan riwayat berarti
 * sub-koleksi dan aturan retensinya sendiri, sementara yang dibutuhkan gerbang toko hanyalah
 * status permintaan yang sedang berjalan.
 */
fun buildAccountDeletionRequestPayload(
    uid: String,
    nowMillis: Long,
    reason: String?,
): Map<String, Any> = buildMap {
    put(AccountDeletionFields.UID, uid)
    put(AccountDeletionFields.STATUS, AccountDeletionStatus.PENDING.code)
    put(AccountDeletionFields.REQUESTED_AT, nowMillis)
    reason?.trim()?.takeIf { it.isNotEmpty() }?.let { put(AccountDeletionFields.REASON, it) }
}

/** Memetakan dokumen Firestore menjadi model; `null` bila bentuknya tidak dikenali. */
fun mapAccountDeletionRequest(data: Map<*, *>?): AccountDeletionRequest? {
    if (data == null) return null
    val uid = (data[AccountDeletionFields.UID] as? String)?.takeIf { it.isNotBlank() } ?: return null
    val status = AccountDeletionStatus.fromCode(data[AccountDeletionFields.STATUS] as? String)
        ?: return null
    val requestedAt = (data[AccountDeletionFields.REQUESTED_AT] as? Number)?.toLong() ?: return null
    return AccountDeletionRequest(
        uid = uid,
        status = status,
        requestedAtMillis = requestedAt,
        reason = (data[AccountDeletionFields.REASON] as? String)?.takeIf { it.isNotBlank() },
        handledByUid = (data[AccountDeletionFields.HANDLED_BY_UID] as? String)
            ?.takeIf { it.isNotBlank() },
        handledAtMillis = (data[AccountDeletionFields.HANDLED_AT] as? Number)?.toLong(),
        handledNote = (data[AccountDeletionFields.HANDLED_NOTE] as? String)
            ?.takeIf { it.isNotBlank() },
    )
}
