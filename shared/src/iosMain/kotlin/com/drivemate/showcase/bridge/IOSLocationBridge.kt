package com.drivemate.showcase.bridge

import com.drivemate.showcase.core.model.Location
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.data.repository.IOSLocationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class IOSLocationBridge(
    private val repository: IOSLocationRepository,
    private val scope: CoroutineScope
) {
    private var stopObserver: (() -> Unit)? = null

    /**
     * Publikasikan satu sampel lokasi driver.
     *
     * Swift hanya menyerahkan angka mentah dari CoreLocation; bentuk dokumen, clamp
     * timestamp, dan turunan kecepatan disusun `buildLocationWritePayload` di commonMain —
     * fungsi yang SAMA dipakai Android.
     */
    fun publishDriverLocation(
        driverUid: String,
        latitude: Double,
        longitude: Double,
        speedMps: Double?,
        headingDeg: Double?,
        accuracyM: Double?,
        completion: (Boolean, String?) -> Unit
    ) {
        scope.launch {
            val result = repository.updateDriverLocation(
                Location(
                    driverUid = driverUid,
                    latitude = latitude,
                    longitude = longitude,
                    speedMps = speedMps,
                    headingDeg = headingDeg,
                    accuracyM = accuracyM,
                    updatedAtMillis = TimeUtil.nowEpochMillis()
                )
            )
            when (result) {
                is AppResult.Success -> completion(true, null)
                is AppResult.Failure -> completion(false, result.throwable.message)
            }
        }
    }

    /** Berhenti berbagi: dokumen lokasi DIHAPUS, bukan ditulis kosong. */
    fun clearDriverLocation(completion: (Boolean, String?) -> Unit) {
        scope.launch {
            when (val result = repository.clearDriverLocation()) {
                is AppResult.Success -> completion(true, null)
                is AppResult.Failure -> completion(false, result.throwable.message)
            }
        }
    }

    fun startObservingAllDriverLocations(onChange: (List<IOSDriverLocationSnapshot>) -> Unit) {
        stopObservingLocations()
        stopObserver = repository.observeAllDriverLocationPayloads { payloads ->
            onChange(payloads.map { it.toIosSnapshot() })
        }
    }

    fun startObservingDriverLocation(
        driverUid: String,
        onChange: (IOSDriverLocationSnapshot?) -> Unit
    ) {
        stopObservingLocations()
        stopObserver = repository.observeDriverLocationPayload(driverUid) { payload ->
            onChange(payload?.toIosSnapshot())
        }
    }

    fun stopObservingLocations() {
        stopObserver?.invoke()
        stopObserver = null
    }
}
