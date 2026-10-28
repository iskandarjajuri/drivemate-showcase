package com.drivemate.showcase.data.repository

import com.drivemate.showcase.bridge.IOSLocationPayload
import com.drivemate.showcase.bridge.IOSPlatformBridgeRegistry
import com.drivemate.showcase.core.model.Location
import com.drivemate.showcase.core.model.LocationWritePayloadResult
import com.drivemate.showcase.core.model.buildLocationWritePayload
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.domain.repository.LocationRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class IOSLocationRepository : LocationRepository {
    private val locationBridge
        get() = IOSPlatformBridgeRegistry.locationBridge
            ?: error("IOSLocationPlatformBridge is not installed")

    /**
     * Tulis lokasi live driver ke `locations/{uid}`.
     *
     * Bentuk dokumennya disusun `buildLocationWritePayload` di commonMain — fungsi yang SAMA
     * dipakai `FirebaseLocationRepository` Android, jadi kedua platform menulis dokumen yang
     * identik dan tunduk pada validasi `firestore.rules` yang sama. Menyusunnya terpisah
     * berarti dua kesempatan ditolak karena satu field yang salah nama.
     *
     * Dulu `UnsupportedOperationException` dengan catatan "Android-primary".
     */
    override suspend fun updateDriverLocation(loc: Location): AppResult<Unit> {
        val authUid = locationBridge.currentAuthUid()
        if (authUid.isNullOrBlank()) {
            return AppResult.failure(IllegalStateException("Not signed in"))
        }
        val built = buildLocationWritePayload(
            location = loc,
            authUid = authUid,
            nowMillis = TimeUtil.nowEpochMillis()
        )
        if (built is LocationWritePayloadResult.Rejected) {
            return AppResult.failure(IllegalArgumentException("invalid location: ${built.error}"))
        }
        val ready = built as LocationWritePayloadResult.Ready
        return suspendCancellableCoroutine { cont ->
            locationBridge.writeDriverLocation(ready.path, ready.fields) { error ->
                if (!cont.isCompleted) cont.resume(
                    if (error != null) AppResult.failure(IllegalStateException(error))
                    else AppResult.success(Unit)
                )
            }
        }
    }

    /**
     * Hentikan berbagi lokasi dengan MENGHAPUS dokumennya, bukan menulis koordinat kosong.
     *
     * Dokumen berisi (0,0) akan menaruh driver di tengah Samudra Atlantik pada peta admin —
     * terlihat seperti data, bukan seperti ketiadaan data.
     */
    override suspend fun clearDriverLocation(): AppResult<Unit> {
        val authUid = locationBridge.currentAuthUid()
        if (authUid.isNullOrBlank()) {
            return AppResult.failure(IllegalStateException("Not signed in"))
        }
        return suspendCancellableCoroutine { cont ->
            locationBridge.deleteDriverLocation("locations/$authUid") { error ->
                if (!cont.isCompleted) cont.resume(
                    if (error != null) AppResult.failure(IllegalStateException(error))
                    else AppResult.success(Unit)
                )
            }
        }
    }

    override fun observeAllDrivers(): Flow<AppResult<List<Location>>> = callbackFlow {
        val unsubscribe = observeAllDriverLocations { locations ->
            trySend(AppResult.success(locations)).isSuccess
        }
        awaitClose {
            unsubscribe()
        }
    }

    fun observeAllDriverLocationPayloads(
        onChange: (List<IOSLocationPayload>) -> Unit
    ): () -> Unit = locationBridge.observeAllDriverLocations(onChange)

    fun observeDriverLocationPayload(
        driverUid: String,
        onChange: (IOSLocationPayload?) -> Unit
    ): () -> Unit = locationBridge.observeDriverLocation(driverUid, onChange)

    fun observeAllDriverLocations(onChange: (List<Location>) -> Unit): () -> Unit =
        observeAllDriverLocationPayloads { payloads ->
            onChange(payloads.map { it.toModel() })
        }

    fun observeDriverLocation(
        driverUid: String,
        onChange: (Location?) -> Unit
    ): () -> Unit = observeDriverLocationPayload(driverUid) { payload ->
        onChange(payload?.toModel())
    }
}

private fun IOSLocationPayload.toModel(): Location = Location(
    driverUid = uid,
    latitude = latitude,
    longitude = longitude,
    updatedAtMillis = updatedAtMs
)
