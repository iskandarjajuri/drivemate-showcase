package com.drivemate.showcase.ui.dsv2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Badge ikon lingkaran dengan aksen 3D — **resep bersama**, satu-satunya di repo ini.
 *
 * ## Kenapa berkas ini ada
 * Resep ini lahir di `DriverWorkHistoryAgendaScreen.kt` sebagai `private fun Badge3D` dan
 * inilah yang membuat layar driver "terasa mahal", sementara permukaan admin terasa datar
 * (SSOT desain §0, sebab 1). SSOT mensyaratkan resep ini **diangkat**, bukan disalin:
 * menyalin berarti dua resep yang pasti akan mendrift, dan yang mendrift adalah justru hal
 * yang membedakan kualitas terasa antara dua sisi aplikasi.
 *
 * ## Resepnya — ketiganya wajib, hilang satu maka efek cembungnya hilang
 * 1. isian `Brush.linearGradient` dari [start] ke [end];
 * 2. highlight diagonal `Color.White` alpha 0.55 dari `Offset.Zero` ke `(0.85w, 0.85h)`;
 * 3. `shadow` yang **di-scope ke badge saja**, bukan ke kartu pembungkusnya.
 *
 * ## Ukuran
 * [size] default 44dp seperti pemakaian aslinya di layar driver. Rail status admin memakai
 * 32dp — sengaja lebih kecil (SSOT §1) supaya tidak berebut perhatian dengan isi layar.
 * Ikonnya ikut berskala pada separuh diameter, angka yang sama dengan aslinya (22dp dari 44dp),
 * supaya badge kecil tidak tampak seperti ikon yang kekecilan di dalam lingkaran besar.
 */
@Composable
fun Dsv2Badge3D(
    icon: ImageVector,
    start: Color,
    end: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    elevation: Dp = 6.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            // shadow di-scope ke badge saja
            .shadow(elevation = elevation, shape = CircleShape, clip = false)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(start, end)))
            .drawWithContent {
                drawContent()
                // Highlight diagonal transparan dari kiri-atas -> memberi kesan cembung.
                drawCircle(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
                        start = Offset.Zero,
                        end = Offset(this.size.width * 0.85f, this.size.height * 0.85f)
                    ),
                    radius = this.size.minDimension / 2f
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size / 2)
        )
    }
}
