package com.drivemate.showcase.feature.settings.photo

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivemate.showcase.R
import com.drivemate.showcase.feature.settings.DriverSettingsMaestroTags
import com.drivemate.showcase.feature.settings.SettingsIconTint
import com.drivemate.showcase.feature.settings.SettingsIconTints
import com.drivemate.showcase.ui.AppSpacing
import com.drivemate.showcase.ui.dsv2.Dsv2Badge3D
import com.drivemate.showcase.ui.rememberProfilePhotoBitmap
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CAMERA_CACHE_DIR = "profile_photos"

/**
 * Seluruh alur ganti foto: sheet pilihan -> pemilih sistem -> pratinjau bulat -> unggah.
 *
 * Tanpa izin runtime: galeri lewat Photo Picker (hanya foto yang dipilih yang terbaca),
 * kamera lewat aplikasi kamera bawaan ke URI FileProvider. Tidak ada izin kamera/media
 * di manifest dan tidak boleh ditambah.
 *
 * [showSheet]/[onShowSheetChange] dimiliki pemanggil (layar), bukan di sini, agar kartu
 * profil yang membukanya tetap satu sumber kebenaran.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilePhotoFlow(
    vm: ProfilePhotoViewModel,
    showSheet: Boolean,
    onShowSheetChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val photoJpeg by vm.photoJpeg.collectAsStateWithLifecycle()

    // URI foto kamera yang sedang dibuat; rememberSaveable agar selamat dari rotasi/proses
    // dimatikan selagi aplikasi kamera terbuka.
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    // Sheet ditutup lewat animasi hide() SEBELUM membuka pemilih — membuka activity lain di
    // frame yang sama dengan penutupan sheet membuat transisi tersendat (lag pengambilan).
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetScope = rememberCoroutineScope()

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> vm.onImagePicked(uri) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { taken ->
        val uri = cameraUri
        if (taken && uri != null) vm.onImagePicked(uri)
        else clearCameraCache(context.cacheDir) // dibatalkan: bukan error, bersihkan saja
    }

    // Berkas kamera sementara dihapus begitu hasil olahan siap atau gagal. SENGAJA bukan
    // "selain Processing": saat aplikasi kamera masih terbuka state-nya Idle, dan rotasi layar
    // di saat itu akan menjalankan ulang efek ini — menghapus berkas yang belum diisi kamera.
    LaunchedEffect(uiState) {
        if (cameraUri != null &&
            (uiState is ProfilePhotoUiState.Preview || uiState is ProfilePhotoUiState.Error)
        ) {
            clearCameraCache(context.cacheDir)
            cameraUri = null
        }
    }

    if (showSheet) {
        PhotoSourceSheet(
            sheetState = sheetState,
            hasPhoto = photoJpeg != null,
            onDismiss = { onShowSheetChange(false) },
            onTakePhoto = {
                // Pembuatan berkas (hapus sisa + createTempFile) adalah I/O: jangan di main.
                sheetScope.launch {
                    sheetState.hide()
                    onShowSheetChange(false)
                    val uri = withContext(kotlinx.coroutines.Dispatchers.IO) { createCameraUri(context) }
                    cameraUri = uri
                    try {
                        cameraLauncher.launch(uri)
                    } catch (e: ActivityNotFoundException) {
                        cameraUri = null
                        clearCameraCache(context.cacheDir)
                        vm.reportError(ProfilePhotoErrorCode.NO_CAMERA)
                    }
                }
            },
            onChooseFromGallery = {
                sheetScope.launch {
                    sheetState.hide()
                    onShowSheetChange(false)
                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            },
            onRemove = {
                sheetScope.launch {
                    sheetState.hide()
                    onShowSheetChange(false)
                    vm.deleteCurrentPhoto()
                }
            },
        )
    }

    when (val state = uiState) {
        ProfilePhotoUiState.Idle -> Unit
        // Processing/Uploading ditampilkan sebagai cincin progres tipis di avatar kartu
        // profil (lihat DriverSettingsScreen), bukan dialog pemblokir yang berkedip.
        ProfilePhotoUiState.Processing -> Unit
        ProfilePhotoUiState.Uploading -> Unit
        is ProfilePhotoUiState.Preview -> PhotoPreviewDialog(
            jpeg = state.jpeg,
            onUse = vm::confirmUpload,
            onRepick = {
                vm.dismiss()
                onShowSheetChange(true)
            },
            onDismiss = vm::dismiss,
        )
        is ProfilePhotoUiState.Error -> AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text(stringResource(R.string.driver_profile_photo_error_title)) },
            text = { Text(stringResource(state.code.messageRes)) },
            confirmButton = {
                TextButton(onClick = vm::dismiss) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoSourceSheet(
    sheetState: androidx.compose.material3.SheetState,
    hasPhoto: Boolean,
    onDismiss: () -> Unit,
    onTakePhoto: () -> Unit,
    onChooseFromGallery: () -> Unit,
    onRemove: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Text(
                text = stringResource(R.string.driver_profile_photo_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)
            )
            PhotoOptionRow(
                icon = Icons.Rounded.PhotoCamera,
                tint = SettingsIconTints.language,
                label = stringResource(R.string.driver_profile_photo_take),
                tag = DriverSettingsMaestroTags.PHOTO_OPTION_CAMERA,
                onClick = onTakePhoto,
            )
            PhotoOptionRow(
                icon = Icons.Rounded.PhotoLibrary,
                tint = SettingsIconTints.location,
                label = stringResource(R.string.driver_profile_photo_choose),
                tag = DriverSettingsMaestroTags.PHOTO_OPTION_GALLERY,
                onClick = onChooseFromGallery,
            )
            if (hasPhoto) {
                PhotoOptionRow(
                    icon = Icons.Rounded.Delete,
                    tint = SettingsIconTints.logout,
                    label = stringResource(R.string.driver_profile_photo_remove),
                    tag = DriverSettingsMaestroTags.PHOTO_OPTION_REMOVE,
                    onClick = onRemove,
                )
            }
            Text(
                text = stringResource(R.string.driver_profile_photo_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(AppSpacing.md)
            )
        }
    }
}

/** Satu baris pilihan; SELURUH baris adalah target ketuk, badge 3D dekoratif. */
@Composable
private fun PhotoOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: SettingsIconTint,
    label: String,
    tag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag(tag)
            .clickable(onClick = onClick)
            .semantics { role = Role.Button }
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        Dsv2Badge3D(icon = icon, start = tint.start, end = tint.end, size = 40.dp, elevation = 3.dp)
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PhotoPreviewDialog(
    jpeg: ByteArray,
    onUse: () -> Unit,
    onRepick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val bitmap = rememberProfilePhotoBitmap(jpeg)
    val previewLabel = stringResource(R.string.driver_profile_photo_preview_description)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.driver_profile_photo_preview_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = previewLabel,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(160.dp).clip(CircleShape)
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = previewLabel })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onUse,
                enabled = bitmap != null,
                modifier = Modifier.testTag(DriverSettingsMaestroTags.PHOTO_PREVIEW_CONFIRM)
            ) { Text(stringResource(R.string.driver_profile_photo_preview_use)) }
        },
        dismissButton = {
            TextButton(onClick = onRepick) { Text(stringResource(R.string.driver_profile_photo_preview_repick)) }
        },
    )
}

private fun createCameraUri(context: android.content.Context): Uri {
    val dir = File(context.cacheDir, CAMERA_CACHE_DIR).apply { mkdirs() }
    // Bersihkan sisa percobaan sebelumnya supaya tidak menumpuk di cache.
    dir.listFiles()?.forEach { it.delete() }
    val file = File.createTempFile("capture_", ".jpg", dir)
    return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
}

private fun clearCameraCache(cacheDir: File) {
    File(cacheDir, CAMERA_CACHE_DIR).listFiles()?.forEach { it.delete() }
}
