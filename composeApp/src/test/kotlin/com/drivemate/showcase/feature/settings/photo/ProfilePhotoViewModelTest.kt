package com.drivemate.showcase.feature.settings.photo

import com.drivemate.showcase.core.model.Role
import com.drivemate.showcase.core.model.User
import com.drivemate.showcase.core.model.UserSession
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.error.UserPhotoError
import com.drivemate.showcase.domain.repository.AuthRepository
import com.drivemate.showcase.domain.repository.UserPhotoRepository
import com.drivemate.showcase.domain.usecase.profile.DeleteProfilePhotoUseCase
import com.drivemate.showcase.domain.usecase.profile.GetProfilePhotoUseCase
import com.drivemate.showcase.domain.usecase.profile.SaveProfilePhotoUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private class PhotoFakeAuth(private val uid: String? = "driver-1") : AuthRepository {
    override val userSessionFlow: Flow<UserSession?> = emptyFlow()
    override suspend fun login(email: String, password: String): AppResult<Unit> =
        AppResult.failure(IllegalStateException("NotImplemented"))
    override suspend fun logout(): AppResult<Unit> = AppResult.failure(IllegalStateException("NotImplemented"))
    override suspend fun sendReset(email: String): AppResult<Unit> =
        AppResult.failure(IllegalStateException("NotImplemented"))
    override fun authState(): Flow<User?> = emptyFlow()
    override suspend fun currentUser(): AppResult<User?> =
        AppResult.success(uid?.let { User(uid = it, email = "d@example.com", role = Role.DRIVER) })
}

private class PhotoFakeRepo(
    var remote: ByteArray? = null,
    var saveError: Throwable? = null,
    var deleteError: Throwable? = null,
    var getError: Throwable? = null,
) : UserPhotoRepository {
    var getCalls = 0
    val saved = mutableListOf<ByteArray>()
    override suspend fun get(uid: String): AppResult<ByteArray?> {
        getCalls++
        return getError?.let { AppResult.failure(it) } ?: AppResult.success(remote)
    }
    override suspend fun save(uid: String, jpeg: ByteArray, nowMillis: Long): AppResult<Unit> {
        saveError?.let { return AppResult.failure(it) }
        saved += jpeg
        remote = jpeg
        return AppResult.success(Unit)
    }
    override suspend fun delete(uid: String): AppResult<Unit> {
        deleteError?.let { return AppResult.failure(it) }
        remote = null
        return AppResult.success(Unit)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProfilePhotoViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun vm(
        repo: PhotoFakeRepo = PhotoFakeRepo(),
        store: ProfilePhotoStore = ProfilePhotoStore(),
        auth: PhotoFakeAuth = PhotoFakeAuth(),
        local: ProfilePhotoLocalStore = newLocalStore(),
    ): ProfilePhotoViewModel {
        val getPhoto = GetProfilePhotoUseCase(auth, repo)
        val savePhoto = SaveProfilePhotoUseCase(auth, repo)
        val deletePhoto = DeleteProfilePhotoUseCase(auth, repo)
        return ProfilePhotoViewModel(
            authRepository = auth,
            getPhoto = getPhoto,
            savePhoto = savePhoto,
            deletePhoto = deletePhoto,
            store = store,
            localStore = local,
            processUri = { error("tidak dipakai di test ini") },
            workDispatcher = dispatcher,
            nowMillis = { 1_000L },
        )
    }

    private val jpeg = byteArrayOf(1, 2, 3, 4)

    private fun newLocalStore() = ProfilePhotoLocalStore(
        java.nio.file.Files.createTempDirectory("pp-local").toFile()
    )

    @Test
    fun init_memuatFotoSekaliDanMenampilkannya() {
        val repo = PhotoFakeRepo(remote = jpeg)
        val store = ProfilePhotoStore()
        val first = vm(repo, store)
        assertContentEquals(jpeg, first.photoJpeg.value)
        assertEquals(1, repo.getCalls)

        // ViewModel kedua (Home) memakai cache; tidak bertanya lagi ke repository.
        val second = vm(repo, store)
        assertContentEquals(jpeg, second.photoJpeg.value)
        assertEquals(1, repo.getCalls)
    }

    @Test
    fun init_tanpaFoto_nullTapiTetapDianggapSudahDimuat() {
        val repo = PhotoFakeRepo(remote = null)
        val store = ProfilePhotoStore()
        val v = vm(repo, store)
        assertNull(v.photoJpeg.value)
        assertTrue(store.isLoaded("driver-1"))
        vm(repo, store)
        assertEquals(1, repo.getCalls)
    }

    @Test
    fun init_gagalMemuat_tidakMenjadiGalatLayar() {
        val repo = PhotoFakeRepo(getError = UserPhotoError.Network)
        val v = vm(repo)
        assertSame(ProfilePhotoUiState.Idle, v.uiState.value)
        assertNull(v.photoJpeg.value)
    }

    @Test
    fun cache_milikAkunLain_tidakPernahDitampilkan() {
        val store = ProfilePhotoStore()
        store.put("akun-lain", byteArrayOf(9, 9, 9))
        val repo = PhotoFakeRepo(remote = jpeg)
        val v = vm(repo, store, PhotoFakeAuth("driver-1"))
        // Cache akun lain tidak dianggap "sudah dimuat" untuk driver-1 -> dimuat ulang.
        assertEquals(1, repo.getCalls)
        assertContentEquals(jpeg, v.photoJpeg.value)
    }

    @Test
    fun hasilOlahan_berhasilMenjadiPreview_gagalMenjadiError() {
        val v = vm()
        v.applyProcessResult(ProfilePhotoProcessor.Result.Success(jpeg))
        assertContentEquals(jpeg, assertIs<ProfilePhotoUiState.Preview>(v.uiState.value).jpeg)

        v.applyProcessResult(ProfilePhotoProcessor.Result.Failure(ProfilePhotoErrorCode.NOT_AN_IMAGE))
        assertEquals(ProfilePhotoUiState.Error(ProfilePhotoErrorCode.NOT_AN_IMAGE), v.uiState.value)
    }

    @Test
    fun konfirmasi_menyimpan_memperbaruiCache_dankembaliIdle() {
        val repo = PhotoFakeRepo()
        val store = ProfilePhotoStore()
        val v = vm(repo, store)
        v.applyProcessResult(ProfilePhotoProcessor.Result.Success(jpeg))
        v.confirmUpload()

        assertSame(ProfilePhotoUiState.Idle, v.uiState.value)
        assertEquals(1, repo.saved.size)
        assertContentEquals(jpeg, v.photoJpeg.value)
    }

    @Test
    fun konfirmasi_tanpaPreview_tidakMelakukanApaPun() {
        val repo = PhotoFakeRepo()
        val v = vm(repo)
        v.confirmUpload()
        assertSame(ProfilePhotoUiState.Idle, v.uiState.value)
        assertTrue(repo.saved.isEmpty())
    }

    @Test
    fun simpanDitolakRules_fotoTetapTampilLokal_statusBelumTersinkron() {
        val repo = PhotoFakeRepo(remote = jpeg, saveError = UserPhotoError.PermissionDenied)
        val local = newLocalStore()
        val v = vm(repo, local = local)
        val baru = byteArrayOf(7, 7)
        v.applyProcessResult(ProfilePhotoProcessor.Result.Success(baru))
        v.confirmUpload()

        assertSame(ProfilePhotoUiState.Idle, v.uiState.value)
        assertContentEquals(baru, v.photoJpeg.value)
        assertEquals(PhotoSyncState.Unsynced, v.syncState.value)
        assertEquals(ProfilePhotoErrorCode.NO_PERMISSION, v.syncError.value)
        assertContentEquals(baru, local.read("driver-1"))
        assertTrue(local.isPending("driver-1"))
    }

    @Test
    fun offlineLaluCobaLagi_menjadiTersinkron() {
        val repo = PhotoFakeRepo(saveError = UserPhotoError.Network)
        val local = newLocalStore()
        val v = vm(repo, local = local)
        v.applyProcessResult(ProfilePhotoProcessor.Result.Success(jpeg))
        v.confirmUpload()
        assertEquals(PhotoSyncState.Unsynced, v.syncState.value)
        assertEquals(ProfilePhotoErrorCode.NETWORK, v.syncError.value)

        repo.saveError = null
        v.retrySync()
        assertEquals(PhotoSyncState.Synced, v.syncState.value)
        assertEquals(1, repo.saved.size)
        assertTrue(!local.isPending("driver-1"))
    }

    @Test
    fun init_fotoPending_langsungTampilDanDicobaUnggahLagi() {
        val local = newLocalStore()
        local.write("driver-1", jpeg, pending = true)
        val repo = PhotoFakeRepo()
        val v = vm(repo, local = local)
        assertContentEquals(jpeg, v.photoJpeg.value)
        assertEquals(PhotoSyncState.Synced, v.syncState.value)
        assertEquals(1, repo.saved.size)
        assertEquals(0, repo.getCalls)
    }

    @Test
    fun init_salinanLokalTampilMeskiRemoteGagalDimuat() {
        val local = newLocalStore()
        local.write("driver-1", jpeg, pending = false)
        val v = vm(PhotoFakeRepo(getError = UserPhotoError.Network), local = local)
        assertContentEquals(jpeg, v.photoJpeg.value)
        assertEquals(PhotoSyncState.Synced, v.syncState.value)
    }

    @Test
    fun localStore_clearAll_membuangSemuaFotoAkun() {
        val local = newLocalStore()
        local.write("a", jpeg, pending = true)
        local.write("b", jpeg, pending = false)
        local.clearAll()
        assertNull(local.read("a"))
        assertNull(local.read("b"))
        assertTrue(!local.isPending("a"))
    }

    @Test
    fun galatDomain_dipetakanKeKodeUi() {
        assertEquals(ProfilePhotoErrorCode.NO_PERMISSION, UserPhotoError.PermissionDenied.toErrorCode())
        assertEquals(ProfilePhotoErrorCode.NETWORK, UserPhotoError.Network.toErrorCode())
        assertEquals(ProfilePhotoErrorCode.TOO_LARGE, UserPhotoError.TooLarge.toErrorCode())
        assertEquals(ProfilePhotoErrorCode.NOT_AN_IMAGE, UserPhotoError.Empty.toErrorCode())
        assertEquals(ProfilePhotoErrorCode.UPLOAD_FAILED, IllegalStateException("x").toErrorCode())
        assertEquals(ProfilePhotoErrorCode.UPLOAD_FAILED, UserPhotoError.Unknown().toErrorCode())
    }

    @Test
    fun hapus_menghapusFotoDariCache() {
        val repo = PhotoFakeRepo(remote = jpeg)
        val v = vm(repo)
        assertContentEquals(jpeg, v.photoJpeg.value)
        v.deleteCurrentPhoto()
        assertSame(ProfilePhotoUiState.Idle, v.uiState.value)
        assertNull(v.photoJpeg.value)
        assertNull(repo.remote)
    }

    @Test
    fun hapusGagal_fotoTetapAda_danGalatDitampilkan() {
        val repo = PhotoFakeRepo(remote = jpeg, deleteError = UserPhotoError.Network)
        val v = vm(repo)
        v.deleteCurrentPhoto()
        assertEquals(ProfilePhotoUiState.Error(ProfilePhotoErrorCode.NETWORK), v.uiState.value)
        assertContentEquals(jpeg, v.photoJpeg.value)
    }

    @Test
    fun dismiss_menutupPreviewDanGalat_danReportErrorMenampilkanKodeSendiri() {
        val v = vm()
        v.applyProcessResult(ProfilePhotoProcessor.Result.Success(jpeg))
        v.dismiss()
        assertSame(ProfilePhotoUiState.Idle, v.uiState.value)

        v.reportError(ProfilePhotoErrorCode.NO_CAMERA)
        assertEquals(ProfilePhotoUiState.Error(ProfilePhotoErrorCode.NO_CAMERA), v.uiState.value)
        v.dismiss()
        assertSame(ProfilePhotoUiState.Idle, v.uiState.value)
    }

    @Test
    fun tanpaSesi_tidakMemuatApaPun() {
        val repo = PhotoFakeRepo(remote = jpeg)
        val v = vm(repo, auth = PhotoFakeAuth(uid = null))
        assertEquals(0, repo.getCalls)
        assertNull(v.photoJpeg.value)
    }
}
