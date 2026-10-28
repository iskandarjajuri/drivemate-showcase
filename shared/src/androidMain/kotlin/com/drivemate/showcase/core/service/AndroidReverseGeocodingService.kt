package com.drivemate.showcase.core.service

import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.drivemate.showcase.core.log.DMLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class AndroidReverseGeocodingService(
    private val geocoder: Geocoder
) : ReverseGeocodingService {

    private companion object { const val TAG = "DM/Geo" }

    override suspend fun resolve(lat: Double, lng: Double): String? = withContext(Dispatchers.IO) {
        val addr = reverseGeocodeInternal(lat, lng)
        val text = addr?.getAddressLine(0)
        if (!text.isNullOrBlank()) {
            val trimmed = if (text.length > 80) text.take(80) + "…" else text
            DMLog.i(TAG, "reverse-geocode success")
            trimmed
        } else {
            DMLog.d(TAG, "reverse-geocode empty result")
            null
        }
    }

    private suspend fun reverseGeocodeInternal(
        latitude: Double,
        longitude: Double
    ): Address? {
        return try {
            // API 33+ (TIRAMISU): use the async GeocodeListener to avoid blocking the thread.
            // API < 33: fall back to the synchronous overload (deprecated but only option);
            //   the blocking call is safe here because the caller already dispatches on Dispatchers.IO.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    val listener = object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            if (!cont.isActive) return
                            cont.resume(addresses.firstOrNull())
                        }

                        override fun onError(errorMessage: String?) {
                            if (!cont.isActive) return
                            val msg = errorMessage?.takeIf { it.isNotBlank() } ?: "unknown error"
                            DMLog.d(TAG, "reverse-geocode failed: $msg")
                            cont.resume(null)
                        }
                    }
                    try {
                        geocoder.getFromLocation(latitude, longitude, 1, listener)
                    } catch (t: Throwable) {
                        DMLog.d(TAG, "reverse-geocode failed: ${t.message}")
                        if (cont.isActive) cont.resume(null)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
            }
        } catch (se: SecurityException) {
            DMLog.d(TAG, "reverse-geocode security exception: ${se.message}")
            null
        } catch (t: Throwable) {
            DMLog.d(TAG, "reverse-geocode failed: ${t.message}")
            null
        }
    }
}
