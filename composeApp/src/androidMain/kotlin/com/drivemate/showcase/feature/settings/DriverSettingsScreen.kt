package com.drivemate.showcase.feature.settings

import com.drivemate.showcase.ui.dsv2.dmClickable
import com.drivemate.showcase.ui.dsv2.DmPressKind
import com.drivemate.showcase.feature.settings.photo.PhotoSyncState
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.exclude
import com.drivemate.showcase.ui.dsv2.DmScreenHeader
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.ripple
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivemate.showcase.feature.settings.photo.ProfilePhotoFlow
import com.drivemate.showcase.feature.settings.photo.ProfilePhotoUiState
import com.drivemate.showcase.feature.settings.photo.ProfilePhotoViewModel
import com.drivemate.showcase.ui.ProfileAvatar
import com.drivemate.showcase.ui.rememberProfilePhotoBitmap
import com.drivemate.showcase.BuildConfig
import com.drivemate.showcase.R
import com.drivemate.showcase.components.DMTopAppBar
import com.drivemate.showcase.ui.AppPalette
import com.drivemate.showcase.ui.AppShapes
import com.drivemate.showcase.ui.AppSpacing
import com.drivemate.showcase.ui.SoftCard
import com.drivemate.showcase.ui.dsv2.Dsv2Badge3D
import com.drivemate.showcase.ui.patterns.DmConfirmDialog

@Composable
fun DriverSettingsScreen(
    vm: DriverSettingsViewModel,
    photoVm: ProfilePhotoViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onOpenLocationUsage: () -> Unit,
    onOpenSop: () -> Unit,
    onOpenManageAccount: () -> Unit
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var showReminderTimePicker by rememberSaveable { mutableStateOf(false) }
    var showLanguageSheet by rememberSaveable { mutableStateOf(false) }
    var showLogoutConfirm by rememberSaveable { mutableStateOf(false) }
    var showPhotoSheet by rememberSaveable { mutableStateOf(false) }
    val photoJpeg by photoVm.photoJpeg.collectAsStateWithLifecycle()
    val photoBitmap = rememberProfilePhotoBitmap(photoJpeg)
    val syncState by photoVm.syncState.collectAsStateWithLifecycle()
    val photoUiState by photoVm.uiState.collectAsStateWithLifecycle()
    // Processing/Uploading = cincin progres tipis di avatar, bukan dialog pemblokir.
    val photoBusy = photoUiState is ProfilePhotoUiState.Processing ||
        photoUiState is ProfilePhotoUiState.Uploading
    val photoBusyLabelRes = when (photoUiState) {
        ProfilePhotoUiState.Processing -> R.string.driver_profile_photo_processing
        ProfilePhotoUiState.Uploading -> R.string.driver_profile_photo_uploading
        else -> null
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Granted or not, the reminder is scheduled either way; without the
          permission the OS simply won't show it -- no extra state to track. */ }

    Scaffold(
        topBar = { DmScreenHeader(title = stringResource(R.string.driver_settings_title), onBack = onBack) },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .windowInsetsPadding(WindowInsets.safeDrawing.exclude(WindowInsets.statusBars))
                .padding(AppSpacing.xs)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            DriverProfileHeader(
                name = state.driverName?.toDisplayTitleCase(),
                role = stringResource(R.string.driver_role_default),
                email = state.driverEmail,
                photo = photoBitmap,
                photoBusy = photoBusy,
                photoBusyLabelRes = photoBusyLabelRes,
                syncState = if (photoJpeg != null) syncState else PhotoSyncState.None,
                onRetrySync = photoVm::retrySync,
                onChangePhoto = { showPhotoSheet = true }
            )

            SettingsGroupLabel(stringResource(R.string.driver_settings_section_preferences))
            SoftCard(modifier = Modifier.fillMaxWidth()) {
                SettingsRow(
                    icon = Icons.Rounded.Language,
                    tint = SettingsIconTints.language,
                    label = stringResource(R.string.driver_settings_language_label),
                    value = languageDisplayLabel(state.languageTag),
                    valueLeading = { LanguageValueLeading(state.languageTag) },
                    trailing = SettingsTrailing.Chevron,
                    onClick = { showLanguageSheet = true },
                    modifier = Modifier.testTag(DriverSettingsMaestroTags.LANGUAGE_ROW)
                )
                SettingsRowDivider()

                // TODO(2026-10-09, Iskandar): tampilkan lagi toggle Tap-out/Pengumuman/Pesan setelah push server aktif; kunci preferensi dipertahankan.
                SettingsRow(
                    icon = Icons.Rounded.Notifications,
                    tint = SettingsIconTints.reminder,
                    label = stringResource(R.string.driver_settings_checklist_reminder_label),
                    trailing = SettingsTrailing.None,
                    switchChecked = state.checklistReminderEnabled,
                    onCheckedChange = { enabled ->
                        vm.setChecklistReminderEnabled(enabled)
                        if (enabled) {
                            requestNotificationPermissionIfNeeded(context, notificationPermissionLauncher)
                            DriverChecklistReminderScheduler.schedule(
                                context,
                                state.checklistReminderHour,
                                state.checklistReminderMinute
                            )
                        } else {
                            DriverChecklistReminderScheduler.cancel(context)
                        }
                    },
                    modifier = Modifier.testTag(DriverSettingsMaestroTags.REMINDER_SWITCH)
                )
                if (state.checklistReminderEnabled) {
                    SettingsRowDivider()
                    SettingsRow(
                        icon = Icons.Rounded.Schedule,
                        tint = SettingsIconTints.time,
                        label = stringResource(R.string.driver_settings_reminder_time_picker_title),
                        value = stringResource(
                            R.string.driver_settings_checklist_reminder_time_format,
                            "%02d:%02d".format(state.checklistReminderHour, state.checklistReminderMinute)
                        ),
                        trailing = SettingsTrailing.Chevron,
                        onClick = { showReminderTimePicker = true },
                        modifier = Modifier.padding(start = AppSpacing.lg)
                            .testTag(DriverSettingsMaestroTags.REMINDER_TIME_ROW)
                    )
                }
                if (Build.VERSION.SDK_INT < 33) {
                    Text(
                        text = stringResource(R.string.driver_settings_language_unavailable_below_13),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = AppSpacing.xs)
                    )
                }
            }

            SettingsGroupLabel(stringResource(R.string.driver_settings_section_help_privacy))
            SoftCard(modifier = Modifier.fillMaxWidth()) {
                SettingsRow(
                    icon = Icons.Rounded.HelpOutline,
                    tint = SettingsIconTints.help,
                    label = stringResource(R.string.driver_settings_sop_help_label),
                    trailing = SettingsTrailing.Chevron,
                    onClick = onOpenSop,
                    modifier = Modifier.testTag(DriverSettingsMaestroTags.SOP_ROW)
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Rounded.LocationOn,
                    tint = SettingsIconTints.location,
                    label = stringResource(R.string.driver_settings_location_usage_label),
                    trailing = SettingsTrailing.Chevron,
                    onClick = onOpenLocationUsage,
                    modifier = Modifier.testTag(DriverSettingsMaestroTags.LOCATION_ROW)
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Rounded.ManageAccounts,
                    tint = SettingsIconTints.system,
                    label = stringResource(R.string.driver_settings_manage_account_label),
                    trailing = SettingsTrailing.Chevron,
                    onClick = onOpenManageAccount
                )
            }

            val logoutLabel = stringResource(R.string.driver_settings_logout_label)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag(DriverSettingsMaestroTags.LOGOUT_BUTTON)
                    .clip(RoundedCornerShape(AppShapes.button))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.error.copy(alpha = 0.35f),
                        RoundedCornerShape(AppShapes.button)
                    )
                    .dmClickable(
                        onClick = { showLogoutConfirm = true },
                        role = Role.Button,
                        kind = DmPressKind.Meaningful
                    )
                    .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.error, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(AppSpacing.sm))
                Text(
                    text = logoutLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }

            val appVersionLabel = stringResource(R.string.driver_settings_app_version_label)
            Text(
                text = stringResource(R.string.driver_settings_footer_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "$appVersionLabel ${BuildConfig.VERSION_NAME}"
                    }
            )
        }
    }

    if (showLanguageSheet) {
        LanguagePickerSheet(
            currentTag = state.languageTag,
            onSelect = { tag ->
                showLanguageSheet = false
                vm.setLanguageTag(tag)
                applyAppLanguage(tag)
            },
            onDismiss = { showLanguageSheet = false }
        )
    }

    if (showReminderTimePicker) {
        ReminderTimePickerDialog(
            initialHour = state.checklistReminderHour,
            initialMinute = state.checklistReminderMinute,
            onConfirm = { hour, minute ->
                showReminderTimePicker = false
                vm.setChecklistReminderTime(hour, minute)
                if (state.checklistReminderEnabled) {
                    DriverChecklistReminderScheduler.schedule(context, hour, minute)
                }
            },
            onDismiss = { showReminderTimePicker = false }
        )
    }

    ProfilePhotoFlow(
        vm = photoVm,
        showSheet = showPhotoSheet,
        onShowSheetChange = { showPhotoSheet = it }
    )

    if (showLogoutConfirm) {
        DmConfirmDialog(
            title = stringResource(R.string.dm_logout_title),
            message = stringResource(R.string.dm_logout_message),
            confirmLabel = stringResource(R.string.dm_logout_confirm),
            cancelLabel = stringResource(R.string.dm_logout_cancel),
            destructive = true,
            onConfirm = {
                showLogoutConfirm = false
                onLogout()
            },
            onDismiss = { showLogoutConfirm = false }
        )
    }
}

/**
 * Header profil ala media sosial di tengah layar, tanpa kartu pembungkus: avatar 104dp dengan
 * cincin gradien, lencana kamera, nama, chip peran, email, dan chip status sinkron foto.
 * Seluruh avatar = satu target ketuk (ganti foto).
 */
@Composable
private fun DriverProfileHeader(
    name: String?,
    role: String,
    email: String?,
    photo: ImageBitmap?,
    photoBusy: Boolean,
    photoBusyLabelRes: Int?,
    syncState: PhotoSyncState,
    onRetrySync: () -> Unit,
    onChangePhoto: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        EditableProfileAvatar(
            name = name,
            photo = photo,
            busy = photoBusy,
            busyLabelRes = photoBusyLabelRes,
            onClick = onChangePhoto
        )
        // Teks digabung jadi satu node bacaan; avatar sengaja DI LUAR gabungan karena ia
        // tombol tersendiri (ganti foto) dan harus tetap bisa difokus TalkBack.
        Column(
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
        ) {
            Text(
                text = name ?: role,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = role,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
            if (!email.isNullOrBlank()) {
                Text(
                    text = email,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        PhotoSyncChip(syncState = syncState, onRetry = onRetrySync)
    }
}

/** Chip status sinkron foto; disembunyikan bila tidak ada foto. "Belum tersinkron" bisa diketuk. */
@Composable
private fun PhotoSyncChip(syncState: PhotoSyncState, onRetry: () -> Unit) {
    if (syncState == PhotoSyncState.None) return
    val unsynced = syncState == PhotoSyncState.Unsynced
    val label = stringResource(
        when (syncState) {
            PhotoSyncState.Synced -> R.string.driver_profile_sync_synced
            PhotoSyncState.Syncing -> R.string.driver_profile_sync_syncing
            else -> R.string.driver_profile_sync_unsynced
        }
    )
    val retry = stringResource(R.string.driver_profile_sync_retry)
    val container = if (unsynced) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
    val content = if (unsynced) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .heightIn(min = if (unsynced) 48.dp else 0.dp)
            .clip(RoundedCornerShape(50))
            .background(container)
            .then(if (unsynced) Modifier.dmClickable(onClick = onRetry, role = Role.Button) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = content)
        if (unsynced) {
            Text(
                text = retry,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = content
            )
        }
    }
}

/**
 * Avatar 104dp dengan cincin gradien 4dp (brand → ungu → cyan, tepi dalam warna latar) dan
 * lencana kamera 32dp kanan-bawah; seluruhnya SATU target ketuk. Selagi [busy], avatar ditutup
 * cincin progres tipis (bukan dialog) dan lencana disembunyikan.
 */
@Composable
private fun EditableProfileAvatar(
    name: String?,
    photo: ImageBitmap?,
    busy: Boolean,
    busyLabelRes: Int?,
    onClick: () -> Unit
) {
    val changeLabel = stringResource(R.string.driver_profile_photo_change_label)
    val background = MaterialTheme.colorScheme.background
    val ring = Brush.sweepGradient(listOf(MaterialTheme.colorScheme.primary, ProfileRingPurple, ProfileRingCyan, MaterialTheme.colorScheme.primary))
    Box(
        modifier = Modifier
            .size(112.dp)
            .testTag(DriverSettingsMaestroTags.PROFILE_AVATAR)
            .dmClickable(
                onClick = onClick,
                role = Role.Button,
                kind = DmPressKind.Meaningful,
                onClickLabel = changeLabel,
                enabled = !busy
            )
            .semantics { contentDescription = changeLabel }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(104.dp)
                .background(ring, CircleShape)
                .padding(4.dp)
                .background(background, CircleShape)
                .padding(3.dp)
        ) {
            ProfileAvatar(
                name = name,
                photo = photo,
                size = 90.dp,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                textStyle = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        if (busy) {
            val busyLabel = busyLabelRes?.let { stringResource(it) }
            CircularProgressIndicator(
                strokeWidth = 3.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(104.dp)
                    .semantics { busyLabel?.let { contentDescription = it } }
            )
        } else {
            Dsv2Badge3D(
                icon = Icons.Rounded.PhotoCamera,
                start = SettingsIconTints.language.start,
                end = SettingsIconTints.language.end,
                size = 32.dp,
                elevation = 2.dp,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}

private val ProfileRingPurple = Color(0xFF7C3AED)
private val ProfileRingCyan = Color(0xFF06B6D4)

@Composable
private fun SettingsGroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = AppSpacing.sm)
    )
}

private sealed interface SettingsTrailing {
    data object Chevron : SettingsTrailing
    data object None : SettingsTrailing
}

/**
 * Satu baris Settings: badge 3D + label + nilai opsional + trailing (chevron/switch).
 * Seluruh baris menerima klik; baris switch memakai [Modifier.toggleable] dengan Role.Switch
 * supaya klik di mana pun pada baris mengubah nilai dan TalkBack membacanya sebagai switch.
 */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    tint: SettingsIconTint,
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    valueLeading: (@Composable () -> Unit)? = null,
    trailing: SettingsTrailing = SettingsTrailing.Chevron,
    switchChecked: Boolean? = null,
    onClick: (() -> Unit)? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null
) {
    val interactionModifier = when {
        onCheckedChange != null -> Modifier.toggleable(
            value = switchChecked == true,
            role = Role.Switch,
            onValueChange = onCheckedChange
        )
        onClick != null -> Modifier
            .clickable(onClick = onClick)
            .semantics { role = Role.Button }
        else -> Modifier
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(interactionModifier)
            .padding(vertical = AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        Dsv2Badge3D(
            icon = icon,
            start = tint.start,
            end = tint.end,
            size = 36.dp,
            elevation = 3.dp
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        if (value != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                valueLeading?.invoke()
                if (valueLeading != null) Spacer(Modifier.width(AppSpacing.xxs))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        when {
            onCheckedChange != null -> Switch(
                checked = switchChecked == true,
                onCheckedChange = null,
                colors = settingsSwitchColors()
            )
            trailing == SettingsTrailing.Chevron -> Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> Unit
        }
    }
}

/** Switch OFF tetap jelas bisa disentuh: track terang dengan outline kontras, bukan warna pucat. */
@Composable
private fun settingsSwitchColors() = SwitchDefaults.colors(
    checkedTrackColor = AppPalette.primary,
    checkedThumbColor = MaterialTheme.colorScheme.surface,
    uncheckedTrackColor = MaterialTheme.colorScheme.surface,
    uncheckedBorderColor = AppPalette.outline,
    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
private fun SettingsRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 52.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

/** Bendera bulat 28dp untuk bahasa terpilih; "Ikuti perangkat" memakai badge ikon smartphone. */
@Composable
private fun LanguageValueLeading(tag: String?, modifier: Modifier = Modifier) {
    val flagRes = SettingsLanguageOptions.flagResFor(tag)
    if (flagRes != null) {
        Image(
            painter = painterResource(flagRes),
            contentDescription = null,
            modifier = modifier
                .size(28.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Dsv2Badge3D(
            icon = Icons.Rounded.Smartphone,
            start = SettingsIconTints.system.start,
            end = SettingsIconTints.system.end,
            modifier = modifier,
            size = 28.dp,
            elevation = 2.dp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePickerSheet(
    currentTag: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Text(
            text = stringResource(R.string.driver_settings_language_label),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)
        )
        listOf(
            null to stringResource(R.string.driver_settings_language_system),
            "id" to stringResource(R.string.driver_settings_language_id),
            "en" to stringResource(R.string.driver_settings_language_en),
            "ja" to stringResource(R.string.driver_settings_language_ja)
        ).forEach { (tag, label) ->
            val selected = SettingsLanguageOptions.normalizeTag(currentTag) == SettingsLanguageOptions.normalizeTag(tag)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onSelect(tag) }
                    )
                    .padding(horizontal = AppSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
            ) {
                if (tag == null) {
                    Dsv2Badge3D(
                        icon = Icons.Rounded.Smartphone,
                        start = SettingsIconTints.system.start,
                        end = SettingsIconTints.system.end,
                        size = 40.dp,
                        elevation = 3.dp
                    )
                } else {
                    LanguageValueLeading(tag)
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                RadioButton(selected = selected, onClick = null)
            }
        }
        Spacer(Modifier.height(AppSpacing.lg))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val pickerState = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.driver_settings_reminder_time_picker_title)) },
        text = { TimePicker(state = pickerState) },
        confirmButton = {
            TextButton(onClick = { onConfirm(pickerState.hour, pickerState.minute) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun languageDisplayLabel(tag: String?): String = when (tag) {
    "id", "in" -> stringResource(R.string.driver_settings_language_id)
    "en" -> stringResource(R.string.driver_settings_language_en)
    "ja" -> stringResource(R.string.driver_settings_language_ja)
    else -> stringResource(R.string.driver_settings_language_system)
}

/** Applies immediately (process-wide) in addition to persisting via [DriverSettingsViewModel]. */
private fun applyAppLanguage(tag: String?) {
    AppCompatDelegate.setApplicationLocales(
        if (tag == null) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
    )
}

private fun requestNotificationPermissionIfNeeded(
    context: Context,
    launcher: ActivityResultLauncher<String>
) {
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
