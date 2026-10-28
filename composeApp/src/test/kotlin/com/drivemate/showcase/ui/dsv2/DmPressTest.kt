package com.drivemate.showcase.ui.dsv2

import org.junit.Assert.assertEquals
import org.junit.Test

class DmPressTest {

    @Test
    fun `pressed mengikuti angka tiap kind`() {
        assertEquals(0.92f, pressScaleTarget(pressed = true, reduceMotion = false, kind = DmPressKind.Icon), 0.0001f)
        assertEquals(0.90f, pressScaleTarget(pressed = true, reduceMotion = false, kind = DmPressKind.Tab), 0.0001f)
        assertEquals(0.96f, pressScaleTarget(pressed = true, reduceMotion = false, kind = DmPressKind.Meaningful), 0.0001f)
    }

    @Test
    fun `tidak ditekan selalu skala penuh`() {
        DmPressKind.entries.forEach { kind ->
            assertEquals(1f, pressScaleTarget(pressed = false, reduceMotion = false, kind = kind), 0.0001f)
            assertEquals(1f, pressScaleTarget(pressed = false, reduceMotion = true, kind = kind), 0.0001f)
        }
    }

    @Test
    fun `reduce motion menonaktifkan skala untuk semua kind`() {
        DmPressKind.entries.forEach { kind ->
            assertEquals(1f, pressScaleTarget(pressed = true, reduceMotion = true, kind = kind), 0.0001f)
        }
    }
}
