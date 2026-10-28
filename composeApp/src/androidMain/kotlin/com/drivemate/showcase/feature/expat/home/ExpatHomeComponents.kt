package com.drivemate.showcase.feature.expat.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Badge
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drivemate.showcase.R
import com.drivemate.showcase.feature.driver.draw155Gradient
import com.drivemate.showcase.feature.driver.notifications.formatNotificationBadge
import com.drivemate.showcase.feature.expat.ExpatMaestroTags
import com.drivemate.showcase.feature.expat.ManagedDriverSummaryUi
import com.drivemate.showcase.feature.settings.SettingsIconTints
import com.drivemate.showcase.ui.AppRoleColors
import com.drivemate.showcase.ui.AppSpacing
import com.drivemate.showcase.ui.LocalReduceMotion
import com.drivemate.showcase.ui.dsv2.DmPressKind
import com.drivemate.showcase.ui.dsv2.DmElevatedCard
import com.drivemate.showcase.ui.dsv2.DmTonalCard
import com.drivemate.showcase.ui.dsv2.Dsv2Badge3D
import com.drivemate.showcase.ui.dsv2.dmClickable
import com.drivemate.showcase.ui.patterns.rememberDmAuthBrandPalette
import com.drivemate.showcase.ui.patterns.rememberDmAuthIsDark

// Skala radius Beranda mengikuti Beranda Driver: hero 24, kartu 16, tile 14, ikon 12.
private val HeroRadius = 24.dp
private val CardRadius = 16.dp
private val TileRadius = 14.dp

// TODO(2026-11-03, Iskandar): nilai gradien tile sama dengan TILE_GRADIENT_* milik Beranda Driver (private); ekstrak ke
// komponen bersama lalu hapus salinan ini (SSOT redesain Expat, D4).
private val TileGradientReview = listOf(Color(0xFF4ADE80), Color(0xFF16A34A))
private val TileGradientDrivers = listOf(Color(0xFF60A5FA), Color(0xFF2563EB))
private val TileGradientMessages = listOf(Color(0xFF22D3EE), Color(0xFF0891B2))
private val TileGradientAnnouncements = listOf(Color(0xFFA78BFA), Color(0xFF7C3AED))
private const val TILE_PULSE_MAX_SCALE = 1.07f

/** Animasi masuk berurutan (alpha + naik 8dp): hanya bila [play]; reduce motion = langsung tampil. */
@Composable
internal fun Modifier.expatStaggerIn(index: Int, play: Boolean): Modifier {
    val reduce = LocalReduceMotion.current
    val progress = remember { Animatable(if (play && !reduce) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            progress.animateTo(1f, tween(durationMillis = 280, delayMillis = expatStaggerDelayMillis(index)))
        }
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 8.dp.toPx()
    }
}

/** Hitung naik angka hero (500 ms) sekali; setelahnya angka mengikuti nilai. Reduce motion: langsung. */
@Composable
private fun rememberCountUp(target: Int, play: Boolean, onFinished: () -> Unit): Int {
    val reduce = LocalReduceMotion.current
    val anim = remember { Animatable(if (play && !reduce) 0f else target.toFloat()) }
    LaunchedEffect(target) {
        if (play && !reduce) anim.animateTo(target.toFloat(), tween(500, easing = FastOutSlowInEasing)) else anim.snapTo(target.toFloat())
        onFinished()
    }
    return anim.value.toInt().coerceAtLeast(0)
}

/**
 * Hero gradien: gradien brand 155° yang sama dengan hero Driver (`draw155Gradient` + `rememberDmAuthBrandPalette`) plus
 * pendar teal aksen peran Expat (SSOT D2). Teks mewarisi putih lewat [LocalContentColor] seperti hero Driver.
 */
@Composable
internal fun ExpatHeroShell(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(HeroRadius)
    val brand = rememberDmAuthBrandPalette(rememberDmAuthIsDark())
    val glow = AppRoleColors.expat
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 2.dp, shape = shape, clip = false)
            .clip(shape)
            .drawBehind {
                draw155Gradient(listOf(brand.gradientStart, brand.gradientEnd))
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(glow.copy(alpha = 0.38f), Color.Transparent),
                        center = Offset(size.width * 0.92f, 0f),
                        radius = size.width * 0.75f
                    )
                )
            }
    ) {
        CompositionLocalProvider(LocalContentColor provides brand.wordmarkPrimary) {
            Column(
                modifier = Modifier.padding(AppSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) { content() }
        }
    }
}

/** Titik "menunggu": berdenyut hanya saat [enabled]; reduce motion = titik diam (informasi ada pada titiknya). */
@Composable
private fun ExpatPulseDot(enabled: Boolean) {
    val reduce = LocalReduceMotion.current
    val alpha = if (!enabled || reduce) {
        1f
    } else {
        val t = rememberInfiniteTransition(label = "expatPendingDot")
        val a by t.animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "expatPendingDotAlpha"
        )
        a
    }
    Box(Modifier.size(8.dp).background(SettingsIconTints.reminder.start.copy(alpha = alpha), CircleShape))
}

/** Hero Beranda. Keadaan "semua selesai" dipertahankan dari logika kejujuran: hanya dipanggil bila data tersedia. */
@Composable
internal fun ExpatHeroCard(
    pendingTotal: Int,
    reviewedTotal: Int,
    countUpPending: Boolean,
    onCountUpFinished: () -> Unit,
    onStart: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val countCd = pluralStringResource(R.plurals.expat_home_hero_count_cd_format, pendingTotal, pendingTotal)
    ExpatHeroShell(modifier.testTag(ExpatMaestroTags.HOME_HERO)) {
        if (pendingTotal > 0 && onStart != null) {
            val shown = rememberCountUp(pendingTotal, play = countUpPending, onFinished = onCountUpFinished)
            Column(
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = countCd },
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                    ExpatPulseDot(enabled = expatPulseEnabled(pendingTotal))
                    Text(
                        text = stringResource(R.string.expat_home_hero_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = LocalContentColor.current.copy(alpha = 0.9f)
                    )
                }
                Text(
                    text = shown.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
            }
            ExpatHeroProgress(pending = pendingTotal, reviewed = reviewedTotal)
            ExpatHeroButton(
                text = stringResource(R.string.expat_home_hero_action),
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().testTag(ExpatMaestroTags.HOME_HERO_ACTION)
            )
        } else {
            Text(
                text = stringResource(R.string.expat_home_hero_all_clear_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.expat_home_hero_all_clear_message),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalContentColor.current.copy(alpha = 0.9f)
            )
        }
    }
}

/** Bilah progres ditinjau/total di dalam hero, dengan teks hitungan (status tidak hanya warna). */
@Composable
private fun ExpatHeroProgress(pending: Int, reviewed: Int) {
    val reduce = LocalReduceMotion.current
    val target = expatReviewProgress(pending, reviewed)
    val anim = remember { Animatable(if (reduce) target else 0f) }
    LaunchedEffect(target) {
        if (reduce) anim.snapTo(target) else anim.animateTo(target, tween(700, easing = FastOutSlowInEasing))
    }
    val label = stringResource(R.string.expat_home_hero_progress_format, reviewed, pending + reviewed)
    Column(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = label },
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)
    ) {
        Box(
            Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(LocalContentColor.current.copy(alpha = 0.22f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(anim.value.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(LocalContentColor.current)
            )
        }
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = LocalContentColor.current.copy(alpha = 0.9f))
    }
}

/** Tombol di atas hero gradien: wadah terang, teks gelap merek (kontras tinggi di kedua tema). */
@Composable
private fun ExpatHeroButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val brand = rememberDmAuthBrandPalette(rememberDmAuthIsDark())
    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .dmClickable(onClick = onClick, role = Role.Button, kind = DmPressKind.Meaningful, hapticType = HapticFeedbackType.VirtualKey),
        shape = RoundedCornerShape(TileRadius),
        color = brand.wordmarkPrimary,
        contentColor = brand.gradientEnd
    ) {
        Box(Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm), contentAlignment = Alignment.Center) {
            Text(text = text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Ringkasan tiga angka dengan badge 3D (ikon + angka + teks, bukan warna saja). */
@Composable
internal fun ExpatSummaryStrip(totalDrivers: Int, pendingTotal: Int, reviewedTotal: Int, modifier: Modifier = Modifier) {
    DmTonalCard(modifier = modifier.fillMaxWidth().testTag(ExpatMaestroTags.HOME_SUMMARY)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top
        ) {
            ExpatSummaryStat(Icons.Outlined.People, SettingsIconTints.language.start, SettingsIconTints.language.end,
                stringResource(R.string.expat_home_summary_drivers_label), totalDrivers, Modifier.weight(1f))
            ExpatSummaryStat(Icons.Outlined.Schedule, SettingsIconTints.reminder.start, SettingsIconTints.reminder.end,
                stringResource(R.string.expat_home_summary_pending_label), pendingTotal, Modifier.weight(1f))
            ExpatSummaryStat(Icons.Outlined.CheckCircle, SettingsIconTints.location.start, SettingsIconTints.location.end,
                stringResource(R.string.expat_home_summary_reviewed_label), reviewedTotal, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ExpatSummaryStat(icon: ImageVector, start: Color, end: Color, label: String, value: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)
    ) {
        Dsv2Badge3D(icon = icon, start = start, end = end, size = 32.dp, elevation = 3.dp)
        Text(text = value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "Perlu perhatian": avatar inisial + nama dan hitungan (teks yang sama dengan sebelumnya) + progres per driver. */
@Composable
internal fun ExpatAttentionCard(
    attention: List<ManagedDriverSummaryUi>,
    onOpenDriver: (driverUid: String) -> Unit,
    modifier: Modifier = Modifier
) {
    DmElevatedCard(modifier = modifier.fillMaxWidth().testTag(ExpatMaestroTags.HOME_ATTENTION)) {
        Text(text = stringResource(R.string.expat_home_attention_title), style = MaterialTheme.typography.titleSmall)
        if (attention.isEmpty()) {
            Text(
                text = stringResource(R.string.expat_home_attention_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = AppSpacing.xs)
            )
        } else {
            attention.forEach { driver ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .dmClickable(onClick = { onOpenDriver(driver.uid) }, role = Role.Button, kind = DmPressKind.Meaningful)
                        .padding(vertical = AppSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    ExpatMiniAvatar(driver.displayName)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
                        Text(
                            text = stringResource(R.string.expat_home_attention_row_format, driver.displayName, driver.pendingCount),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        val reviewed = driver.approvedCount + driver.rejectedCount
                        val progress = expatReviewProgress(driver.pendingCount, reviewed)
                        Box(
                            Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                                .clearAndSetSemantics {}
                        ) {
                            Box(
                                Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape)
                                    .background(AppRoleColors.expat)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Avatar inisial 40dp (dekoratif: nama dibacakan di sebelahnya). */
@Composable
internal fun ExpatMiniAvatar(name: String?, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 40.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(AppRoleColors.expat, lerp(AppRoleColors.expat, Color.Black, 0.28f))))
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = homeAvatarInitials(name),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1
        )
    }
}

/** Avatar di header (target sentuh ≥ 48dp): membuka Pengaturan. */
@Composable
internal fun ExpatHeaderAvatar(name: String?, onClick: () -> Unit, contentDescription: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .dmClickable(onClick = onClick, role = Role.Button, kind = DmPressKind.Icon),
        contentAlignment = Alignment.Center
    ) {
        ExpatMiniAvatar(name = name, size = 36.dp, modifier = Modifier.semantics { this.contentDescription = contentDescription })
    }
}

/** Aksi cepat 2×2. Denyut hanya pada tile Tinjau dan hanya saat ada yang menunggu. */
@Composable
internal fun ExpatQuickTiles(
    pendingTotal: Int,
    chatUnread: Int,
    onReview: () -> Unit,
    onDrivers: () -> Unit,
    onMessages: () -> Unit,
    onAnnouncements: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            ExpatQuickTile(
                title = stringResource(R.string.expat_nav_tab_review),
                icon = Icons.Outlined.CheckCircle,
                gradient = TileGradientReview,
                pulse = expatPulseEnabled(pendingTotal),
                badgeText = formatNotificationBadge(pendingTotal),
                onClick = onReview,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            ExpatQuickTile(
                title = stringResource(R.string.expat_nav_tab_drivers),
                icon = Icons.Outlined.People,
                gradient = TileGradientDrivers,
                onClick = onDrivers,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            ExpatQuickTile(
                title = stringResource(R.string.expat_chat_title),
                icon = Icons.AutoMirrored.Outlined.Chat,
                gradient = TileGradientMessages,
                badgeText = formatNotificationBadge(chatUnread),
                badgeTag = ExpatMaestroTags.CHAT_UNREAD_BADGE,
                onClick = onMessages,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            ExpatQuickTile(
                title = stringResource(R.string.expat_announcements_title),
                icon = Icons.Outlined.Campaign,
                gradient = TileGradientAnnouncements,
                onClick = onAnnouncements,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}

@Composable
private fun ExpatQuickTile(
    title: String,
    icon: ImageVector,
    gradient: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    pulse: Boolean = false,
    badgeText: String? = null,
    badgeTag: String? = null
) {
    val reduce = LocalReduceMotion.current
    val scale = if (!pulse || reduce) {
        1f
    } else {
        val t = rememberInfiniteTransition(label = "expatTilePulse")
        val s by t.animateFloat(
            initialValue = 1f,
            targetValue = TILE_PULSE_MAX_SCALE,
            animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "expatTilePulseScale"
        )
        s
    }
    val shape = RoundedCornerShape(TileRadius)
    Surface(
        modifier = modifier
            .heightIn(min = 88.dp)
            .dmClickable(onClick = onClick, role = Role.Button, kind = DmPressKind.Meaningful),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.sm).semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .size(44.dp)
                        .scale(scale)
                        .clip(RoundedCornerShape(12.dp))
                        .drawBehind { draw155Gradient(gradient) },
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(imageVector = icon, contentDescription = null, tint = Color.White)
                }
                badgeText?.let {
                    Badge(modifier = if (badgeTag != null) Modifier.testTag(badgeTag) else Modifier) { Text(it) }
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
