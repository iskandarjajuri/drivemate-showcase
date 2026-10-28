package com.drivemate.showcase.feature.expat

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.drivemate.showcase.R
import com.drivemate.showcase.feature.driver.notifications.formatNotificationBadge
import com.drivemate.showcase.feature.expat.settings.ExpatSettingsTags
import com.drivemate.showcase.ui.dsv2.DmIconButton

/**
 * Lonceng notifikasi di header layar Expat. Notifikasi bukan tab lagi: lonceng membuka rute
 * `expat_notifications` (bar disembunyikan di sana, ada tombol kembali). Badge "9+" di atas 9, hilang saat 0.
 */
@Composable
fun ExpatBellAction(
    unread: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Layar yang menaruh lonceng di slot topBar (subtree terpisah) meminta flag resource-id agar tag terbaca Maestro. */
    exposeResourceId: Boolean = false
) {
    val badge = formatNotificationBadge(unread)
    Box(
        if (exposeResourceId) modifier.semantics { testTagsAsResourceId = true } else modifier
    ) {
        DmIconButton(
            onClick = onClick,
            imageVector = Icons.Outlined.Notifications,
            contentDescription = if (badge != null) {
                stringResource(R.string.expat_home_header_bell_cd_count_format, unread)
            } else {
                stringResource(R.string.expat_home_header_bell_cd)
            },
            modifier = Modifier.testTag(ExpatSettingsTags.BELL)
        )
        badge?.let { Badge(modifier = Modifier.align(Alignment.TopEnd)) { Text(it) } }
    }
}
