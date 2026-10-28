package com.drivemate.showcase.feature.settings.photo

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.error.UserPhotoError
import com.drivemate.showcase.domain.repository.AuthRepository
import com.drivemate.showcase.domain.usecase.profile.DeleteProfilePhotoUseCase
import com.drivemate.showcase.domain.usecase.profile.GetProfilePhotoUseCase
import com.drivemate.showcase.domain.usecase.profile.SaveProfilePhotoUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Alur ganti foto profil driver: pilih -> olah -> pratinjau -> simpan LOKAL (tampil segera) ->
 * unggah ke Firestore sebagai upaya terbaik (atau hapus).
 *
 * Dipakai Settings (alur penuh) dan Home (hanya [photoJpeg]). Sumber tampilan = [ProfilePhotoStore]
 * (memori, bersama); sumber persisten = [ProfilePhotoLocalStore] (filesDir). Foto yang belum
 * terunggah ditandai pending dan dicoba lagi tiap ViewModel dibuat (Settings/Home dibuka) atau
 * lewat [retrySync]. Hanya transformasi state — tidak ada logika UI, tidak ada query Firestore
 * langsung (semuanya lewat use case).
 */
class ProfilePhotoViewModel(
    private val authRepository: AuthRepository,
    private val getPhoto: GetProfilePhotoUseCase,
    private val savePhoto: SaveProfilePhotoUseCase,
    private val deletePhoto: DeleteProfilePhotoUseCase,
    private val store: ProfilePhotoStore,
    private val localStore: ProfilePhotoLocalStore,
    private val processUri: (Uri) -> ProfilePhotoProcessor.Result,
    private val workDispatcher: CoroutineDispatcher,
    private val nowMillis: () -> Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfilePhotoUiState>(ProfilePhotoUiState.Idle)
    val uiState: StateFlow<ProfilePhotoUiState> = _uiState.asStateFlow()

    private val currentUid = MutableStateFlow<String?>(null)

    private val _syncState = MutableStateFlow(PhotoSyncState.None)

    /** Status sinkron foto (chip di Settings). [PhotoSyncState.None] = tidak ada foto. */
    val syncState: StateFlow<PhotoSyncState> = _syncState.asStateFlow()

    private val _syncError = MutableStateFlow<ProfilePhotoErrorCode?>(null)

    /** Penyebab unggah terakhir yang gagal; null bila tidak ada. */
    val syncError: StateFlow<ProfilePhotoErrorCode?> = _syncError.asStateFlow()

    private var syncJob: Job? = null

    /** JPEG foto akun yang sedang login; `null` = belum ada atau belum dimuat (tampilkan inisial). */
    val photoJpeg: StateFlow<ByteArray?> = combine(store.entry, currentUid) { entry, uid ->
        if (entry != null && uid != null && entry.uid == uid) entry.jpeg else null
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            val uid = resolveUid() ?: return@launch
            currentUid.value = uid
            val local = withContext(workDispatcher) { localStore.read(uid) }
            val pending = local != null && withContext(workDispatcher) { localStore.isPending(uid) }
            if (local != null && pending) {
                store.put(uid, local)
                syncPending(uid, local)
                return@launch
            }
            if (store.isLoaded(uid)) {
                _syncState.value = if (store.entry.value?.jpeg != null) PhotoSyncState.Synced else PhotoSyncState.None
                return@launch
            }
            if (local != null) {
                store.put(uid, local)
                _syncState.value = PhotoSyncState.Synced
            }
            when (val res = getPhoto()) {
                is AppResult.Success -> {
                    val remote = res.data
                    store.put(uid, remote)
                    withContext(workDispatcher) {
                        if (remote != null) localStore.write(uid, remote, pending = false) else localStore.delete(uid)
                    }
                    _syncState.value = if (remote != null) PhotoSyncState.Synced else PhotoSyncState.None
                }
                // Gagal memuat bukan galat layar: tampilan jatuh ke salinan lokal / inisial.
                // Dicoba lagi saat layar berikutnya membuat ViewModel baru.
                is AppResult.Failure -> DMLog.w(TAG, "foto profil belum bisa dimuat: ${res.throwable.javaClass.simpleName}")
            }
        }
    }

    /** Hasil pemilih galeri/kamera. `null` = dibatalkan; bukan error, kembali diam. */
    fun onImagePicked(uri: Uri?) {
        if (uri == null || _uiState.value.isBusy()) return
        _uiState.value = ProfilePhotoUiState.Processing
        viewModelScope.launch {
            val result = withContext(workDispatcher) { processUri(uri) }
            applyProcessResult(result)
        }
    }

    /** Terpisah dari [onImagePicked] agar transisi state bisa diuji tanpa Bitmap/Uri. */
    internal fun applyProcessResult(result: ProfilePhotoProcessor.Result) {
        _uiState.value = when (result) {
            is ProfilePhotoProcessor.Result.Success -> ProfilePhotoUiState.Preview(result.jpeg)
            is ProfilePhotoProcessor.Result.Failure -> ProfilePhotoUiState.Error(result.code)
        }
    }

    /**
     * Pengguna mengonfirmasi pratinjau: simpan LOKAL dan tampilkan segera, lalu unggah di latar.
     * Gagal unggah TIDAK membuang foto; statusnya menjadi [PhotoSyncState.Unsynced].
     */
    fun confirmUpload() {
        val preview = _uiState.value as? ProfilePhotoUiState.Preview ?: return
        _uiState.value = ProfilePhotoUiState.Uploading
        viewModelScope.launch {
            val uid = currentUid.value ?: resolveUid()
            if (uid == null) {
                _uiState.value = ProfilePhotoUiState.Error(ProfilePhotoErrorCode.UPLOAD_FAILED)
                return@launch
            }
            currentUid.value = uid
            val saved = try {
                withContext(workDispatcher) { localStore.write(uid, preview.jpeg, pending = true) }
                true
            } catch (t: Exception) {
                DMLog.e(TAG, "simpan lokal gagal: ${t.javaClass.simpleName}", t)
                false
            }
            if (!saved) {
                _uiState.value = ProfilePhotoUiState.Error(ProfilePhotoErrorCode.PROCESSING_FAILED)
                return@launch
            }
            store.put(uid, preview.jpeg)
            _uiState.value = ProfilePhotoUiState.Idle
            syncPending(uid, preview.jpeg)
        }
    }

    /** Coba lagi mengunggah foto yang belum tersinkron. */
    fun retrySync() {
        val uid = currentUid.value ?: return
        if (_syncState.value != PhotoSyncState.Unsynced) return
        viewModelScope.launch {
            val local = withContext(workDispatcher) { localStore.read(uid) } ?: return@launch
            syncPending(uid, local)
        }
    }

    private suspend fun syncPending(uid: String, jpeg: ByteArray) {
        syncJob?.cancel()
        val job = viewModelScope.launch {
            _syncState.value = PhotoSyncState.Syncing
            _syncError.value = null
            when (val res = savePhoto(jpeg, nowMillis())) {
                is AppResult.Success -> {
                    withContext(workDispatcher) { localStore.markSynced(uid) }
                    _syncState.value = PhotoSyncState.Synced
                }
                is AppResult.Failure -> {
                    val code = res.throwable.toErrorCode()
                    DMLog.w(TAG, "unggah foto gagal: ${res.throwable.javaClass.simpleName} -> $code")
                    _syncError.value = code
                    _syncState.value = PhotoSyncState.Unsynced
                }
            }
        }
        syncJob = job
    }

    fun deleteCurrentPhoto() {
        if (_uiState.value.isBusy()) return
        _uiState.value = ProfilePhotoUiState.Uploading
        syncJob?.cancel()
        viewModelScope.launch {
            when (val res = deletePhoto()) {
                is AppResult.Success -> {
                    currentUid.value?.let { uid ->
                        withContext(workDispatcher) { localStore.delete(uid) }
                        store.put(uid, null)
                    }
                    _syncState.value = PhotoSyncState.None
                    _syncError.value = null
                    _uiState.value = ProfilePhotoUiState.Idle
                }
                is AppResult.Failure -> _uiState.value = ProfilePhotoUiState.Error(res.throwable.toErrorCode())
            }
        }
    }

    /** Galat yang ditemukan UI sebelum pengolahan (mis. tidak ada aplikasi kamera). */
    fun reportError(code: ProfilePhotoErrorCode) {
        if (!_uiState.value.isBusy()) _uiState.value = ProfilePhotoUiState.Error(code)
    }

    /** Pratinjau dibatalkan atau galat ditutup. Tidak membatalkan unggahan yang sedang berjalan. */
    fun dismiss() {
        if (_uiState.value !is ProfilePhotoUiState.Uploading && _uiState.value !is ProfilePhotoUiState.Processing) {
            _uiState.value = ProfilePhotoUiState.Idle
        }
    }

    private suspend fun resolveUid(): String? = when (val res = authRepository.currentUser()) {
        is AppResult.Success -> res.data?.uid
        is AppResult.Failure -> null
    }

    private fun ProfilePhotoUiState.isBusy() =
        this is ProfilePhotoUiState.Processing || this is ProfilePhotoUiState.Uploading

    private companion object {
        const val TAG = "DM/ProfilePhotoVM"
    }
}

/** Memetakan galat domain ke kode UI. Galat tak dikenal = tahap unggah gagal ([ProfilePhotoErrorCode.UPLOAD_FAILED]). */
internal fun Throwable.toErrorCode(): ProfilePhotoErrorCode = when (this) {
    UserPhotoError.PermissionDenied -> ProfilePhotoErrorCode.NO_PERMISSION
    UserPhotoError.Network -> ProfilePhotoErrorCode.NETWORK
    UserPhotoError.TooLarge -> ProfilePhotoErrorCode.TOO_LARGE
    UserPhotoError.Empty -> ProfilePhotoErrorCode.NOT_AN_IMAGE
    else -> ProfilePhotoErrorCode.UPLOAD_FAILED
}
