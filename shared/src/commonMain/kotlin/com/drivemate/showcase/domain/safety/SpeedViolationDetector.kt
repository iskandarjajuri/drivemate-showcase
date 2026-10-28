package com.drivemate.showcase.domain.safety

import com.drivemate.showcase.core.model.SpeedLimitConfig

/**
 * State mesin deteksi kecepatan untuk SATU driver. Disimpan di sisi pemanggil
 * (Android: `LocationTrackingService`), bukan di detektor — detektor sendiri murni.
 */
data class SpeedMonitorState(
    /** Kapan notifikasi terakhir dikirim. 0 = belum pernah / sudah di-reset. */
    val lastNotifiedAtMillis: Long = 0L,
    /** Kapan driver mulai berada di bawah batas secara meyakinkan. 0 = sedang tidak di bawah. */
    val belowLimitSinceMillis: Long = 0L
)

data class SpeedCheckResult(
    val state: SpeedMonitorState,
    /** Driver sedang di atas batas SEKARANG — ini yang menyalakan banner in-app. */
    val isOverLimit: Boolean,
    /** Pelanggaran baru yang layak dinotifikasi — ini yang memicu tulis `speed_violations`. */
    val shouldNotify: Boolean
)

/**
 * Deteksi pelanggaran batas kecepatan — fungsi murni, tanpa I/O, tanpa waktu sistem
 * (semua lewat parameter `nowMillis`) sehingga bisa diuji deterministik di `commonTest`.
 *
 * ## Kenapa deteksi di klien, bukan di Cloud Function
 * Server hanya bisa mengevaluasi field `speedKph` yang DIKIRIM klien — jadi deteksi
 * sisi server tidak menambah kepercayaan sedikit pun dibanding deteksi sisi klien,
 * sementara ongkosnya besar (`locations/{uid}` ditulis tiap 10–15 detik saat driver
 * melaju). Klien juga melihat SETIAP sampel GPS, bukan cuma yang lolos throttle tulis,
 * jadi deteksinya justru lebih responsif. Banner in-app pun wajib sisi klien.
 * Cloud Function tetap dipakai, tapi hanya untuk fan-out notifikasi ke 3 penerima.
 *
 * ## Cooldown & histeresis
 * - [COOLDOWN_MS] = 5 menit. Tanpa ini, satu episode ngebut berkelanjutan mengirim
 *   notifikasi tiap 10–15 detik ke 3 orang sekaligus. 5 menit menekan spam di dalam
 *   satu episode (episode ngebut khas berlangsung < 5 menit) tapi tetap memunculkan
 *   perilaku berulang yang genuinely terpisah — 10 menit terlalu lama untuk sinyal
 *   keselamatan, 1–2 menit masih terasa spam bagi penerima.
 * - [CLEARANCE_BAND_KPH] + [SUSTAINED_BELOW_MS]: cooldown TIDAK langsung hilang begitu
 *   kecepatan turun di bawah batas. Tanpa histeresis, driver yang berkendara di 79/81
 *   km/jam terhadap batas 80 akan bolak-balik melewati ambang dan memicu notifikasi
 *   ulang tiap crossing — persis yang dicegah cooldown. Cooldown baru di-reset kalau
 *   kecepatan turun minimal 5 km/jam di bawah batas DAN bertahan begitu 60 detik.
 */
object SpeedViolationDetector {

    const val COOLDOWN_MS: Long = 5 * 60 * 1000L
    const val CLEARANCE_BAND_KPH: Double = 5.0
    const val SUSTAINED_BELOW_MS: Long = 60 * 1000L

    fun evaluate(
        speedKph: Double?,
        config: SpeedLimitConfig?,
        state: SpeedMonitorState,
        nowMillis: Long
    ): SpeedCheckResult {
        // Fail-closed: config belum ada / dimatikan / nilainya tidak masuk akal.
        if (config == null || !config.enabled || config.speedLimitKph <= 0.0) {
            return SpeedCheckResult(SpeedMonitorState(), isOverLimit = false, shouldNotify = false)
        }
        // Tidak ada data kecepatan (GPS belum lock / provider tidak menyediakan):
        // jangan mengklaim apa pun, dan jangan sentuh state.
        if (speedKph == null) {
            return SpeedCheckResult(state, isOverLimit = false, shouldNotify = false)
        }

        if (speedKph < config.speedLimitKph) {
            return evaluateBelowLimit(speedKph, config, state, nowMillis)
        }

        val cooldownExpired = state.lastNotifiedAtMillis == 0L ||
            (nowMillis - state.lastNotifiedAtMillis) >= COOLDOWN_MS

        return if (cooldownExpired) {
            SpeedCheckResult(
                state = SpeedMonitorState(lastNotifiedAtMillis = nowMillis, belowLimitSinceMillis = 0L),
                isOverLimit = true,
                shouldNotify = true
            )
        } else {
            SpeedCheckResult(
                state = state.copy(belowLimitSinceMillis = 0L),
                isOverLimit = true,
                shouldNotify = false
            )
        }
    }

    private fun evaluateBelowLimit(
        speedKph: Double,
        config: SpeedLimitConfig,
        state: SpeedMonitorState,
        nowMillis: Long
    ): SpeedCheckResult {
        val comfortablyBelow = speedKph <= config.speedLimitKph - CLEARANCE_BAND_KPH
        if (!comfortablyBelow) {
            // Zona mati histeresis: di bawah batas tapi belum cukup jauh untuk
            // dianggap "sudah melambat". Tahan state apa adanya.
            return SpeedCheckResult(state, isOverLimit = false, shouldNotify = false)
        }

        val since = if (state.belowLimitSinceMillis == 0L) nowMillis else state.belowLimitSinceMillis
        val sustained = (nowMillis - since) >= SUSTAINED_BELOW_MS

        val newState = if (sustained) {
            SpeedMonitorState()
        } else {
            state.copy(belowLimitSinceMillis = since)
        }
        return SpeedCheckResult(newState, isOverLimit = false, shouldNotify = false)
    }
}
