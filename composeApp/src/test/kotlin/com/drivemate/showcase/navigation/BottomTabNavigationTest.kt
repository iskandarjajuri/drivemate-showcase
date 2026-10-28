package com.drivemate.showcase.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class BottomTabNavigationTest {
    private val home = "driver_home"

    @Test
    fun homeDariHistory_popKeHome_bukanRestore() =
        assertEquals(TabNavigationAction.PopToHome, tabNavigationAction("driver_history", home, home))

    @Test
    fun homeDariLayarMana_pun_popKeHome() =
        assertEquals(TabNavigationAction.PopToHome, tabNavigationAction("driver_attendance", home, home))

    @Test
    fun ketukTabAktif_tidakAdaAksi() {
        assertEquals(TabNavigationAction.None, tabNavigationAction(home, home, home))
        assertEquals(TabNavigationAction.None, tabNavigationAction("driver_history", "driver_history", home))
    }

    @Test
    fun tabLain_tetapRestore() =
        assertEquals(TabNavigationAction.NavigateWithRestore, tabNavigationAction(home, "driver_history", home))
}
