package com.drivemate.showcase.location

import android.Manifest
import android.content.Context
import android.location.Location
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.drivemate.showcase.util.UiErrorLogger
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Ambil last-known location secara suspend.
 * - Mengembalikan Pair(lat, lon) atau null bila permission belum ada atau tidak tersedia.
 * - Tidak memicu sensor baru (hemat baterai); hanya baca cache FusedLocation.
 */
internal fun hasLocationPermission(context: Context): Boolean {
    val fineGranted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val coarseGranted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    return fineGranted || coarseGranted
}

suspend fun getLastKnownLatLngOrNull(context: Context): Pair<Double, Double>? {
    if (!hasLocationPermission(context)) return null

    val client = LocationServices.getFusedLocationProviderClient(context)
    val loc: Location? = try {
        suspendCancellableCoroutine { cont ->
            client.lastLocation
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { e ->
                    UiErrorLogger.e("DM/LocationHelper", "lastLocation failed", e)
                    cont.resume(null)
                }
        }
    } catch (se: SecurityException) {
        UiErrorLogger.e("DM/LocationHelper", "lastLocation security exception", se)
        null
    } catch (t: Throwable) {
        UiErrorLogger.e("DM/LocationHelper", "lastLocation error", t)
        null
    }

    return loc?.let { it.latitude to it.longitude }
}
