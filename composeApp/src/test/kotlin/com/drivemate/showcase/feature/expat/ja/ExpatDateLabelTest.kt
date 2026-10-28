package com.drivemate.showcase.feature.expat.ja

import java.time.LocalDate
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class ExpatDateLabelTest {
    private val fallbackDipanggil = mutableListOf<Triple<Int, String, String>>()
    private val fallback: (Int, Locale, String) -> String = { day, loc, skeleton ->
        fallbackDipanggil += Triple(day, loc.language, skeleton)
        "FB:$day:${loc.language}:$skeleton"
    }

    private fun epoch(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay().toInt()

    @Test
    fun jepang_singkat_bulanHariDanHariDalamPekan() {
        assertEquals("10月27日(火)", expatDateLabel(epoch(2026, 10, 27), Locale.JAPAN, withYear = false, formatter = fallback))
        assertEquals("10月17日(土)", expatDateLabel(epoch(2026, 10, 17), Locale.JAPAN, withYear = false, formatter = fallback))
        assertEquals("1月1日(木)", expatDateLabel(epoch(2026, 1, 1), Locale.JAPAN, withYear = false, formatter = fallback))
    }

    @Test
    fun jepang_denganTahun() {
        assertEquals("2026年10月27日(火)", expatDateLabel(epoch(2026, 10, 27), Locale.JAPAN, withYear = true, formatter = fallback))
    }

    @Test
    fun jepang_lokalTanpaNegara_tetapJepang() {
        assertEquals("10月27日(火)", expatDateLabel(epoch(2026, 10, 27), Locale.forLanguageTag("ja"), withYear = false, formatter = fallback))
    }

    @Test
    fun jepang_tidakMemanggilFallback() {
        expatDateLabel(epoch(2026, 10, 27), Locale.JAPAN, withYear = true, formatter = fallback)
        assertEquals(emptyList(), fallbackDipanggil)
    }

    @Test
    fun bukanJepang_memintaHariDalamPekanLewatSkeleton() {
        val day = epoch(2026, 10, 27)
        val indonesia = Locale.forLanguageTag("id-ID") // kode bahasa "id" (JDK modern) atau "in" (Android lama)
        assertEquals("FB:$day:${indonesia.language}:MMMEd", expatDateLabel(day, indonesia, withYear = false, formatter = fallback))
        assertEquals("FB:$day:en:yMMMEd", expatDateLabel(day, Locale.US, withYear = true, formatter = fallback))
        assertEquals(
            listOf(Triple(day, indonesia.language, "MMMEd"), Triple(day, "en", "yMMMEd")),
            fallbackDipanggil
        )
    }

    @Test
    fun bukanJepang_flagTahunMengubahSkeleton() {
        val day = epoch(2026, 10, 27)
        assertEquals("FB:$day:en:MMMEd", expatDateLabel(day, Locale.US, withYear = false, formatter = fallback))
        assertEquals("FB:$day:en:yMMMEd", expatDateLabel(day, Locale.US, withYear = true, formatter = fallback))
    }
}
