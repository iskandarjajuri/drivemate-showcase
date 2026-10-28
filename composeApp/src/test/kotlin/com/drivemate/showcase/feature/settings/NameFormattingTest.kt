package com.drivemate.showcase.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class NameFormattingTest {

    @Test
    fun toDisplayTitleCase_capitalizesEachLowercaseWord() {
        assertEquals("Iskandar Jajuri", "iskandar jajuri".toDisplayTitleCase())
    }

    @Test
    fun toDisplayTitleCase_leavesAlreadyCapitalizedNameUnchanged() {
        assertEquals("Iskandar Jajuri", "Iskandar Jajuri".toDisplayTitleCase())
    }

    @Test
    fun toDisplayTitleCase_leavesAllUppercaseWordUnchanged() {
        // Treated as an acronym/intentional casing, not a name to title-case.
        assertEquals("DRIVER01", "DRIVER01".toDisplayTitleCase())
    }

    @Test
    fun toDisplayTitleCase_leavesMixedCaseWordUnchanged() {
        assertEquals("mcDonald", "mcDonald".toDisplayTitleCase())
    }

    @Test
    fun toDisplayTitleCase_handlesEmptyString() {
        assertEquals("", "".toDisplayTitleCase())
    }
}
