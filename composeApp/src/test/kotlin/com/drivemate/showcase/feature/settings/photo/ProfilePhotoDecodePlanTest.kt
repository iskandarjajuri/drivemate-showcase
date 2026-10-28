package com.drivemate.showcase.feature.settings.photo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regresi bug PR-F: pembacaan ukuran memakai `BitmapFactory.decodeStream` dengan
 * `inJustDecodeBounds = true`, yang SELALU mengembalikan bitmap null. Logika lama
 * menafsirkan null itu sebagai NOT_AN_IMAGE sehingga SEMUA foto ditolak.
 * Logika baru ([planDecode]) hanya menilai dari dimensi outWidth/outHeight.
 */
class ProfilePhotoDecodePlanTest {

    @Test
    fun fotoNormal_lolosWalauBitmapBoundsNull() {
        // Mensimulasikan bounds-only read: bitmap null, tapi dimensi terisi.
        val plan = planDecode(width = 4000, height = 3000)
        assertNull(plan.error)
        assertEquals(computeInSampleSize(4000, 3000), plan.sampleSize)
    }

    @Test
    fun dimensiNol_ditolakSebagaiBukanGambar() {
        assertEquals(ProfilePhotoErrorCode.NOT_AN_IMAGE, planDecode(0, 3000).error)
        assertEquals(ProfilePhotoErrorCode.NOT_AN_IMAGE, planDecode(4000, 0).error)
        assertEquals(ProfilePhotoErrorCode.NOT_AN_IMAGE, planDecode(0, 0).error)
        assertEquals(ProfilePhotoErrorCode.NOT_AN_IMAGE, planDecode(-1, 100).error)
    }

    @Test
    fun fotoKecil_tetapSampleSize1() {
        val plan = planDecode(width = 200, height = 200)
        assertNull(plan.error)
        assertEquals(1, plan.sampleSize)
    }

    @Test
    fun fotoBesar_sampleSizeMenurunkanResolusi() {
        // 8000x6000: sisi terpendek 6000; 6000/2 >= 256, /4 >= 256, /8 >= 256, /16 >= 256 -> 16.
        val plan = planDecode(width = 8000, height = 6000)
        assertNull(plan.error)
        assertEquals(16, plan.sampleSize)
    }
}
