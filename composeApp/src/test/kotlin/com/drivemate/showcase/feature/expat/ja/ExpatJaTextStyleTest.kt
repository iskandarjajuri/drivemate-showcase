package com.drivemate.showcase.feature.expat.ja

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExpatJaTextStyleTest {
    private val sizes = listOf(12f, 14f, 16f, 18f, 22f)

    private fun base(size: Float, lineHeight: Float = size * 1.3f) = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = size.sp,
        lineHeight = lineHeight.sp
    )

    private fun ratio(style: TextStyle) = style.lineHeight.value / style.fontSize.value

    @Test
    fun isi_jepang_rasioMinimal1koma5_dan_lineBreakPhrase() {
        for (role in listOf(ExpatTextRole.BODY, ExpatTextRole.CAPTION)) {
            for (size in sizes) {
                val out = expatJaTextStyle(role, true, base(size))
                assertTrue(ratio(out) >= 1.5f - 0.0001f, "rasio $role $size = ${ratio(out)}")
                assertEquals(ExpatJaBodyLineBreak, out.lineBreak)
                assertEquals(base(size).fontSize, out.fontSize)
            }
        }
    }

    @Test
    fun isi_jepang_memakaiWordBreakPhrase_bukanPresetParagraph() {
        assertEquals(LineBreak.WordBreak.Phrase, ExpatJaBodyLineBreak.wordBreak)
        assertEquals(LineBreak.Strictness.Strict, ExpatJaBodyLineBreak.strictness)
        assertEquals(LineBreak.Strategy.HighQuality, ExpatJaBodyLineBreak.strategy)
        assertFalse(ExpatJaBodyLineBreak == LineBreak.Paragraph)
    }

    @Test
    fun judul_jepang_rasioMinimal1koma4_dan_lineBreakHeading() {
        for (size in sizes) {
            val out = expatJaTextStyle(ExpatTextRole.TITLE, true, base(size))
            assertTrue(ratio(out) >= 1.4f - 0.0001f, "rasio judul $size = ${ratio(out)}")
            assertEquals(LineBreak.Heading, out.lineBreak)
        }
    }

    @Test
    fun tombol_jepang_lineBreakHeading_tinggiBarisTakDiubah() {
        val input = base(14f, 20f)
        val out = expatJaTextStyle(ExpatTextRole.BUTTON, true, input)
        assertEquals(LineBreak.Heading, out.lineBreak)
        assertEquals(input.lineHeight, out.lineHeight)
        assertNull(expatJaLineHeightRatio(ExpatTextRole.BUTTON))
    }

    @Test
    fun bukanJepang_semuaPeran_mengembalikanGayaMasukanTanpaUbah() {
        for (role in ExpatTextRole.entries) {
            for (size in sizes) {
                val input = base(size)
                assertEquals(input, expatJaTextStyle(role, false, input))
            }
        }
    }

    @Test
    fun jepang_gayaTanpaUkuranSp_tinggiBarisTetap() {
        val input = TextStyle(fontWeight = FontWeight.SemiBold)
        val out = expatJaTextStyle(ExpatTextRole.BODY, true, input)
        assertEquals(input.lineHeight, out.lineHeight)
        assertEquals(ExpatJaBodyLineBreak, out.lineBreak)
    }

    @Test
    fun konstantaRasio_memenuhiBatasDads() {
        assertTrue(EXPAT_JA_BODY_LINE_HEIGHT_RATIO >= 1.5f)
        assertTrue(EXPAT_JA_CAPTION_LINE_HEIGHT_RATIO >= 1.5f)
        assertTrue(EXPAT_JA_TITLE_LINE_HEIGHT_RATIO >= 1.4f)
    }

    @Test
    fun deteksiBahasaJepang() {
        assertTrue(isJapaneseLanguage("ja"))
        assertTrue(isJapaneseLanguage("ja-JP"))
        assertFalse(isJapaneseLanguage("en"))
        assertFalse(isJapaneseLanguage("id"))
        assertFalse(isJapaneseLanguage("jav"))
        assertFalse(isJapaneseLanguage(""))
        assertFalse(isJapaneseLanguage(null))
    }
}
