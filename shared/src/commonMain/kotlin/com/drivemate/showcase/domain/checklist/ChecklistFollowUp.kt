package com.drivemate.showcase.domain.checklist

/**
 * Tindak lanjut temuan checklist — C3 pada PRD §7 (Blok 4.1).
 *
 * Sebelum ini, `hasIssue` adalah jalan buntu: aplikasi memberi tahu ada temuan, lalu tidak
 * punya cara apa pun untuk mencatat bahwa temuan itu ditangani. Akibatnya baris pengecualian
 * yang sama muncul terus sampai dokumen hariannya berlalu, dan tidak ada tempat menuliskan
 * siapa yang menanganinya atau apa yang dikerjakan.
 *
 * ## Kode status DISIMPAN, label DITERJEMAHKAN
 * Nilai yang masuk Firestore adalah [STATUS_NEW]/[STATUS_REVIEWED]/[STATUS_CLOSED] huruf
 * kecil — bukan teks yang tampil di layar. Repo ini pernah kena tepat bug ini: iOS menulis
 * label bahaya yang sudah dilokalkan sementara Android menulis kode stabil, jadi nilai di
 * Firestore ikut berubah mengikuti bahasa perangkat driver.
 *
 * ## Mesin statusnya MAJU, dan tiap tepi punya alasannya
 * - `new → reviewed` — seseorang melihatnya. Butuh **penanggung jawab**: temuan yang
 *   "ditinjau" tanpa pemilik tidak bergerak ke mana pun.
 * - `reviewed → closed` — butuh **catatan penutup**. Menutup temuan tanpa menuliskan apa
 *   yang dikerjakan menghapus satu-satunya nilai jejaknya.
 * - `new → closed` **DITOLAK.** Menutup temuan yang belum pernah ditinjau adalah persis
 *   bentuk penyelesaian di atas kertas yang mekanisme ini ada untuk mencegah.
 * - `closed → reviewed` **DIIZINKAN, dan butuh catatan.** Kesalahan ketik harus bisa
 *   dikoreksi; yang tidak boleh adalah koreksi yang tak berjejak. Karena itu tiap
 *   perpindahan MASUK maupun KELUAR dari `closed` membawa catatan.
 * - `→ new` **DITOLAK** dari status apa pun. Mengembalikan temuan ke "belum tersentuh"
 *   menghapus kenyataan bahwa ia pernah ditangani.
 *
 * Kekambuhan **tidak** dimodelkan sebagai membuka ulang: dokumen checklist adalah satu per
 * driver per hari (`{uid}_{epochDay}`), jadi rem yang bermasalah lagi besok melahirkan
 * temuan baru pada dokumen baru — tidak ada yang perlu dibuka ulang.
 *
 * Fungsi MURNI: nol I/O, nol pembacaan jam sistem.
 */

/** Belum tersentuh. Juga arti dari field yang belum ada sama sekali. */
const val STATUS_NEW: String = "new"

/** Sudah ditinjau, ada penanggung jawabnya, belum selesai. */
const val STATUS_REVIEWED: String = "reviewed"

/** Selesai, dengan catatan penutup. */
const val STATUS_CLOSED: String = "closed"

enum class ChecklistFollowUpStatus(val code: String) {
    NEW(STATUS_NEW),
    REVIEWED(STATUS_REVIEWED),
    CLOSED(STATUS_CLOSED);

    companion object {
        /**
         * Kode tak dikenal maupun field yang tidak ada sama-sama menjadi [NEW].
         *
         * Bukan `null`, dan itu keputusan sadar: seluruh dokumen checklist yang sudah ada di
         * production tidak punya field ini, dan membacanya sebagai "tidak diketahui" akan
         * membuat setiap temuan lama tampil sebagai keadaan keempat yang tidak bisa
         * ditindak. [NEW] adalah kebenarannya — belum ada yang menanganinya.
         */
        fun fromCode(raw: String?): ChecklistFollowUpStatus =
            entries.firstOrNull { it.code == raw?.trim()?.lowercase() } ?: NEW
    }
}

/** Keadaan tindak lanjut satu temuan. */
data class ChecklistFollowUp(
    val status: ChecklistFollowUpStatus = ChecklistFollowUpStatus.NEW,
    /** UID penanggung jawab. Kosong berarti belum ada. */
    val ownerUid: String? = null,
    /** Catatan penutup. */
    val note: String? = null,
    val updatedAtMillis: Long? = null,
    val updatedByUid: String? = null,
) {
    val isClosed: Boolean get() = status == ChecklistFollowUpStatus.CLOSED

    /** Masih menuntut perhatian — inilah yang boleh memunculkan baris pengecualian. */
    val isOutstanding: Boolean get() = status != ChecklistFollowUpStatus.CLOSED
}

/** Alasan sebuah perpindahan ditolak. Enum, bukan `@StringRes`: tiap platform memetakannya sendiri. */
enum class ChecklistFollowUpError {
    /** `new → closed`: menutup temuan yang belum pernah ditinjau. */
    CANNOT_CLOSE_UNREVIEWED,
    /** `→ new` dari status apa pun. */
    CANNOT_RETURN_TO_NEW,
    /** `→ reviewed` tanpa penanggung jawab. */
    OWNER_REQUIRED,
    /** Masuk atau keluar dari `closed` tanpa catatan BARU (catatan lama tidak cukup). */
    NOTE_REQUIRED,
}

sealed interface ChecklistFollowUpResult {
    /** Perpindahan sah. [followUp] adalah keadaan yang harus ditulis. */
    data class Allowed(val followUp: ChecklistFollowUp) : ChecklistFollowUpResult
    data class Rejected(val error: ChecklistFollowUpError) : ChecklistFollowUpResult
}

/** Panjang maksimum catatan — sejajar dengan alasan override workday admin. */
const val CHECKLIST_FOLLOW_UP_NOTE_MAX_LENGTH: Int = 500

/**
 * Evaluasi satu perpindahan status tindak lanjut.
 *
 * Mengembalikan keadaan yang harus DITULIS, bukan cuma boolean: dengan begitu pemanggil tidak
 * bisa lupa mengisi `updatedAtMillis`/`updatedByUid`, dan tidak ada dua tempat yang menyusun
 * dokumen dengan cara berbeda.
 *
 * `nowMillis` dan `actorUid` disuntikkan — fungsi ini tidak membaca jam maupun sesi.
 */
fun evaluateChecklistFollowUp(
    current: ChecklistFollowUp,
    target: ChecklistFollowUpStatus,
    ownerUid: String?,
    note: String?,
    actorUid: String,
    nowMillis: Long,
): ChecklistFollowUpResult {
    val trimmedNote = note?.trim()?.takeIf { it.isNotEmpty() }
        ?.take(CHECKLIST_FOLLOW_UP_NOTE_MAX_LENGTH)
    val trimmedOwner = ownerUid?.trim()?.takeIf { it.isNotEmpty() } ?: current.ownerUid

    if (target == ChecklistFollowUpStatus.NEW && current.status != ChecklistFollowUpStatus.NEW) {
        return ChecklistFollowUpResult.Rejected(ChecklistFollowUpError.CANNOT_RETURN_TO_NEW)
    }
    if (target == ChecklistFollowUpStatus.CLOSED &&
        current.status == ChecklistFollowUpStatus.NEW
    ) {
        return ChecklistFollowUpResult.Rejected(ChecklistFollowUpError.CANNOT_CLOSE_UNREVIEWED)
    }
    if (target == ChecklistFollowUpStatus.REVIEWED && trimmedOwner == null) {
        return ChecklistFollowUpResult.Rejected(ChecklistFollowUpError.OWNER_REQUIRED)
    }
    // Catatan dituntut saat MASUK `closed` (apa yang dikerjakan) maupun saat KELUAR darinya
    // (kenapa dibuka lagi). Koreksi yang tak berjejak sama buruknya dengan penutupan kosong.
    //
    // Dan catatannya harus **BARU**, bukan sekadar ada: menyalin catatan penutup menjadi
    // alasan pembukaan tidak menjelaskan apa pun. `firestore.rules` menuntut hal yang sama
    // dengan membandingkan `request.resource.data` terhadap `resource.data` — di sana
    // pembandingan itu WAJIB, karena update Firestore bersifat merge sehingga catatan lama
    // masih terbawa dan akan lolos sebagai "ada catatan".
    val touchesClosed = target == ChecklistFollowUpStatus.CLOSED ||
        current.status == ChecklistFollowUpStatus.CLOSED
    if (touchesClosed && target != current.status &&
        (trimmedNote == null || trimmedNote == current.note)
    ) {
        return ChecklistFollowUpResult.Rejected(ChecklistFollowUpError.NOTE_REQUIRED)
    }

    return ChecklistFollowUpResult.Allowed(
        ChecklistFollowUp(
            status = target,
            ownerUid = trimmedOwner,
            // Catatan lama DIPERTAHANKAN bila tidak ada yang baru: menghapusnya karena
            // kolomnya dibiarkan kosong akan menghilangkan catatan penutup yang sudah ada.
            note = trimmedNote ?: current.note,
            updatedAtMillis = nowMillis,
            updatedByUid = actorUid,
        )
    )
}

/** Kunci field Firestore. Dikumpulkan di satu tempat supaya rules dan kode memakai nama yang sama. */
object ChecklistFollowUpFields {
    const val STATUS = "followUpStatus"
    const val OWNER_UID = "followUpOwnerUid"
    const val NOTE = "followUpNote"
    const val UPDATED_AT = "followUpUpdatedAtMillis"
    const val UPDATED_BY = "followUpUpdatedByUid"

    val ALL: List<String> = listOf(STATUS, OWNER_UID, NOTE, UPDATED_AT, UPDATED_BY)
}

/**
 * Baca tindak lanjut dari dokumen mentah.
 *
 * Dokumen tanpa field apa pun → [ChecklistFollowUpStatus.NEW]. Itu keadaan seluruh dokumen
 * production yang sudah ada.
 */
fun mapChecklistFollowUp(map: Map<String, Any?>?): ChecklistFollowUp {
    if (map == null) return ChecklistFollowUp()
    return ChecklistFollowUp(
        status = ChecklistFollowUpStatus.fromCode(map[ChecklistFollowUpFields.STATUS] as? String),
        ownerUid = (map[ChecklistFollowUpFields.OWNER_UID] as? String)?.takeIf { it.isNotBlank() },
        note = (map[ChecklistFollowUpFields.NOTE] as? String)?.takeIf { it.isNotBlank() },
        updatedAtMillis = (map[ChecklistFollowUpFields.UPDATED_AT] as? Number)?.toLong(),
        updatedByUid = (map[ChecklistFollowUpFields.UPDATED_BY] as? String)
            ?.takeIf { it.isNotBlank() },
    )
}

/**
 * Susun potongan dokumen untuk ditulis.
 *
 * Hanya field tindak lanjut — pemanggil yang menggabungkannya sebagai update parsial.
 * `null` TIDAK disertakan: menulis `null` menambah kunci tanpa membawa informasi, dan
 * `firestore.rules` menuntut tiap kunci yang ada bertipe benar.
 */
fun buildChecklistFollowUpPayload(followUp: ChecklistFollowUp): Map<String, Any> = buildMap {
    put(ChecklistFollowUpFields.STATUS, followUp.status.code)
    followUp.ownerUid?.let { put(ChecklistFollowUpFields.OWNER_UID, it) }
    followUp.note?.let { put(ChecklistFollowUpFields.NOTE, it) }
    followUp.updatedAtMillis?.let { put(ChecklistFollowUpFields.UPDATED_AT, it) }
    followUp.updatedByUid?.let { put(ChecklistFollowUpFields.UPDATED_BY, it) }
}

/**
 * Temuan hari ini yang MASIH menagih perhatian, sebagai `driverUid -> checklistId`.
 *
 * Fungsi tersendiri, bukan `filter` sebaris di ViewModel, karena inilah aturan yang membuat
 * Blok 4.1 lebih dari pencatatan: tanpa penyaringan status tertutup, baris pengecualian yang
 * sama tetap muncul setelah temuannya ditangani — dan baris yang tidak bisa dihilangkan
 * mengajari orang mengabaikan seluruh daftarnya. Di sini ia bisa diuji.
 *
 * Satu driver bisa punya lebih dari satu checklist dalam sehari hanya kalau kontrak doc-id
 * `{uid}_{epochDay}` dilanggar; kalaupun terjadi, yang menang adalah yang **terakhir
 * dikirim**, karena itu yang mencerminkan keadaan kendaraan paling baru.
 */
fun outstandingChecklistIssues(
    summaries: List<ChecklistIssueCandidate>,
    todayEpochDay: Int,
): Map<String, String> = summaries
    .filter { it.dateEpochDay == todayEpochDay && it.hasIssue && it.followUpStatus.isOutstanding() }
    .sortedBy { it.submittedAt }
    .associate { it.driverUid to it.id }

/** Bentuk minimal yang dibutuhkan [outstandingChecklistIssues] — memutus ketergantungan ke model admin. */
data class ChecklistIssueCandidate(
    val id: String,
    val driverUid: String,
    val dateEpochDay: Int,
    val submittedAt: Long,
    val hasIssue: Boolean,
    val followUpStatus: ChecklistFollowUpStatus,
)

/** Masih menuntut perhatian — apa pun selain [ChecklistFollowUpStatus.CLOSED]. */
fun ChecklistFollowUpStatus.isOutstanding(): Boolean = this != ChecklistFollowUpStatus.CLOSED
