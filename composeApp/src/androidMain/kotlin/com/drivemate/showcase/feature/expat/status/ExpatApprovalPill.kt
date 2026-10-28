package com.drivemate.showcase.feature.expat.status

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drivemate.showcase.core.model.ApprovalStatus
import com.drivemate.showcase.ui.AppElevation
import com.drivemate.showcase.ui.AppPalette
import com.drivemate.showcase.ui.AppShapes
import com.drivemate.showcase.ui.AppSpacing

/**
 * Bentuk ikon status persetujuan Expat. Tiap status punya bentuk BERBEDA (jam, centang, silang) supaya status
 * tidak dibedakan hanya oleh warna (WCAG 1.4.1; sekitar 5% pria Jepang memiliki variasi penglihatan warna).
 * Rujukan: docs/design/expat-ja-ux.md.
 */
enum class ExpatApprovalIcon { PENDING, APPROVED, REJECTED }

/** Fungsi murni: status persetujuan -> bentuk ikon. */
fun expatApprovalIcon(status: ApprovalStatus): ExpatApprovalIcon = when (status) {
    ApprovalStatus.PENDING -> ExpatApprovalIcon.PENDING
    ApprovalStatus.APPROVED -> ExpatApprovalIcon.APPROVED
    ApprovalStatus.REJECTED -> ExpatApprovalIcon.REJECTED
}

/** Pasangan warna pil: (latar, teks dan ikon). Sama dengan pil bersama (peringatan/sukses/error), disatukan untuk Expat. */
data class ExpatApprovalColors(val container: Color, val content: Color)

/** Membaca [AppPalette] saat dipanggil, sehingga mengikuti mode terang/gelap yang sedang aktif. */
fun expatApprovalColors(status: ApprovalStatus): ExpatApprovalColors = when (status) {
    ApprovalStatus.PENDING -> ExpatApprovalColors(AppPalette.warningContainerSolid, AppPalette.onWarningContainer)
    ApprovalStatus.APPROVED -> ExpatApprovalColors(AppPalette.successContainerSolid, AppPalette.onSuccessContainer)
    ApprovalStatus.REJECTED -> ExpatApprovalColors(AppPalette.errorContainerSolid, AppPalette.onErrorContainer)
}

private fun ExpatApprovalIcon.vector(): ImageVector = when (this) {
    ExpatApprovalIcon.PENDING -> Icons.Filled.Schedule
    ExpatApprovalIcon.APPROVED -> Icons.Filled.CheckCircle
    ExpatApprovalIcon.REJECTED -> Icons.Filled.Cancel
}

/**
 * Pil status persetujuan khusus Expat: ikon + label teks. Dipakai di semua tempat Expat menampilkan status
 * Menunggu/Disetujui/Ditolak. Komponen bersama (`WorkdayStatusPill`) sengaja TIDAK diubah karena dipakai Driver/Admin.
 * Ikon dekoratif (contentDescription null); teks pil yang dibacakan TalkBack, sekali saja.
 */
@Composable
fun ExpatApprovalPill(
    status: ApprovalStatus,
    text: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val colors = expatApprovalColors(status)
    val horizontal = if (compact) AppSpacing.xs else AppSpacing.sm
    val vertical = if (compact) AppSpacing.xxs else AppSpacing.xs
    val iconSize = if (compact) 12.dp else 14.dp
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = text },
        shape = RoundedCornerShape(AppShapes.field),
        color = colors.container,
        border = BorderStroke(AppSpacing.xxs / 4, colors.content.copy(alpha = 0.22f)),
        tonalElevation = AppElevation.none,
        shadowElevation = AppElevation.low
    ) {
        Row(
            modifier = Modifier.padding(horizontal = horizontal, vertical = vertical),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xxs)
        ) {
            Icon(
                imageVector = expatApprovalIcon(status).vector(),
                contentDescription = null,
                tint = colors.content,
                modifier = Modifier.size(iconSize)
            )
            Text(
                text = text,
                color = colors.content,
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
