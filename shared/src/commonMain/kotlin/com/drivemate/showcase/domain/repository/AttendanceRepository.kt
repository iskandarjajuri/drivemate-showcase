package com.drivemate.showcase.domain.repository

import com.drivemate.showcase.core.model.Attendance
import com.drivemate.showcase.core.model.TodayAttendance
import com.drivemate.showcase.core.util.AppResult

/**
 * Kontrak repository untuk data kehadiran (attendance) lintas platform (KMM).
 *
 * Layer: **DOMAIN** — interface abstrak yang diimplementasikan oleh layer **DATA**
 * (misalnya `FirebaseAttendanceRepository` atau `InMemoryAttendanceRepository`).
 *
 * Semua fungsi bersifat `suspend` dan harus dipanggil dari coroutine.
 *
 * Semua hasil operasi dikemas dalam [AppResult] supaya:
 * - Aman dari exception (error terwakili sebagai `AppResult.Failure`)
 * - Konsisten di Android & iOS (KMM)
 * - Mudah diobservasi di ViewModel/UI (mis. via `onSuccess` / `onFailure`)
 *
 * Catatan istilah:
 * - `driverUid` selalu merujuk ke **Firebase Auth UID** (bukan email atau auto ID lain).
 * - Batas "hari ini" mengikuti zona waktu perangkat dan util [com.drivemate.showcase.core.util.TimeUtil] (range [start, end)).
 */
interface AttendanceRepository {

    // --------------------------------------------------
    // 🟢 TAP IN
    // --------------------------------------------------

    /**
     * Melakukan Tap‑In (check‑in) untuk driver.
     *
     * @param att entity [Attendance] minimal berisi:
     *  - [Attendance.driverUid] — **harus sama** dengan `FirebaseAuth.currentUser?.uid`
     *  - [Attendance.timestampMillis] — epoch millis saat tap dilakukan
     *  - (opsional) [Attendance.latitude], [Attendance.longitude], [Attendance.note]
     *
     * Perilaku yang diharapkan implementasi:
     * - Menolak Tap‑In jika sudah ada Tap‑In aktif pada hari yang sama (belum di‑Tap‑Out).
     *
     * @return [AppResult.Success] bila sukses, atau [AppResult.Failure] bila gagal.
     */
    suspend fun checkIn(att: Attendance): AppResult<Unit>

    // --------------------------------------------------
    // 🔴 TAP OUT
    // --------------------------------------------------

    /**
     * Melakukan Tap‑Out (check‑out) untuk driver aktif.
     *
     * Implementasi wajib mengikat Tap‑Out ke Tap‑In terakhir pada hari ini
     * yang belum ditutup (belum memiliki "OUT").
     *
     * @return [AppResult.Success] bila sukses,
     *         [AppResult.Failure] bila belum Tap‑In hari ini atau terjadi kesalahan lain.
     */
    suspend fun checkOut(
        timestampMillis: Long,
        locationName: String? = null,
        latitude: Double? = null,
        longitude: Double? = null
    ): AppResult<Unit>

    // --------------------------------------------------
    // 📅 DAILY ATTENDANCE (1 doc per day)
    // --------------------------------------------------

    suspend fun upsertTodayClockIn(
        driverUid: String,
        epochDay: Long,
        nowMillis: Long
    ): AppResult<TodayAttendance>

    suspend fun upsertTodayClockOut(
        driverUid: String,
        epochDay: Long,
        nowMillis: Long
    ): AppResult<TodayAttendance>

    suspend fun getTodayAttendance(
        driverUid: String,
        epochDay: Long
    ): AppResult<TodayAttendance?>

    /**
     * Mengambil daftar attendance mentah dalam rentang epoch-day [startEpochDay, endEpochDay]
     * (inklusif) untuk driver tertentu.
     *
     * Implementasi diharapkan mengembalikan data terurut naik berdasarkan timestamp.
     */
    suspend fun getAttendancesByEpochDayRange(
        driverUid: String,
        startEpochDay: Long,
        endEpochDay: Long
    ): AppResult<List<Attendance>>

    // --------------------------------------------------
    // 📅 GET TODAY
    // --------------------------------------------------

    /**
     * Mengambil data presensi driver untuk **hari ini**.
     *
     * Definisi "hari ini":
     * - Menggunakan [com.drivemate.showcase.core.util.TimeUtil.todayRangeMillis] dengan zona waktu perangkat;
     *   rentang waktu adalah [start, end) → 00:00 lokal hingga 00:00 esok hari (eksklusif).
     *
     * @param driverUid UID driver (Firebase Auth).
     * @return [AppResult.Success] dengan [Attendance] jika ada,
     *         atau `null` bila belum Tap‑In; [AppResult.Failure] jika terjadi kesalahan.
     */
    suspend fun getToday(driverUid: String): AppResult<Attendance?>
}
