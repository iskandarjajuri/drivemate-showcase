package com.drivemate.showcase.feature.expat.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivemate.showcase.R
import com.drivemate.showcase.feature.expat.ja.ExpatJaTypography
import com.drivemate.showcase.ui.AppSpacing
import com.drivemate.showcase.ui.SoftCard
import com.drivemate.showcase.ui.dsv2.DmScreenHeader
import com.drivemate.showcase.ui.dsv2.Dsv2SecondaryButton
import com.drivemate.showcase.ui.dsv2.Dsv2Spacing

/**
 * Sub-layar Profil Expat. Semua data read-only dan bertanda "Dikelola admin"; tidak menampilkan uid, token, atau id
 * internal. Foto: hanya inisial, karena aturan `user_photos` tidak mengizinkan peran Expat menulis foto (D3 SSOT).
 *
 * @param managedDriverCount jumlah driver dari data Beranda yang sudah dimuat; `null` = belum bisa dipercaya
 *   (tampil sebagai strip, bukan "0" palsu).
 */
@Composable
fun ExpatProfileScreen(
    vm: ExpatSettingsViewModel,
    managedDriverCount: Int?,
    onBack: () -> Unit,
    onContactAdmin: () -> Unit
) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val roleLabel = stringResource(R.string.chat_role_expat)
    val dash = stringResource(R.string.expat_placeholder_dash)

    ExpatJaTypography {
        Scaffold(
            modifier = Modifier.semantics { testTagsAsResourceId = true },
            containerColor = Color.Transparent,
            topBar = { DmScreenHeader(title = stringResource(R.string.expat_profile_title), onBack = onBack) }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .testTag(ExpatSettingsTags.PROFILE_ROOT),
                contentPadding = PaddingValues(Dsv2Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Dsv2Spacing.md)
            ) {
                item(key = "header") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                    ) {
                        ExpatInitialsAvatar(name = profile?.name, size = 72.dp)
                        Text(
                            text = profile?.name ?: roleLabel,
                            style = MaterialTheme.typography.titleLarge
                        )
                        ExpatRolePill(text = roleLabel)
                    }
                }
                item(key = "fields") {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                        ExpatSettingsGroupLabel(stringResource(R.string.expat_profile_managed_by_admin))
                        SoftCard(modifier = Modifier.fillMaxWidth()) {
                            ExpatProfileField(stringResource(R.string.expat_profile_name_label), profile?.name ?: dash)
                            ExpatSettingsRowDivider()
                            ExpatProfileField(stringResource(R.string.expat_profile_email_label), profile?.email ?: dash)
                            ExpatSettingsRowDivider()
                            ExpatProfileField(stringResource(R.string.expat_profile_role_label), roleLabel)
                            ExpatSettingsRowDivider()
                            ExpatProfileField(
                                stringResource(R.string.expat_managed_drivers_title),
                                managedDriverCount?.toString() ?: dash
                            )
                        }
                    }
                }
                item(key = "hint") {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        Text(
                            text = stringResource(R.string.expat_profile_wrong_data_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Dsv2SecondaryButton(
                            text = stringResource(R.string.expat_action_contact_admin),
                            onClick = onContactAdmin,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(ExpatSettingsTags.PROFILE_CONTACT)
                        )
                    }
                }
            }
        }
    }
}

/** Satu baris data read-only: label kecil di atas, nilai di bawah (membungkus bila panjang, tidak dipotong). */
@Composable
private fun ExpatProfileField(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(vertical = AppSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}
