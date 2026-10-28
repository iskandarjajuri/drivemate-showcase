package com.drivemate.showcase.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.drivemate.showcase.R
import com.drivemate.showcase.components.DMErrorState
import com.drivemate.showcase.components.DMLoadingState
import com.drivemate.showcase.components.DMPrimaryButton
import com.drivemate.showcase.components.DMTopAppBar
import com.drivemate.showcase.domain.account.ACCOUNT_DELETION_REASON_MAX_LENGTH
import com.drivemate.showcase.domain.account.AccountDeletionRequest
import com.drivemate.showcase.domain.account.AccountDeletionStatus
import com.drivemate.showcase.navigation.floatingNavPadding
import com.drivemate.showcase.ui.AppSpacing
import com.drivemate.showcase.ui.SectionTitle
import com.drivemate.showcase.ui.SoftCard
import com.drivemate.showcase.ui.patterns.DmConfirmDialog
import com.drivemate.showcase.util.WorkdayTimeFormatUtil

/**
 * Layar Akun — jalur penghapusan akun di dalam aplikasi (Blok 5, PRD §20).
 *
 * ## Kenapa teksnya berhati-hati, dan kenapa itu bukan sikap defensif
 * Apple 5.1.1(v) dan Play menuntut jalur ini ADA dan mudah ditemukan. Yang tidak boleh
 * dilakukan layar ini adalah **menjanjikan lebih dari yang dilakukan aplikasinya**. Di
 * DriveMate, akun dibuat perusahaan dan catatan absensi/hari kerja adalah bukti upah, jadi
 * tombol yang berbunyi "Hapus akun dan semua data saya" akan menjadi klaim yang tidak bisa
 * ditepati: sebagian catatan memang harus dipertahankan.
 *
 * Karena itu layar ini mengatakan apa adanya — permintaan dicatat, ditinjau manusia, dan
 * sebagian catatan mungkin dipertahankan karena kewajiban hukum. Kalimat itu juga yang akan
 * dibaca reviewer toko, jadi ia harus cocok dengan yang benar-benar terjadi.
 *
 * **Menunggu keputusan Iskandar:** apa yang terjadi setelah permintaan masuk (hapus keras,
 * anonimisasi, atau tolak dengan alasan retensi). Sampai itu ada, layar ini tidak boleh
 * menjanjikan hasil apa pun.
 */
@Composable
fun AccountSettingsScreen(
    vm: AccountSettingsViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val feedback by vm.feedback.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var confirmOpen by rememberSaveable { mutableStateOf(false) }
    var reasonInput by rememberSaveable { mutableStateOf("") }
    var logoutConfirmOpen by rememberSaveable { mutableStateOf(false) }

    val successMessage = feedback.successRes?.let { stringResource(it) }
    val errorMessage = feedback.errorRes?.let { stringResource(it) }

    // Dialog ditutup SEBELUM menunggu snackbar. `showSnackbar` menggantung sampai pesannya
    // hilang, jadi menutupnya sesudah membuat dialog tampak macet meski tulisannya sukses —
    // bug yang sama pernah terjadi di dialog jejak keputusan admin (Blok 4.2).
    androidx.compose.runtime.LaunchedEffect(successMessage) {
        val message = successMessage ?: return@LaunchedEffect
        confirmOpen = false
        reasonInput = ""
        vm.clearFeedback()
        snackbarHostState.showSnackbar(message)
    }
    androidx.compose.runtime.LaunchedEffect(errorMessage) {
        val message = errorMessage ?: return@LaunchedEffect
        confirmOpen = false
        vm.clearFeedback()
        snackbarHostState.showSnackbar(message)
    }

    Scaffold(
        topBar = {
            DMTopAppBar(
                title = stringResource(R.string.account_settings_title),
                onBack = onBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(AppSpacing.md)
                .verticalScroll(rememberScrollState())
                // Inset nav 0.dp di luar rute tab; padding di DALAM area gulir.
                .floatingNavPadding()
                .semantics { testTagsAsResourceId = true }
                .testTag("dm_account_settings_root"),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        ) {
            // SSOT §1 memindahkan keluar dari Admin Home ke DALAM layar Akun. Ia diletakkan
            // di atas jalur penghapusan akun dan TIDAK bergantung pada `state`: keluar harus
            // tetap bisa dilakukan meski pembacaan permintaan penghapusan sedang memuat atau
            // gagal. Kalau ia ikut masuk cabang `when` di bawah, kegagalan baca akan mengunci
            // satu-satunya jalan keluar pengguna dari aplikasi.
            AccountSignOutCard(onSignOut = { logoutConfirmOpen = true })

            when {
                state.loading -> DMLoadingState()

                state.errorMessageRes != null -> DMErrorState(
                    message = stringResource(state.errorMessageRes!!),
                    onAction = { vm.load() },
                )

                else -> {
                    val existing = state.request
                    if (existing != null && existing.isOpen) {
                        PendingRequestCard(existing)
                    } else {
                        if (existing != null) HandledRequestCard(existing)
                        DeletionRequestCard(
                            submitting = state.submitting,
                            onRequest = { confirmOpen = true },
                        )
                    }
                }
            }
        }
    }

    if (logoutConfirmOpen) {
        DmConfirmDialog(
            title = stringResource(R.string.dm_logout_title),
            message = stringResource(R.string.dm_logout_message),
            confirmLabel = stringResource(R.string.dm_logout_confirm),
            cancelLabel = stringResource(R.string.dm_logout_cancel),
            destructive = true,
            onConfirm = {
                logoutConfirmOpen = false
                onLogout()
            },
            onDismiss = { logoutConfirmOpen = false },
        )
    }

    if (confirmOpen) {
        AlertDialog(
            onDismissRequest = { if (!state.submitting) confirmOpen = false },
            title = { Text(stringResource(R.string.account_deletion_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    Text(stringResource(R.string.account_deletion_confirm_message))
                    OutlinedTextField(
                        value = reasonInput,
                        onValueChange = {
                            if (it.length <= ACCOUNT_DELETION_REASON_MAX_LENGTH) reasonInput = it
                        },
                        label = { Text(stringResource(R.string.account_deletion_reason_label)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dm_account_deletion_reason"),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.submit(reasonInput) },
                    enabled = !state.submitting,
                    modifier = Modifier.testTag("dm_account_deletion_confirm"),
                ) { Text(stringResource(R.string.account_deletion_confirm_action)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmOpen = false },
                    enabled = !state.submitting,
                ) { Text(stringResource(R.string.dm_logout_cancel)) }
            },
        )
    }
}

@Composable
private fun DeletionRequestCard(
    submitting: Boolean,
    onRequest: () -> Unit,
) {
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.account_deletion_title))
        Text(
            text = stringResource(R.string.account_deletion_body),
            style = MaterialTheme.typography.bodyMedium,
        )
        // Kalimat retensi berdiri sendiri, tidak dilebur ke paragraf di atas: inilah bagian
        // yang membedakan "akun dihapus" dari "permintaan dicatat", dan ia harus terbaca
        // sebelum tombolnya ditekan, bukan sesudah.
        Text(
            text = stringResource(R.string.account_deletion_retention_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DMPrimaryButton(
            text = stringResource(R.string.account_deletion_action),
            onClick = onRequest,
            enabled = !submitting,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("dm_account_deletion_request"),
        )
    }
}

@Composable
private fun PendingRequestCard(request: AccountDeletionRequest) {
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.account_deletion_status_title))
        Text(
            text = stringResource(
                R.string.account_deletion_status_pending,
                WorkdayTimeFormatUtil.formatDateTimeOrNull(request.requestedAtMillis)
                    ?: stringResource(R.string.admin_placeholder_dash),
            ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("dm_account_deletion_pending"),
        )
        Text(
            text = stringResource(R.string.account_deletion_retention_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HandledRequestCard(request: AccountDeletionRequest) {
    val statusRes = when (request.status) {
        AccountDeletionStatus.COMPLETED -> R.string.account_deletion_status_completed
        AccountDeletionStatus.REJECTED -> R.string.account_deletion_status_rejected
        AccountDeletionStatus.PENDING -> R.string.account_deletion_status_pending
    }
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.account_deletion_status_title))
        Text(
            text = stringResource(
                statusRes,
                WorkdayTimeFormatUtil.formatDateTimeOrNull(request.handledAtMillis ?: request.requestedAtMillis)
                    ?: stringResource(R.string.admin_placeholder_dash),
            ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("dm_account_deletion_handled"),
        )
        request.handledNote?.let { note ->
            Text(text = note, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * Kartu keluar. Dipindah ke sini dari Admin Home (SSOT §1).
 *
 * **Kenapa pemindahannya harus satu perubahan, bukan dua:** sebelum ini layar Akun tidak punya
 * tombol keluar sama sekali. Menghapusnya dari Home lebih dulu, lalu menambahkannya di sini
 * belakangan, akan meninggalkan admin **tanpa satu pun jalan keluar** dari aplikasi di antara
 * kedua perubahan itu.
 */
@Composable
private fun AccountSignOutCard(onSignOut: () -> Unit) {
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        ) {
            // Tanpa SectionTitle: judul yang tersedia (`dm_logout_title`) berbunyi
            // "Sign out of account?" — sah sebagai judul dialog, tapi tanda tanya itu salah
            // sebagai judul seksi, dan judul "Keluar" di atas tombol "Keluar" cuma mengulang.
            // Keterangan + tombol sudah cukup jelas.
            Text(
                text = stringResource(R.string.account_sign_out_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DMPrimaryButton(
                text = stringResource(R.string.dm_logout_confirm),
                onClick = onSignOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dm_account_sign_out"),
            )
        }
    }
}
