package com.drivemate.showcase.domain.calendar

import com.drivemate.showcase.core.model.AttendanceReferenceWindow
import com.drivemate.showcase.core.model.WorkDay
import kotlin.math.max

/**
 * **Satu-satunya penghasil ANGKA lembur di DriveMate** — "Mesin A" pada PRD §17.
 *
 * ## Kenapa file ini ada
 * Sebelum 7 September 2026 ada DUA definisi lembur yang hidup bersamaan dan bisa menjawab
 * berbeda untuk hari yang sama:
 *
 * - **Mesin A** (`computeDailySummary`): `net = (keluar − masuk) − istirahat`, lalu
 *   `lembur = max(0, net − netTerjadwal)`. Sadar istirahat. Dipakai Cloud Function, dan
 *   nilainya tersimpan di `workdays.overtimeMinutes` sebagai **catatan resmi**.
 * - **Mesin B** (`computeAttendanceTimelineZones`): jam kerja di **luar** jendela jadwal
 *   dihitung lembur awal/akhir. **Tidak** sadar istirahat.
 *
 * Contohnya nyata: masuk 07:00, keluar 16:30, hari Senin. Mesin A → net 8j00m, terjadwal
 * 8j00m, **lembur 0**. Mesin B → 30 menit sebelum jendela, **lembur 30**. Hari yang sama, dua
 * angka — dan yang dilihat driver di Riwayat Kerja adalah Mesin B, sementara yang tersimpan
 * sebagai catatan resmi adalah Mesin A.
 *
 * Selama lembur cuma sinyal pengawasan, itu cacat yang bisa ditunda. Sejak ia menjadi dasar
 * upah, ia menjadi selisih pada slip gaji.
 *
 * ## Pembagian tugas yang harus dipertahankan
 * **Mesin B TIDAK dihapus.** Ia tetap benar dan tetap dipakai untuk **menggambar** posisi jam
 * pada timeline (lebar segmen bar Live Timer, bar normal-vs-lembur di Agenda). Yang berubah:
 * ia tidak lagi menjadi sumber angkanya.
 *
 * > **Zona untuk gambar. Mesin A untuk angka.**
 *
 * Fungsi di sini MURNI: nol pembacaan jam sistem, semua masukan disuntikkan.
 */

/**
 * Ambang PP 35/2021: lembur diperhitungkan bila **melampaui** 30 menit.
 *
 * "Melampaui" berarti lebih dari, bukan sekurang-kurangnya — 30 menit tepat tidak melampaui 30.
 *
 * ## AKTIF sejak 7 September 2026
 * Disetujui Iskandar, digabung pembulatan ke bawah kelipatan [OVERTIME_ROUNDING_MINUTES].
 * Angka yang ditampilkan Riwayat Kerja adalah yang **terbayar**; angka mentah tetap tersedia
 * di [WorkHistoryDayMinutes.rawOvertimeMinutes] untuk audit dan rekonstruksi sengketa upah.
 */
const val OVERTIME_MINIMUM_MINUTES: Int = 30

/**
 * Bagian lembur yang terbayar setelah ambang.
 *
 * Ambang menentukan **apakah** lembur dihitung, bukan berapa yang dipotong: 31 menit terbayar
 * 31, bukan 1. Memotong ambangnya akan membuat lembur 31 menit dibayar lebih kecil daripada
 * yang 45 menit dikurangi 14 — dan tidak ada dasar hukumnya.
 *
 * @param minimumMinutes bisa diperketat perusahaan, **tidak pernah dilonggarkan** — aturan yang
 *   sama berlaku pada batas lembur Blok 3.1. Nilai 0 berarti seluruh lembur terbayar.
 */
fun payableOvertimeMinutes(
    rawOvertimeMinutes: Int,
    minimumMinutes: Int = OVERTIME_MINIMUM_MINUTES,
    roundingMinutes: Int = OVERTIME_ROUNDING_MINUTES,
): Int {
    val raw = rawOvertimeMinutes.coerceAtLeast(0)
    if (raw <= minimumMinutes) return 0
    if (roundingMinutes <= 1) return raw
    return (raw / roundingMinutes) * roundingMinutes
}

/**
 * Kelipatan pembulatan lembur terbayar: **15 menit** (keputusan Iskandar, 7 September 2026).
 *
 * ## Arah: ke BAWAH
 * Mengikuti pilihan floor pada `docs/work_calendar/SSOT.md`, dan karena membulatkan ke atas
 * berarti membayar menit yang tidak dikerjakan. Setiap nilai terbayar adalah kelipatan 15 —
 * itulah yang membuat slip gaji bisa diperiksa ulang dengan kalkulator biasa.
 *
 * ## Tebing di 30/31 menit
 * Digabung dengan [OVERTIME_MINIMUM_MINUTES], 30 menit terbayar **0** dan 31 menit terbayar
 * **30**. Satu menit menggeser 30 menit upah. Itu konsekuensi ambang "melampaui 30 menit" pada
 * PP 35/2021, bukan pilihan implementasi. Kalau tebing itu tidak diinginkan, `minimumMinutes = 0`
 * membuat pembulatan bekerja sendiri (29 -> 15, 14 -> 0).
 */
const val OVERTIME_ROUNDING_MINUTES: Int = 15

/** Rincian satu hari kerja. Semua satuan menit. */
data class DayWorkBreakdown(
    /** Kerja bersih: durasi hadir dikurangi istirahat. */
    val netMinutes: Int,
    /** Kerja bersih yang dijadwalkan: jendela dikurangi istirahat. */
    val scheduledNetMinutes: Int,
    /** Kelebihan atas jadwal. Nol bila belum melampaui. */
    val overtimeMinutes: Int,
) {
    /**
     * Bagian normal dari [netMinutes] — yaitu net dikurangi lembur.
     *
     * Diturunkan, bukan dihitung sendiri, supaya `normal + lembur == net` selalu benar dan
     * tidak ada permukaan yang bisa menampilkan dua bagian yang tidak berjumlah utuh.
     */
    val normalMinutes: Int get() = (netMinutes - overtimeMinutes).coerceAtLeast(0)

    /**
     * Lembur setelah ambang PP 35/2021 — lihat [OVERTIME_MINIMUM_MINUTES].
     *
     * Diturunkan, **berdampingan** dengan [overtimeMinutes] yang tetap mentah. Angka mentah
     * tidak pernah dibuang: sengketa upah harus bisa direkonstruksi, dan itu mustahil kalau
     * yang di bawah ambang dihapus dari catatan.
     *
     * Nilai ini belum dipakai permukaan mana pun; ia menunggu keputusan kebijakan.
     */
    val payableOvertimeMinutes: Int get() = payableOvertimeMinutes(overtimeMinutes)
}

/**
 * Rumus lembur, terpisah supaya bisa dipanggil ketika net dan netTerjadwal sudah diketahui
 * dari sumber lain (mis. dokumen `workdays` yang sudah tersimpan).
 *
 * Hari **bukan** hari kerja: seluruh kerja bersih menjadi lembur — bukan `net − 0`, karena
 * pada hari libur tidak ada jam terjadwal yang bisa dilampaui; seluruhnya di luar jadwal.
 * Perilaku ini sengaja identik dengan `computeDailySummary`.
 */
fun overtimeMinutesOf(
    netMinutes: Int,
    scheduledNetMinutes: Int,
    isWorkday: Boolean,
): Int = if (isWorkday) {
    max(0, netMinutes - scheduledNetMinutes)
} else {
    max(0, netMinutes)
}

/**
 * Kerja bersih dari sepasang jam masuk/keluar.
 *
 * `null` untuk salah satu jam berarti hari itu belum tuntas — kembalikan 0, jangan menebak.
 * Hari yang masih berjalan tidak punya angka lembur yang sah, dan menampilkan angka sementara
 * yang nanti berubah lebih buruk daripada tidak menampilkan apa pun.
 *
 * Pembulatan: **floor** ke menit utuh, mengikuti pilihan `docs/work_calendar/SSOT.md`. Pembagian
 * integer di sini sudah floor untuk nilai positif; itu disengaja, bukan kebetulan.
 */
fun netMinutesOf(
    clockInMillis: Long?,
    clockOutMillis: Long?,
    breakMinutes: Int,
): Int {
    if (clockInMillis == null || clockOutMillis == null) return 0
    if (clockOutMillis <= clockInMillis) return 0
    val gross = ((clockOutMillis - clockInMillis) / 60_000L).toInt()
    return (gross - breakMinutes).coerceAtLeast(0)
}

/** Kerja bersih terjadwal dari jendela referensi + istirahat. */
fun scheduledNetMinutesOf(
    window: AttendanceReferenceWindow,
    breakMinutes: Int,
): Int {
    val windowMinutes = ((window.endMillis - window.startMillis) / 60_000L).toInt()
    return (windowMinutes - breakMinutes).coerceAtLeast(0)
}

/**
 * Rincian satu hari — **inilah yang harus dipanggil setiap permukaan** yang menampilkan angka
 * lembur.
 *
 * @param breakMinutes istirahat menurut aturan hari itu. `AttendanceReferenceWindow` sendiri
 *   tidak membawanya (hanya `startMillis`/`endMillis`/`isExplicitPlan`), dan justru ketidaktahuan
 *   itulah yang membuat Mesin B tidak bisa dipakai untuk angka. Pemanggil mengambilnya dari
 *   `WorkSpec.breakMinutes` untuk tanggal tersebut.
 */
fun computeDayWorkBreakdown(
    clockInMillis: Long?,
    clockOutMillis: Long?,
    window: AttendanceReferenceWindow,
    breakMinutes: Int,
    isWorkday: Boolean = true,
): DayWorkBreakdown {
    val net = netMinutesOf(clockInMillis, clockOutMillis, breakMinutes)
    val scheduled = if (isWorkday) scheduledNetMinutesOf(window, breakMinutes) else 0
    return DayWorkBreakdown(
        netMinutes = net,
        scheduledNetMinutes = scheduled,
        overtimeMinutes = overtimeMinutesOf(net, scheduled, isWorkday),
    )
}

/**
 * Lembur **TERCATAT**: nilai `workdays.overtimeMinutes` apa adanya, dilantaikan ke 0.
 *
 * ## Kenapa ini punya nama sendiri (Blok 4.4, kepemilikan pelaporan)
 * PRD §7 D2 menuntut "tidak ada satu pun angka yang dihitung dengan dua rumus berbeda di dua
 * layar". Yang membuat aturan itu mudah dilanggar bukan niat buruk, melainkan **satu kata
 * dipakai untuk dua besaran**: sampai 8 September 2026 layar Data Hub dan layar Reports
 * sama-sama menulis "Overtime", padahal
 *
 * - Data Hub menjumlahkan field dokumen apa adanya — 44 menit tampil **44**;
 * - Reports/Rekap/Riwayat Kerja menampilkan yang **terbayar** lewat
 *   [payableOvertimeMinutes] — 44 menit tampil **30**, dan 30 menit tampil **0**.
 *
 * Keduanya benar untuk pertanyaan yang berbeda. Yang salah adalah memakai satu label untuk
 * keduanya, karena pembaca lalu menyimpulkan salah satunya bug.
 *
 * Jadi: **Data Hub = penjelajahan record mentah** → besaran ini, dan labelnya harus menyebut
 * "tercatat". **Reports = ringkasan periodik** → [payableOvertimeMinutes], labelnya "lembur"
 * polos karena itulah yang dibayar. Selisihnya bukan galat, dan ia disimpan dengan sengaja
 * (lihat [WorkHistoryDayMinutes.unpaidOvertimeMinutes]) supaya sengketa upah bisa
 * direkonstruksi.
 *
 * ## Ini BUKAN pengganti Mesin A
 * Fungsi ini membaca dokumen, tidak menghitung apa pun. Ia berbeda dari
 * [WorkHistoryDayMinutes.rawOvertimeMinutes] pada dua keadaan: saat catatan resminya **0**
 * (Mesin A menghitung ulang dari jam masuk/keluar) dan saat catatannya melebihi total kerja
 * (di sana dibatasi). Karena itu jangan memakainya untuk menghitung upah — pakai untuk
 * menampilkan **isi dokumen**, yang memang tugas Data Hub.
 */
fun recordedOvertimeMinutes(workDay: WorkDay): Int = workDay.overtimeMinutes.coerceAtLeast(0)
