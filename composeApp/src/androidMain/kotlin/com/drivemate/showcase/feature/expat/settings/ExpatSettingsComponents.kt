package com.drivemate.showcase.feature.expat.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.drivemate.showcase.feature.settings.SettingsIconTint
import com.drivemate.showcase.ui.AppRoleColors
import com.drivemate.showcase.ui.AppSpacing
import com.drivemate.showcase.ui.dsv2.DmPressKind
import com.drivemate.showcase.ui.dsv2.Dsv2Badge3D
import com.drivemate.showcase.ui.dsv2.dmClickable

// TODO(2026-11-03, Iskandar): SettingsRow/SettingsGroupLabel milik Pengaturan Driver bersifat private
// (SSOT redesain Expat, D4). Salinan ringan ini hidup di paket Expat; ekstrak ke komponen bersama lalu hapus salinan.

@Composable
internal fun ExpatSettingsGroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = AppSpacing.sm)
    )
}

/**
 * Satu baris: badge 3D + label + nilai opsional + chevron. [onClick] null = baris informasi (tanpa chevron,
 * bukan tombol). Tinggi minimum 56dp dan tumbuh mengikuti font; label dan nilai berbagi lebar agar tidak terpotong.
 */
@Composable
internal fun ExpatSettingsRow(
    icon: ImageVector,
    tint: SettingsIconTint,
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    valueLeading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val interaction = if (onClick != null) {
        Modifier.dmClickable(onClick = onClick, role = Role.Button, kind = DmPressKind.Meaningful)
    } else {
        Modifier.semantics(mergeDescendants = true) {}
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(interaction)
            .padding(vertical = AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        Dsv2Badge3D(icon = icon, start = tint.start, end = tint.end, size = 36.dp, elevation = 3.dp)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        if (value != null) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically
            ) {
                valueLeading?.invoke()
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.padding(start = if (valueLeading != null) AppSpacing.xxs else 0.dp)
                )
            }
        }
        if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun ExpatSettingsRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 52.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

/**
 * Avatar inisial (Expat tidak bisa mengganti foto: aturan `user_photos` hanya mengizinkan Driver menulis).
 * Gradien dari aksen peran Expat ke versi lebih gelap, jadi teks putih ≥ 3:1 pada ujung terang (teks besar)
 * dan jauh di atas 4,5:1 pada ujung gelap. Dekoratif: nama sudah dibacakan di sebelahnya.
 */
@Composable
internal fun ExpatInitialsAvatar(name: String?, size: Dp, modifier: Modifier = Modifier) {
    val start = AppRoleColors.expat
    val end = lerp(AppRoleColors.expat, Color.Black, 0.28f)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(start, end)))
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = expatInitials(name),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1
        )
    }
}

/** Pil peran: warna dari tema (kontras dijamin tema), bukan hex sendiri. */
@Composable
internal fun ExpatRolePill(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        // Garis tepi: di kartu gelap warna wadah nyaris sama dengan kartu, jadi pil tampak bergeser tanpa batas.
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xxs)
        )
    }
}
