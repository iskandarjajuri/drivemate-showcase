package com.drivemate.showcase

import com.drivemate.showcase.core.model.Role
import com.drivemate.showcase.core.model.User
import com.drivemate.showcase.core.model.UserSession
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.error.UserPhotoError
import com.drivemate.showcase.domain.profile.PROFILE_PHOTO_MAX_BYTES
import com.drivemate.showcase.domain.profile.validateProfilePhotoSize
import com.drivemate.showcase.domain.repository.AuthRepository
import com.drivemate.showcase.domain.repository.UserPhotoRepository
import com.drivemate.showcase.domain.usecase.profile.DeleteProfilePhotoUseCase
import com.drivemate.showcase.domain.usecase.profile.GetProfilePhotoUseCase
import com.drivemate.showcase.domain.usecase.profile.SaveProfilePhotoUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * PR-E — foto profil driver.
 *
 * Yang dijaga: (1) uid SELALU berasal dari sesi, tidak pernah dari pemanggil;
 * (2) ukuran tidak sah ditolak SEBELUM menyentuh repository (jaringan);
 * (3) batas 150000 byte tepat di tepinya — angka yang sama ditegakkan `firestore.rules`.
 */
class ProfilePhotoUseCaseTest {

    private class FakeAuth(private val user: User?) : AuthRepository {
        override val userSessionFlow: Flow<UserSession?> = flowOf(null)
        override suspend fun login(email: String, password: String): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun logout(): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun sendReset(email: String): AppResult<Unit> = AppResult.Success(Unit)
        override fun authState(): Flow<User?> = flowOf(user)
        override suspend fun currentUser(): AppResult<User?> = AppResult.Success(user)
    }

    private class FakePhotos : UserPhotoRepository {
        val calls = mutableListOf<String>()
        var stored: ByteArray? = null
        override suspend fun get(uid: String): AppResult<ByteArray?> {
            calls += "get:$uid"
            return AppResult.Success(stored)
        }

        override suspend fun save(uid: String, jpeg: ByteArray, nowMillis: Long): AppResult<Unit> {
            calls += "save:$uid:${jpeg.size}:$nowMillis"
            stored = jpeg
            return AppResult.Success(Unit)
        }

        override suspend fun delete(uid: String): AppResult<Unit> {
            calls += "delete:$uid"
            stored = null
            return AppResult.Success(Unit)
        }
    }

    private val driver = User(uid = "driver-1", email = "d@example.com", role = Role.DRIVER)

    // ===== validateProfilePhotoSize =====

    @Test
    fun validate_tepiBatas() {
        assertEquals(UserPhotoError.Empty, validateProfilePhotoSize(0))
        assertEquals(UserPhotoError.Empty, validateProfilePhotoSize(-1))
        assertNull(validateProfilePhotoSize(1))
        assertNull(validateProfilePhotoSize(PROFILE_PHOTO_MAX_BYTES))
        assertEquals(UserPhotoError.TooLarge, validateProfilePhotoSize(PROFILE_PHOTO_MAX_BYTES + 1))
    }

    @Test
    fun batas_samaDenganRules() {
        // firestore.rules: jpeg.size() <= 150000. Mengubah angka ini WAJIB mengubah rules + test jest.
        assertEquals(150_000, PROFILE_PHOTO_MAX_BYTES)
    }

    // ===== Simpan =====

    @Test
    fun simpan_memakaiUidDariSesi() = runBlocking {
        val repo = FakePhotos()
        val result = SaveProfilePhotoUseCase(FakeAuth(driver), repo)(ByteArray(1000), nowMillis = 42L)
        assertIs<AppResult.Success<Unit>>(result)
        assertEquals(listOf("save:driver-1:1000:42"), repo.calls)
    }

    @Test
    fun simpan_terlaluBesar_ditolakTanpaMenyentuhRepository() = runBlocking {
        val repo = FakePhotos()
        val result = SaveProfilePhotoUseCase(FakeAuth(driver), repo)(
            ByteArray(PROFILE_PHOTO_MAX_BYTES + 1), nowMillis = 1L)
        assertIs<AppResult.Failure>(result)
        assertSame(UserPhotoError.TooLarge, result.throwable)
        assertTrue(repo.calls.isEmpty())
    }

    @Test
    fun simpan_kosong_ditolak() = runBlocking {
        val repo = FakePhotos()
        val result = SaveProfilePhotoUseCase(FakeAuth(driver), repo)(ByteArray(0), nowMillis = 1L)
        assertIs<AppResult.Failure>(result)
        assertSame(UserPhotoError.Empty, result.throwable)
        assertTrue(repo.calls.isEmpty())
    }

    @Test
    fun simpan_tanpaSesi_ditolak() = runBlocking {
        val repo = FakePhotos()
        val result = SaveProfilePhotoUseCase(FakeAuth(null), repo)(ByteArray(10), nowMillis = 1L)
        assertIs<AppResult.Failure>(result)
        assertSame(UserPhotoError.NotSignedIn, result.throwable)
        assertTrue(repo.calls.isEmpty())
    }

    // ===== Baca / hapus =====

    @Test
    fun baca_mengembalikanFotoAtauNull() = runBlocking {
        val repo = FakePhotos()
        val get = GetProfilePhotoUseCase(FakeAuth(driver), repo)
        assertNull((get() as AppResult.Success).data)

        repo.stored = byteArrayOf(1, 2, 3)
        assertContentEquals(byteArrayOf(1, 2, 3), (get() as AppResult.Success).data)
        assertEquals(listOf("get:driver-1", "get:driver-1"), repo.calls)
    }

    @Test
    fun hapus_memakaiUidDariSesi_dantanpaSesiDitolak() = runBlocking {
        val repo = FakePhotos()
        assertIs<AppResult.Success<Unit>>(DeleteProfilePhotoUseCase(FakeAuth(driver), repo)())
        assertEquals(listOf("delete:driver-1"), repo.calls)

        val none = FakePhotos()
        val denied = DeleteProfilePhotoUseCase(FakeAuth(null), none)()
        assertIs<AppResult.Failure>(denied)
        assertSame(UserPhotoError.NotSignedIn, denied.throwable)
        assertTrue(none.calls.isEmpty())
    }
}
