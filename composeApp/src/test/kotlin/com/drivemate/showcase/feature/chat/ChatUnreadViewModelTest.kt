package com.drivemate.showcase.feature.chat

import com.drivemate.showcase.core.model.ChatThreadSummary
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.ChatThreadRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class UnreadFakeThreads(val source: MutableSharedFlow<AppResult<List<ChatThreadSummary>>>) : ChatThreadRepository {
    override fun observeThreads(): Flow<AppResult<List<ChatThreadSummary>>> = source
    override suspend fun markRead(threadId: String, nowMillis: Long) = AppResult.success(Unit)
    override fun observePartnerReadAt(threadId: String, partnerUid: String): Flow<Long?> = emptyFlow()
    override fun observeHiddenIds(threadId: String): Flow<Set<String>> = emptyFlow()
    override suspend fun deleteForEveryone(threadId: String, messageId: String, nowMillis: Long) = AppResult.success(Unit)
    override suspend fun hideForMe(threadId: String, messageId: String) = AppResult.success(Unit)
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatUnreadViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun th(id: String, unread: Int) = ChatThreadSummary(id, "p$id", "x", false, "p$id", 1L, unread)

    @Test
    fun awalNol_lalu_naikTurunMengikutiThread_realtime() = runTest(dispatcher) {
        val source = MutableSharedFlow<AppResult<List<ChatThreadSummary>>>(replay = 1)
        val vm = ChatUnreadViewModel(UnreadFakeThreads(source))
        runCurrent()
        assertEquals(0, vm.unread.value)
        source.emit(AppResult.success(listOf(th("a", 1))))
        runCurrent()
        assertEquals(1, vm.unread.value)
        source.emit(AppResult.success(listOf(th("a", 1), th("b", 2))))
        runCurrent()
        assertEquals(3, vm.unread.value)
        // thread dibuka -> penanda baca -> server menolkan unread
        source.emit(AppResult.success(listOf(th("a", 0), th("b", 2))))
        runCurrent()
        assertEquals(2, vm.unread.value)
    }

    @Test
    fun galatSumber_mempertahankanAngkaTerakhir_bukanNol() = runTest(dispatcher) {
        val source = MutableSharedFlow<AppResult<List<ChatThreadSummary>>>(replay = 1)
        val vm = ChatUnreadViewModel(UnreadFakeThreads(source))
        source.emit(AppResult.success(listOf(th("a", 4))))
        runCurrent()
        source.emit(AppResult.failure(RuntimeException("offline")))
        runCurrent()
        assertEquals(4, vm.unread.value)
    }
}
