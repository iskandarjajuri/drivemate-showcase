package com.drivemate.showcase.tracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.drivemate.showcase.R
import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.util.UiErrorLogger
import com.drivemate.showcase.core.model.Location as DriverLocation
import com.drivemate.showcase.core.model.TrackingIssue
import com.drivemate.showcase.core.model.SpeedLimitConfig
import com.drivemate.showcase.core.model.SpeedViolation
import com.drivemate.showcase.core.model.TrackingStatus
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.SpeedTier
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.core.util.speedKphFromMps
import com.drivemate.showcase.core.util.speedTierFromMps
import com.drivemate.showcase.domain.repository.AuthRepository
import com.drivemate.showcase.domain.safety.SpeedMonitorState
import com.drivemate.showcase.domain.safety.SpeedViolationDetector
import com.drivemate.showcase.domain.usecase.location.UpdateDriverLocationUseCase
import com.drivemate.showcase.domain.usecase.safety.GetSpeedLimitConfigUseCase
import com.drivemate.showcase.domain.usecase.safety.ReportSpeedViolationUseCase
import com.drivemate.showcase.domain.usecase.tracking.ObserveTrackingStatusUseCase
import com.drivemate.showcase.domain.usecase.tracking.UpdateTrackingStatusUseCase
import com.drivemate.showcase.location.hasLocationPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.getKoin
import kotlin.math.abs
import kotlin.random.Random

/**
 * Foreground service untuk live tracking driver saat on-duty.
 */
class LocationTrackingService : Service() {
    companion object {
        const val ACTION_START = "com.drivemate.showcase.tracking.START"
        const val ACTION_STOP = "com.drivemate.showcase.tracking.STOP"
        const val ACTION_STOP_PERMISSION_MISSING =
            "com.drivemate.showcase.tracking.STOP_PERMISSION_MISSING"
        private const val CHANNEL_ID = "driver_tracking"
        private const val NOTIF_ID = 2101

        private const val INTERVAL_IDLE_MIN_MS = 60_000L
        private const val INTERVAL_IDLE_MAX_MS = 120_000L
        private const val INTERVAL_SLOW_MIN_MS = 20_000L
        private const val INTERVAL_SLOW_MAX_MS = 30_000L
        private const val INTERVAL_FAST_MIN_MS = 10_000L
        private const val INTERVAL_FAST_MAX_MS = 15_000L

        private const val DISTANCE_IDLE_MIN_M = 10.0
        private const val DISTANCE_IDLE_MAX_M = 15.0
        private const val DISTANCE_SLOW_MIN_M = 15.0
        private const val DISTANCE_SLOW_MAX_M = 25.0
        private const val DISTANCE_FAST_MIN_M = 30.0
        private const val DISTANCE_FAST_MAX_M = 50.0

        private const val JITTER_FRACTION = 0.1
        private const val REQUEST_MIN_DISTANCE_M = 5f
        private const val LOG_TAG = "DM/DriverTracking"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val updateLocation by lazy { getKoin().get<UpdateDriverLocationUseCase>() }
    private val observeTrackingStatus by lazy { getKoin().get<ObserveTrackingStatusUseCase>() }
    private val updateTrackingStatus by lazy { getKoin().get<UpdateTrackingStatusUseCase>() }
    private val authRepo by lazy { getKoin().get<AuthRepository>() }
    private val getSpeedLimitConfig by lazy { getKoin().get<GetSpeedLimitConfigUseCase>() }
    private val reportSpeedViolation by lazy { getKoin().get<ReportSpeedViolationUseCase>() }
    private val random = Random.Default

    private var driverUid: String? = null
    private var locationCallback: LocationCallback? = null
    private var currentIntervalMs: Long = INTERVAL_IDLE_MAX_MS
    private var currentSpeedTier: SpeedTier? = null
    private var lastWrittenAtMillis: Long = 0L
    private var lastWrittenLocation: Location? = null
    private var trackingActive: Boolean = false
    private var batteryOptimizationEnabled: Boolean = false
    private var monitorJob: Job? = null

    // R3 — deteksi batas kecepatan (sisi klien; lihat SpeedViolationDetector untuk alasannya).
    private var speedLimitConfig: SpeedLimitConfig? = null
    private var speedMonitorState = SpeedMonitorState()
    private var overSpeedLimit: Boolean = false
    private var lastSpeedKph: Double? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopTracking(TrackingStopReason.UserRequested)
            ACTION_STOP_PERMISSION_MISSING -> stopTracking(TrackingStopReason.PermissionMissing)
            else -> startTracking()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopTrackingInternal()
        super.onDestroy()
    }

    private fun startTracking() {
        if (trackingActive) return
        batteryOptimizationEnabled = isBatteryOptimizationEnabled()
        val precheckIssue = checkPreconditions()
        if (precheckIssue != null) {
            val reason = when (precheckIssue) {
                TrackingIssue.PermissionMissing -> WriteSkipReason.PERMISSION_MISSING
                TrackingIssue.ProviderDisabled -> WriteSkipReason.PROVIDER_DISABLED
            }
            logDecision(
                TrackingDecision.skip(
                    reason = reason,
                    intervalMs = currentIntervalMs,
                    distanceThresholdM = 0.0,
                    distanceDeltaM = 0.0
                ),
                speedMps = null
            )
            updateStatus(active = false, issue = precheckIssue)
            stopSelf()
            return
        }
        startForeground(NOTIF_ID, buildNotification())
        trackingActive = true
        lastWrittenAtMillis = 0L
        lastWrittenLocation = null
        // R3: mulai dari state bersih tiap sesi tracking.
        speedMonitorState = SpeedMonitorState()
        overSpeedLimit = false
        lastSpeedKph = null
        loadSpeedLimitConfig()
        updateStatus(active = true, issue = null)

        serviceScope.launch {
            val uid = when (val res = authRepo.currentUser()) {
                is AppResult.Success -> res.data?.uid
                is AppResult.Failure -> null
            }
            if (uid.isNullOrBlank()) {
                stopTracking(TrackingStopReason.Unknown)
                return@launch
            }
            driverUid = uid
            requestUpdates(intervalForTier(SpeedTier.Idle))
            startMonitor()
        }
    }

    private fun stopTracking(reason: TrackingStopReason) {
        stopTrackingInternal()
        stopForeground(STOP_FOREGROUND_REMOVE)
        updateStatus(active = false, issue = reason.toIssue())
        stopSelf()
    }

    private fun stopTrackingInternal() {
        trackingActive = false
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        locationCallback = null
        currentSpeedTier = null
        // R3: jangan biarkan banner "melebihi batas" menyala setelah tracking berhenti.
        overSpeedLimit = false
        lastSpeedKph = null
        speedMonitorState = SpeedMonitorState()
        monitorJob?.cancel()
        monitorJob = null
    }

    private fun requestUpdates(intervalMs: Long) {
        if (!hasLocationPermission(this)) {
            logTrackingError("permission_missing")
            handleIssue(TrackingIssue.PermissionMissing)
            return
        }
        val request = LocationRequest.Builder(intervalMs)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .setMaxUpdateDelayMillis(intervalMs * 2)
            .setMinUpdateDistanceMeters(REQUEST_MIN_DISTANCE_M)
            .build()

        val callback = locationCallback ?: object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val issue = checkPreconditions()
                if (issue != null) {
                    handleIssue(issue)
                    return
                }
                val latest = result.lastLocation ?: return
                handleLocation(latest)
            }

            override fun onLocationAvailability(availability: com.google.android.gms.location.LocationAvailability) {
                if (availability.isLocationAvailable) return
                if (!isProviderEnabled()) {
                    handleIssue(TrackingIssue.ProviderDisabled)
                }
            }
        }.also { locationCallback = it }

        @SuppressLint("MissingPermission")
        runCatching {
            fusedClient.removeLocationUpdates(callback)
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        }.onFailure { err ->
            if (err is SecurityException) {
                UiErrorLogger.e("DM/Tracking", "request updates security exception", err)
                logTrackingError("security_exception")
                handleIssue(TrackingIssue.PermissionMissing)
                return
            }
            UiErrorLogger.e("DM/Tracking", "request updates failed", err)
            logTrackingError("request_updates_failed")
            val issue = checkPreconditions()
            if (issue != null) {
                handleIssue(issue)
            } else {
                stopTracking(TrackingStopReason.Unknown)
            }
        }
        currentIntervalMs = intervalMs
    }

    private fun handleLocation(loc: Location) {
        val now = TimeUtil.nowEpochMillis()
        val speedMps = if (loc.hasSpeed()) loc.speed.toDouble() else null
        val tier = speedTierFromMps(speedMps)
        val requestedInterval = intervalForTier(tier)
        if (currentSpeedTier != tier || abs(requestedInterval - currentIntervalMs) > 1000) {
            requestUpdates(requestedInterval)
            currentSpeedTier = tier
        }

        // R3: dievaluasi pada SETIAP sampel GPS — sengaja sebelum decideWrite(), karena
        // throttle tulis di bawah disetel untuk tampilan peta, bukan untuk keselamatan.
        evaluateSpeedLimit(speedMps, loc, now)

        val decision = decideWrite(loc, now, speedMps, tier)
        logDecision(decision, speedMps)
        if (!decision.shouldWrite) return

        lastWrittenAtMillis = now
        lastWrittenLocation = loc

        val uid = driverUid ?: return
        val payload = DriverLocation(
            driverUid = uid,
            latitude = loc.latitude,
            longitude = loc.longitude,
            speedMps = speedMps,
            speedKph = speedKphFromMps(speedMps),
            headingDeg = loc.bearing.toDouble(),
            accuracyM = loc.accuracy.toDouble(),
            updatedAtMillis = now,
            locationName = null
        )

        serviceScope.launch {
            updateLocation(payload)
        }
    }

    private fun intervalForTier(tier: SpeedTier): Long = when (tier) {
        SpeedTier.Idle, SpeedTier.Unknown -> midpointInterval(INTERVAL_IDLE_MIN_MS, INTERVAL_IDLE_MAX_MS)
        SpeedTier.Slow -> midpointInterval(INTERVAL_SLOW_MIN_MS, INTERVAL_SLOW_MAX_MS)
        SpeedTier.Fast -> midpointInterval(INTERVAL_FAST_MIN_MS, INTERVAL_FAST_MAX_MS)
    }

    private fun decideWrite(
        loc: Location,
        now: Long,
        speedMps: Double?,
        tier: SpeedTier
    ): TrackingDecision {
        driverUid ?: return TrackingDecision.skip(
            reason = WriteSkipReason.NOT_ON_DUTY,
            intervalMs = currentIntervalMs,
            distanceThresholdM = 0.0,
            distanceDeltaM = 0.0
        )
        if (loc.latitude.isNaN() || loc.longitude.isNaN()) {
            return TrackingDecision.skip(
                reason = WriteSkipReason.NO_LOCATION,
                intervalMs = currentIntervalMs,
                distanceThresholdM = 0.0,
                distanceDeltaM = 0.0
            )
        }

        val policy = policyForTier(tier)
        val intervalMs = jitterInterval(policy.minIntervalMs, policy.maxIntervalMs)
        val distanceThresholdM = jitterDistance(policy.minDistanceM, policy.maxDistanceM)

        if (lastWrittenLocation == null || lastWrittenAtMillis == 0L) {
            return TrackingDecision.write(intervalMs, distanceThresholdM, 0.0)
        }

        val last = lastWrittenLocation ?: return TrackingDecision.write(intervalMs, distanceThresholdM, 0.0)
        val distanceDelta = distanceMeters(
            last.latitude,
            last.longitude,
            loc.latitude,
            loc.longitude
        ).toDouble()
        val elapsed = now - lastWrittenAtMillis
        val tooSoon = elapsed < intervalMs
        val tooClose = distanceDelta < distanceThresholdM

        if (tooSoon && tooClose) {
            val reason = WriteSkipReason.TOO_SOON
            return TrackingDecision.skip(reason, intervalMs, distanceThresholdM, distanceDelta)
        }

        return TrackingDecision.write(intervalMs, distanceThresholdM, distanceDelta)
    }

    private fun logDecision(decision: TrackingDecision, speedMps: Double?) {
        val speedLabel = speedMps?.let { "%.2f".format(it) } ?: "null"
        val reason = decision.reason?.name ?: "NONE"
        val distanceLabel = "%.1f".format(decision.distanceDeltaM)
        val thresholdLabel = "%.1f".format(decision.distanceThresholdM)
        DMLog.d(
            LOG_TAG,
            "tracking_decision intervalChosenMs=${decision.intervalMs} " +
                "speedMps=$speedLabel distanceDeltaM=$distanceLabel " +
                "distanceThresholdM=$thresholdLabel writeSkippedReason=$reason"
        )
    }

    private fun policyForTier(tier: SpeedTier): TrackingPolicy = when (tier) {
        SpeedTier.Idle, SpeedTier.Unknown -> TrackingPolicy(
            minIntervalMs = INTERVAL_IDLE_MIN_MS,
            maxIntervalMs = INTERVAL_IDLE_MAX_MS,
            minDistanceM = DISTANCE_IDLE_MIN_M,
            maxDistanceM = DISTANCE_IDLE_MAX_M
        )
        SpeedTier.Slow -> TrackingPolicy(
            minIntervalMs = INTERVAL_SLOW_MIN_MS,
            maxIntervalMs = INTERVAL_SLOW_MAX_MS,
            minDistanceM = DISTANCE_SLOW_MIN_M,
            maxDistanceM = DISTANCE_SLOW_MAX_M
        )
        SpeedTier.Fast -> TrackingPolicy(
            minIntervalMs = INTERVAL_FAST_MIN_MS,
            maxIntervalMs = INTERVAL_FAST_MAX_MS,
            minDistanceM = DISTANCE_FAST_MIN_M,
            maxDistanceM = DISTANCE_FAST_MAX_M
        )
    }

    private fun jitterInterval(minMs: Long, maxMs: Long): Long {
        val base = random.nextLong(minMs, maxMs + 1)
        val jittered = (base * (1 + randomJitter())).toLong()
        return jittered.coerceIn(minMs, maxMs)
    }

    private fun jitterDistance(minM: Double, maxM: Double): Double {
        val base = random.nextDouble(minM, maxM)
        val jittered = base * (1 + randomJitter())
        return jittered.coerceIn(minM, maxM)
    }

    private fun midpointInterval(minMs: Long, maxMs: Long): Long =
        minMs + ((maxMs - minMs) / 2)

    private fun randomJitter(): Double =
        random.nextDouble(-JITTER_FRACTION, JITTER_FRACTION)

    private fun distanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    private fun checkPreconditions(): TrackingIssue? {
        if (!hasLocationPermission(this)) return TrackingIssue.PermissionMissing
        if (!isProviderEnabled()) return TrackingIssue.ProviderDisabled
        return null
    }

    private fun startMonitor() {
        if (monitorJob?.isActive == true) return
        monitorJob = serviceScope.launch {
            while (trackingActive) {
                delay(30_000)
                val issue = checkPreconditions()
                if (issue != null) {
                    handleIssue(issue)
                    break
                }
            }
        }
    }

    private fun handleIssue(issue: TrackingIssue) {
        val reason = when (issue) {
            TrackingIssue.PermissionMissing -> WriteSkipReason.PERMISSION_MISSING
            TrackingIssue.ProviderDisabled -> WriteSkipReason.PROVIDER_DISABLED
        }
        logDecision(
            TrackingDecision.skip(
                reason = reason,
                intervalMs = currentIntervalMs,
                distanceThresholdM = 0.0,
                distanceDeltaM = 0.0
            ),
            speedMps = null
        )
        stopTracking(issue.toStopReason())
    }

    /**
     * R3 — evaluasi batas kecepatan pada setiap sampel GPS.
     *
     * Deteksi ada di sini, BUKAN di Cloud Function: server hanya bisa menilai field
     * `speedKph` yang dikirim klien ini juga, jadi deteksi sisi server tidak menambah
     * kepercayaan sama sekali sementara ongkosnya besar (`locations/{uid}` ditulis tiap
     * 10-15 detik saat melaju). Cloud Function tetap dipakai untuk fan-out notifikasi.
     *
     * Cooldown ADA DI DUA TEMPAT dan itu disengaja: di sini untuk menekan tulis yang
     * tidak perlu, dan lagi di Cloud Function untuk menekan fan-out. Klien tidak boleh
     * jadi satu-satunya penjaga anti-spam terhadap tiga penerima notifikasi.
     */
    private fun evaluateSpeedLimit(speedMps: Double?, loc: Location, now: Long) {
        val speedKph = speedKphFromMps(speedMps)
        lastSpeedKph = speedKph

        val result = SpeedViolationDetector.evaluate(
            speedKph = speedKph,
            config = speedLimitConfig,
            state = speedMonitorState,
            nowMillis = now
        )
        speedMonitorState = result.state

        if (result.isOverLimit != overSpeedLimit) {
            overSpeedLimit = result.isOverLimit
            updateStatus(active = trackingActive, issue = null)
        } else {
            overSpeedLimit = result.isOverLimit
        }

        if (!result.shouldNotify) return

        val uid = driverUid ?: return
        val limit = speedLimitConfig?.speedLimitKph ?: return
        val over = speedKph ?: return

        DMLog.i(LOG_TAG, "speed violation detected (cooldown passed) — reporting event")
        serviceScope.launch {
            when (
                val res = reportSpeedViolation(
                    SpeedViolation(
                        driverUid = uid,
                        speedKph = over,
                        speedLimitKph = limit,
                        occurredAtMillis = now,
                        latitude = loc.latitude,
                        longitude = loc.longitude
                    )
                )
            ) {
                is AppResult.Success -> DMLog.i(LOG_TAG, "speed violation reported")
                is AppResult.Failure ->
                    DMLog.w(LOG_TAG, "speed violation report failed: ${res.throwable.message}")
            }
        }
    }

    /**
     * Muat konfigurasi batas kecepatan sekali saat tracking mulai. Fail-closed:
     * kalau `app_config/safety` belum ada atau gagal dibaca, [speedLimitConfig] tetap
     * null dan detektor mematikan dirinya sendiri.
     */
    private fun loadSpeedLimitConfig() {
        serviceScope.launch {
            when (val res = getSpeedLimitConfig()) {
                is AppResult.Success -> {
                    val cfg = res.data
                    speedLimitConfig = cfg
                    if (cfg == null) {
                        DMLog.i(LOG_TAG, "speed limit config absent — R3 detection disabled (fail-closed)")
                    } else {
                        DMLog.i(LOG_TAG, "speed limit config loaded (enabled=${cfg.enabled})")
                    }
                }
                is AppResult.Failure -> {
                    speedLimitConfig = null
                    DMLog.w(LOG_TAG, "speed limit config read failed — R3 disabled: ${res.throwable.message}")
                }
            }
        }
    }

    private fun updateStatus(active: Boolean, issue: TrackingIssue?) {
        val hideLocationWhenOffDuty = observeTrackingStatus().value.hideLocationWhenOffDuty
        updateTrackingStatus(
            TrackingStatus(
                active = active,
                issue = issue,
                batteryOptimizationEnabled = batteryOptimizationEnabled,
                hideLocationWhenOffDuty = hideLocationWhenOffDuty,
                overSpeedLimit = overSpeedLimit,
                currentSpeedKph = lastSpeedKph,
                speedLimitKph = speedLimitConfig?.speedLimitKph
            )
        )
    }

    private fun isProviderEnabled(): Boolean {
        if (!hasLocationPermission(this)) return false
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        val gpsEnabled = runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }
            .onFailure { err ->
                if (err is SecurityException) {
                    UiErrorLogger.e("DM/Tracking", "provider check security exception", err)
                }
            }
            .getOrDefault(false)
        val networkEnabled = runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }
            .onFailure { err ->
                if (err is SecurityException) {
                    UiErrorLogger.e("DM/Tracking", "provider check security exception", err)
                }
            }
            .getOrDefault(false)
        return gpsEnabled || networkEnabled
    }

    private fun isBatteryOptimizationEnabled(): Boolean {
        val power = getSystemService(POWER_SERVICE) as PowerManager
        val ignoring = power.isIgnoringBatteryOptimizations(packageName)
        return !ignoring
    }

    private data class TrackingPolicy(
        val minIntervalMs: Long,
        val maxIntervalMs: Long,
        val minDistanceM: Double,
        val maxDistanceM: Double
    )

    private data class TrackingDecision(
        val shouldWrite: Boolean,
        val intervalMs: Long,
        val distanceThresholdM: Double,
        val distanceDeltaM: Double,
        val reason: WriteSkipReason?
    ) {
        companion object {
            fun write(intervalMs: Long, distanceThresholdM: Double, distanceDeltaM: Double) =
                TrackingDecision(true, intervalMs, distanceThresholdM, distanceDeltaM, null)

            fun skip(
                reason: WriteSkipReason,
                intervalMs: Long,
                distanceThresholdM: Double,
                distanceDeltaM: Double
            ) = TrackingDecision(false, intervalMs, distanceThresholdM, distanceDeltaM, reason)
        }
    }

    private enum class WriteSkipReason {
        TOO_SOON,
        TOO_CLOSE,
        NO_LOCATION,
        PERMISSION_MISSING,
        PROVIDER_DISABLED,
        NOT_ON_DUTY,
        UNKNOWN
    }

    private enum class TrackingStopReason {
        UserRequested,
        PermissionMissing,
        ProviderDisabled,
        Unknown
    }

    private fun TrackingStopReason.toIssue(): TrackingIssue? = when (this) {
        TrackingStopReason.PermissionMissing -> TrackingIssue.PermissionMissing
        TrackingStopReason.ProviderDisabled -> TrackingIssue.ProviderDisabled
        else -> null
    }

    private fun TrackingIssue.toStopReason(): TrackingStopReason = when (this) {
        TrackingIssue.PermissionMissing -> TrackingStopReason.PermissionMissing
        TrackingIssue.ProviderDisabled -> TrackingStopReason.ProviderDisabled
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(getString(R.string.tracking_notification_title))
            .setContentText(getString(R.string.tracking_notification_content))
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun ensureChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.tracking_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun logTrackingError(message: String) {
        val uid = driverUid ?: "unknown"
        DMLog.e(
            "[DRIVER_HISTORY]",
            "authUid=$uid role=driver success=false error=$message"
        )
    }
}
