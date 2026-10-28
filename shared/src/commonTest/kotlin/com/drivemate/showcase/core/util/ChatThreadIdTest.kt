package com.drivemate.showcase.core.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ChatThreadIdTest {

    @Test
    fun `buildOneToOneChatThreadId sorts participant ids`() {
        val result = buildOneToOneChatThreadId("driver-2", "admin-1")
        assertEquals("admin-1__driver-2", result)
    }

    @Test
    fun `buildOneToOneChatThreadId rejects duplicate participant`() {
        assertFailsWith<IllegalArgumentException> {
            buildOneToOneChatThreadId("admin-1", "admin-1")
        }
    }
}
