package com.drivemate.showcase

import com.drivemate.showcase.core.model.ChatMessage
import com.drivemate.showcase.core.model.TranslationDto
import com.drivemate.showcase.core.util.parseChatTranslation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChatTranslationParserTest {
    @Test
    fun dokumenLama_tanpaFieldBaru_semuaNull() {
        val p = parseChatTranslation(mapOf("body" to "halo", "createdAtMillis" to 1L))
        assertNull(p.srcLang); assertNull(p.register); assertNull(p.translations); assertNull(p.translationStatus)
    }

    @Test
    fun dokumenTerjemahanLengkap_terbaca() {
        val p = parseChatTranslation(
            mapOf(
                "srcLang" to "ja", "register" to "polite", "translationStatus" to "done",
                "translations" to mapOf("id" to mapOf("text" to "Besok berangkat jam 6 ya.", "uncertain" to true))
            )
        )
        assertEquals("ja", p.srcLang)
        assertEquals("polite", p.register)
        assertEquals("done", p.translationStatus)
        assertEquals(mapOf("id" to TranslationDto("Besok berangkat jam 6 ya.", true)), p.translations)
    }

    @Test
    fun nilaiTidakSah_diabaikanTanpaMelempar() {
        val p = parseChatTranslation(
            mapOf(
                "srcLang" to "fr", "register" to 5, "translationStatus" to "weird",
                "translations" to mapOf("en" to mapOf("text" to "x"), "id" to mapOf("text" to " "), "ja" to "bukan map")
            )
        )
        assertNull(p.srcLang); assertNull(p.register); assertNull(p.translationStatus); assertNull(p.translations)
    }

    @Test
    fun uncertainTidakAda_defaultFalse() {
        val p = parseChatTranslation(mapOf("translations" to mapOf("ja" to mapOf("text" to "こんにちは"))))
        assertEquals(false, p.translations?.get("ja")?.uncertain)
    }

    @Test
    fun chatMessage_konstruktorLamaTetapValid() {
        val m = ChatMessage("1", "t", "u", null, "hai", 1L)
        assertNull(m.translations); assertNull(m.translationStatus)
    }
}
