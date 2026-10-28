package com.drivemate.showcase.navigation

import com.drivemate.showcase.core.model.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Lima tab Expat; Notifikasi dan Profil bukan tab sehingga bar tersembunyi di sana. */
class ExpatNavRoutesTest {

    @Test
    fun `bar Expat punya tepat lima tab`() {
        assertEquals(
            setOf("expat_home", "expat_review_queue", "expat_drivers", "expat_chats", "expat_settings"),
            expatFloatingNavRoutes
        )
    }

    @Test
    fun `bar tampil di tab Pengaturan tetapi tidak di Notifikasi dan Profil`() {
        assertTrue(isFloatingNavRoute(Role.EXPAT, NavRoutes.ExpatSettings.route))
        assertFalse(isFloatingNavRoute(Role.EXPAT, NavRoutes.ExpatNotifications.route))
        assertFalse(isFloatingNavRoute(Role.EXPAT, NavRoutes.ExpatProfile.route))
    }

    @Test
    fun `rute baru tidak mengubah bar peran lain`() {
        assertFalse(isFloatingNavRoute(Role.DRIVER, NavRoutes.ExpatSettings.route))
        assertFalse(isFloatingNavRoute(Role.ADMIN, NavRoutes.ExpatSettings.route))
    }
}
