package com.drivemate.showcase.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Cincin dan badge tab Tinjau: progres = ditinjau / (menunggu + ditinjau); memuat/gagal tidak pernah "selesai". */
class ExpatReviewRingStateTest {

    @Test
    fun `progres = ditinjau dibagi total`() {
        val s = ExpatReviewRingState(pending = 16, reviewed = 19, dataReady = true)
        assertEquals(35, s.total)
        assertEquals(19f / 35f, s.progress)
        assertFalse(s.isComplete)
        assertEquals(ExpatReviewDescriptionKind.PROGRESS, s.descriptionKind)
    }

    @Test
    fun `semua ditinjau dan data tepercaya = selesai tanpa badge`() {
        val s = ExpatReviewRingState(pending = 0, reviewed = 35, dataReady = true)
        assertEquals(1f, s.progress)
        assertTrue(s.isComplete)
        assertNull(s.badgeText)
        assertEquals(ExpatReviewDescriptionKind.COMPLETE, s.descriptionKind)
    }

    @Test
    fun `data belum tepercaya (memuat atau gagal) tidak pernah selesai`() {
        val s = ExpatReviewRingState(pending = 0, reviewed = 35, dataReady = false)
        assertEquals(0f, s.progress)
        assertFalse(s.isComplete)
        assertEquals(ExpatReviewDescriptionKind.LABEL_ONLY, s.descriptionKind)
    }

    @Test
    fun `total nol bukan selesai`() {
        val s = ExpatReviewRingState(pending = 0, reviewed = 0, dataReady = true)
        assertEquals(0f, s.progress)
        assertFalse(s.isComplete)
        assertNull(s.badgeText)
        assertEquals(ExpatReviewDescriptionKind.LABEL_ONLY, s.descriptionKind)
    }

    @Test
    fun `badge menampilkan sisa menunggu dengan batas 9+`() {
        assertEquals("1", ExpatReviewRingState(1, 4, true).badgeText)
        assertEquals("9", ExpatReviewRingState(9, 0, true).badgeText)
        assertEquals("9+", ExpatReviewRingState(10, 0, true).badgeText)
        assertEquals("9+", ExpatReviewRingState(16, 19, true).badgeText)
    }

    @Test
    fun `angka negatif dari sumber dianggap nol`() {
        val s = ExpatReviewRingState(pending = -3, reviewed = 5, dataReady = true)
        assertEquals(5, s.total)
        assertEquals(0, s.pendingCount)
        assertTrue(s.isComplete)
    }

    @Test
    fun `deskripsi membawa angka yang sama dengan cincin`() {
        val s = ExpatReviewRingState(pending = 16, reviewed = 19, dataReady = true)
        assertEquals(16, s.pendingCount)
        assertEquals(19, s.reviewedCount)
        assertEquals(35, s.total)
    }
}
