package com.drivemate.showcase

import com.drivemate.showcase.core.model.SpeedLimitConfig
import com.drivemate.showcase.domain.safety.SpeedMonitorState
import com.drivemate.showcase.domain.safety.SpeedViolationDetector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpeedViolationDetectorTest {

    private val limit80 = SpeedLimitConfig(speedLimitKph = 80.0, enabled = true)
    private val t0 = 1_700_000_000_000L
    private val cooldown = SpeedViolationDetector.COOLDOWN_MS
    private val sustained = SpeedViolationDetector.SUSTAINED_BELOW_MS

    // ---------- fail-closed ----------

    @Test
    fun config_null_never_detects() {
        val r = SpeedViolationDetector.evaluate(200.0, null, SpeedMonitorState(), t0)
        assertFalse(r.isOverLimit, "config null harus fail-closed")
        assertFalse(r.shouldNotify)
    }

    @Test
    fun config_disabled_never_detects() {
        val cfg = SpeedLimitConfig(speedLimitKph = 80.0, enabled = false)
        val r = SpeedViolationDetector.evaluate(200.0, cfg, SpeedMonitorState(), t0)
        assertFalse(r.isOverLimit)
        assertFalse(r.shouldNotify)
    }

    @Test
    fun config_nonpositive_limit_never_detects() {
        val cfg = SpeedLimitConfig(speedLimitKph = 0.0, enabled = true)
        val r = SpeedViolationDetector.evaluate(200.0, cfg, SpeedMonitorState(), t0)
        assertFalse(r.isOverLimit)
        assertFalse(r.shouldNotify)
    }

    @Test
    fun null_speed_leaves_state_untouched() {
        val prior = SpeedMonitorState(lastNotifiedAtMillis = t0, belowLimitSinceMillis = 0L)
        val r = SpeedViolationDetector.evaluate(null, limit80, prior, t0 + 1000)
        assertFalse(r.isOverLimit)
        assertFalse(r.shouldNotify)
        assertEquals(prior, r.state, "tanpa data kecepatan, state tidak boleh berubah")
    }

    // ---------- threshold crossing ----------

    @Test
    fun below_limit_does_not_notify() {
        val r = SpeedViolationDetector.evaluate(79.9, limit80, SpeedMonitorState(), t0)
        assertFalse(r.isOverLimit)
        assertFalse(r.shouldNotify)
    }

    @Test
    fun exactly_at_limit_counts_as_violation() {
        val r = SpeedViolationDetector.evaluate(80.0, limit80, SpeedMonitorState(), t0)
        assertTrue(r.isOverLimit, "kecepatan == batas dihitung melanggar")
        assertTrue(r.shouldNotify)
    }

    @Test
    fun first_violation_notifies_and_records_time() {
        val r = SpeedViolationDetector.evaluate(95.0, limit80, SpeedMonitorState(), t0)
        assertTrue(r.isOverLimit)
        assertTrue(r.shouldNotify)
        assertEquals(t0, r.state.lastNotifiedAtMillis)
    }

    // ---------- cooldown ----------

    @Test
    fun second_violation_inside_cooldown_is_suppressed() {
        val first = SpeedViolationDetector.evaluate(95.0, limit80, SpeedMonitorState(), t0)
        assertTrue(first.shouldNotify)

        val second = SpeedViolationDetector.evaluate(97.0, limit80, first.state, t0 + 15_000)
        assertTrue(second.isOverLimit, "masih di atas batas -> banner tetap menyala")
        assertFalse(second.shouldNotify, "dalam cooldown -> tidak boleh notifikasi ulang")
        assertEquals(t0, second.state.lastNotifiedAtMillis, "waktu notifikasi tidak boleh digeser")
    }

    @Test
    fun sustained_speeding_refires_exactly_after_cooldown() {
        val first = SpeedViolationDetector.evaluate(95.0, limit80, SpeedMonitorState(), t0)

        val justBefore = SpeedViolationDetector.evaluate(95.0, limit80, first.state, t0 + cooldown - 1)
        assertFalse(justBefore.shouldNotify)

        val atBoundary = SpeedViolationDetector.evaluate(95.0, limit80, first.state, t0 + cooldown)
        assertTrue(atBoundary.shouldNotify, "tepat di batas cooldown sudah boleh menyala lagi")
        assertEquals(t0 + cooldown, atBoundary.state.lastNotifiedAtMillis)
    }

    // ---------- histeresis ----------

    @Test
    fun dipping_just_below_limit_does_not_clear_cooldown() {
        // Skenario 79/81 km/jam terhadap batas 80 — tanpa histeresis ini memicu
        // notifikasi ulang tiap crossing.
        val first = SpeedViolationDetector.evaluate(95.0, limit80, SpeedMonitorState(), t0)

        val dip = SpeedViolationDetector.evaluate(79.0, limit80, first.state, t0 + 10_000)
        assertFalse(dip.isOverLimit)
        assertEquals(t0, dip.state.lastNotifiedAtMillis, "turun tipis tidak boleh me-reset cooldown")

        val backUp = SpeedViolationDetector.evaluate(81.0, limit80, dip.state, t0 + 20_000)
        assertTrue(backUp.isOverLimit)
        assertFalse(backUp.shouldNotify, "crossing bolak-balik tidak boleh memicu notifikasi baru")
    }

    @Test
    fun slowing_down_briefly_is_not_enough_to_clear() {
        val first = SpeedViolationDetector.evaluate(95.0, limit80, SpeedMonitorState(), t0)
        // 60 km/jam = jauh di bawah band, tapi belum bertahan cukup lama.
        val slow = SpeedViolationDetector.evaluate(60.0, limit80, first.state, t0 + 10_000)
        assertEquals(t0, slow.state.lastNotifiedAtMillis, "belum sustained -> cooldown belum di-reset")

        val backUp = SpeedViolationDetector.evaluate(95.0, limit80, slow.state, t0 + 20_000)
        assertFalse(backUp.shouldNotify)
    }

    @Test
    fun sustained_slow_clears_cooldown_so_new_episode_fires_immediately() {
        val first = SpeedViolationDetector.evaluate(95.0, limit80, SpeedMonitorState(), t0)

        val slowStart = SpeedViolationDetector.evaluate(60.0, limit80, first.state, t0 + 10_000)
        val slowSustained =
            SpeedViolationDetector.evaluate(60.0, limit80, slowStart.state, t0 + 10_000 + sustained)
        assertEquals(0L, slowSustained.state.lastNotifiedAtMillis, "sustained di bawah -> cooldown reset")

        // Episode ngebut BARU, masih jauh di dalam jendela 5 menit sejak notifikasi pertama.
        val newEpisode =
            SpeedViolationDetector.evaluate(95.0, limit80, slowSustained.state, t0 + 10_000 + sustained + 1000)
        assertTrue(newEpisode.shouldNotify, "episode baru setelah benar-benar melambat harus langsung menyala")
    }
}
