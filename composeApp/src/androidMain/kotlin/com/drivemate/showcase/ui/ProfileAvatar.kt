package com.drivemate.showcase.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Avatar bulat bersama (Home + Settings driver): foto bila ada, selain itu inisial nama.
 *
 * Warna dan gaya teks dioper pemanggil supaya tampilan inisial di tiap layar tidak berubah
 * dari sebelum ada foto. Foto bersifat dekoratif ([photo] tanpa deskripsi sendiri) — nama
 * sudah dibacakan oleh pemanggil; [contentDescription] hanya bila avatar berdiri sendiri.
 */
@Composable
fun ProfileAvatar(
    name: String?,
    photo: ImageBitmap?,
    size: Dp,
    containerColor: Color,
    contentColor: Color,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(containerColor)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (photo != null) {
            Image(
                bitmap = photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(text = profileInitials(name), style = textStyle, color = contentColor)
        }
    }
}

/** Dua huruf awal nama; "?" bila kosong. */
fun profileInitials(name: String?): String =
    name?.trim()?.split(" ")?.filter { it.isNotBlank() }?.take(2)
        ?.joinToString("") { it.first().uppercase() }
        ?.ifBlank { null }
        ?: "?"

/**
 * Mendekode JPEG foto profil (256 px, kecil) ke [ImageBitmap] di luar thread utama.
 * `null` selama belum selesai atau bila [jpeg] kosong/rusak — pemanggil menampilkan inisial.
 */
@Composable
fun rememberProfilePhotoBitmap(jpeg: ByteArray?): ImageBitmap? =
    produceState<ImageBitmap?>(initialValue = null, jpeg) {
        value = if (jpeg == null) {
            null
        } else {
            withContext(Dispatchers.Default) {
                BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)?.asImageBitmap()
            }
        }
    }.value
