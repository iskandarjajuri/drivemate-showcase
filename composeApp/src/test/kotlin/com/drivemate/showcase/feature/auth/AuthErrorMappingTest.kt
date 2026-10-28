package com.drivemate.showcase.feature.auth

import kotlin.test.Test
import kotlin.test.assertEquals

/** Login failures are classified from the exception message, so the real messages are pinned here. */
class AuthErrorMappingTest {

    private fun reasonOf(message: String?) = mapAuthError(RuntimeException(message)).reason

    @Test
    fun `wrong password message from the SDK and the Auth emulator is invalid credentials`() {
        assertEquals(
            AuthErrorReason.InvalidCredentials,
            reasonOf("The password is invalid or the user does not have a password.")
        )
    }

    @Test
    fun `current production message is invalid credentials`() {
        assertEquals(
            AuthErrorReason.InvalidCredentials,
            reasonOf("The supplied auth credential is incorrect, malformed or has expired.")
        )
    }

    @Test
    fun `network problems are reported as offline`() {
        assertEquals(AuthErrorReason.Offline, reasonOf("A network error has occurred"))
    }

    @Test
    fun `unrecognised messages fall back to unknown`() {
        assertEquals(AuthErrorReason.Unknown, reasonOf("something unexpected"))
    }
}
