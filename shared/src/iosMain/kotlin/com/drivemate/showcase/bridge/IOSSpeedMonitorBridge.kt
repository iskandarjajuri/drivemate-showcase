package com.drivemate.showcase.bridge

import com.drivemate.showcase.core.model.SpeedLimitConfig
import com.drivemate.showcase.core.model.SpeedViolation
import com.drivemate.showcase.core.model.UserSession
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.domain.safety.SpeedMonitorState
import com.drivemate.showcase.domain.safety.SpeedViolationDetector
import com.drivemate.showcase.domain.usecase.safety.GetSpeedLimitConfigUseCase
import com.drivemate.showcase.domain.usecase.safety.ReportSpeedViolationUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Monitor batas kecepatan untuk iOS — padanan bagian R3 di `LocationTrackingService` Android.
 *
 * `SpeedViolationDetector` sendiri MURNI dan sudah lama ada di commonMain; yang belum ada di
 * iOS adalah pemegang state-nya. Tanpa kelas ini, driver iOS tidak pernah diperingatkan saat
 * melampaui batas — dan itu bagian R3 yang paling langsung terlihat penggunanya.
 *
 * ## State ada di sini, bukan di Swift
 * Cooldown dan histeresis (`SpeedMonitorState`) menentukan berapa banyak notifikasi yang
 * dikirim ke TIGA orang sekaligus. Menyimpannya di SwiftUI berarti state itu ikut hilang tiap
 * kali view dibuang, cooldown-nya ter-reset, dan satu episode ngebut mengirim notifikasi
 * berulang-ulang. Android menyimpannya di service karena alasan yang sama.
 *
 * ## Config dibaca sekali per sesi tracking
 * `app_config/safety` jarang berubah, dan membacanya per sampel akan menembak Firestore tiap
 * 10-15 detik selama driver melaju.
 */
internal class IOSSpeedMonitorBridge(
    private val scope: CoroutineScope,
    private val getSpeedLimitConfig: GetSpeedLimitConfigUseCase,
    private val reportSpeedViolation: ReportSpeedViolationUseCase,
    private val currentSession: () -> UserSession?
) {
    private var config: SpeedLimitConfig? = null
    private var configLoaded = false
    private var state = SpeedMonitorState()

    /**
     * Muat config batas kecepatan. Panggil sekali saat mulai melacak.
     *
     * Gagal baca DIPERLAKUKAN SAMA dengan "belum dikonfigurasi": deteksi mati. Menganggapnya
     * "mungkin ada batas" akan memunculkan banner berdasarkan angka yang tidak diketahui.
     */
    fun loadConfig(completion: (Boolean) -> Unit) {
        scope.launch {
            config = when (val res = getSpeedLimitConfig()) {
                is AppResult.Success -> res.data
                is AppResult.Failure -> null
            }
            configLoaded = true
            completion(config != null)
        }
    }

    /** Lupakan state dan config — dipanggil saat driver berhenti bertugas. */
    fun reset() {
        state = SpeedMonitorState()
        config = null
        configLoaded = false
    }

    /**
     * Evaluasi satu sampel kecepatan.
     *
     * Mengembalikan status untuk banner lewat [completion]; penulisan `speed_violations`
     * dilakukan di sini juga bila detektor bilang layak dinotifikasi.
     *
     * @param speedKph kecepatan sekarang; null bila GPS belum menyediakannya — detektor
     *   sengaja tidak mengklaim apa pun dalam keadaan itu.
     */
    fun evaluateSample(
        speedKph: Double?,
        latitude: Double?,
        longitude: Double?,
        completion: (IOSSpeedStatusSnapshot) -> Unit
    ) {
        val now = TimeUtil.nowEpochMillis()
        val result = SpeedViolationDetector.evaluate(
            speedKph = speedKph,
            config = config,
            state = state,
            nowMillis = now
        )
        state = result.state

        completion(
            IOSSpeedStatusSnapshot(
                overSpeedLimit = result.isOverLimit,
                currentSpeedKph = speedKph,
                speedLimitKph = config?.speedLimitKph
            )
        )

        if (!result.shouldNotify) return
        val uid = currentSession()?.uid ?: return
        val limit = config?.speedLimitKph ?: return
        val over = speedKph ?: return

        // Tidak melog kecepatan/koordinat/uid — aturan "tidak ada data sensitif di log",
        // sama seperti FirebaseSpeedViolationRepository Android.
        scope.launch {
            reportSpeedViolation(
                SpeedViolation(
                    driverUid = uid,
                    speedKph = over,
                    speedLimitKph = limit,
                    occurredAtMillis = now,
                    latitude = latitude,
                    longitude = longitude
                )
            )
        }
    }
}

/**
 * Status kecepatan untuk banner in-app.
 *
 * Angka dibawa MENTAH, bukan sebagai teks: Android memformatnya lewat string resource
 * `driver_speed_limit_banner` dengan pembulatan di UI, dan iOS harus melakukan hal yang sama
 * supaya kedua platform menampilkan angka yang sepakat.
 */
data class IOSSpeedStatusSnapshot(
    val overSpeedLimit: Boolean,
    val currentSpeedKph: Double?,
    val speedLimitKph: Double?
)
