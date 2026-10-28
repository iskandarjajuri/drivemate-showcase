package com.drivemate.showcase.feature.expat.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.drivemate.showcase.ui.AppRoleColors

// TODO(2026-11-03, Iskandar): inisial dan avatar kini ada di tiga tempat (Pengaturan, Beranda, daftar); satukan setelah
// PR redesain Expat merge.

/** Avatar inisial driver (dekoratif: nama dibacakan di sebelahnya). Teks putih di atas gradien aksen Expat. */
@Composable
internal fun ExpatQueueAvatar(name: String?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(AppRoleColors.expat, lerp(AppRoleColors.expat, Color.Black, 0.28f))))
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = queueAvatarInitials(name),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1
        )
    }
}
