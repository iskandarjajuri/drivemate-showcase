package com.drivemate.showcase.bridge

import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.domain.account.AccountDeletionRequest
import com.drivemate.showcase.domain.usecase.account.GetAccountDeletionRequestUseCase
import com.drivemate.showcase.domain.usecase.account.RequestAccountDeletionUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Layar Akun iOS — jalur penghapusan akun di dalam aplikasi (Blok 5, PRD §20).
 *
 * Padanan `AccountSettingsViewModel` Android. Yang **tidak** dicermin adalah pemilihan
 * string-nya: Android memetakan galat ke `R.string.*` di ViewModel, sedangkan di sini bridge
 * menyerahkan [IOSAccountDeletionSubmitResult.invalidErrorRaw] dan Swift-lah yang memilih
 * kunci `L10n`. commonMain tidak punya akses `Localizable.strings`, dan mengarangnya di Kotlin
 * berarti teks bahasa Inggris keras di dalam shared — pola yang sama dipakai label CSV rekap
 * bulanan.
 *
 * ## Nol jam sistem dibaca di Swift
 * `nowMillis` diputuskan di sini lewat [TimeUtil.nowEpochMillis], dan seluruh label tanggal
 * dirakit di Kotlin dengan [IOSDriverTimezone]. Konsisten dengan bridge iOS lain, dan alasannya
 * praktis: stempel waktu yang ditulis ke `deletion_requests` harus sezona dengan yang
 * ditampilkan kembali, kalau tidak permintaan yang dikirim pukul 00:30 WIB terbaca sebagai
 * kemarin di layar yang menampilkannya.
 */
internal class IOSAccountBridge(
    private val scope: CoroutineScope,
    private val getRequest: GetAccountDeletionRequestUseCase,
    private val requestDeletion: RequestAccountDeletionUseCase,
    private val nowMillis: () -> Long = { TimeUtil.nowEpochMillis() },
) {

    /**
     * Muat permintaan yang sedang berjalan, bila ada.
     *
     * Hasil dibungkus [IOSAccountDeletionStateSnapshot] alih-alih mengembalikan
     * `(snapshot?, error?)` langsung: tanpa pembungkus itu, "tidak ada permintaan" dan "gagal
     * memuat" sama-sama tiba di Swift sebagai `(nil, nil)`/`(nil, error)` yang mudah
     * dicampur, dan `docs/PRD_DRIVEMATE.md` §9 menuntut ketiga keadaan (kosong sungguhan,
     * belum dimuat, gagal memuat) **berbeda secara visual**. Pembungkus ini yang membuat
     * pembedaan itu mungkin di sisi UI.
     */
    fun load(completion: (IOSAccountDeletionStateSnapshot?, String?) -> Unit) {
        scope.launch {
            when (val res = getRequest()) {
                is AppResult.Success -> completion(
                    IOSAccountDeletionStateSnapshot(request = res.data?.toIosSnapshot()),
                    null
                )
                is AppResult.Failure -> completion(
                    null,
                    res.throwable.message ?: "Failed to load account status."
                )
            }
        }
    }

    /**
     * Kirim permintaan penghapusan akun untuk pengguna yang sedang login.
     *
     * `uid` sengaja TIDAK diterima sebagai parameter — [RequestAccountDeletionUseCase]
     * mengambilnya dari sesi. Menerimanya di sini akan membuat Swift bisa mengirim uid orang
     * lain tanpa melanggar tipe apa pun.
     */
    fun submit(reason: String?, completion: (IOSAccountDeletionSubmitResult) -> Unit) {
        scope.launch {
            when (val outcome = requestDeletion(reason, nowMillis())) {
                is RequestAccountDeletionUseCase.Outcome.Submitted -> completion(
                    IOSAccountDeletionSubmitResult(submitted = true)
                )
                is RequestAccountDeletionUseCase.Outcome.Invalid -> completion(
                    IOSAccountDeletionSubmitResult(invalidErrorRaw = outcome.error.name)
                )
                is RequestAccountDeletionUseCase.Outcome.Failed -> completion(
                    IOSAccountDeletionSubmitResult(
                        failureMessage = outcome.throwable.message
                            ?: "Failed to send the request."
                    )
                )
            }
        }
    }
}

/** Pembungkus keadaan layar Akun. `request == null` berarti belum pernah ada permintaan. */
data class IOSAccountDeletionStateSnapshot(
    val request: IOSAccountDeletionSnapshot?
)

/**
 * Satu permintaan, siap dirender.
 *
 * `statusRaw` membawa nama enum (`PENDING`/`COMPLETED`/`REJECTED`), mengikuti konvensi
 * `*Raw` bridge lain. Swift memetakannya ke teks; ia TIDAK boleh menyimpulkan status dari
 * ada-tidaknya `handledAtLabel`, karena dokumen yang ditangani tanpa stempel waktu tetap
 * mungkin ada dan akan terbaca sebagai masih menunggu.
 */
data class IOSAccountDeletionSnapshot(
    val statusRaw: String,
    /** Masih menunggu tindakan manusia — tombol kirim harus disembunyikan saat `true`. */
    val isOpen: Boolean,
    val requestedAtLabel: String,
    val handledAtLabel: String?,
    val handledNote: String?
)

/**
 * Hasil pengiriman.
 *
 * Dibawa sebagai satu tipe, bukan tiga parameter lambda nullable: `Boolean` di dalam tipe
 * lambda dibungkus jadi `KotlinBoolean` saat menyeberang ke Swift, sedangkan sebagai
 * **properti data class** ia tetap `Bool` yang wajar dipakai.
 */
data class IOSAccountDeletionSubmitResult(
    val submitted: Boolean = false,
    /** Nama `AccountDeletionError` bila ditolak sebelum menyentuh jaringan. */
    val invalidErrorRaw: String? = null,
    /** Pesan mentah bila penulisan gagal (izin, jaringan). */
    val failureMessage: String? = null
)

private fun AccountDeletionRequest.toIosSnapshot(): IOSAccountDeletionSnapshot =
    IOSAccountDeletionSnapshot(
        statusRaw = status.name,
        isOpen = isOpen,
        requestedAtLabel = formatDateTimeLabel(requestedAtMillis),
        handledAtLabel = handledAtMillis?.let { formatDateTimeLabel(it) },
        handledNote = handledNote
    )

/**
 * "YYYY/MM/DD HH:MM" di Asia/Jakarta — bentuk tanggal yang sama dipakai layar iOS lain
 * (`formatYmdFromEpochDay` memakai garis miring, bukan tanda hubung).
 *
 * Dirakit dari dua helper yang sudah ada alih-alih memformat sendiri, dan `.toInt()` di sini
 * bukan basa-basi: `epochDayOf` mengembalikan `Long` sedangkan `formatYmdFromEpochDay`
 * menerima `Int` hari kalender. Menyerahkan nilai **milis** ke fungsi kedua itu adalah
 * kesalahan yang tidak tertangkap compiler kalau tipenya kebetulan cocok, dan hasilnya
 * tanggal di tahun yang jauh — bukan galat, cuma angka yang salah di layar.
 */
private fun formatDateTimeLabel(millis: Long): String {
    val epochDay = TimeUtil.epochDayOf(millis, IOSDriverTimezone).toInt()
    val date = TimeUtil.formatYmdFromEpochDay(epochDay, IOSDriverTimezone)
    val time = TimeUtil.formatHm(millis, IOSDriverTimezone)
    return "$date $time"
}
