package com.drivemate.showcase.feature.chat

import com.drivemate.showcase.core.model.ChatContact
import com.drivemate.showcase.core.model.ChatMessage
import com.drivemate.showcase.core.model.Role
import com.drivemate.showcase.core.model.User
import com.drivemate.showcase.core.model.UserSession
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.AuthRepository
import com.drivemate.showcase.domain.repository.ChatDirectoryRepository
import com.drivemate.showcase.domain.repository.ChatRepository
import com.drivemate.showcase.domain.repository.ChatThreadRepository
import com.drivemate.showcase.domain.usecase.chat.ObserveChatRoomUseCase
import com.drivemate.showcase.domain.usecase.chat.SendMessageUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private class ThreadFakeAuth(private val role: Role) : AuthRepository {
    override val userSessionFlow: Flow<UserSession?> = emptyFlow()
    override suspend fun login(email: String, password: String): AppResult<Unit> = AppResult.success(Unit)
    override suspend fun logout(): AppResult<Unit> = AppResult.success(Unit)
    override suspend fun sendReset(email: String): AppResult<Unit> = AppResult.success(Unit)
    override fun authState(): Flow<User?> = emptyFlow()
    override suspend fun currentUser(): AppResult<User?> =
        AppResult.success(User(uid = "me", email = "m@example.com", role = role))
}

private class ThreadFakeChat : ChatRepository {
    override fun observeMessages(partnerUid: String): Flow<AppResult<List<ChatMessage>>> =
        flowOf(AppResult.success(emptyList()))
    override suspend fun sendMessage(partnerUid: String, body: String) = AppResult.success(Unit)
}

private class ThreadFakeThreads : ChatThreadRepository {
    override fun observeThreads() = emptyFlow<AppResult<List<com.drivemate.showcase.core.model.ChatThreadSummary>>>()
    override suspend fun markRead(threadId: String, nowMillis: Long) = AppResult.success(Unit)
    override fun observePartnerReadAt(threadId: String, partnerUid: String): Flow<Long?> = emptyFlow()
    override fun observeHiddenIds(threadId: String): Flow<Set<String>> = emptyFlow()
    override suspend fun deleteForEveryone(threadId: String, messageId: String, nowMillis: Long) = AppResult.success(Unit)
    override suspend fun hideForMe(threadId: String, messageId: String) = AppResult.success(Unit)
}

private class ThreadFakeDirectory(private val contacts: AppResult<List<ChatContact>>) : ChatDirectoryRepository {
    override fun observeDirectory(): Flow<AppResult<List<ChatContact>>> = flowOf(contacts)
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatThreadViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun vm(role: Role, directory: AppResult<List<ChatContact>>) = ChatThreadViewModel(
        partnerUid = "p",
        partnerName = "Mitra",
        observeChatRoom = ObserveChatRoomUseCase(ThreadFakeChat()),
        sendMessage = SendMessageUseCase(ThreadFakeChat()),
        authRepository = ThreadFakeAuth(role),
        threadRepository = ThreadFakeThreads(),
        directoryRepository = ThreadFakeDirectory(directory)
    )

    @Test
    fun bahasaBacaDariPeranPengguna_bukanLocalePonsel() = runTest(dispatcher) {
        val expat = vm(Role.EXPAT, AppResult.success(emptyList()))
        val driver = vm(Role.DRIVER, AppResult.success(emptyList()))
        runCurrent()
        assertEquals("ja", expat.uiState.value.viewerLang)
        assertEquals("id", driver.uiState.value.viewerLang)
    }

    @Test
    fun bahasaLawanBicaraDariDirektori_langEksplisitAtauPeran() = runTest(dispatcher) {
        val byRole = vm(Role.DRIVER, AppResult.success(listOf(ChatContact("p", "Mitra", Role.EXPAT, null))))
        val byLang = vm(Role.DRIVER, AppResult.success(listOf(ChatContact("p", "Mitra", Role.EXPAT, null, lang = "id"))))
        runCurrent()
        assertEquals("ja", byRole.uiState.value.partnerLang)
        assertEquals("id", byLang.uiState.value.partnerLang)
    }

    @Test
    fun direktoriGagal_tidakMenjatuhkanThread_bahasaLawanBicaraKosong() = runTest(dispatcher) {
        val v = vm(Role.DRIVER, AppResult.failure(RuntimeException("denied")))
        runCurrent()
        assertNull(v.uiState.value.partnerLang)
        assertEquals(null, v.uiState.value.errorRes)
        assertEquals("id", v.uiState.value.viewerLang)
    }
}
