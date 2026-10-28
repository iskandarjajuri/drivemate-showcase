package com.drivemate.showcase.domain.workday

import com.drivemate.showcase.core.model.WorkDay

/**
 * Jejak keputusan **dua langkah** untuk satu hari kerja — D1 pada PRD §7 (Blok 4.2).
 *
 * Alurnya, sesuai jawaban Q4: **supervisor meninjau → admin memutuskan.** Dua langkah,
 * masing-masing dengan pelaku dan waktunya sendiri.
 *
 * ## Apa yang sebenarnya salah sebelum ini
 * Dokumen `workdays` sudah menyimpan satu keputusan (`approvedByUid`/`rejectedByUid`) plus
 * jejak override admin (`override*`). Yang tampak seperti dua langkah sebetulnya satu, dan
 * lebih buruk: `adminOverrideDecision` **menimpa** `approvedByUid` dengan uid admin. Jadi
 * begitu seorang admin melakukan override, dokumen tidak lagi menyimpan siapa supervisor
 * yang meninjau. `overrideFromStatus` menyimpan status sebelumnya, bukan pelakunya — dan
 * pada override kedua ia menyimpan status admin pertama, bukan status supervisor.
 *
 * Karena itu langkah 1 mendapat slot sendiri yang **sekali tulis lalu beku**, bukan sekadar
 * dibaca ulang dari field yang sudah ada.
 *
 * ## Bentuk di Firestore: satu map, bukan tujuh field datar
 * ```
 * decisionTrail: {
 *   supervisor: { status, byUid, atMillis },
 *   admin:      { status, byUid, atMillis, note? }
 * }
 * ```
 * Bentuk datar (`supervisorReviewByUid`, `adminDecisionStatus`, …) sudah dicoba dan
 * **tidak bisa dievaluasi**: aturan update `workdays` berada beberapa puluh ekspresi dari
 * plafon 1000 ekspresi milik Firestore, dan tujuh entri tambahan di `hasOnlyAllowed`
 * mendorong dua jalur write terberat melewatinya. Lihat komentar panjang di
 * `firestore.rules` (`decisionTrailWriteValid`) untuk angka-angkanya.
 *
 * ## Yang JUJUR soal cakupannya
 * Langkah 2 hanya ada kalau seorang admin **benar-benar bertindak** atas hari itu. Untuk
 * hari kerja yang tidak pernah dibuka admin — mayoritasnya — jejaknya tetap satu langkah.
 * Mekanisme ini membuat keputusan admin **bisa dicatat dan tercatat saat terjadi**; jaminan
 * "selalu dua langkah" menuntut admin memutuskan setiap hari kerja, dan itu perubahan
 * proses kerja perusahaan, bukan perubahan layar — diusulkan terpisah di
 * `docs/proposals/4.2-alur-dua-langkah-penuh.md`, tidak diimplementasi di sini.
 *
 * Begitu pula: workday yang difinalisasi admin langsung dari `pending` (tanpa supervisor)
 * **tidak** bisa punya langkah 2 — rules menuntut langkah 1 sudah ada lebih dulu. Jejaknya
 * satu langkah, dan itu memang keadaannya, bukan kekurangan pencatatan.
 *
 * Fungsi MURNI: nol I/O, nol pembacaan jam sistem.
 */

/** Nama field map di Firestore. Kode disimpan; label diterjemahkan di lapis presentasi. */
object WorkdayDecisionTrailFields {
    const val TRAIL: String = "decisionTrail"
    const val SUPERVISOR: String = "supervisor"
    const val ADMIN: String = "admin"
    const val STATUS: String = "status"
    const val BY_UID: String = "byUid"
    const val AT_MILLIS: String = "atMillis"
    const val NOTE: String = "note"
}

/** Batas catatan langkah 2 — sama dengan `overrideReason`/`rejectReason` (rules: `<= 500`). */
const val WORKDAY_DECISION_NOTE_MAX_LENGTH: Int = 500

/** Peran pelaku satu langkah. Bukan nilai yang disimpan — posisi di map yang menentukannya. */
enum class WorkdayDecisionRole { SUPERVISOR, ADMIN }

/**
 * Keputusan yang dicatat satu langkah.
 *
 * Sengaja TIDAK punya anggota `PENDING`: langkah yang mencatat "belum diputuskan" bukan
 * langkah. Kode yang tidak dikenal menjadi `null` — lihat [fromCode].
 */
enum class WorkdayDecision(val code: String) {
    APPROVED("approved"),
    REJECTED("rejected");

    companion object {
        /**
         * `null` untuk kode tak dikenal, termasuk `"pending"` dan field yang tidak ada.
         *
         * Fail-closed, berbeda dari [com.drivemate.showcase.domain.checklist.ChecklistFollowUpStatus.fromCode]
         * yang jatuh ke `NEW`: di sana "tidak ada field" punya arti yang benar dan bisa
         * ditindak ("belum ada yang menanganinya"). Di sini tidak ada arti default yang
         * benar — mengarang "disetujui" atau "ditolak" untuk dokumen yang datanya rusak
         * berarti melaporkan keputusan yang tidak pernah diambil, pada dokumen yang
         * menjadi dasar perhitungan upah.
         */
        fun fromCode(raw: String?): WorkdayDecision? =
            entries.firstOrNull { it.code == raw?.trim()?.lowercase() }
    }
}

/** Satu langkah dalam jejak: siapa, kapan, memutuskan apa. */
data class WorkdayDecisionStep(
    val role: WorkdayDecisionRole,
    val decision: WorkdayDecision,
    val actorUid: String,
    val atMillis: Long?,
    /** Hanya langkah admin yang punya catatan; langkah supervisor selalu `null`. */
    val note: String? = null,
)

/**
 * Jejak lengkap satu hari kerja.
 *
 * [supervisorStep] bisa berasal dari slot barunya ATAU disimpulkan dari
 * `approvedByUid`/`rejectedByUid` untuk dokumen lama — lihat [buildWorkdayDecisionTrail].
 */
data class WorkdayDecisionTrail(
    val supervisorStep: WorkdayDecisionStep?,
    val adminStep: WorkdayDecisionStep?,
    /**
     * `true` kalau langkah 1 hanya bisa disimpulkan dari field lama, bukan dibaca dari
     * slotnya sendiri. Penting untuk ditampilkan apa adanya: pada dokumen seperti itu
     * pelaku langkah 1 **bisa saja sudah tertimpa** oleh override admin sebelumnya, jadi
     * nama yang tampil belum tentu nama supervisor yang sebenarnya meninjau.
     */
    val supervisorStepIsInferred: Boolean = false,
) {
    val recordedStepCount: Int
        get() = (if (supervisorStep != null) 1 else 0) + (if (adminStep != null) 1 else 0)

    /** Kedua langkah tercatat. */
    val isComplete: Boolean get() = supervisorStep != null && adminStep != null

    /**
     * Apakah admin sepakat dengan supervisor. `null` kalau salah satu langkah belum ada —
     * bukan `false`: "belum diputuskan" dan "tidak setuju" adalah dua hal yang berbeda.
     */
    val adminAgreesWithSupervisor: Boolean?
        get() {
            val supervisor = supervisorStep ?: return null
            val admin = adminStep ?: return null
            return supervisor.decision == admin.decision
        }

    /**
     * Apakah langkah 2 sudah **kedaluwarsa** terhadap keputusan dokumen sekarang.
     *
     * Keadaan ini nyata, bukan teoretis: `adminOverrideDecision` mengubah status dokumen
     * tanpa menyentuh jejak, jadi langkah 2 tertinggal mencatat keputusan yang sudah tidak
     * berlaku. Selama itu terjadi, [adminAgreesWithSupervisor] **tidak boleh dipercaya** —
     * ia membandingkan langkah 2 dengan langkah 1, dan keduanya bisa sama-sama sepakat
     * pada keputusan yang sudah ditinggalkan dokumennya.
     */
    fun adminStepIsStale(documentDecision: WorkdayDecision?): Boolean {
        val admin = adminStep ?: return false
        return documentDecision != null && admin.decision != documentDecision
    }
}

/** Alasan penolakan saat admin mencoba mencatat langkah 2. */
enum class WorkdayAdminDecisionError {
    /** Belum ada keputusan supervisor untuk ditindaklanjuti. */
    SUPERVISOR_STEP_MISSING,

    /** Uid pelaku kosong — tidak ada yang bisa dicatat sebagai pengambil keputusan. */
    ACTOR_REQUIRED,

    /**
     * Keputusan yang dicatat tidak sama dengan keputusan dokumen saat ini.
     *
     * Ketidaksepakatan HARUS lewat jalur override (`adminOverrideDecision`), yang menuntut
     * `overrideReason` dan mengubah statusnya sekalian. Mencatat "ditolak" sementara
     * dokumennya masih "disetujui" akan melahirkan dokumen yang membantah dirinya sendiri.
     */
    DECISION_MISMATCH,

    /** Catatan melebihi [WORKDAY_DECISION_NOTE_MAX_LENGTH] karakter. */
    NOTE_TOO_LONG,

    /**
     * Langkah 2 sudah mencatat keputusan yang sama dengan yang ingin dicatat.
     *
     * Namanya soal "sudah tercatat", tapi yang dijaga adalah **tidak ada yang berubah**:
     * menulis ulang langkah yang sudah sepakat hanya menukar pelaku dan waktunya, sehingga
     * admin kedua bisa menghapus jejak admin pertama tanpa mengubah keputusan apa pun.
     * `firestore.rules` memaksakan syarat yang sama (`before.admin.status != nextStatus`).
     *
     * Yang **boleh** terjadi, dan disengaja: kalau langkah 2 sudah kedaluwarsa karena
     * override mengubah keputusan dokumen, admin **mana pun** boleh mencatat ulang — dan
     * pelaku barulah yang tersimpan. Langkah 2 adalah snapshot keputusan admin yang
     * BERLAKU SEKARANG, bukan riwayat setiap admin yang pernah menyentuhnya; admin yang
     * keputusannya sudah disusul override tidak lagi tercatat di slot itu, dan jejak
     * perubahannya ada di `override*`. Riwayat penuh adalah pekerjaan lain — lihat
     * `docs/proposals/4.2-alur-dua-langkah-penuh.md`.
     */
    ALREADY_RECORDED,
}

sealed interface WorkdayAdminDecisionResult {
    /** Payload siap tulis, sudah lolos seluruh validasi. */
    data class Accepted(val step: WorkdayDecisionStep) : WorkdayAdminDecisionResult

    data class Rejected(val error: WorkdayAdminDecisionError) : WorkdayAdminDecisionResult
}

/**
 * Membaca kedua slot dari map mentah `decisionTrail` di Firestore.
 *
 * `null` kalau tidak ada satu pun langkah yang bisa dibaca — dokumen tanpa field ini, dan
 * dokumen yang field-nya rusak, sama-sama menjadi `null`. Pengisian langkah 1 dari field
 * keputusan lama dilakukan [resolveDecisionTrail], bukan di sini: fungsi ini melaporkan apa
 * yang dokumen **benar-benar catat**, dan pemisahan itu yang membuat
 * [WorkdayDecisionTrail.supervisorStepIsInferred] bisa jujur.
 */
fun mapWorkdayDecisionTrail(trail: Map<*, *>?): WorkdayDecisionTrail? {
    if (trail == null) return null
    val supervisorStep = mapStepOrNull(
        raw = trail[WorkdayDecisionTrailFields.SUPERVISOR],
        role = WorkdayDecisionRole.SUPERVISOR,
        allowNote = false,
    )
    val adminStep = mapStepOrNull(
        raw = trail[WorkdayDecisionTrailFields.ADMIN],
        role = WorkdayDecisionRole.ADMIN,
        allowNote = true,
    )
    if (supervisorStep == null && adminStep == null) return null
    return WorkdayDecisionTrail(supervisorStep = supervisorStep, adminStep = adminStep)
}

private fun mapStepOrNull(
    raw: Any?,
    role: WorkdayDecisionRole,
    allowNote: Boolean,
): WorkdayDecisionStep? {
    val map = raw as? Map<*, *> ?: return null
    // Tanpa keputusan atau tanpa pelaku, ia bukan langkah — dan menampilkannya sebagai
    // langkah setengah akan membuat pembaca mengira ada yang sudah memutuskan.
    val decision = WorkdayDecision.fromCode(map[WorkdayDecisionTrailFields.STATUS] as? String)
        ?: return null
    val actorUid = (map[WorkdayDecisionTrailFields.BY_UID] as? String)
        ?.trim()?.takeIf { it.isNotEmpty() }
        ?: return null
    return WorkdayDecisionStep(
        role = role,
        decision = decision,
        actorUid = actorUid,
        atMillis = (map[WorkdayDecisionTrailFields.AT_MILLIS] as? Number)?.toLong(),
        note = if (allowNote) {
            (map[WorkdayDecisionTrailFields.NOTE] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        } else {
            null
        },
    )
}

/** Keputusan yang saat ini berlaku pada dokumen, dari `status` atau field pelakunya. */
fun WorkDay.effectiveDecision(): WorkdayDecision? =
    WorkdayDecision.fromCode(status)
        ?: when {
            !rejectedByUid.isNullOrBlank() -> WorkdayDecision.REJECTED
            !approvedByUid.isNullOrBlank() -> WorkdayDecision.APPROVED
            else -> null
        }

/**
 * Jejak yang layak ditampilkan: slot yang tercatat, plus langkah 1 yang **disimpulkan**
 * dari `approvedByUid`/`rejectedByUid` kalau slotnya belum ada.
 *
 * Pengisian itu perlu karena seluruh workday yang sudah ditinjau expat di production tidak
 * punya `decisionTrail`. Tanpa fallback, setiap catatan lama akan tampil sebagai "belum
 * ditinjau siapa pun" — salah, dan salah dengan cara yang membuat admin menagih ulang
 * peninjauan yang sudah dilakukan.
 *
 * Hasilnya menandai dirinya dengan [WorkdayDecisionTrail.supervisorStepIsInferred] supaya
 * layar bisa mengatakannya apa adanya: pada dokumen lama yang pernah di-override,
 * `approvedByUid` **sudah tertimpa** uid admin, jadi nama yang tampil di langkah 1 belum
 * tentu supervisor yang sebenarnya meninjau.
 */
fun WorkDay.resolveDecisionTrail(): WorkdayDecisionTrail {
    val recorded = decisionTrail
    if (recorded?.supervisorStep != null) return recorded
    val decision = effectiveDecision()
    val actorUid = when (decision) {
        WorkdayDecision.APPROVED -> approvedByUid
        WorkdayDecision.REJECTED -> rejectedByUid
        null -> null
    }?.trim()?.takeIf { it.isNotEmpty() }
    val atMillis = when (decision) {
        WorkdayDecision.APPROVED -> approvedAtMillis
        WorkdayDecision.REJECTED -> rejectedAtMillis
        null -> null
    }
    val inferred = if (decision != null && actorUid != null) {
        WorkdayDecisionStep(
            role = WorkdayDecisionRole.SUPERVISOR,
            decision = decision,
            actorUid = actorUid,
            atMillis = atMillis,
        )
    } else {
        null
    }
    return WorkdayDecisionTrail(
        supervisorStep = inferred,
        adminStep = recorded?.adminStep,
        supervisorStepIsInferred = inferred != null,
    )
}

/**
 * Memvalidasi pencatatan langkah 2 oleh admin.
 *
 * @param trail jejak dokumen saat ini.
 * @param documentDecision keputusan yang saat ini berlaku pada dokumen.
 * @param target keputusan yang ingin dicatat admin.
 */
fun evaluateAdminWorkdayDecision(
    trail: WorkdayDecisionTrail,
    documentDecision: WorkdayDecision?,
    target: WorkdayDecision,
    note: String?,
    actorUid: String,
    nowMillis: Long,
): WorkdayAdminDecisionResult {
    val trimmedActor = actorUid.trim()
    if (trimmedActor.isEmpty()) {
        return WorkdayAdminDecisionResult.Rejected(WorkdayAdminDecisionError.ACTOR_REQUIRED)
    }
    if (trail.supervisorStep == null || documentDecision == null) {
        return WorkdayAdminDecisionResult.Rejected(
            WorkdayAdminDecisionError.SUPERVISOR_STEP_MISSING
        )
    }
    if (target != documentDecision) {
        return WorkdayAdminDecisionResult.Rejected(WorkdayAdminDecisionError.DECISION_MISMATCH)
    }
    val existingAdminStep = trail.adminStep
    if (existingAdminStep != null && existingAdminStep.decision == target) {
        return WorkdayAdminDecisionResult.Rejected(WorkdayAdminDecisionError.ALREADY_RECORDED)
    }
    val trimmedNote = note?.trim()?.takeIf { it.isNotEmpty() }
    if (trimmedNote != null && trimmedNote.length > WORKDAY_DECISION_NOTE_MAX_LENGTH) {
        return WorkdayAdminDecisionResult.Rejected(WorkdayAdminDecisionError.NOTE_TOO_LONG)
    }
    return WorkdayAdminDecisionResult.Accepted(
        WorkdayDecisionStep(
            role = WorkdayDecisionRole.ADMIN,
            decision = target,
            actorUid = trimmedActor,
            atMillis = nowMillis,
            note = trimmedNote,
        )
    )
}

/**
 * Payload satu langkah, untuk ditulis pada path bertitik `decisionTrail.<slot>`.
 *
 * Path bertitik, BUKAN penulisan seluruh map: `update` pada map bersarang di Firestore
 * **mengganti** map itu seluruhnya, jadi menulis `decisionTrail` utuh saat mencatat langkah
 * 2 akan menghapus langkah 1. Rules menolak write seperti itu (`adminStepWriteValid`
 * menuntut langkah 1 lestari), jadi kesalahan ini gagal keras — tapi lebih baik tidak
 * ditulis sejak awal.
 *
 * Kunci `note` dihilangkan sepenuhnya kalau catatannya kosong; rules mengizinkan `note`
 * absen tapi tidak mengizinkan kunci di luar keempatnya.
 */
fun buildWorkdayDecisionStepPayload(step: WorkdayDecisionStep): Map<String, Any> {
    val payload = mutableMapOf<String, Any>(
        WorkdayDecisionTrailFields.STATUS to step.decision.code,
        WorkdayDecisionTrailFields.BY_UID to step.actorUid,
    )
    step.atMillis?.let { payload[WorkdayDecisionTrailFields.AT_MILLIS] = it }
    step.note?.let { payload[WorkdayDecisionTrailFields.NOTE] = it }
    return payload
}

/** Nama path bertitik untuk satu slot langkah. */
fun workdayDecisionStepPath(role: WorkdayDecisionRole): String {
    val slot = when (role) {
        WorkdayDecisionRole.SUPERVISOR -> WorkdayDecisionTrailFields.SUPERVISOR
        WorkdayDecisionRole.ADMIN -> WorkdayDecisionTrailFields.ADMIN
    }
    return "${WorkdayDecisionTrailFields.TRAIL}.$slot"
}
