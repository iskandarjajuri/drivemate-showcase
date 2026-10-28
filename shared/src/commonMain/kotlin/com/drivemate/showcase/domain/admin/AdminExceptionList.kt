package com.drivemate.showcase.domain.admin

/**
 * Daftar pengecualian yang bisa ditindak — A2 pada PRD §7.
 *
 * Satu daftar berisi **hanya hal yang butuh perhatian manusia hari ini**, urut kegentingan.
 * Bukan feed aktivitas: aktivitas menjawab "apa yang terjadi", daftar ini menjawab "apa yang
 * harus saya kerjakan sekarang". Dua pertanyaan berbeda, dan mencampurnya membuat yang kedua
 * hilang di dalam yang pertama.
 *
 * Fungsi MURNI: `todayEpochDay` dan `nowMillis` disuntikkan, nol pembacaan jam sistem.
 */

/**
 * Jenis pengecualian, **terurut menurut kegentingan** — urutan deklarasi enum inilah
 * urutannya, jadi `ordinal` bisa dipakai menyortir dan tidak ada tabel prioritas kedua yang
 * bisa berbeda pendapat.
 *
 * Alasan urutannya, dari yang paling genting:
 * 1. Driver menyatakan dirinya tidak fit — risiko keselamatan seketika, dan satu-satunya
 *    pemicu di mana orangnya sendiri sudah meminta tolong.
 * 2. Lembur melampaui batas legal — pelanggaran regulasi yang sudah terjadi.
 * 3. Pelanggaran kecepatan — kejadian keselamatan yang sudah terjadi.
 * 4. Checklist bermasalah — cacat kendaraan; menunggu berarti mengoperasikannya.
 * 5. Lembur mendekati batas — masih bisa dicegah.
 * 6. Terjadwal tapi belum absen — operasional, bisa jadi cuma lupa.
 * 7. Hari kerja menunggu persetujuan terlalu lama — tunggakan admin sendiri.
 * 8. SOP belum diterima — terus-menerus, dan tidak menghalangi hari ini.
 */
enum class AdminExceptionKind {
    SAFETY_NOT_FIT,
    OVERTIME_VIOLATION,
    SPEED_VIOLATION,
    CHECKLIST_ISSUE,
    OVERTIME_WARNING,
    NOT_CLOCKED_IN,
    WORKDAY_PENDING_TOO_LONG,
    SOP_NOT_ACCEPTED,
}

/**
 * Satu baris daftar pengecualian.
 *
 * @param driverName **wajib nama, bukan UID.** PRD §7.A2 menuntutnya, dan alasannya bukan
 *   kosmetik: admin tidak mengenali UID, jadi baris ber-UID memaksa satu langkah pencarian
 *   tambahan justru pada layar yang ada untuk menghapus langkah itu. `null` berarti namanya
 *   belum termuat — UI menampilkan penanda, bukan UID.
 * @param occurredAtMillis kapan masalahnya muncul. `null` untuk pemicu yang tidak punya
 *   momen tunggal (SOP belum diterima berlaku terus-menerus).
 * @param recordId tujuan aksi utama. `null` bila pemicu tidak menunjuk satu record.
 */
data class AdminException(
    val kind: AdminExceptionKind,
    val driverUid: String,
    val driverName: String?,
    val occurredAtMillis: Long? = null,
    val recordId: String? = null,
    /** Angka pendukung, mis. menit lembur atau berapa hari menunggu. */
    val magnitude: Int? = null,
    /**
     * Batas yang dilampaui, bila pemicunya soal batas.
     *
     * Dibawa berdampingan dengan [magnitude] karena PRD §7.B1 menuntut keduanya disebut:
     * label warna tanpa angka memberi tahu admin ada masalah tanpa memberi tahu seberapa.
     */
    val limitMinutes: Int? = null,
    /** Cakupan batas (harian/pekanan), bila relevan. */
    val scope: OvertimeLimitScope? = null,
)

/** Ambang "terjadwal tapi belum absen": awal 30 menit setelah jadwal mulai (PRD §7.A2). */
const val NOT_CLOCKED_IN_GRACE_MINUTES: Int = 30

/** Ambang "menunggu persetujuan terlalu lama": 3 hari (PRD §7.A2). */
const val WORKDAY_PENDING_MAX_DAYS: Int = 3

/**
 * Masukan mentah untuk membangun daftar. Dipisah jadi data class supaya penambahan pemicu
 * tidak mengubah tanda tangan fungsi tiap kali — dan supaya pemanggil tidak bisa menukar
 * urutan argumen `Set<String>` yang tipenya identik.
 */
data class AdminExceptionInputs(
    /** Driver yang terjadwal kerja hari ini. Penyebut yang sama dipakai kartu kepatuhan. */
    val scheduledDriverUids: Set<String> = emptySet(),
    val driverNames: Map<String, String> = emptyMap(),
    val attendanceInUids: Set<String> = emptySet(),
    /** `driverUid` -> `recordId` checklist hari ini yang punya temuan. */
    val checklistIssues: Map<String, String> = emptyMap(),
    /** `driverUid` -> `recordId` deklarasi keselamatan hari ini yang menyatakan tidak fit. */
    val safetyNotFit: Map<String, String> = emptyMap(),
    val sopNotAcceptedUids: Set<String> = emptySet(),
    /** Hari kerja berstatus menunggu: `driverUid` -> (recordId, dateEpochDay). */
    val pendingWorkdays: List<PendingWorkday> = emptyList(),
    /**
     * Millis jadwal mulai hari ini. `null` berarti hari ini bukan hari kerja — dan pemicu
     * "belum absen" lalu TIDAK berlaku sama sekali, bukan berlaku dengan ambang default.
     */
    val scheduleStartMillis: Long? = null,
    /**
     * Lembur **MENTAH** hari ini per driver, dalam menit.
     *
     * Mentah, bukan yang terbayar. Batas legal PP 35/2021 mengukur waktu yang DIKERJAKAN;
     * orang yang bekerja lembur 5 jam tidak berhenti melanggar karena pembulatan 15 menit
     * membayarnya 4 jam 45. Memakai angka terbayar di sini akan membuat aplikasi ikut
     * menutupi pelanggaran.
     */
    val dailyOvertimeByDriver: Map<String, Int> = emptyMap(),
    /** Lembur mentah pekan berjalan per driver, dalam menit. */
    val weeklyOvertimeByDriver: Map<String, Int> = emptyMap(),
    /**
     * Dokumen hari kerja **terakhir** dalam jendela pekanan, per driver.
     *
     * Satu peta untuk kedua cakupan, dengan sengaja: untuk pelanggaran harian dokumen ini
     * memang hari ini, dan untuk pelanggaran pekanan ia adalah catatan yang membuat
     * akumulasinya menyeberang. Keduanya menjawab pertanyaan admin yang sama — "lembur mana
     * ini?" — jadi kriteria terima A2 ("satu ketukan sampai ke record") terpenuhi tanpa
     * tujuan navigasi kedua yang perlu dijelaskan.
     */
    val overtimeRecordIdByDriver: Map<String, String> = emptyMap(),
    /** Batas yang berlaku. Default = regulasi. */
    val overtimeLimits: OvertimeLimits = OvertimeLimits(),
)

/** Hari kerja yang masih menunggu persetujuan. */
data class PendingWorkday(
    val recordId: String,
    val driverUid: String,
    val dateEpochDay: Int,
)

/**
 * Susun daftar pengecualian, urut kegentingan lalu waktu terbaru.
 *
 * ## Dua pemicu yang BELUM aktif
 * [AdminExceptionKind.OVERTIME_WARNING]/[AdminExceptionKind.OVERTIME_VIOLATION] menunggu
 * konfigurasi batas dari Blok 3.1, dan [AdminExceptionKind.SPEED_VIOLATION] menunggu jalur
 * BACA `speed_violations` dari Blok 3.3 — repo ini baru punya `ReportSpeedViolationUseCase`
 * untuk menulis. Ketiganya sudah ada di enum supaya urutan kegentingan lengkap sejak sekarang
 * dan tidak perlu disusun ulang nanti; datanya yang belum ada, bukan tempatnya.
 */
fun buildAdminExceptionList(
    inputs: AdminExceptionInputs,
    todayEpochDay: Int,
    nowMillis: Long,
    graceMinutes: Int = NOT_CLOCKED_IN_GRACE_MINUTES,
    pendingMaxDays: Int = WORKDAY_PENDING_MAX_DAYS,
): List<AdminException> {
    val out = mutableListOf<AdminException>()
    fun nameOf(uid: String) = inputs.driverNames[uid]

    // 1. Tidak fit — paling genting.
    inputs.safetyNotFit.forEach { (uid, recordId) ->
        out += AdminException(AdminExceptionKind.SAFETY_NOT_FIT, uid, nameOf(uid), recordId = recordId)
    }

    // 2. Checklist bermasalah.
    inputs.checklistIssues.forEach { (uid, recordId) ->
        out += AdminException(AdminExceptionKind.CHECKLIST_ISSUE, uid, nameOf(uid), recordId = recordId)
    }

    // 3. Terjadwal tapi belum absen — hanya bila hari ini hari kerja DAN masa tenggang lewat.
    //    Tanpa penjaga masa tenggang, daftar ini akan penuh setiap pagi pukul 07:31 dengan
    //    orang yang sedang di jalan, dan admin belajar mengabaikannya.
    val start = inputs.scheduleStartMillis
    if (start != null && nowMillis >= start + graceMinutes * 60_000L) {
        (inputs.scheduledDriverUids - inputs.attendanceInUids).forEach { uid ->
            out += AdminException(
                kind = AdminExceptionKind.NOT_CLOCKED_IN,
                driverUid = uid,
                driverName = nameOf(uid),
                occurredAtMillis = start + graceMinutes * 60_000L,
                magnitude = ((nowMillis - start) / 60_000L).toInt(),
            )
        }
    }

    // 4. Batas lembur (B1). Dievaluasi per cakupan, jadi satu driver bisa memunculkan
    //    baris harian DAN pekanan — menyembunyikan salah satunya akan menyembunyikan
    //    pelanggaran yang berbeda jenisnya.
    //
    //    DIBATASI ke `driverNames.keys`. Jendela lembur membentang tujuh hari, jadi tanpa
    //    batas ini seorang driver yang sudah dinonaktifkan tapi punya hari kerja pekan lalu
    //    akan muncul sebagai baris "driver tidak dikenal" yang tidak bisa ditindak. Pemicu
    //    lain tidak punya masalah ini karena sumbernya memang catatan hari ini.
    //
    //    Yang TIDAK dipakai sebagai penyaring: "terjadwal hari ini". Driver yang hari ini
    //    libur tapi melampaui batas pekanan Senin–Jumat justru paling perlu terlihat — di
    //    situlah admin masih bisa mengubah jadwal besok.
    val overtimeUids = (inputs.dailyOvertimeByDriver.keys + inputs.weeklyOvertimeByDriver.keys)
        .filter { it in inputs.driverNames }
    overtimeUids.forEach { uid ->
        evaluateOvertimeLimits(
            dailyMinutes = inputs.dailyOvertimeByDriver[uid] ?: 0,
            weeklyMinutes = inputs.weeklyOvertimeByDriver[uid] ?: 0,
            limits = inputs.overtimeLimits,
        ).forEach { status ->
            val kind = when (status.level) {
                OvertimeLimitLevel.VIOLATION -> AdminExceptionKind.OVERTIME_VIOLATION
                OvertimeLimitLevel.WARNING -> AdminExceptionKind.OVERTIME_WARNING
                OvertimeLimitLevel.OK -> return@forEach
            }
            out += AdminException(
                kind = kind,
                driverUid = uid,
                driverName = nameOf(uid),
                recordId = inputs.overtimeRecordIdByDriver[uid],
                magnitude = status.actualMinutes,
                limitMinutes = status.limitMinutes,
                scope = status.scope,
            )
        }
    }

    // 5. Menunggu persetujuan terlalu lama.
    inputs.pendingWorkdays.forEach { w ->
        val days = todayEpochDay - w.dateEpochDay
        if (days > pendingMaxDays) {
            out += AdminException(
                kind = AdminExceptionKind.WORKDAY_PENDING_TOO_LONG,
                driverUid = w.driverUid,
                driverName = nameOf(w.driverUid),
                recordId = w.recordId,
                magnitude = days,
            )
        }
    }

    // 6. SOP belum diterima — terus-menerus, tanpa momen tunggal.
    inputs.sopNotAcceptedUids.forEach { uid ->
        out += AdminException(AdminExceptionKind.SOP_NOT_ACCEPTED, uid, nameOf(uid))
    }

    // Urut kegentingan (ordinal enum), lalu yang terbaru dulu di dalam kegentingan yang sama.
    // Baris tanpa waktu diletakkan SETELAH yang berwaktu: "tidak diketahui kapan" bukan
    // "baru saja", dan menaruhnya di atas akan mendorong kejadian nyata ke bawah.
    return out.sortedWith(
        compareBy<AdminException> { it.kind.ordinal }
            .thenBy { it.occurredAtMillis == null }
            .thenByDescending { it.occurredAtMillis ?: Long.MIN_VALUE }
            .thenBy { it.driverName ?: it.driverUid }
    )
}
