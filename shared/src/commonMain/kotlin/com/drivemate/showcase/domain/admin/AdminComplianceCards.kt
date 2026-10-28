package com.drivemate.showcase.domain.admin

/**
 * Kartu kepatuhan **berpenyebut** untuk Admin Dashboard — A1 pada PRD §7.
 *
 * ## Kenapa penyebut, bukan hitungan
 * Kartu lama menampilkan hitungan mentah: "9 checklist". Angka itu tidak bisa dijawab tanpa
 * pertanyaan susulan — sembilan dari berapa? Admin yang melihat "9" tidak tahu apakah hari itu
 * beres atau ada tiga orang yang terlewat. "9/12" menjawabnya dalam satu pandangan.
 *
 * ## Penyebutnya dari mana — dan satu batasan yang harus diketahui
 * PRD menulis penyebut = driver "terjadwal kerja hari ini menurut Work Calendar + assignment
 * aktif". Diperiksa ke kode: **tidak ada data penjadwalan kerja per-driver di sistem ini.**
 * `DriverProfile` hanya membawa uid/nama/email, dan `driver_assignments` adalah penugasan
 * **supervisor** (expat↔driver), bukan jadwal kerja.
 *
 * Memakai penugasan supervisor sebagai penyaring penyebut akan **mengecilkan penyebut** untuk
 * driver yang belum punya supervisor — dan kepatuhan lalu terlihat lebih baik daripada
 * kenyataan. Untuk kartu kepatuhan, salah ke arah itu lebih buruk daripada tidak ada kartu.
 *
 * Jadi penyebutnya: **seluruh driver aktif, pada hari yang Work Calendar sebut hari kerja.**
 * [excludedDriverUids] disediakan untuk pengecualian per-driver (cuti, sakit) yang **belum
 * dimodelkan sistem ini** — parameternya ada supaya bentuknya sudah benar saat data itu lahir,
 * dan sekarang selalu kosong. Ia bukan spekulasi: PRD §7.A1 menuntutnya, datanya yang belum ada.
 *
 * Fungsi MURNI: `todayEpochDay` dan status hari kerja disuntikkan, nol pembacaan jam sistem.
 */

/** Satu rasio kepatuhan. */
data class ComplianceRatio(
    val done: Int,
    val scheduled: Int,
) {
    /** `null` bila tidak ada yang terjadwal — pembagian nol bukan "0%", ia tak terdefinisi. */
    val percent: Int? get() = if (scheduled <= 0) null else (done * 100) / scheduled

    /** Berapa yang belum. Inilah yang sebenarnya dicari admin. */
    val outstanding: Int get() = (scheduled - done).coerceAtLeast(0)

    val isComplete: Boolean get() = scheduled > 0 && done >= scheduled
}

/**
 * Keadaan kartu. **Empat keadaan yang harus berbeda secara visual**, bukan tiga.
 *
 * [NO_SCHEDULE_TODAY] terpisah dari [READY] dengan penyebut nol karena keduanya berarti hal yang
 * sangat berbeda: yang pertama "tidak ada yang perlu dikerjakan hari ini", yang kedua "ada yang
 * perlu dikerjakan dan tak seorang pun mengerjakannya". Menampilkan keduanya sebagai "0" adalah
 * kesalahan yang PRD §7.A1 sebut eksplisit.
 *
 * [ERROR] terpisah dari nol sungguhan dengan alasan yang sama, dan itu pernah jadi bug nyata di
 * repo ini — banner galat ter-render di belakang top bar sehingga kegagalan memuat terlihat
 * seperti "tidak ada data".
 */
enum class AdminComplianceState {
    LOADING,
    ERROR,
    NO_SCHEDULE_TODAY,
    READY,
}

/** Keempat kartu kepatuhan Admin Dashboard. */
data class AdminComplianceCards(
    val state: AdminComplianceState,
    val attendance: ComplianceRatio = ComplianceRatio(0, 0),
    val checklist: ComplianceRatio = ComplianceRatio(0, 0),
    val safety: ComplianceRatio = ComplianceRatio(0, 0),
    val sop: ComplianceRatio = ComplianceRatio(0, 0),
) {
    /**
     * Total yang belum beres di keempat kartu — satu angka yang menjawab "hari ini bersih?".
     *
     * Nol hanya pada [AdminComplianceState.READY]; pada keadaan lain angka ini tidak punya arti
     * dan sengaja dikembalikan nol tanpa berpura-pura bermakna.
     */
    val totalOutstanding: Int
        get() = if (state != AdminComplianceState.READY) {
            0
        } else {
            attendance.outstanding + checklist.outstanding + safety.outstanding + sop.outstanding
        }
}

/**
 * Hitung keempat kartu.
 *
 * @param isCompanyWorkday apakah Work Calendar menyebut hari ini hari kerja perusahaan.
 *   `false` pada Sabtu/Minggu dan hari libur — dan pada override yang menjadikan Sabtu hari
 *   kerja, ia `true`.
 * @param activeDriverUids seluruh driver aktif. Penyebut sebelum pengecualian.
 * @param excludedDriverUids pengecualian per-driver (cuti/sakit). **Belum ada datanya**; lihat
 *   catatan di kepala file.
 * @param attendanceInUids driver yang sudah tap-in hari ini.
 * @param checklistSubmittedUids driver yang sudah mengirim checklist hari ini.
 * @param safetySubmittedUids driver yang sudah mengirim deklarasi keselamatan hari ini.
 * @param sopAcceptedUids driver yang sudah menerima SOP. **Tidak dibatasi hari ini** — SOP
 *   sekali diterima berlaku terus, jadi penyebutnya tetap terjadwal-hari-ini tapi pembilangnya
 *   kumulatif. Perbedaan itu disengaja dan diuji.
 */
fun computeAdminComplianceCards(
    isCompanyWorkday: Boolean,
    activeDriverUids: Set<String>,
    attendanceInUids: Set<String>,
    checklistSubmittedUids: Set<String>,
    safetySubmittedUids: Set<String>,
    sopAcceptedUids: Set<String>,
    excludedDriverUids: Set<String> = emptySet(),
): AdminComplianceCards {
    if (!isCompanyWorkday) {
        return AdminComplianceCards(state = AdminComplianceState.NO_SCHEDULE_TODAY)
    }
    val scheduled = activeDriverUids - excludedDriverUids
    val n = scheduled.size

    // Pembilang di-INTERSEKSI dengan yang terjadwal, tidak dihitung apa adanya. Kalau driver
    // yang sedang cuti tetap mengirim checklist, ia tidak boleh membuat pembilang melebihi
    // penyebut — "13/12" adalah angka yang merusak kepercayaan pada seluruh kartu.
    return AdminComplianceCards(
        state = AdminComplianceState.READY,
        attendance = ComplianceRatio((attendanceInUids intersect scheduled).size, n),
        checklist = ComplianceRatio((checklistSubmittedUids intersect scheduled).size, n),
        safety = ComplianceRatio((safetySubmittedUids intersect scheduled).size, n),
        sop = ComplianceRatio((sopAcceptedUids intersect scheduled).size, n),
    )
}
