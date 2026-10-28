package com.drivemate.showcase

import com.drivemate.showcase.core.model.SpeedViolation
import com.drivemate.showcase.core.model.buildSpeedViolationPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Bentuk dokumen `speed_violations` — dipakai KEDUA platform.
 *
 * `firestore.rules` hanya mengizinkan `create` bila `driverUid == request.auth.uid`, jadi
 * bentuk yang salah muncul sebagai `PERMISSION_DENIED` yang terbaca seperti masalah izin.
 * Dan karena koleksi ini append-only, dokumen salah yang terlanjur masuk tidak bisa
 * diperbaiki dengan menimpanya.
 */
class SpeedViolationPayloadTest {

    private fun violation(
        uid: String = "driver1",
        speed: Double = 80.0,
        limit: Double = 60.0,
        lat: Double? = null,
        lon: Double? = null
    ) = SpeedViolation(
        driverUid = uid,
        speedKph = speed,
        speedLimitKph = limit,
        occurredAtMillis = 1_755_000_000_000,
        latitude = lat,
        longitude = lon
    )

    @Test
    fun reporting_a_violation_for_someone_else_is_refused() {
        // Menahannya di sini mencegah tulisan yang pasti ditolak rules — DAN mencegah bug
        // di mana pelanggaran tercatat atas nama driver lain.
        assertNull(buildSpeedViolationPayload(violation(uid = "driver1"), authUid = "driver2"))
    }

    @Test
    fun a_blank_auth_uid_is_refused() {
        assertNull(buildSpeedViolationPayload(violation(), authUid = ""))
    }

    @Test
    fun the_four_required_fields_are_always_present() {
        val fields = assertNotNull(buildSpeedViolationPayload(violation(), "driver1"))
        for (key in listOf("driverUid", "speedKph", "speedLimitKph", "occurredAtMillis")) {
            assertTrue(fields.containsKey(key), "field wajib hilang: $key")
        }
    }

    @Test
    fun the_driver_uid_written_is_the_authenticated_one() {
        val fields = assertNotNull(buildSpeedViolationPayload(violation(uid = "driver1"), "driver1"))
        assertEquals("driver1", fields["driverUid"])
    }

    @Test
    fun coordinates_are_omitted_when_unknown_rather_than_written_as_null() {
        val fields = assertNotNull(buildSpeedViolationPayload(violation(), "driver1"))
        assertFalse(fields.containsKey("latitude"))
        assertFalse(fields.containsKey("longitude"))
    }

    @Test
    fun coordinates_are_included_when_known() {
        val fields = assertNotNull(
            buildSpeedViolationPayload(violation(lat = -6.2, lon = 106.8), "driver1")
        )
        assertEquals(-6.2, fields["latitude"])
        assertEquals(106.8, fields["longitude"])
    }

    @Test
    fun no_unexpected_keys_are_ever_written() {
        val allowed = setOf(
            "driverUid", "speedKph", "speedLimitKph", "occurredAtMillis", "latitude", "longitude"
        )
        val fields = assertNotNull(
            buildSpeedViolationPayload(violation(lat = -6.2, lon = 106.8), "driver1")
        )
        assertTrue(fields.keys.all { it in allowed }, "kunci tak terduga: ${fields.keys - allowed}")
    }

    @Test
    fun the_recorded_speed_and_limit_are_preserved_exactly() {
        // Angka ini masuk ke notifikasi yang dibaca driver dan expat; pembulatan di sini
        // akan membuat pesan tidak cocok dengan apa yang tercatat.
        val fields = assertNotNull(
            buildSpeedViolationPayload(violation(speed = 83.4, limit = 60.0), "driver1")
        )
        assertEquals(83.4, fields["speedKph"])
        assertEquals(60.0, fields["speedLimitKph"])
    }
}
