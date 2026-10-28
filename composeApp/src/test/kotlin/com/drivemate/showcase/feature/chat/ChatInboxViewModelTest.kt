package com.drivemate.showcase.feature.chat

import com.drivemate.showcase.R
import com.drivemate.showcase.core.model.ChatContact
import com.drivemate.showcase.core.model.ChatThreadSummary
import com.drivemate.showcase.core.model.Role
import com.drivemate.showcase.core.model.User
import com.drivemate.showcase.core.model.UserSession
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.AuthRepository
import com.drivemate.showcase.domain.repository.ChatDirectoryRepository
import com.drivemate.showcase.domain.repository.ChatThreadRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class InboxFakeAuth : AuthRepository {
    override val userSessionFlow: Flow<UserSession?> = emptyFlow()
    override suspend fun login(email: String, password: String): AppResult<Unit> = AppResult.success(Unit)
    override suspend fun logout(): AppResult<Unit> = AppResult.success(Unit)
    override suspend fun sendReset(email: String): AppResult<Unit> = AppResult.success(Unit)
    override fun authState(): Flow<User?> = emptyFlow()
    override suspend fun currentUser(): AppResult<User?> =
        AppResult.success(User(uid = "me", email = "m@example.com", role = Role.DRIVER))
}

/** Sumber yang dapat diprogram: menghitung kolektor aktif untuk menguji kebocoran listener saat retry. */
private class FakeThreads(var script: () -> Flow<AppResult<List<ChatThreadSummary>>>) : ChatThreadRepository {
    val active = AtomicInteger(0)
    val subscriptions = AtomicInteger(0)
    override fun observeThreads(): Flow<AppResult<List<ChatThreadSummary>>> = callbackFlow {
        active.incrementAndGet(); subscriptions.incrementAndGet()
        script().collect { trySend(it) }
        awaitClose { active.decrementAndGet() }
    }
    override suspend fun markRead(threadId: String, nowMillis: Long) = AppResult.success(Unit)
    override fun observePartnerReadAt(threadId: String, partnerUid: String): Flow<Long?> = emptyFlow()
    override fun observeHiddenIds(threadId: String): Flow<Set<String>> = emptyFlow()
    override suspend fun deleteForEveryone(threadId: String, messageId: String, nowMillis: Long) = AppResult.success(Unit)
    override suspend fun hideForMe(threadId: String, messageId: String) = AppResult.success(Unit)
}

private class FakeDirectory(var script: () -> Flow<AppResult<List<ChatContact>>>) : ChatDirectoryRepository {
    val active = AtomicInteger(0)
    override fun observeDirectory(): Flow<AppResult<List<ChatContact>>> = callbackFlow {
        active.incrementAndGet()
        script().collect { trySend(it) }
        awaitClose { active.decrementAndGet() }
    }
}

/** Flow yang tidak pernah emit (listener bisu karena galat ditelan). */
private fun <T> silent(): Flow<T> = callbackFlow { awaitClose { } }

@OptIn(ExperimentalCoroutinesApi::class)
class ChatInboxViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val thread = ChatThreadSummary("a__b", "b", "Halo", false, "b", 10L, 2)
    private val contact = ChatContact("b", "Budi", Role.DRIVER, null)
    private val denied = IllegalStateException("PERMISSION_DENIED")

    private fun vm(threads: FakeThreads, dir: FakeDirectory) =
        ChatInboxViewModel(threads, dir, InboxFakeAuth(), "Tidak dikenal", loadTimeoutMillis = 10_000L)

    private fun TestScope.settle() { runCurrent() }

    @Test
    fun keduaSumberSukses_daftarTampilDenganNamaDariDirektori() = kotlinx.coroutines.test.runTest(dispatcher) {
        val v = vm(FakeThreads { flowOf(AppResult.success(listOf(thread))) }, FakeDirectory { flowOf(AppResult.success(listOf(contact))) })
        settle()
        val s = v.uiState.value
        assertFalse(s.isLoading); assertNull(s.errorRes)
        assertEquals("Budi", s.threads.single().name)
        assertEquals("me", s.myUid)
        assertFalse(s.contactsLoading)
    }

    @Test
    fun direktoriBisu_threadTetapTampilSetelahThreadSukses_lalu_kontakGagalSetelahTimeout() = kotlinx.coroutines.test.runTest(dispatcher) {
        val v = vm(FakeThreads { flowOf(AppResult.success(listOf(thread))) }, FakeDirectory { silent() })
        settle()
        // Daftar thread TIDAK menunggu direktori; nama jatuh ke fallback.
        assertFalse(v.uiState.value.isLoading)
        assertEquals("Tidak dikenal", v.uiState.value.threads.single().name)
        assertTrue(v.uiState.value.contactsLoading)
        advanceTimeBy(10_001); settle()
        assertEquals(R.string.chat_contacts_error_load_failed, v.uiState.value.contactsErrorRes)
        assertFalse(v.uiState.value.contactsLoading)
        assertNull(v.uiState.value.errorRes)
    }

    @Test
    fun direktoriDitolak_threadTetapTampil() = kotlinx.coroutines.test.runTest(dispatcher) {
        val v = vm(FakeThreads { flowOf(AppResult.success(listOf(thread))) }, FakeDirectory { flowOf(AppResult.failure(denied)) })
        settle()
        assertEquals(1, v.uiState.value.threads.size)
        assertEquals(R.string.chat_contacts_error_load_failed, v.uiState.value.contactsErrorRes)
        assertNull(v.uiState.value.errorRes)
    }

    @Test
    fun threadKosong_adalahEmptyBukanLoading() = kotlinx.coroutines.test.runTest(dispatcher) {
        val v = vm(FakeThreads { flowOf(AppResult.success(emptyList())) }, FakeDirectory { flowOf(AppResult.success(emptyList())) })
        settle()
        assertFalse(v.uiState.value.isLoading); assertTrue(v.uiState.value.threads.isEmpty()); assertNull(v.uiState.value.errorRes)
    }

    @Test
    fun threadDitolak_galatBukanSpinner_lalu_retrySukses() = kotlinx.coroutines.test.runTest(dispatcher) {
        val threads = FakeThreads { flowOf(AppResult.failure(denied)) }
        val v = vm(threads, FakeDirectory { flowOf(AppResult.success(listOf(contact))) })
        settle()
        assertFalse(v.uiState.value.isLoading)
        assertEquals(R.string.chat_inbox_error_load_failed, v.uiState.value.errorRes)
        threads.script = { flowOf(AppResult.success(listOf(thread))) }
        v.refresh(); settle()
        assertNull(v.uiState.value.errorRes)
        assertEquals(1, v.uiState.value.threads.size)
    }

    @Test
    fun keduanyaBisu_loadingLaluGalatSetelahTimeout() = kotlinx.coroutines.test.runTest(dispatcher) {
        val v = vm(FakeThreads { silent() }, FakeDirectory { silent() })
        settle()
        assertTrue(v.uiState.value.isLoading)
        advanceTimeBy(9_999); settle()
        assertTrue(v.uiState.value.isLoading)
        advanceTimeBy(2); settle()
        assertFalse(v.uiState.value.isLoading)
        assertEquals(R.string.chat_inbox_error_load_failed, v.uiState.value.errorRes)
    }

    @Test
    fun sumberTerlambatPulihSetelahTimeout() = kotlinx.coroutines.test.runTest(dispatcher) {
        val late = kotlinx.coroutines.flow.flow {
            kotlinx.coroutines.delay(15_000)
            emit(AppResult.success(listOf(thread)))
        }
        val v = vm(FakeThreads { late }, FakeDirectory { flowOf(AppResult.success(listOf(contact))) })
        advanceTimeBy(10_001); settle()
        assertEquals(R.string.chat_inbox_error_load_failed, v.uiState.value.errorRes)
        advanceTimeBy(5_000); settle()
        assertNull(v.uiState.value.errorRes)
        assertEquals(1, v.uiState.value.threads.size)
    }

    @Test
    fun retryTidakMenggandakanKolektor() = kotlinx.coroutines.test.runTest(dispatcher) {
        val threads = FakeThreads { flowOf(AppResult.success(listOf(thread))) }
        val dir = FakeDirectory { flowOf(AppResult.success(listOf(contact))) }
        val v = vm(threads, dir)
        settle()
        repeat(3) { v.refresh(); settle() }
        assertEquals(4, threads.subscriptions.get())
        assertEquals(1, threads.active.get())
        assertEquals(1, dir.active.get())
    }
}
