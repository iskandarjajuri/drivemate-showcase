package com.drivemate.showcase.feature.settings.photo

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.ImageDecoder
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.core.graphics.createBitmap
import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.domain.profile.PROFILE_PHOTO_MAX_BYTES
import java.io.ByteArrayOutputStream

/**
 * Mengolah foto pilihan driver menjadi JPEG kecil: decode hemat memori -> putar sesuai EXIF ->
 * potong persegi di tengah -> skala 256 px -> JPEG q85 (turun 70 -> 55 bila > 150 KB).
 *
 * Re-encode OTOMATIS membuang metadata EXIF, termasuk koordinat GPS — foto tidak pernah
 * meninggalkan perangkat dengan lokasinya. Hanya membaca URI yang diberikan pemilih sistem;
 * tidak ada izin penyimpanan yang diminta.
 *
 * BLOKIR (I/O + CPU): panggil dari dispatcher latar, bukan thread utama.
 *
 * Catatan perbaikan (PR-F): pembacaan ukuran memakai `inJustDecodeBounds`, dan
 * `BitmapFactory.decodeStream` SELALU mengembalikan null pada mode itu. Logika lama
 * menafsirkan null itu sebagai gagal, sehingga SEMUA foto ditolak. Kini keberhasilan
 * pembacaan ukuran ditentukan oleh dimensi (outWidth/outHeight), BUKAN oleh nilai balik
 * decode — lihat [planDecode].
 */
class ProfilePhotoProcessor(private val resolver: ContentResolver) {

    sealed interface Result {
        /** [jpeg] sudah <= [PROFILE_PHOTO_MAX_BYTES]. */
        class Success(val jpeg: ByteArray) : Result
        data class Failure(val code: ProfilePhotoErrorCode) : Result
    }

    fun process(uri: Uri): Result = try {
        processInternal(uri)
    } catch (oom: OutOfMemoryError) {
        DMLog.e(TAG, "OOM saat mengolah foto", oom)
        Result.Failure(ProfilePhotoErrorCode.TOO_LARGE)
    } catch (t: Throwable) {
        DMLog.e(TAG, "gagal mengolah foto: ${t.javaClass.simpleName}", t)
        Result.Failure(ProfilePhotoErrorCode.PROCESSING_FAILED)
    }

    private fun processInternal(uri: Uri): Result {
        // 1) Hanya ukuran, tanpa mengalokasikan piksel. Bitmap HASIL decode sengaja diabaikan:
        //    pada mode bounds-only decodeStream selalu null — yang sahih hanya outWidth/outHeight.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        } catch (oom: OutOfMemoryError) {
            throw oom // biar process() memetakan ke TOO_LARGE
        } catch (t: Throwable) {
            DMLog.e(TAG, "URI foto tak terbaca: ${t.javaClass.simpleName}", t)
            return Result.Failure(ProfilePhotoErrorCode.NOT_AN_IMAGE)
        }
        val plan = planDecode(width = bounds.outWidth, height = bounds.outHeight)
        if (plan.error != null) {
            DMLog.e(TAG, "ukuran gambar tak valid: ${bounds.outWidth}x${bounds.outHeight}")
            return Result.Failure(plan.error)
        }

        // 2) Decode terkecil yang masih >= 256 px (cegah OOM di perangkat kelas menengah).
        //    API 28+: ImageDecoder menerapkan orientasi EXIF otomatis (HEIC ikut didukung).
        //    API 26-27: BitmapFactory, rotasi EXIF manual di langkah 3.
        val decoded = decodeSampled(uri, plan.sampleSize)
            ?: run {
                DMLog.e(TAG, "decode gagal (format tak didukung atau berkas rusak)")
                return Result.Failure(ProfilePhotoErrorCode.NOT_AN_IMAGE)
            }

        // 3) Rotasi EXIF (hanya jalur BitmapFactory), lalu potong tengah + skala ke kanvas putih
        //    (JPEG tak punya alfa).
        val rotated = if (Build.VERSION.SDK_INT >= IMAGE_DECODER_MIN_SDK) decoded
        else rotate(decoded, readRotationDegrees(uri))
        val crop = centerSquareCrop(rotated.width, rotated.height)
        val side = outputSide(crop.side)
        if (side <= 0) {
            rotated.recycle()
            return Result.Failure(ProfilePhotoErrorCode.NOT_AN_IMAGE)
        }
        val output = createBitmap(side, side)
        Canvas(output).apply {
            drawColor(Color.WHITE)
            drawBitmap(
                rotated,
                Rect(crop.left, crop.top, crop.left + crop.side, crop.top + crop.side),
                Rect(0, 0, side, side),
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
        }
        rotated.recycle()

        // 4) JPEG; turunkan kualitas bertahap sampai muat.
        try {
            for (quality in PROFILE_PHOTO_JPEG_QUALITIES) {
                val buffer = ByteArrayOutputStream()
                output.compress(Bitmap.CompressFormat.JPEG, quality, buffer)
                if (buffer.size() <= PROFILE_PHOTO_MAX_BYTES) return Result.Success(buffer.toByteArray())
            }
            return Result.Failure(ProfilePhotoErrorCode.TOO_LARGE)
        } finally {
            output.recycle()
        }
    }

    private fun decodeSampled(uri: Uri, sampleSize: Int): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= IMAGE_DECODER_MIN_SDK) {
            ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(resolver, uri)
            ) { decoder, _, _ ->
                decoder.setTargetSampleSize(sampleSize)
                // WAJIB perangkat lunak: alokator bawaan bisa menghasilkan bitmap HARDWARE, dan
                // Canvas(output).drawBitmap(hardware) melempar IllegalArgumentException
                // "Software rendering doesn't support hardware bitmaps" (terbukti di logcat A52s).
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }
    } catch (oom: OutOfMemoryError) {
        throw oom // biar process() memetakan ke TOO_LARGE, bukan "bukan gambar"
    } catch (t: Throwable) {
        // OOM ditangkap lagi di process(); di sini cukup laporkan kelas penyebabnya.
        DMLog.e(TAG, "decode foto gagal: ${t.javaClass.simpleName}", t)
        null
    }

    private fun readRotationDegrees(uri: Uri): Int = try {
        resolver.openInputStream(uri)?.use { stream ->
            val orientation = ExifInterface(stream)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            exifOrientationToDegrees(orientation)
        } ?: 0
    } catch (ignored: Exception) {
        0 // Tanpa EXIF terbaca, anggap tegak; lebih baik daripada menolak foto yang sah.
    }

    private fun rotate(source: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return source
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (rotated !== source) source.recycle()
        return rotated
    }

    private companion object {
        const val TAG = "DM/ProfilePhoto"
        const val IMAGE_DECODER_MIN_SDK = 28
    }
}

/**
 * Rencana decode dari hasil pembacaan ukuran (bounds-only).
 *
 * @param sampleSize nilai [computeInSampleSize] untuk dimensi yang valid.
 * @param error kode gagal, atau null bila dimensi valid dan decode boleh lanjut.
 */
internal data class DecodePlan(val sampleSize: Int, val error: ProfilePhotoErrorCode?)

/**
 * Keputusan "apakah pembacaan ukuran berhasil" berdasarkan DIMENSI, bukan nilai balik decode.
 * Regresi untuk bug lama: decodeStream pada mode bounds-only mengembalikan null untuk SEMUA
 * foto; logika lama menafsirkan itu sebagai NOT_AN_IMAGE, logika baru tidak.
 */
internal fun planDecode(width: Int, height: Int): DecodePlan = when {
    width <= 0 || height <= 0 -> DecodePlan(sampleSize = 1, error = ProfilePhotoErrorCode.NOT_AN_IMAGE)
    else -> DecodePlan(sampleSize = computeInSampleSize(width, height), error = null)
}
