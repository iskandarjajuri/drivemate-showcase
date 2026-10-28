package com.drivemate.showcase

import com.drivemate.showcase.core.model.LOCATION_TIMESTAMP_MAX_SKEW_MILLIS
import com.drivemate.showcase.core.model.Location
import com.drivemate.showcase.core.model.LocationWriteError
import com.drivemate.showcase.core.model.LocationWritePayloadResult
import com.drivemate.showcase.core.model.buildLocationWritePayload
import com.drivemate.showcase.core.model.clampLocationTimestamp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Bentuk dokumen `locations/{uid}` — dipakai KEDUA platform.
 *
 * `firestore.rules` memvalidasi dokumen ini ketat (field wajib + daftar field yang
 * diizinkan), jadi satu field salah nama muncul sebagai `PERMISSION_DENIED` yang terbaca
 * seperti masalah izin, bukan masalah bentuk. Sebelum ini aturannya hanya ada di
 * `FirebaseLocationRepository` Android dan nol test.
 */
class LocationWritePayloadTest {

    private val now = 1_755_000_000_000L

    private fun loc(
        uid: String = "driver1",
        lat: Double? = -6.2,
        lon: Double? = 106.8,
        speedMps: Double? = null,
        speedKph: Double? = null,
        heading: Double? = null,
        accuracy: Double? = null,
        name: String? = null,
        updatedAt: Long = now
    ) = Location(
        driverUid = uid,
        latitude = lat,
        longitude = lon,
        speedMps = speedMps,
        speedKph = speedKph,
        headingDeg = heading,
        accuracyM = accuracy,
        updatedAtMillis = updatedAt,
        locationName = name
    )

    private fun ready(result: LocationWritePayloadResult): LocationWritePayloadResult.Ready {
        assertIs<LocationWritePayloadResult.Ready>(result)
        return result
    }

    // ---------- Penolakan ----------

    @Test
    fun writing_someone_elses_location_is_rejected() {
        // Rules menolaknya juga, tapi menahannya di sini mencegah panggilan yang pasti gagal
        // — dan mencegah bug di mana driver menimpa titik driver lain.
        val r = buildLocationWritePayload(loc(uid = "driver1"), authUid = "driver2", nowMillis = now)
        assertIs<LocationWritePayloadResult.Rejected>(r)
        assertEquals(LocationWriteError.UID_MISMATCH, r.error)
    }

    @Test
    fun a_blank_auth_uid_is_rejected() {
        val r = buildLocationWritePayload(loc(), authUid = "", nowMillis = now)
        assertIs<LocationWritePayloadResult.Rejected>(r)
        assertEquals(LocationWriteError.UID_MISMATCH, r.error)
    }

    @Test
    fun missing_coordinates_are_rejected() {
        assertIs<LocationWritePayloadResult.Rejected>(
            buildLocationWritePayload(loc(lat = null), "driver1", now)
        )
        assertIs<LocationWritePayloadResult.Rejected>(
            buildLocationWritePayload(loc(lon = null), "driver1", now)
        )
    }

    @Test
    fun impossible_coordinates_are_rejected() {
        // CoreLocation bisa mengembalikan (0,0) atau nilai tak valid saat fix gagal.
        // Menuliskannya menaruh driver di tengah Samudra Atlantik pada peta admin.
        for (bad in listOf(loc(lat = 91.0), loc(lat = -91.0), loc(lon = 181.0), loc(lon = -181.0))) {
            val r = buildLocationWritePayload(bad, "driver1", now)
            assertIs<LocationWritePayloadResult.Rejected>(r)
            assertEquals(LocationWriteError.COORDINATES_OUT_OF_RANGE, r.error)
        }
    }

    // ---------- Field wajib ----------

    @Test
    fun the_four_required_fields_are_always_present() {
        val fields = ready(buildLocationWritePayload(loc(), "driver1", now)).fields
        for (key in listOf("driverUid", "latitude", "longitude", "updatedAtMillis")) {
            assertTrue(fields.containsKey(key), "field wajib hilang: $key")
        }
    }

    @Test
    fun the_document_path_is_keyed_by_the_authenticated_uid() {
        assertEquals("locations/driver1", ready(buildLocationWritePayload(loc(), "driver1", now)).path)
    }

    @Test
    fun optional_fields_are_omitted_rather_than_written_as_null() {
        val fields = ready(buildLocationWritePayload(loc(), "driver1", now)).fields
        for (key in listOf("speedMps", "speedKph", "headingDeg", "accuracyM", "locationName")) {
            assertFalse(fields.containsKey(key), "field opsional kosong seharusnya dilewati: $key")
        }
    }

    @Test
    fun a_blank_location_name_is_omitted() {
        val fields = ready(buildLocationWritePayload(loc(name = "   "), "driver1", now)).fields
        assertFalse(fields.containsKey("locationName"))
    }

    @Test
    fun no_unexpected_keys_are_ever_written() {
        // `hasOnlyAllowed` di rules menolak kunci di luar daftar ini.
        val allowed = setOf(
            "driverUid", "latitude", "longitude", "updatedAtMillis",
            "speedMps", "speedKph", "headingDeg", "accuracyM", "locationName"
        )
        val fields = ready(
            buildLocationWritePayload(
                loc(speedMps = 10.0, heading = 90.0, accuracy = 5.0, name = "Gate 1"),
                "driver1", now
            )
        ).fields
        assertTrue(fields.keys.all { it in allowed }, "kunci tak terduga: ${fields.keys - allowed}")
    }

    // ---------- Turunan kecepatan ----------

    @Test
    fun kph_is_derived_when_only_mps_is_known() {
        val fields = ready(buildLocationWritePayload(loc(speedMps = 10.0), "driver1", now)).fields
        assertEquals(10.0, fields["speedMps"])
        assertEquals(36.0, fields["speedKph"])
    }

    @Test
    fun mps_is_derived_when_only_kph_is_known() {
        val fields = ready(buildLocationWritePayload(loc(speedKph = 36.0), "driver1", now)).fields
        assertEquals(10.0, fields["speedMps"])
        assertEquals(36.0, fields["speedKph"])
    }

    @Test
    fun a_zero_speed_is_written_not_treated_as_absent() {
        // Driver yang berhenti adalah informasi; menghilangkannya membuat admin melihat
        // kecepatan terakhir yang bergerak seolah masih berlaku.
        val fields = ready(buildLocationWritePayload(loc(speedMps = 0.0), "driver1", now)).fields
        assertEquals(0.0, fields["speedMps"])
        assertEquals(0.0, fields["speedKph"])
    }

    // ---------- Clamp timestamp ----------

    @Test
    fun a_sane_device_timestamp_is_kept() {
        assertEquals(now - 5_000, clampLocationTimestamp(now - 5_000, now))
    }

    @Test
    fun a_timestamp_far_in_the_future_is_clamped_to_now() {
        // Jam perangkat yang salah membuat admin melihat driver "terakhir terlihat" di masa
        // depan, dan urutan di peta jadi kacau — tanpa error apa pun.
        val future = now + LOCATION_TIMESTAMP_MAX_SKEW_MILLIS + 1
        assertEquals(now, clampLocationTimestamp(future, now))
    }

    @Test
    fun a_timestamp_far_in_the_past_is_clamped_to_now() {
        val past = now - LOCATION_TIMESTAMP_MAX_SKEW_MILLIS - 1
        assertEquals(now, clampLocationTimestamp(past, now))
    }

    @Test
    fun a_timestamp_exactly_at_the_skew_limit_is_kept() {
        val edge = now - LOCATION_TIMESTAMP_MAX_SKEW_MILLIS
        assertEquals(edge, clampLocationTimestamp(edge, now))
    }

    @Test
    fun the_clamp_is_applied_to_the_written_payload_too() {
        val future = now + LOCATION_TIMESTAMP_MAX_SKEW_MILLIS + 1
        val fields = ready(buildLocationWritePayload(loc(updatedAt = future), "driver1", now)).fields
        assertEquals(now, fields["updatedAtMillis"])
    }
}
