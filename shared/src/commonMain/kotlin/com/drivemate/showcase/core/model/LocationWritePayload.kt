package com.drivemate.showcase.core.model

import com.drivemate.showcase.core.util.speedKphFromMps
import com.drivemate.showcase.core.util.speedMpsFromKph
import kotlin.math.abs

/**
 * Aturan penyusunan dokumen `locations/{uid}` — dipakai KEDUA platform.
 *
 * ## Kenapa ini di commonMain
 * `firestore.rules` memvalidasi dokumen ini secara ketat: field wajib
 * (`driverUid`/`latitude`/`longitude`/`updatedAtMillis`) dan daftar field yang diizinkan.
 * Menyusun payload-nya sendiri-sendiri di Android dan iOS berarti dua kesempatan untuk
 * ditolak `PERMISSION_DENIED` karena satu field yang salah nama — dan itu terlihat seperti
 * masalah izin, bukan masalah bentuk dokumen.
 *
 * Aturan lain yang tidak boleh berbeda antar-platform: **clamp timestamp**. Perangkat dengan
 * jam salah bisa mengirim lokasi bertanggal tahun depan; admin lalu melihat driver "terakhir
 * terlihat" di masa depan, dan urutan di peta jadi kacau. Sebelum ini clamp itu hanya ada di
 * `FirebaseLocationRepository` Android.
 */

/** Selisih maksimum antara timestamp perangkat dan jam sekarang sebelum di-clamp. */
const val LOCATION_TIMESTAMP_MAX_SKEW_MILLIS: Long = 24L * 60 * 60 * 1000

/** Alasan sebuah [Location] tidak bisa ditulis. */
enum class LocationWriteError {
    /** `driverUid` tidak sama dengan user yang login — rules akan menolaknya. */
    UID_MISMATCH,

    /** Lintang/bujur kosong. */
    MISSING_COORDINATES,

    /** Koordinat di luar rentang yang mungkin. */
    COORDINATES_OUT_OF_RANGE
}

/** Payload siap tulis, atau alasan penolakannya. */
sealed class LocationWritePayloadResult {
    data class Ready(
        val path: String,
        val fields: Map<String, Any>
    ) : LocationWritePayloadResult()

    data class Rejected(val error: LocationWriteError) : LocationWritePayloadResult()
}

/**
 * Susun dokumen `locations/{uid}`.
 *
 * Fungsi MURNI — `authUid` dan `nowMillis` disuntikkan, nol pembacaan jam sistem, supaya
 * clamp timestamp bisa diuji deterministik.
 *
 * Field opsional hanya disertakan bila ada nilainya. Menuliskan `null` akan menambah kunci
 * yang tidak ada di daftar `hasOnlyAllowed` rules pada beberapa jalur, dan tetap saja tidak
 * membawa informasi apa pun.
 */
fun buildLocationWritePayload(
    location: Location,
    authUid: String,
    nowMillis: Long
): LocationWritePayloadResult {
    if (authUid.isBlank() || location.driverUid != authUid) {
        return LocationWritePayloadResult.Rejected(LocationWriteError.UID_MISMATCH)
    }
    val lat = location.latitude ?: return LocationWritePayloadResult.Rejected(
        LocationWriteError.MISSING_COORDINATES
    )
    val lon = location.longitude ?: return LocationWritePayloadResult.Rejected(
        LocationWriteError.MISSING_COORDINATES
    )
    if (lat !in -90.0..90.0 || lon !in -180.0..180.0) {
        return LocationWritePayloadResult.Rejected(LocationWriteError.COORDINATES_OUT_OF_RANGE)
    }

    val timestamp = clampLocationTimestamp(location.updatedAtMillis, nowMillis)

    val fields = mutableMapOf<String, Any>(
        "driverUid" to authUid,
        "latitude" to lat,
        "longitude" to lon,
        "updatedAtMillis" to timestamp
    )
    // Keduanya diturunkan satu sama lain supaya konsumen mana pun menemukan satuan yang
    // dibutuhkannya tanpa mengonversi sendiri — dan supaya iOS tidak mengarang faktor
    // konversinya sendiri.
    (location.speedMps ?: speedMpsFromKph(location.speedKph))?.let { fields["speedMps"] = it }
    (location.speedKph ?: speedKphFromMps(location.speedMps))?.let { fields["speedKph"] = it }
    location.headingDeg?.let { fields["headingDeg"] = it }
    location.accuracyM?.let { fields["accuracyM"] = it }
    location.locationName?.takeIf { it.isNotBlank() }?.let { fields["locationName"] = it }

    return LocationWritePayloadResult.Ready(path = "locations/$authUid", fields = fields)
}

/**
 * Pakai timestamp perangkat kecuali menyimpang lebih dari [LOCATION_TIMESTAMP_MAX_SKEW_MILLIS]
 * dari jam sekarang; kalau menyimpang, pakai jam sekarang.
 *
 * Perangkat dengan jam salah akan membuat admin melihat driver "terakhir terlihat" di masa
 * depan, dan urutan di peta jadi kacau tanpa ada error apa pun.
 */
fun clampLocationTimestamp(deviceMillis: Long, nowMillis: Long): Long =
    if (abs(deviceMillis - nowMillis) > LOCATION_TIMESTAMP_MAX_SKEW_MILLIS) nowMillis else deviceMillis
