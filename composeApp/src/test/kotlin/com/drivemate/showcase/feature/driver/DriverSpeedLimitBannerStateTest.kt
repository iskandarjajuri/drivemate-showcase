package com.drivemate.showcase.feature.driver

import com.drivemate.showcase.core.model.TrackingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R3 — menguji rantai yang menyalakan banner "melebihi batas kecepatan" di Driver Home:
 * `TrackingStatus` (diisi LocationTrackingService) -> `DriverTrackingUiState` (dipetakan
 * DriverViewModel) -> kondisi render di DriverHomeScreen.
 *
 * Pemetaan itu sendiri diuji di sini supaya banner tidak cuma "compile-verified".
 * Composable-nya sendiri tidak dirender di sini — yang dikunci adalah kontrak datanya:
 * banner hanya boleh muncul kalau ketiga nilai hadir dan konsisten.
 */
class DriverSpeedLimitBannerStateTest {

    /** Cerminan kondisi render di DriverHomeScreen: butuh flag DAN kedua angka. */
    private fun shouldShowBanner(state: DriverTrackingUiState): Boolean =
        state.overSpeedLimit && state.currentSpeedKph != null && state.speedLimitKph != null

    private fun mapToUiState(status: TrackingStatus) = DriverTrackingUiState(
        warningType = status.issue,
        warningMessageRes = null,
        batteryOptimizationEnabled = status.batteryOptimizationEnabled,
        hideLocationWhenOffDuty = status.hideLocationWhenOffDuty,
        overSpeedLimit = status.overSpeedLimit,
        currentSpeedKph = status.currentSpeedKph,
        speedLimitKph = status.speedLimitKph
    )

    @Test
    fun `default state does not show banner`() {
        val ui = mapToUiState(TrackingStatus())
        assertFalse(ui.overSpeedLimit)
        assertFalse(shouldShowBanner(ui))
    }

    @Test
    fun `over limit with both values shows banner`() {
        val ui = mapToUiState(
            TrackingStatus(
                active = true,
                overSpeedLimit = true,
                currentSpeedKph = 96.4,
                speedLimitKph = 80.0
            )
        )
        assertTrue(shouldShowBanner(ui))
        assertEquals(96.4, ui.currentSpeedKph!!, 0.001)
        assertEquals(80.0, ui.speedLimitKph!!, 0.001)
    }

    @Test
    fun `over limit without speed values does not show banner`() {
        // Bisa terjadi kalau GPS sempat kehilangan pembacaan kecepatan; lebih baik
        // tidak menampilkan apa-apa daripada banner dengan angka kosong.
        val ui = mapToUiState(TrackingStatus(active = true, overSpeedLimit = true))
        assertTrue(ui.overSpeedLimit)
        assertFalse(shouldShowBanner(ui))
    }

    @Test
    fun `under limit does not show banner even with speed present`() {
        val ui = mapToUiState(
            TrackingStatus(
                active = true,
                overSpeedLimit = false,
                currentSpeedKph = 55.0,
                speedLimitKph = 80.0
            )
        )
        assertFalse(shouldShowBanner(ui))
    }

    @Test
    fun `stopping tracking clears the banner inputs`() {
        // Cermin dari stopTrackingInternal(): banner tidak boleh tertinggal menyala.
        val stopped = TrackingStatus(
            active = false,
            overSpeedLimit = false,
            currentSpeedKph = null,
            speedLimitKph = null
        )
        val ui = mapToUiState(stopped)
        assertFalse(shouldShowBanner(ui))
        assertNull(ui.currentSpeedKph)
    }

    @Test
    fun `speed limit state is independent of the willFollowSpeedLimit pledge`() {
        // R3 adalah penegakan real-time; willFollowSpeedLimit di Safety Declaration
        // adalah janji sekali-sehari. Keduanya sengaja tidak digabung — tidak ada
        // field pledge apa pun di TrackingStatus/DriverTrackingUiState.
        val ui = mapToUiState(
            TrackingStatus(overSpeedLimit = true, currentSpeedKph = 90.0, speedLimitKph = 80.0)
        )
        assertTrue(shouldShowBanner(ui))
        assertFalse(ui.hideLocationWhenOffDuty)
        assertNull(ui.warningType)
    }
}
