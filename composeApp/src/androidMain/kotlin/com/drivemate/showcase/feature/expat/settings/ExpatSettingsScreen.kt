package com.drivemate.showcase.feature.expat.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivemate.showcase.R
import com.drivemate.showcase.feature.expat.ExpatBellAction
import com.drivemate.showcase.feature.expat.ja.ExpatJaTypography
import com.drivemate.showcase.feature.settings.SettingsIconTints
import com.drivemate.showcase.feature.settings.SettingsLanguageOptions
import com.drivemate.showcase.navigation.floatingNavContentPadding
import com.drivemate.showcase.ui.AppSpacing
import com.drivemate.showcase.ui.SoftCard
import com.drivemate.showcase.ui.dsv2.DmPressKind
import com.drivemate.showcase.ui.dsv2.DmScreenHeader
import com.drivemate.showcase.ui.dsv2.Dsv2Badge3D
import com.drivemate.showcase.ui.dsv2.Dsv2Spacing
import com.drivemate.showcase.ui.dsv2.dmClickable
import com.drivemate.showcase.ui.patterns.DmConfirmDialog

/**
 * Tab Pengaturan Expat. Hanya kontrol yang punya konsumen nyata: bahasa (per-aplikasi), pintasan ke pengaturan
 * notifikasi sistem, Hubungi admin (Pesan), versi, Keluar (dengan konfirmasi), dan zona berisiko (layar Akun yang
 * sudah ada). Tidak ada toggle palsu; ukuran teks hanya informasi karena mengikuti sistem.
 */
@Composable
fun ExpatSettingsScreen(
    vm: ExpatSettingsViewModel,
    notificationUnread: Int,
    onOpenNotifications: () -> Unit,
    onOpenProfile: () -> Unit,
    onContactAdmin: () -> Unit,
    onOpenAccountRisk: () -> Unit,
    onLogout: () -> Unit
) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showLanguageSheet by rememberSaveable { mutableStateOf(false) }
    var showLogoutConfirm by rememberSaveable { mutableStateOf(false) }
    val roleLabel = stringResource(R.string.chat_role_expat)
    val displayName = profile?.name ?: roleLabel
    val versionName = remember(context) { expatAppVersionName(context) }
    val languageTag = currentExpatAppLanguageTag()

    ExpatJaTypography {
        Scaffold(
            // Flag harus di Scaffold: lonceng ada di slot topBar, subtree terpisah dari konten.
            modifier = Modifier.semantics { testTagsAsResourceId = true },
            containerColor = Color.Transparent,
            topBar = {
                DmScreenHeader(
                    title = stringResource(R.string.expat_settings_title),
                    actions = { ExpatBellAction(unread = notificationUnread, onClick = onOpenNotifications) }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .testTag(ExpatSettingsTags.SETTINGS_ROOT),
                contentPadding = floatingNavContentPadding(PaddingValues(Dsv2Spacing.md)),
                verticalArrangement = Arrangement.spacedBy(Dsv2Spacing.md)
            ) {
                item(key = "profile") {
                    SoftCard(modifier = Modifier.fillMaxWidth()) {
                        val openProfileCd = stringResource(R.string.expat_settings_profile_open_cd)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .dmClickable(
                                    onClick = onOpenProfile,
                                    role = Role.Button,
                                    kind = DmPressKind.Meaningful,
                                    onClickLabel = openProfileCd
                                )
                                .testTag(ExpatSettingsTags.PROFILE_CARD),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
                        ) {
                            ExpatInitialsAvatar(name = profile?.name, size = 72.dp)
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)
                            ) {
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                ExpatRolePill(text = roleLabel)
                                profile?.email?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "display") {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                        ExpatSettingsGroupLabel(stringResource(R.string.expat_settings_group_display))
                        SoftCard(modifier = Modifier.fillMaxWidth()) {
                            ExpatSettingsRow(
                                icon = Icons.Rounded.Language,
                                tint = SettingsIconTints.language,
                                // Nama bahasa netral lintas peran: memakai ulang string bahasa milik Pengaturan Driver.
                                label = stringResource(R.string.driver_settings_language_label),
                                value = languageDisplayLabel(languageTag),
                                valueLeading = { ExpatLanguageLeading(languageTag) },
                                onClick = { showLanguageSheet = true },
                                modifier = Modifier.testTag(ExpatSettingsTags.LANGUAGE_ROW)
                            )
                            ExpatSettingsRowDivider()
                            ExpatSettingsRow(
                                icon = Icons.Rounded.TextFields,
                                tint = SettingsIconTints.time,
                                label = stringResource(R.string.expat_settings_text_size_label),
                                value = stringResource(R.string.expat_settings_text_size_value)
                            )
                        }
                    }
                }

                item(key = "notifications") {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                        ExpatSettingsGroupLabel(stringResource(R.string.expat_settings_group_notifications))
                        SoftCard(modifier = Modifier.fillMaxWidth()) {
                            ExpatSettingsRow(
                                icon = Icons.Rounded.Notifications,
                                tint = SettingsIconTints.reminder,
                                label = stringResource(R.string.expat_settings_system_notifications),
                                onClick = {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                },
                                modifier = Modifier.testTag(ExpatSettingsTags.NOTIFICATION_ROW)
                            )
                        }
                    }
                }

                item(key = "about") {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                        ExpatSettingsGroupLabel(stringResource(R.string.expat_settings_group_about))
                        SoftCard(modifier = Modifier.fillMaxWidth()) {
                            ExpatSettingsRow(
                                icon = Icons.Rounded.SupportAgent,
                                tint = SettingsIconTints.help,
                                label = stringResource(R.string.expat_action_contact_admin),
                                onClick = onContactAdmin,
                                modifier = Modifier.testTag(ExpatSettingsTags.CONTACT_ROW)
                            )
                            ExpatSettingsRowDivider()
                            ExpatSettingsRow(
                                icon = Icons.Rounded.Info,
                                tint = SettingsIconTints.system,
                                label = stringResource(R.string.expat_settings_version_label),
                                value = versionName.ifBlank { stringResource(R.string.expat_placeholder_dash) },
                                modifier = Modifier.testTag(ExpatSettingsTags.VERSION_ROW)
                            )
                        }
                    }
                }

                item(key = "account") {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                        ExpatSettingsGroupLabel(stringResource(R.string.expat_settings_group_account))
                        SoftCard(modifier = Modifier.fillMaxWidth()) {
                            ExpatSettingsRow(
                                icon = Icons.AutoMirrored.Rounded.Logout,
                                tint = SettingsIconTints.system,
                                label = stringResource(R.string.expat_settings_logout),
                                onClick = { showLogoutConfirm = true },
                                modifier = Modifier.testTag(ExpatSettingsTags.LOGOUT_ROW)
                            )
                        }
                    }
                }

                item(key = "risk") {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                        ExpatSettingsGroupLabel(stringResource(R.string.expat_settings_group_risk))
                        SoftCard(modifier = Modifier.fillMaxWidth()) {
                            ExpatSettingsRow(
                                icon = Icons.Rounded.DeleteForever,
                                tint = SettingsIconTints.logout,
                                label = stringResource(R.string.expat_settings_delete_account),
                                onClick = onOpenAccountRisk,
                                modifier = Modifier.testTag(ExpatSettingsTags.RISK_ROW)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showLanguageSheet) {
        ExpatLanguageSheet(
            currentTag = languageTag,
            onSelect = { tag ->
                showLanguageSheet = false
                applyExpatAppLanguage(tag)
            },
            onDismiss = { showLanguageSheet = false }
        )
    }

    if (showLogoutConfirm) {
        // Gaya netral (bukan destruktif): keluar bisa dibatalkan dengan masuk lagi.
        DmConfirmDialog(
            title = stringResource(R.string.expat_settings_logout_confirm_title),
            message = stringResource(R.string.expat_settings_logout_confirm_message),
            confirmLabel = stringResource(R.string.expat_settings_logout),
            cancelLabel = stringResource(R.string.action_cancel),
            destructive = false,
            onConfirm = {
                showLogoutConfirm = false
                onLogout()
            },
            onDismiss = { showLogoutConfirm = false }
        )
    }
}

@Composable
private fun languageDisplayLabel(tag: String?): String = when (tag) {
    "id" -> stringResource(R.string.driver_settings_language_id)
    "en" -> stringResource(R.string.driver_settings_language_en)
    "ja" -> stringResource(R.string.driver_settings_language_ja)
    else -> stringResource(R.string.driver_settings_language_system)
}

/** Bendera bulat untuk bahasa terpilih; "ikuti sistem" memakai badge ikon telepon. */
@Composable
private fun ExpatLanguageLeading(tag: String?, size: androidx.compose.ui.unit.Dp = 28.dp) {
    val flagRes = SettingsLanguageOptions.flagResFor(tag)
    if (flagRes != null) {
        Image(
            painter = painterResource(flagRes),
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Dsv2Badge3D(
            icon = Icons.Rounded.Smartphone,
            start = SettingsIconTints.system.start,
            end = SettingsIconTints.system.end,
            size = size,
            elevation = 2.dp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpatLanguageSheet(
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
            val selected = currentTag == tag
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(tag) })
                    .padding(horizontal = AppSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
            ) {
                ExpatLanguageLeading(tag, size = 40.dp)
                Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                RadioButton(selected = selected, onClick = null)
            }
        }
        Spacer(Modifier.height(AppSpacing.lg))
    }
}
