package com.drivemate.showcase.feature.settings.photo

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfilePhotoGeometryTest {

    // ===== centerSquareCrop =====

    @Test
    fun crop_landscape_memotongKiriKananSamaRata() {
        assertEquals(SquareCrop(left = 500, top = 0, side = 3000), centerSquareCrop(4000, 3000))
    }

    @Test
    fun crop_portrait_memotongAtasBawahSamaRata() {
        assertEquals(SquareCrop(left = 0, top = 500, side = 3000), centerSquareCrop(3000, 4000))
    }

    @Test
    fun crop_persegi_tidakMemotong() {
        assertEquals(SquareCrop(0, 0, 512), centerSquareCrop(512, 512))
    }

    @Test
    fun crop_selisihGanjil_dibulatkanKeBawah() {
        // 101 x 100 -> sisa 1 piksel; kiri = 0 (bukan 1), sisi 100.
        assertEquals(SquareCrop(0, 0, 100), centerSquareCrop(101, 100))
        assertEquals(SquareCrop(1, 0, 100), centerSquareCrop(102, 100))
    }

    @Test
    fun crop_sangatKecil_tetapSah() {
        assertEquals(SquareCrop(0, 0, 1), centerSquareCrop(1, 1))
        assertEquals(SquareCrop(4, 0, 2), centerSquareCrop(10, 2))
    }

    @Test
    fun crop_dimensiTidakSah_sisiNol() {
        assertEquals(SquareCrop(0, 0, 0), centerSquareCrop(0, 100))
        assertEquals(SquareCrop(0, 0, 0), centerSquareCrop(100, -5))
    }

    // ===== computeInSampleSize =====

    @Test
    fun sample_fotoKamera12MP_diperkecilTanpaMelewatiTarget() {
        // 4000x3000 -> sisi terpendek 3000; 3000/8=375 >= 256 tetapi 3000/16=187 < 256 => 8.
        assertEquals(8, computeInSampleSize(4000, 3000))
        assertEquals(8, computeInSampleSize(3000, 4000))
    }

    @Test
    fun sample_menyisakanSetidaknyaTarget() {
        listOf(256 to 256, 511 to 900, 512 to 512, 1024 to 700, 5000 to 5000).forEach { (w, h) ->
            val s = computeInSampleSize(w, h)
            org.junit.Assert.assertTrue("w=$w h=$h s=$s", minOf(w, h) / s >= 256 || s == 1)
        }
    }

    @Test
    fun sample_sudahKecil_tidakDiperkecil() {
        assertEquals(1, computeInSampleSize(256, 256))
        assertEquals(1, computeInSampleSize(300, 300))
        assertEquals(1, computeInSampleSize(100, 100))
    }

    @Test
    fun sample_tepatDuaKaliTarget() {
        assertEquals(2, computeInSampleSize(512, 512))
        assertEquals(1, computeInSampleSize(511, 511))
    }

    @Test
    fun sample_inputTidakSah_aman() {
        assertEquals(1, computeInSampleSize(0, 0))
        assertEquals(1, computeInSampleSize(-1, 500))
        assertEquals(1, computeInSampleSize(500, 500, targetSidePx = 0))
    }

    // ===== outputSide =====

    @Test
    fun output_dibatasiTarget_tidakDiperbesar() {
        assertEquals(256, outputSide(3000))
        assertEquals(256, outputSide(256))
        assertEquals(100, outputSide(100))
        assertEquals(1, outputSide(1))
        assertEquals(0, outputSide(0))
    }

    // ===== exifOrientationToDegrees =====

    @Test
    fun exif_putaranDikenal() {
        assertEquals(0, exifOrientationToDegrees(1))
        assertEquals(90, exifOrientationToDegrees(6))
        assertEquals(180, exifOrientationToDegrees(3))
        assertEquals(270, exifOrientationToDegrees(8))
    }

    @Test
    fun exif_cerminDanTakDikenal_dianggapNol() {
        listOf(0, 2, 4, 5, 7, 9, -1, 99).forEach { assertEquals("o=$it", 0, exifOrientationToDegrees(it)) }
    }

    @Test
    fun kualitas_turunBertahap() {
        assertEquals(listOf(85, 70, 55), PROFILE_PHOTO_JPEG_QUALITIES)
    }
}
