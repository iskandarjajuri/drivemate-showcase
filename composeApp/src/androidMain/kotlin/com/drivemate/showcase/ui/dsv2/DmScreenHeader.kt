package com.drivemate.showcase.ui.dsv2

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivemate.showcase.R
import com.drivemate.showcase.ui.ds.DriveMateTopBar
import com.drivemate.showcase.ui.motionDuration

/**
 * `true` bila rute aktif dimiliki header baru (`DmScreenHeader` menghitung inset status bar
 * SENDIRI), sehingga `DriveMateRootScaffold` tidak boleh menambah inset atas lagi. Diisi
 * oleh root; layar yang dipakai lintas peran (Chat) memakainya lewat [DmAdaptiveTopBar].
 */
val LocalScreenHeaderOwnsTopInset = compositionLocalOf { false }

/** Tinggi isi header (di luar inset status bar). */
val DmScreenHeaderHeight = 56.dp

/** Tinggi total yang ditutup header: status bar + [DmScreenHeaderHeight]. */
@Composable
fun screenHeaderInset(): Dp =
    with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() } + DmScreenHeaderHeight

/** Padding atas supaya item pertama kontainer gulir tidak tertutup header yang melayang. */
@Composable
fun screenHeaderContentPadding(base: PaddingValues = PaddingValues()): PaddingValues {
    val dir = LocalLayoutDirection.current
    return PaddingValues(
        start = base.calculateStartPadding(dir),
        top = base.calculateTopPadding() + screenHeaderInset(),
        end = base.calculateEndPadding(dir),
        bottom = base.calculateBottomPadding()
    )
}

/**
 * Header layar driver: TRANSPARAN, menyatu dengan latar layar (tanpa bar putih, garis, atau
 * elevasi). Inset status bar dihitung sekali di sini. Tombol kembali/aksi berupa lingkaran
 * 40dp (target sentuh 48dp) berlatar surface + bayangan tipis; judul berupa teks langsung di
 * atas latar. [scrolled] = true menggambar scrim gradien (warna latar 100% → 0%) di belakang
 * header, animasi ±200 ms (reduce-motion: langsung); posisi paling atas tanpa scrim.
 */
@Composable
fun DmScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    scrolled: Boolean = false,
    titleMaxLines: Int = 1,
    subtitleMaxLines: Int = 1,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val background = MaterialTheme.colorScheme.background
    val scrimAlpha by animateFloatAsState(
        targetValue = if (scrolled) 1f else 0f,
        animationSpec = tween(durationMillis = motionDuration(200)),
        label = "dmScreenHeaderScrim"
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                if (scrimAlpha > 0f) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(background, background.copy(alpha = 0f)),
                            startY = 0f,
                            endY = size.height
                        ),
                        alpha = scrimAlpha
                    )
                }
            }
            .statusBarsPadding()
            .heightIn(min = DmScreenHeaderHeight)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (onBack != null) {
            DmHeaderCircleButton(onClick = onBack)
        } else {
            Box(Modifier.size(12.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = subtitleMaxLines,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        actions()
    }
}

@Composable
private fun DmHeaderCircleButton(onClick: () -> Unit) {
    val label = stringResource(R.string.action_back)
    Box(
        modifier = Modifier
            .size(48.dp)
            .dmClickable(onClick = onClick, role = Role.Button)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .shadow(elevation = 2.dp, shape = CircleShape)
                .background(MaterialTheme.colorScheme.surface, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Untuk layar yang dipakai lintas peran (mis. Chat): header baru bila rute dimiliki header
 * driver, selain itu bar lama -- supaya layar admin/expat tidak berubah.
 */
@Composable
fun DmAdaptiveTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    if (LocalScreenHeaderOwnsTopInset.current) {
        DmScreenHeader(title = title, onBack = onBack, actions = actions)
    } else {
        DriveMateTopBar(title = title, onBack = onBack, actions = actions)
    }
}
