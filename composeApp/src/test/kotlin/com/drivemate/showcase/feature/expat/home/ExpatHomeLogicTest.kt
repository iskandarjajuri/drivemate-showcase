package com.drivemate.showcase.feature.expat.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExpatHomeLogicTest {

    @Test
    fun `jeda masuk berurutan 40 ms per item dan berhenti di item keenam`() {
        assertEquals(0, expatStaggerDelayMillis(0))
        assertEquals(40, expatStaggerDelayMillis(1))
        assertEquals(200, expatStaggerDelayMillis(5))
        assertEquals(200, expatStaggerDelayMillis(6))
        assertEquals(200, expatStaggerDelayMillis(40))
        assertEquals(0, expatStaggerDelayMillis(-3))
    }

    @Test
    fun `denyut hanya saat ada yang menunggu`() {
        assertTrue(expatPulseEnabled(1))
        assertFalse(expatPulseEnabled(0))
        assertFalse(expatPulseEnabled(-1))
    }

    @Test
    fun `progres hero = ditinjau per total, nol bila kosong`() {
        assertEquals(19f / 35f, expatReviewProgress(pending = 16, reviewed = 19))
        assertEquals(1f, expatReviewProgress(pending = 0, reviewed = 4))
        assertEquals(0f, expatReviewProgress(pending = 5, reviewed = 0))
        assertEquals(0f, expatReviewProgress(pending = 0, reviewed = 0))
        assertEquals(1f, expatReviewProgress(pending = -2, reviewed = 3))
    }

    @Test
    fun `inisial avatar`() {
        assertEquals("DS", homeAvatarInitials("Driver Uji Satu"))
        assertEquals("E", homeAvatarInitials("Expat1"))
        assertEquals("?", homeAvatarInitials(null))
        assertEquals("?", homeAvatarInitials("  "))
        assertEquals("田太", homeAvatarInitials("田中 太郎"))
    }
}
