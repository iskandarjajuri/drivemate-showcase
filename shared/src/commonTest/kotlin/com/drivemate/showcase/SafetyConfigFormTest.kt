package com.drivemate.showcase

import com.drivemate.showcase.domain.safety.SAFETY_SPEED_LIMIT_MAX_KPH
import com.drivemate.showcase.domain.safety.SAFETY_SPEED_LIMIT_MIN_KPH
import com.drivemate.showcase.domain.safety.SafetyConfigInputError
import com.drivemate.showcase.domain.safety.SafetyConfigInputResult
import com.drivemate.showcase.domain.safety.SpeedMonitorState
import com.drivemate.showcase.domain.safety.SpeedViolationDetector
import com.drivemate.showcase.domain.safety.buildSafetyConfigPayload
import com.drivemate.showcase.domain.safety.mapSpeedLimitConfigOrNull
import com.drivemate.showcase.domain.safety.validateSpeedLimitInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Blok 3.4 — setelan keselamatan admin (C2 pada PRD §7), dan fail-closed-nya. */
class SafetyConfigFormTest {

    // ── Pemetaan dokumen: FAIL-CLOSED ───────────────────────────────────────────

    @Test
    fun absentDocumentMeansDetectionOff() {
        assertNull(mapSpeedLimitConfigOrNull(null))
    }

    @Test
    fun enabledTrueWithoutLimitStillMeansDetectionOff() {
        // Bentuk kegagalan paling berbahaya di layar ini: sakelar menyala tanpa batas yang
        // sah. Kalau ini dibaca sebagai "aktif", deteksi berjalan dengan batas entah berapa.
        assertNull(mapSpeedLimitConfigOrNull(mapOf("enabled" to true)))
    }

    @Test
    fun nonPositiveLimitMeansDetectionOff() {
        assertNull(mapSpeedLimitConfigOrNull(mapOf("speedLimitKph" to 0, "enabled" to true)))
        assertNull(mapSpeedLimitConfigOrNull(mapOf("speedLimitKph" to -80, "enabled" to true)))
    }

    @Test
    fun missingEnabledIsReadAsFalseNotTrue() {
        // Bidang yang tidak ada bukan izin.
        val c = mapSpeedLimitConfigOrNull(mapOf("speedLimitKph" to 80))
        assertEquals(80.0, c?.speedLimitKph)
        assertFalse(c?.enabled ?: true)
    }

    @Test
    fun completeDocumentIsMapped() {
        val c = mapSpeedLimitConfigOrNull(mapOf("speedLimitKph" to 80.5, "enabled" to true))
        assertEquals(80.5, c?.speedLimitKph)
        assertTrue(c?.enabled ?: false)
    }

    @Test
    fun mappedConfigDrivesDetectorAndBrokenDocumentSilencesIt() {
        // Rantai lengkapnya, bukan cuma mapper-nya: dokumen rusak -> null -> detektor diam.
        val broken = mapSpeedLimitConfigOrNull(mapOf("enabled" to true))
        val quiet = SpeedViolationDetector.evaluate(200.0, broken, SpeedMonitorState(), 1_000L)
        assertFalse(quiet.isOverLimit)
        assertFalse(quiet.shouldNotify)

        val good = mapSpeedLimitConfigOrNull(mapOf("speedLimitKph" to 80, "enabled" to true))
        val loud = SpeedViolationDetector.evaluate(200.0, good, SpeedMonitorState(), 1_000L)
        assertTrue(loud.isOverLimit)
    }

    // ── Validasi masukan ────────────────────────────────────────────────────────

    private fun error(raw: String): SafetyConfigInputError? =
        (validateSpeedLimitInput(raw) as? SafetyConfigInputResult.Invalid)?.error

    private fun value(raw: String): Double? =
        (validateSpeedLimitInput(raw) as? SafetyConfigInputResult.Valid)?.speedLimitKph

    @Test
    fun blankIsRejectedAsEmpty() {
        assertEquals(SafetyConfigInputError.EMPTY, error(""))
        assertEquals(SafetyConfigInputError.EMPTY, error("   "))
    }

    @Test
    fun nonNumberIsRejected() {
        assertEquals(SafetyConfigInputError.NOT_A_NUMBER, error("delapan puluh"))
        assertEquals(SafetyConfigInputError.NOT_A_NUMBER, error("80 km/jam"))
    }

    @Test
    fun typoThatWouldSilentlyDisableDetectionIsRejected() {
        // `800` bukan cuma "di luar rentang": ia membuat deteksi tidak pernah menyala
        // sementara layar tetap menampilkan "aktif". `firestore.rules` hanya menuntut
        // `> 0`, jadi rules TIDAK menangkap ini — di sinilah ia tertangkap.
        assertEquals(SafetyConfigInputError.OUT_OF_RANGE, error("800"))
    }

    @Test
    fun tooLowIsRejectedBecauseEveryTripWouldViolate() {
        assertEquals(SafetyConfigInputError.OUT_OF_RANGE, error("5"))
    }

    @Test
    fun boundariesAreInclusive() {
        assertEquals(SAFETY_SPEED_LIMIT_MIN_KPH, value(SAFETY_SPEED_LIMIT_MIN_KPH.toInt().toString()))
        assertEquals(SAFETY_SPEED_LIMIT_MAX_KPH, value(SAFETY_SPEED_LIMIT_MAX_KPH.toInt().toString()))
    }

    @Test
    fun commaIsAcceptedAsDecimalSeparator() {
        // Papan ketik Indonesia menghasilkan koma; menolaknya terbaca seperti bug.
        assertEquals(80.5, value("80,5"))
    }

    @Test
    fun surroundingWhitespaceIsTolerated() {
        assertEquals(80.0, value("  80  "))
    }

    @Test
    fun invalidInputIsNotClampedIntoRange() {
        // Memaksa 800 menjadi 120 juga menyembunyikan salah ketiknya, dan admin akan
        // mengira ia menyimpan 800.
        assertNull(value("800"))
    }

    // ── Bentuk dokumen yang ditulis ─────────────────────────────────────────────

    @Test
    fun payloadCarriesExactlyThreeKeys() {
        // `firestore.rules:1431` memakai `hasOnlyAllowed`; satu kunci tambahan membuat
        // seluruh tulisan PERMISSION_DENIED.
        val payload = buildSafetyConfigPayload(80.0, true, 1_700_000_000_000L)
        assertEquals(setOf("speedLimitKph", "enabled", "updatedAtMillis"), payload.keys)
        assertEquals(80.0, payload["speedLimitKph"])
        assertEquals(true, payload["enabled"])
    }

    @Test
    fun payloadRoundTripsThroughTheMapper() {
        // Yang ditulis harus terbaca kembali sebagai hal yang sama. Kalau tidak, satu sisi
        // sedang salah dan tidak ada yang memberi tahu.
        val payload = buildSafetyConfigPayload(95.0, false, 1L)
        val back = mapSpeedLimitConfigOrNull(payload)
        assertEquals(95.0, back?.speedLimitKph)
        assertFalse(back?.enabled ?: true)
    }
}
