package com.drivemate.showcase.feature.settings.photo

import com.drivemate.showcase.domain.profile.PROFILE_PHOTO_SIDE_PX

/**
 * Hitungan geometri foto profil — sengaja FUNGSI MURNI (tanpa Bitmap/Android) supaya bisa diuji
 * unit di JVM. Pemroses yang menyentuh Bitmap ([ProfilePhotoProcessor]) hanya memanggilnya.
 */

/** Persegi potongan tengah: pojok kiri-atas ([left], [top]) dan panjang sisi [side], dalam piksel. */
data class SquareCrop(val left: Int, val top: Int, val side: Int)

/**
 * Potongan persegi di tengah gambar berukuran [width] x [height].
 * Sisi = sisi terpendek; sisa dibagi rata (pembulatan ke bawah di kiri/atas).
 * Dimensi tidak positif menghasilkan sisi 0 — pemanggil menolaknya sebagai bukan gambar.
 */
fun centerSquareCrop(width: Int, height: Int): SquareCrop {
    if (width <= 0 || height <= 0) return SquareCrop(0, 0, 0)
    val side = minOf(width, height)
    return SquareCrop(left = (width - side) / 2, top = (height - side) / 2, side = side)
}

/**
 * `inSampleSize` terbesar (pangkat dua) yang MASIH menyisakan sisi terpendek >= [targetSidePx].
 * Mencegah OOM pada foto kamera 12 MP+ tanpa membuang resolusi yang dibutuhkan hasil akhir.
 * Gambar yang sudah lebih kecil dari target mendapat 1 (tidak diperkecil).
 */
fun computeInSampleSize(width: Int, height: Int, targetSidePx: Int = PROFILE_PHOTO_SIDE_PX): Int {
    if (width <= 0 || height <= 0 || targetSidePx <= 0) return 1
    val shortest = minOf(width, height)
    var sample = 1
    while (shortest / (sample * 2) >= targetSidePx) sample *= 2
    return sample
}

/**
 * Sisi hasil akhir: [targetSidePx], tetapi tidak pernah lebih besar dari sisi potongan —
 * foto yang sangat kecil tidak diperbesar (hanya membuat buram dan menambah byte).
 */
fun outputSide(cropSide: Int, targetSidePx: Int = PROFILE_PHOTO_SIDE_PX): Int =
    if (cropSide <= 0) 0 else minOf(cropSide, targetSidePx)

/**
 * Derajat putar searah jarum jam untuk nilai tag EXIF Orientation.
 * Konstanta ditulis sebagai angka (6/3/8) agar fungsi ini tidak bergantung pada kelas Android.
 * Orientasi cermin (2, 4, 5, 7) dan nilai tak dikenal diperlakukan sebagai 0: kamera bawaan
 * tidak menulisnya untuk hasil `TakePicture`, dan mencerminkan foto diam-diam lebih berbahaya
 * daripada membiarkannya apa adanya.
 */
fun exifOrientationToDegrees(orientation: Int): Int = when (orientation) {
    EXIF_ROTATE_90 -> 90
    EXIF_ROTATE_180 -> 180
    EXIF_ROTATE_270 -> 270
    else -> 0
}

/** Urutan kualitas JPEG yang dicoba; berhenti pada yang pertama muat di batas ukuran. */
val PROFILE_PHOTO_JPEG_QUALITIES: List<Int> = listOf(85, 70, 55)

private const val EXIF_ROTATE_90 = 6
private const val EXIF_ROTATE_180 = 3
private const val EXIF_ROTATE_270 = 8
