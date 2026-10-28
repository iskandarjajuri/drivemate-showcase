package com.drivemate.showcase.navigation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FloatingNavBarStateTest {
    private fun state() = FloatingNavBarState(collapseThreshold = 24f, expandThreshold = 8f)

    @Test
    fun awalnyaExpanded() = assertFalse(state().collapsed)

    @Test
    fun gulirBawahDiAtasAmbang_collapsed() {
        val s = state()
        s.onScroll(20f)
        assertFalse(s.collapsed)
        s.onScroll(5f)
        assertTrue(s.collapsed)
    }

    @Test
    fun tepatDiAmbang_belumCollapsed() {
        val s = state()
        s.onScroll(24f)
        assertFalse(s.collapsed)
    }

    @Test
    fun gulirAtasDiAtasAmbang_expanded() {
        val s = state()
        s.onScroll(30f)
        s.onScroll(-8f)
        assertTrue(s.collapsed)
        s.onScroll(-1f)
        assertFalse(s.collapsed)
    }

    @Test
    fun pembalikanArah_memulaiAkumulasiBaru() {
        val s = state()
        s.onScroll(20f)
        s.onScroll(-3f)
        s.onScroll(10f)
        assertFalse(s.collapsed)
    }

    @Test
    fun resetKembaliExpanded() {
        val s = state()
        s.onScroll(100f)
        s.reset()
        assertFalse(s.collapsed)
        s.onScroll(10f)
        assertFalse(s.collapsed)
    }
}
