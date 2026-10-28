package com.drivemate.showcase.feature.chat

import com.drivemate.showcase.core.model.ChatMessage
import com.drivemate.showcase.core.model.Role
import com.drivemate.showcase.core.model.TranslationDto
import com.drivemate.showcase.core.util.effectiveChatLang
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatDisplayTest {
    private val now = 1_000_000L

    private fun msg(
        at: Long = now - 60_000,
        src: String? = null,
        status: String? = null,
        tr: Map<String, TranslationDto>? = null,
    ) = ChatMessage("m", "t", "u", null, "asli", at, srcLang = src, register = "polite", translations = tr, translationStatus = status)

    private val doneJaToId = msg(src = "ja", status = "done", tr = mapOf("id" to TranslationDto("terjemahan", false)))

    @Test
    fun masukBahasaBerbeda_tampilTerjemahanDenganChip() {
        val d = chooseDisplayText("id", isMine = false, message = doneJaToId, nowMillis = now, featureActive = true)
        assertEquals("terjemahan", d.primary)
        assertEquals("asli", d.original)
        assertEquals("ja", d.fromLang)
        assertEquals("polite", d.register)
        assertTrue(d.translated)
    }

    @Test
    fun uncertain_terbawa() {
        val m = msg(src = "ja", status = "done", tr = mapOf("id" to TranslationDto("x", true)))
        assertTrue(chooseDisplayText("id", false, m, now, true).uncertain)
    }

    @Test
    fun bahasaBacaSamaDenganAsal_tampilAsli() {
        val d = chooseDisplayText("ja", false, doneJaToId, now, true)
        assertEquals("asli", d.primary)
        assertFalse(d.translated)
    }

    @Test
    fun bahasaBacaInggris_tampilAsli() {
        val d = chooseDisplayText("en", false, doneJaToId, now, true)
        assertEquals("asli", d.primary)
        assertFalse(d.translated)
    }

    @Test
    fun terjemahanUntukBahasaBacaTidakAda_tampilAsli() {
        val d = chooseDisplayText("ja", false, msg(src = "id", status = "done", tr = mapOf("id" to TranslationDto("x"))), now, true)
        assertFalse(d.translated)
    }

    @Test
    fun pesanSendiri_asliDenganLabelBahasaSeberang() {
        val d = chooseDisplayText("id", isMine = true, message = doneJaToId.copy(srcLang = "id", translations = mapOf("ja" to TranslationDto("日本語"))), nowMillis = now, featureActive = true)
        assertEquals("asli", d.primary)
        assertEquals("ja", d.sentLang)
    }

    @Test
    fun pesanSendiri_tanpaTerjemahan_tanpaLabel() {
        val d = chooseDisplayText("id", true, msg(), now, true)
        assertNull(d.sentLang)
    }

    @Test
    fun baruMasukTanpaStatus_fiturAktif_pending() {
        val d = chooseDisplayText("id", false, msg(at = now - 3_000), now, featureActive = true)
        assertEquals(TranslationUiState.Pending, d.state)
        assertEquals("asli", d.primary)
    }

    @Test
    fun baruMasukTanpaStatus_fiturTidakAktif_biasa() {
        assertEquals(TranslationUiState.None, chooseDisplayText("id", false, msg(at = now - 3_000), now, false).state)
    }

    @Test
    fun lewat20Detik_tanpaStatus_tidakPending() {
        assertEquals(TranslationUiState.None, chooseDisplayText("id", false, msg(at = now - 20_000), now, true).state)
        assertEquals(TranslationUiState.Pending, chooseDisplayText("id", false, msg(at = now - 19_999), now, true).state)
    }

    @Test
    fun petunjukComposer_hanyaBilaBahasaLawanBicaraBeda() {
        assertEquals("ja", translationTargetHint(viewerLang = "id", partnerLang = "ja"))
        assertEquals("id", translationTargetHint(viewerLang = "ja", partnerLang = "id"))
        assertNull(translationTargetHint(viewerLang = "id", partnerLang = "id"))
        assertNull(translationTargetHint(viewerLang = "id", partnerLang = null))
        assertNull(translationTargetHint(viewerLang = null, partnerLang = "ja"))
    }

    @Test
    fun terjemahanTampilTanpaMelihatLocalePonsel_selamaBahasaEfektifSesuai() {
        // Bahasa baca diturunkan dari peran (expat -> ja), bukan locale; locale en-US tidak relevan lagi.
        val viewer = effectiveChatLang(null, Role.EXPAT)
        val m = msg(src = "id", status = "done", tr = mapOf("ja" to TranslationDto("日本語")))
        val d = chooseDisplayText(viewer.orEmpty(), isMine = false, message = m, nowMillis = now, featureActive = true)
        assertEquals("日本語", d.primary)
        assertEquals("asli", d.original)
    }

    @Test
    fun failed_tampilAsliDenganStatusGagal() {
        val d = chooseDisplayText("id", false, msg(status = "failed"), now, true)
        assertEquals(TranslationUiState.Failed, d.state)
        assertEquals("asli", d.primary)
    }

    @Test
    fun skipped_danBahasaLain_tampilBiasa() {
        assertEquals(TranslationUiState.None, chooseDisplayText("id", false, msg(src = "other", status = "skipped"), now, true).state)
    }

    @Test
    fun penghitungKarakter_danBahasaTulis() {
        assertFalse(showCharCounter(899)); assertTrue(showCharCounter(900))
        assertEquals("ja", composerLanguage("ja")); assertNull(composerLanguage("en"))
    }
}
